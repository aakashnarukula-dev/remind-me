package com.gurthuchey.remindercall;

import android.content.Context;
import android.util.Base64;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

final class VoiceClipCache {
    private static final String DIRECTORY = "natural_voice_v2";
    private static final Object LOCK = new Object();
    static final String BUNDLED_MEDICINE_PROMPT_TEXT =
            "అయితే త్వరగా వెళ్లి టాబ్లెట్ వేసుకోండి. నేను లైన్‌లోనే ఉంటాను. "
            + "వేసుకుని వచ్చాక, “వేసుకున్నా” బటన్ నొక్కండి. లేదా ఇంకా సమయం కావాలంటే, "
            + "“తర్వాత గుర్తుచేయి” బటన్ నొక్కండి.";
    static final String BUNDLED_FIVE_MINUTE_RESPONSE_TEXT = SpeechText.reminderDelayed(5);

    private VoiceClipCache() {}

    static File fileFor(Context context, String text, String language) {
        synchronized (LOCK) {
            String code = AppLanguage.normalize(language);
            File file = new File(new File(context.getFilesDir(), DIRECTORY),
                    id(text, code) + ".mp3");
            if ("te".equals(code) && BUNDLED_MEDICINE_PROMPT_TEXT.equals(text)) {
                installBundledClip(context, file, R.raw.medicine_not_taken);
            } else if ("te".equals(code) && BUNDLED_FIVE_MINUTE_RESPONSE_TEXT.equals(text)) {
                installBundledClip(context, file, R.raw.reminder_delayed_5);
            }
            return valid(file) ? file : null;
        }
    }

    static boolean prepareBlocking(Context context, DocumentReference member,
            RemoteStore.Config config) {
        if (complete(context, config)) return true;
        try {
            QuerySnapshot snapshot = Tasks.await(member.collection("voiceClips").get(Source.SERVER),
                    15, TimeUnit.SECONDS);
            return cache(context, snapshot, config);
        } catch (Exception ignored) { return false; }
    }

    static void activate(Context context, RemoteStore.Config config) {
        Set<String> wanted = entries(config).keySet();
        synchronized (LOCK) {
            File[] files = new File(context.getFilesDir(), DIRECTORY).listFiles();
            if (files == null) return;
            for (File file : files) {
                String name = file.getName();
                if (name.endsWith(".tmp") || (name.endsWith(".mp3")
                        && !wanted.contains(name.substring(0, name.length() - 4)))) {
                    file.delete();
                }
            }
        }
    }

    private static boolean complete(Context context, RemoteStore.Config config) {
        synchronized (LOCK) {
            for (ClipEntry entry : entries(config).values()) {
                if (fileFor(context, entry.text, entry.language) == null) return false;
            }
            return true;
        }
    }

    private static boolean cache(Context context, QuerySnapshot snapshot,
            RemoteStore.Config config) {
        Map<String, ClipEntry> wanted = entries(config);
        synchronized (LOCK) {
            File directory = new File(context.getFilesDir(), DIRECTORY);
            if (!directory.isDirectory() && !directory.mkdirs()) return false;
            for (QueryDocumentSnapshot document : snapshot) {
                ClipEntry expected = wanted.get(document.getId());
                if (expected == null || !expected.text.equals(document.getString("text"))
                        || !voice(expected.language).equals(document.getString("voice"))) continue;
                String encoded = document.getString("audioBase64");
                if (encoded == null || encoded.length() < 300 || encoded.length() > 950_000) continue;
                File target = new File(directory, document.getId() + ".mp3");
                if (valid(target)) continue;
                try {
                    byte[] audio = Base64.decode(encoded, Base64.DEFAULT);
                    if (!mp3(audio) || audio.length > 700_000) continue;
                    File temporary = new File(directory, document.getId() + ".tmp");
                    try (FileOutputStream output = new FileOutputStream(temporary, false)) {
                        output.write(audio);
                        output.getFD().sync();
                    }
                    if (target.exists() && !target.delete()) {
                        temporary.delete();
                        continue;
                    }
                    if (!temporary.renameTo(target)) temporary.delete();
                } catch (Exception ignored) {}
            }
            for (ClipEntry entry : wanted.values()) {
                if (fileFor(context, entry.text, entry.language) == null) return false;
            }
            return true;
        }
    }

