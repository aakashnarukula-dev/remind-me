package com.gurthuchey.admin;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

final class AdminFirebase {
    interface Listener { void onStatus(String status, boolean ready); }
    interface DeviceListener { void onDevices(List<PhoneDevice> devices); }
    interface MemberListener { void onMembers(List<Models.Member> members); }
    interface PairingCodeCallback { void complete(boolean success, String code, String message); }
    interface DeleteCallback { void complete(boolean success); }

    static final class PhoneDevice {
        String uid;
        String model;
        String memberId;
        String status;
        String appVersion;
        String deviceKey;
        long updatedAt;

        boolean isWaiting() { return memberId == null || memberId.isEmpty(); }
    }

    private static final String PREFS = "maathra_firebase_admin";
    private static final String KEY_FAMILY_ID = "family_id";
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final SharedPreferences preferences;
    private String familyId;
    private Listener listener;
    private boolean ready;
    private boolean initialPublishComplete;
    private ListenerRegistration devicesRegistration;
    private ListenerRegistration membersRegistration;

    AdminFirebase(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        familyId = preferences.getString(KEY_FAMILY_ID, null);
    }

    void start(List<Models.Member> members, Listener listener) {
        this.listener = listener;
        report("Connecting to Firebase…", false);
        if (auth.getCurrentUser() == null || auth.getCurrentUser().isAnonymous()) {
            report("Admin sign-in required", false);
            return;
        }
        resolveFamily();
    }

    void publishAll(List<Models.Member> members) {
        if (!ready) return;
        if (members.isEmpty()) {
            initialPublishComplete = true;
            syncPhoneAssignments(members);
            return;
        }
        report("Publishing changes…", initialPublishComplete);
        WriteBatch batch = firestore.batch();
        for (Models.Member member : members) {
            batch.set(memberReference(member.id), memberMap(member), SetOptions.merge());
        }
        // One atomic snapshot prevents the realtime listener from replacing a complete
        // local family with only the first of several concurrently-uploaded people.
        batch.commit()
                .addOnSuccessListener(unused -> {
                    initialPublishComplete = true;
                    syncPhoneAssignments(members);
                })
                .addOnFailureListener(error -> report("Sync pending — will retry when online",
                        initialPublishComplete));
    }

    void publishMember(Models.Member member) {
        if (!ready) return;
        report("Publishing changes…", true);
        memberReference(member.id).set(memberMap(member), SetOptions.merge())
                .addOnSuccessListener(unused -> report("Firebase synced", true))
                .addOnFailureListener(error -> report("Sync pending — will retry when online", true));
    }

    void deleteMember(String memberId, DeleteCallback callback) {
        if (!ready) {
            callback.complete(false);
            return;
        }
        report("Removing member…", true);
        firestore.collection("families").document(familyId).collection("devices")
                .whereEqualTo("memberId", memberId).get()
                .addOnSuccessListener(devices -> {
                    WriteBatch batch = firestore.batch();
                    batch.delete(memberReference(memberId));
                    for (DocumentSnapshot device : devices.getDocuments()) {
                        batch.delete(device.getReference());
                    }
                    batch.commit()
                            .addOnSuccessListener(unused -> {
                                report("Firebase synced", true);
                                callback.complete(true);
                            })
                            .addOnFailureListener(error -> {
                                report("Delete pending — check connection", true);
                                callback.complete(false);
                            });
                })
                .addOnFailureListener(error -> {
                    report("Delete pending — check connection", true);
                    callback.complete(false);
                });
    }

