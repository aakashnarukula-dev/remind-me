package com.gurthuchey.remindercall;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.messaging.FirebaseMessaging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class RemoteSync {
    interface Callback { void complete(boolean success, String message); }
    private static final Object APPLY_LOCK = new Object();

    private final Context context;
    private final RemoteStore store;
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private ListenerRegistration memberListener;
    private ListenerRegistration deviceListener;

    RemoteSync(Context context) {
        this.context = context.getApplicationContext();
        this.store = new RemoteStore(this.context);
    }

    boolean isPaired() { return store.pairing() != null; }

    void begin(Callback callback) {
        if (!isPaired()) {
            callback.complete(false, "Sign in to restore your reminders");
            return;
        }
        ScheduleSyncJob.scheduleCatchUp(context);
        ensureUser(user -> {
            updateDevice(user);
            listen(callback);
        }, () -> callback.complete(false, "Sign in to sync schedule changes"));
    }

    void restoreForSignedInPhone(Callback callback) {
        FirebaseUser user = auth.getCurrentUser();
        String phone = user == null ? "" : user.getPhoneNumber();
        if (user == null || phone == null || phone.isEmpty()) {
            callback.complete(false, "Phone sign-in is required");
            return;
        }
        String assignmentId = sha256(phone);
        firestore.collection("phoneAssignments").document(assignmentId).get(Source.SERVER)
                .addOnSuccessListener(snapshot -> {
                    String family = snapshot.getString("familyId");
                    String memberId = snapshot.getString("memberId");
                    if (!snapshot.exists() || family == null || family.isEmpty()
                            || memberId == null || memberId.isEmpty()) {
                        callback.complete(false,
                                "This number is not assigned yet. Ask Admin to add it to your member.");
                        return;
                    }
                    FirebaseMessaging.getInstance().getToken().addOnCompleteListener(tokenTask -> {
                        Map<String, Object> phoneDocument = deviceDetails(
                                tokenTask.isSuccessful() ? tokenTask.getResult() : "");
                        phoneDocument.put("memberId", memberId);
                        phoneDocument.put("status", "assigned");
                        phoneDocument.put("assignmentId", assignmentId);
                        phoneDocument.put("phoneE164", phone);
                        phoneDocument.put("createdAt", FieldValue.serverTimestamp());
                        device(family, user.getUid()).set(phoneDocument, SetOptions.merge())
                                .addOnSuccessListener(unused -> {
                                    store.savePairing(family, memberId);
                                    syncNow((success, message) -> {
                                        stop();
                                        // Keep listening even while Cloud TTS is still preparing.
                                        // The current offline schedule remains active until every clip for
                                        // the new revision is available.
                                        listen(callback);
                                        callback.complete(success, message);
                                    });
                                })
                                .addOnFailureListener(error -> callback.complete(false,
                                        "Could not restore reminders. Check the connection and try again."));
                    });
                })
                .addOnFailureListener(error -> callback.complete(false,
                        "Could not find this member. Check the connection and try again."));
    }

    void pair(String rawCode, Callback callback) {
        String code = rawCode == null ? "" : rawCode.replaceAll("[^0-9]", "");
        if (code.length() != 6) {
            callback.complete(false, "Enter all 6 digits");
            return;
        }
        ensureUser(user -> FirebaseMessaging.getInstance().getToken().addOnCompleteListener(tokenTask -> {
            String token = tokenTask.isSuccessful() ? tokenTask.getResult() : "";
            DocumentReference codeRef = firestore.collection("pairingCodes").document(code);
            codeRef.get(Source.SERVER).addOnSuccessListener(snapshot -> {
                if (!snapshot.exists() || snapshot.getTimestamp("expiresAt") == null) {
                    callback.complete(false, "This code is invalid or expired");
                    return;
                }
                String usedBy = snapshot.getString("usedByUid");
                String family = snapshot.getString("familyId");
                String member = snapshot.getString("memberId");
                if ((usedBy != null && !usedBy.isEmpty()) || family == null || member == null) {
                    callback.complete(false, "This code was already used or is incomplete");
                    return;
                }
                Map<String, Object> used = new HashMap<>();
                used.put("usedByUid", user.getUid());
                used.put("usedAt", FieldValue.serverTimestamp());
                Map<String, Object> phone = deviceDetails(token);
                phone.put("memberId", member);
                phone.put("status", "assigned");
                phone.put("pairingCode", code);
                phone.put("createdAt", FieldValue.serverTimestamp());
                WriteBatch batch = firestore.batch();
                batch.update(codeRef, used);
                batch.set(device(family, user.getUid()), phone, SetOptions.merge());
                batch.commit().addOnSuccessListener(unused -> {
                    store.savePairing(family, member);
                    syncNow((success, message) -> {
                        stop();
                        listen(callback);
                        callback.complete(success, success ? "Connected to Admin" : message);
                    });
                }).addOnFailureListener(error -> callback.complete(false,
                        "Could not connect. Request a new code and try again."));
            }).addOnFailureListener(error -> callback.complete(false,
                    "Could not connect. Check the internet and code."));
        }), () -> callback.complete(false, "Could not sign in to Firebase"));
    }

    void syncNow(Callback callback) {
        RemoteStore.Pairing pairing = store.pairing();
        if (pairing == null) {
            callback.complete(false, "Enter the 6-digit code from Admin");
            return;
        }
        member(pairing).get(Source.SERVER).addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                clearLocal();
                callback.complete(false, "This phone was removed in Admin");
                return;
            }
            new Thread(() -> {
                try {
                    ApplyResult result = applyScheduleDocument(context, store, snapshot);
                    if (!result.voiceReady) {
                        ScheduleSyncJob.scheduleCatchUp(context);
                        callback.complete(false,
                                "Natural voice is still preparing. Using the previous offline schedule.");
                        return;
                    }
                    callback.complete(true, result.changed
                            ? "Schedule updated automatically" : "Schedule is current");
                } catch (Exception error) {
                    ScheduleSyncJob.scheduleCatchUp(context);
                    callback.complete(false, "Could not update the schedule");
                }
            }, "reminder-call-voice-sync").start();
        }).addOnFailureListener(error -> callback.complete(false, "Using the offline schedule"));
    }

    void updateToken(String token) {
        FirebaseUser user = auth.getCurrentUser();
        RemoteStore.Pairing pairing = store.pairing();
        if (user != null && pairing != null) device(pairing.familyId, user.getUid())
                .update(deviceDetails(token));
    }

    void saveOwnSchedules(RemoteStore.Config edited, Callback callback) {
        RemoteStore.Pairing pairing = store.pairing();
        if (pairing == null || edited == null || !pairing.memberId.equals(edited.memberId)) {
            callback.complete(false, "Phone is not connected");
            return;
        }
        ensureUser(user -> {
            List<Map<String, Object>> schedules = new ArrayList<>();
            for (RemoteStore.Schedule item : edited.schedules) {
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
                schedule.put("medicineKey", "generic");
                schedule.put("enabled", true);
                List<Map<String, Object>> questions = new ArrayList<>();
                for (RemoteStore.ScriptQuestion itemQuestion : item.questions) {
                    Map<String, Object> question = new HashMap<>();
                    question.put("prompt", itemQuestion.prompt);
                    List<Map<String, Object>> answers = new ArrayList<>();
                    for (RemoteStore.ScriptAnswer itemAnswer : itemQuestion.answers) {
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
            Map<String, Object> update = new HashMap<>();
            update.put("schedules", schedules);
            update.put("revision", FieldValue.increment(1L));
            update.put("updatedAt", FieldValue.serverTimestamp());
            member(pairing).update(update)
                    .addOnSuccessListener(unused -> callback.complete(true,
                            "Reminder saved. It activates when its natural voice is ready."))
                    .addOnFailureListener(error -> callback.complete(false,
                            "Could not save reminder. Check the internet and try again."));
        }, () -> callback.complete(false,
                "Sign in again to save this reminder."));
    }

    void updateLanguage(String requested, Callback callback) {
        RemoteStore.Pairing pairing = store.pairing();
        if (pairing == null) {
            callback.complete(false, "Phone is not connected");
            return;
        }
        String language = AppLanguage.normalize(requested);
        ensureUser(user -> {
            Map<String, Object> update = new HashMap<>();
            update.put("language", language);
            update.put("revision", FieldValue.increment(1L));
            update.put("updatedAt", FieldValue.serverTimestamp());
            member(pairing).update(update)
                    .addOnSuccessListener(unused -> callback.complete(true,
                            "Language updated"))
                    .addOnFailureListener(error -> callback.complete(false,
                            "Could not update language. Check the internet and try again."));
        }, () -> callback.complete(false, "Sign in again to change language."));
    }

    void stop() {
        if (memberListener != null) memberListener.remove();
        if (deviceListener != null) deviceListener.remove();
        memberListener = null;
        deviceListener = null;
    }

    static boolean syncBlocking(Context context) {
        RemoteStore store = new RemoteStore(context);
        RemoteStore.Pairing pairing = store.pairing();
        if (pairing == null) return false;
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            FirebaseUser user = auth.getCurrentUser();
            if (user == null || user.isAnonymous() || user.getPhoneNumber() == null) return false;
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            DocumentSnapshot phone = Tasks.await(db.collection("families").document(pairing.familyId)
                    .collection("devices").document(user.getUid()).get(Source.SERVER),
                    12, TimeUnit.SECONDS);
            if (!phone.exists() || !pairing.memberId.equals(phone.getString("memberId"))) {
                new RemoteSync(context).clearLocal();
                return false;
            }
            DocumentSnapshot member = Tasks.await(db.collection("families").document(pairing.familyId)
                    .collection("members").document(pairing.memberId).get(Source.SERVER),
                    12, TimeUnit.SECONDS);
            if (!member.exists()) {
                new RemoteSync(context).clearLocal();
                return false;
            }
            ApplyResult result = applyScheduleDocument(context, store, member);
            return result.stale || result.voiceReady;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void listen(Callback callback) {
        RemoteStore.Pairing pairing = store.pairing();
        if (pairing == null || memberListener != null) return;
        ensureUser(user -> {
            memberListener = member(pairing).addSnapshotListener((snapshot, error) -> {
                if (error != null || snapshot == null) return;
                if (!snapshot.exists()) {
                    if (!snapshot.getMetadata().isFromCache()) clearLocal();
                    return;
                }
                new Thread(() -> {
                    try {
                        ApplyResult result = applyScheduleDocument(context, store, snapshot);
                        if (!result.voiceReady) {
                            if (!snapshot.getMetadata().isFromCache()) {
                                ScheduleSyncJob.scheduleCatchUp(context);
                            }
                            return;
                        }
                        if (result.changed) {
                            callback.complete(true, snapshot.getMetadata().isFromCache()
                                    ? "Schedule restored from this phone"
                                    : "Schedule updated automatically");
                        }
                    } catch (Exception ignored) {
                        if (!snapshot.getMetadata().isFromCache()) {
                            ScheduleSyncJob.scheduleCatchUp(context);
                        }
                    }
                }, "reminder-call-realtime-voice").start();
            });
            deviceListener = device(pairing.familyId, user.getUid())
                    .addSnapshotListener((snapshot, error) -> {
                        if (error != null || snapshot == null || snapshot.getMetadata().isFromCache()) return;
                        if (!snapshot.exists() || !pairing.memberId.equals(snapshot.getString("memberId"))) {
                            clearLocal();
                            callback.complete(false, "This phone was removed in Admin");
                        }
                    });
        }, () -> {});
    }

    private static ApplyResult applyScheduleDocument(Context context, RemoteStore store,
            DocumentSnapshot snapshot) throws Exception {
        RemoteStore.Config updated = RemoteStore.Config.fromRemote(
                snapshot.getId(), snapshot.getData());
        RemoteStore.Config beforeDownload;
        synchronized (APPLY_LOCK) {
            beforeDownload = store.load();
            if (beforeDownload != null && beforeDownload.revision > updated.revision) {
                return new ApplyResult(false, true, true);
            }
        }

        // Never expose or schedule a revision until every required natural voice clip is
        // present and validated. Firestore listeners can receive the member write before
        // the server finishes generating clips; the later FCM sync retries this atomically.
        if (!VoiceClipCache.prepareBlocking(context, snapshot.getReference(), updated)) {
            return new ApplyResult(false, false, false);
        }

        synchronized (APPLY_LOCK) {
            RemoteStore.Config current = store.load();
            if (current != null && current.revision > updated.revision) {
                return new ApplyResult(false, true, true);
            }
            // Profile-only changes (for example the UI language) do not always alter the
            // reminder payload or its historical revision. Compare the complete cached
            // document as well so those changes still reach an already-installed phone.
            boolean changed = current == null || current.revision != updated.revision
                    || !current.toJson().toString().equals(updated.toJson().toString());
            if (changed) {
                store.save(updated);
                ReminderScheduler.replaceScheduledCalls(context, current, updated);
            } else {
                // A valid Firebase snapshot is also an alarm-health checkpoint. Android/OEM
                // battery management can remove alarms without changing the cached schedule,
                // so an unchanged revision must still rebuild every expected reminder.
                ReminderScheduler.scheduleAll(context, updated);
            }
            VoiceClipCache.activate(context, updated);
            return new ApplyResult(changed, false, true);
        }
    }

    private static final class ApplyResult {
        final boolean changed;
        final boolean stale;
        final boolean voiceReady;

        ApplyResult(boolean changed, boolean stale, boolean voiceReady) {
            this.changed = changed;
            this.stale = stale;
            this.voiceReady = voiceReady;
        }
    }

    private void clearLocal() {
        ReminderScheduler.cancelAll(context, store.load());
        store.clearPairingAndSchedule();
        stop();
    }

    private void updateDevice(FirebaseUser user) {
        RemoteStore.Pairing pairing = store.pairing();
        if (pairing == null) return;
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task ->
                device(pairing.familyId, user.getUid()).update(
                        deviceDetails(task.isSuccessful() ? task.getResult() : "")));
    }

    private Map<String, Object> deviceDetails(String token) {
        Map<String, Object> value = new HashMap<>();
        if (token != null && !token.isEmpty()) value.put("fcmToken", token);
        value.put("model", (Build.MANUFACTURER + " " + Build.MODEL).trim());
        value.put("platform", "android");
        value.put("appVersion", installedVersion());
        value.put("deviceKey", stableDeviceKey());
        value.put("updatedAt", FieldValue.serverTimestamp());
        return value;
    }

    private String installedVersion() {
        try {
            String version = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
            return "Reminder Call " + (version == null ? "" : version);
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            return "Reminder Call";
        }
    }

    @SuppressLint("HardwareIds")
    private String stableDeviceKey() {
        String id = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (id == null) return "";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((context.getPackageName() + ":" + id).getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            for (byte value : digest) output.append(String.format(java.util.Locale.US, "%02x", value & 0xff));
            return output.toString();
        } catch (Exception ignored) { return ""; }
    }

    private void ensureUser(UserAction action, Runnable failure) {
        FirebaseUser current = auth.getCurrentUser();
        if (current != null && !current.isAnonymous()
                && current.getPhoneNumber() != null && !current.getPhoneNumber().isEmpty()) {
            action.run(current);
        } else {
            failure.run();
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            for (byte item : digest) output.append(String.format(java.util.Locale.US, "%02x", item & 0xff));
            return output.toString();
        } catch (Exception impossible) {
            return "";
        }
    }

    private DocumentReference member(RemoteStore.Pairing pairing) {
        return firestore.collection("families").document(pairing.familyId)
                .collection("members").document(pairing.memberId);
    }

    private DocumentReference device(String family, String uid) {
        return firestore.collection("families").document(family).collection("devices").document(uid);
    }

    private interface UserAction { void run(FirebaseUser user); }
}