    private static void installBundledClip(Context context, File target, int resource) {
        if (matchesBundledClip(context, target, resource)) return;
        File directory = target.getParentFile();
        if (directory == null || (!directory.isDirectory() && !directory.mkdirs())) return;
        File temporary = new File(directory, target.getName() + ".tmp");
        try (java.io.InputStream input = context.getResources()
                     .openRawResource(resource);
             FileOutputStream output = new FileOutputStream(temporary, false)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            output.getFD().sync();
            if (!valid(temporary)) {
                temporary.delete();
                return;
            }
            if (target.exists() && !target.delete()) {
                temporary.delete();
                return;
            }
            if (!temporary.renameTo(target)) temporary.delete();
        } catch (Exception ignored) {
            temporary.delete();
        }
    }

    private static boolean matchesBundledClip(Context context, File target, int resource) {
        if (!valid(target)) return false;
        try (java.io.InputStream bundled = context.getResources()
                     .openRawResource(resource);
             java.io.InputStream cached = new java.io.FileInputStream(target)) {
            byte[] bundledBuffer = new byte[8192];
            byte[] cachedBuffer = new byte[8192];
            while (true) {
                int bundledCount = bundled.read(bundledBuffer);
                int cachedCount = cached.read(cachedBuffer);
                if (bundledCount != cachedCount) return false;
                if (bundledCount < 0) return true;
                for (int index = 0; index < bundledCount; index++) {
                    if (bundledBuffer[index] != cachedBuffer[index]) return false;
                }
            }
        } catch (Exception ignored) { return false; }
    }

    private static Map<String, ClipEntry> entries(RemoteStore.Config config) {
        Map<String, ClipEntry> values = new LinkedHashMap<>();
        if (config == null) return values;
        for (RemoteStore.Schedule schedule : config.schedules) {
            if (!schedule.enabled) continue;
            String language = AppLanguage.normalize(schedule.language);
            if (schedule.custom()) {
                for (RemoteStore.ScriptQuestion question : schedule.questions) {
                    String prompt = SpeechText.custom(question.prompt,
                            config.memberName, schedule.label, language);
                    add(values, prompt, language);
                    for (RemoteStore.ScriptAnswer answer : question.answers) {
                        String response = SpeechText.custom(answer.response,
                                config.memberName, schedule.label, language);
                        add(values, response, language);
                    }
                }
            } else {
                add(values, SpeechText.medicineQuestion(config.memberName,
                        schedule.label, language), language);
                if (schedule.preMinutes > 0) {
                    add(values, SpeechText.mealQuestion(config.memberName, schedule.label,
                            schedule.hour < 12, schedule.preMinutes, language), language);
                }
                add(values, SpeechText.medicineTaken(config.memberName, language), language);
                add(values, SpeechText.medicineNotTaken(language), language);
                add(values, SpeechText.reminderDelayQuestion(language), language);
                for (int minutes : CallService.DELAY_MINUTES) {
                    add(values, SpeechText.reminderDelayed(minutes, language), language);
                }
            }
            if (schedule.confirmationMinutes > 0) {
                add(values, SpeechText.confirmationQuestion(config.memberName, schedule.label,
                        schedule.category, language), language);
                add(values, SpeechText.medicineTaken(config.memberName, language), language);
                add(values, SpeechText.confirmationNotTaken(language), language);
                add(values, SpeechText.reminderDelayQuestion(language), language);
                for (int minutes : CallService.DELAY_MINUTES) {
                    add(values, SpeechText.reminderDelayed(minutes, language), language);
                }
            }
        }
        return values;
    }

    private static void add(Map<String, ClipEntry> values, String text, String language) {
        if (text == null || text.isEmpty()) return;
        ClipEntry entry = new ClipEntry(text, language);
        values.put(id(text, entry.language), entry);
    }

    private static final class ClipEntry {
        final String text;
        final String language;

        ClipEntry(String text, String language) {
            this.text = text;
            this.language = AppLanguage.normalize(language);
        }
    }

    private static String id(String text, String language) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest((voice(language) + "\n" + text).getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            for (byte value : bytes) output.append(String.format(java.util.Locale.US, "%02x", value & 0xff));
            return output.toString();
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    private static String voice(String language) {
        return AppLanguage.normalize(language) + "-IN-Chirp3-HD-Leda";
    }

    private static boolean valid(File file) {
        if (!file.isFile() || file.length() < 256 || file.length() > 700_000) return false;
        try (java.io.FileInputStream input = new java.io.FileInputStream(file)) {
            byte[] header = new byte[3];
            return input.read(header) == 3 && mp3(header);
        } catch (Exception ignored) { return false; }
    }

    private static boolean mp3(byte[] audio) {
        return audio != null && audio.length >= 3
                && ((audio[0] == 'I' && audio[1] == 'D' && audio[2] == '3')
                || ((audio[0] & 0xff) == 0xff && (audio[1] & 0xe0) == 0xe0));
    }
}