    void createPairingCode(Models.Member member, PairingCodeCallback callback) {
        if (!ready || auth.getCurrentUser() == null) {
            callback.complete(false, "", "Firebase is still connecting");
            return;
        }
        // Reading first avoids rewriting an existing member and incrementing its
        // schedule revision merely because the admin requested a reconnection code.
        DocumentReference reference = memberReference(member.id);
        reference.get(Source.SERVER).addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                createPairingCode(member, callback, 0);
                return;
            }
            // A newly-created member must exist remotely before issuing their code.
            reference.set(memberMap(member), SetOptions.merge())
                    .addOnSuccessListener(unused -> createPairingCode(member, callback, 0))
                    .addOnFailureListener(error -> callback.complete(false, "",
                            "Could not create the code. Check the internet and try again."));
        }).addOnFailureListener(error -> callback.complete(false, "",
                "Could not create the code. Check the internet and try again."));
    }

    private void createPairingCode(Models.Member member, PairingCodeCallback callback, int attempt) {
        String code = String.format(Locale.US, "%06d", new SecureRandom().nextInt(1_000_000));
        Map<String, Object> value = new HashMap<>();
        value.put("familyId", familyId);
        value.put("memberId", member.id);
        value.put("memberName", member.name);
        value.put("ownerUid", auth.getCurrentUser() == null ? "" : auth.getCurrentUser().getUid());
        value.put("usedByUid", "");
        value.put("createdAt", FieldValue.serverTimestamp());
        value.put("expiresAt", new java.util.Date(System.currentTimeMillis() + 10 * 60 * 1000L));
        firestore.collection("pairingCodes").document(code).set(value)
                .addOnSuccessListener(unused -> callback.complete(true, code,
                        "Code ready — it expires in 10 minutes and works once"))
                .addOnFailureListener(error -> {
                    if (attempt < 2) createPairingCode(member, callback, attempt + 1);
                    else callback.complete(false, "", "Could not create the code. Check the internet and try again.");
                });
    }

    boolean isReady() { return ready; }

    void listenToDevices(DeviceListener deviceListener) {
        if (!ready || devicesRegistration != null) return;
        devicesRegistration = firestore.collection("families").document(familyId).collection("devices")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) return;
                    List<PhoneDevice> devices = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        PhoneDevice device = new PhoneDevice();
                        device.uid = document.getId();
                        device.model = text(document.get("model"), "Android phone");
                        device.memberId = text(document.get("memberId"), "");
                        device.status = text(document.get("status"), device.memberId.isEmpty() ? "pending" : "assigned");
                        device.appVersion = text(document.get("appVersion"), "");
                        device.deviceKey = text(document.get("deviceKey"), "");
                        if (document.getTimestamp("updatedAt") != null) {
                            device.updatedAt = document.getTimestamp("updatedAt").toDate().getTime();
                        }
                        devices.add(device);
                    }
                    devices.sort((left, right) -> {
                        if (left.isWaiting() != right.isWaiting()) return left.isWaiting() ? -1 : 1;
                        return Long.compare(right.updatedAt, left.updatedAt);
                    });
                    deviceListener.onDevices(devices);
                });
    }

    void deleteDevice(String deviceUid) {
        deleteDevice(deviceUid, success -> {});
    }

    void deleteDevice(String deviceUid, DeleteCallback callback) {
        if (!ready || deviceUid == null || deviceUid.isEmpty()) {
            callback.complete(false);
            return;
        }
        report("Removing phone…", true);
        firestore.collection("families").document(familyId).collection("devices")
                .document(deviceUid).delete()
                .addOnSuccessListener(unused -> {
                    report("Firebase synced", true);
                    callback.complete(true);
                })
                .addOnFailureListener(error -> {
                    report("Could not remove phone — check connection", true);
                    callback.complete(false);
                });
    }

    void listenToMembers(MemberListener memberListener) {
        if (!ready || membersRegistration != null) return;
        membersRegistration = firestore.collection("families").document(familyId).collection("members")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) return;
                    List<Models.Member> remoteMembers = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        try {
                            remoteMembers.add(Models.Member.fromRemote(document.getId(), document.getData()));
                        } catch (RuntimeException ignored) {}
                    }
                    remoteMembers.sort((left, right) -> left.name.compareToIgnoreCase(right.name));
                    memberListener.onMembers(remoteMembers);
                });
    }

    private void resolveFamily() {
        firestore.collection("adminConfig").document("primary").get()
                .addOnSuccessListener(document -> {
                    String configuredFamily = text(document.get("familyId"), "");
                    if (configuredFamily.isEmpty()) {
                        report("Admin family is not configured", false);
                        return;
                    }
                    familyId = configuredFamily;
                    preferences.edit().putString(KEY_FAMILY_ID, familyId).apply();
                    ready = true;
                    initialPublishComplete = true;
                    report("Firebase connected", true);
                })
                .addOnFailureListener(error -> {
                    // A previously authenticated installation can still open its
                    // cached Firestore snapshot while temporarily offline.
                    if (familyId != null && !familyId.isEmpty()) {
                        ready = true;
                        initialPublishComplete = true;
                        report("Offline — showing saved family", true);
                    } else {
                        report("Connect to the internet to restore the family", false);
                    }
                });
    }

    private DocumentReference memberReference(String memberId) {
        return firestore.collection("families").document(familyId).collection("members").document(memberId);
    }

    private Map<String, Object> memberMap(Models.Member member) {
        Map<String, Object> value = new HashMap<>();
        value.put("id", member.id);
        value.put("name", member.name);
        value.put("phoneE164", member.phoneE164 == null ? "" : member.phoneE164);
        value.put("relation", member.relation);
        value.put("language", AppLanguage.normalize(member.language));
        // A server-side increment cannot collide when two edits are saved within the
        // same millisecond, unlike a phone-clock timestamp.
        value.put("revision", FieldValue.increment(1L));
        value.put("updatedAt", FieldValue.serverTimestamp());
        List<Map<String, Object>> schedules = new ArrayList<>();
        for (Models.Schedule item : member.schedules) {
            Map<String, Object> schedule = new HashMap<>();
            schedule.put("id", item.id);
            schedule.put("label", item.label);
            schedule.put("category", item.category);
            schedule.put("language", AppLanguage.normalize(item.language));
            schedule.put("hour", item.hour);
            schedule.put("minute", item.minute);
            schedule.put("days", item.days);
            schedule.put("preMinutes", item.preMinutes);
            schedule.put("confirmationMinutes", item.confirmationMinutes);
            schedule.put("medicineKey", item.medicineKey);
            schedule.put("enabled", item.enabled);
            List<Map<String, Object>> questions = new ArrayList<>();
            for (Models.ScriptQuestion itemQuestion : item.questions) {
                Map<String, Object> question = new HashMap<>();
                question.put("prompt", itemQuestion.prompt);
                List<Map<String, Object>> answers = new ArrayList<>();
                for (Models.ScriptAnswer itemAnswer : itemQuestion.answers) {
                    Map<String, Object> answer = new HashMap<>();
                    answer.put("label", itemAnswer.label);
                    answer.put("response", itemAnswer.response);
                    answers.add(answer);
                }
                question.put("answers", answers);
                questions.add(question);
            }
            schedule.put("questions", questions);
            schedules.add(schedule);
        }
        value.put("schedules", schedules);
        return value;
    }

    private void syncPhoneAssignments(List<Models.Member> members) {
        if (!ready || auth.getCurrentUser() == null) return;
        firestore.collection("phoneAssignments").whereEqualTo("familyId", familyId).get()
                .addOnSuccessListener(existing -> {
                    WriteBatch batch = firestore.batch();
                    for (DocumentSnapshot document : existing.getDocuments()) {
                        batch.delete(document.getReference());
                    }
                    for (Models.Member member : members) {
                        String phone = member.phoneE164 == null ? "" : member.phoneE164.trim();
                        if (phone.isEmpty()) continue;
                        Map<String, Object> value = new HashMap<>();
                        value.put("familyId", familyId);
                        value.put("memberId", member.id);
                        value.put("phoneE164", phone);
                        value.put("ownerUid", auth.getCurrentUser().getUid());
                        value.put("updatedAt", FieldValue.serverTimestamp());
                        batch.set(firestore.collection("phoneAssignments").document(sha256(phone)), value);
                    }
                    batch.commit()
                            .addOnSuccessListener(unused -> report("Firebase synced", true))
                            .addOnFailureListener(error -> report(
                                    "Reminders saved; phone login sync is pending", true));
                })
                .addOnFailureListener(error -> report(
                        "Reminders saved; phone login sync is pending", true));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            for (byte item : digest) output.append(String.format(Locale.US, "%02x", item & 0xff));
            return output.toString();
        } catch (Exception impossible) {
            return "";
        }
    }

    private void report(String status, boolean connected) {
        if (listener != null) listener.onStatus(status, connected);
    }

    void stop() {
        if (devicesRegistration != null) devicesRegistration.remove();
        if (membersRegistration != null) membersRegistration.remove();
        devicesRegistration = null;
        membersRegistration = null;
        listener = null;
    }

    private static String text(Object value, String fallback) {
        return value instanceof String ? (String) value : fallback;
    }
}
