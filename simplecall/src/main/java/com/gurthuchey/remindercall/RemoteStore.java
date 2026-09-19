package com.gurthuchey.remindercall;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class RemoteStore {
    private static final String PREFS = "remote_medicine_schedule";
    private final SharedPreferences preferences;

    RemoteStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    Pairing pairing() {
        String family = preferences.getString("family", null);
        String member = preferences.getString("member", null);
        return family == null || member == null ? null : new Pairing(family, member);
    }

    void savePairing(String family, String member) {
        preferences.edit().putString("family", family).putString("member", member).commit();
    }

    void clearPairingAndSchedule() {
        preferences.edit().clear().commit();
    }

    Config load() {
        String raw = preferences.getString("config", null);
        if (raw == null) return null;
        try { return Config.fromJson(new JSONObject(raw)); }
        catch (Exception ignored) { return null; }
    }

    void save(Config config) throws Exception {
        if (!preferences.edit().putString("config", config.toJson().toString())
                .putLong("lastSync", System.currentTimeMillis()).commit()) {
            throw new IllegalStateException("Could not save the reminder schedule");
        }
    }

    long lastSync() { return preferences.getLong("lastSync", 0); }

    void registerChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.registerOnSharedPreferenceChangeListener(listener);
    }

    void unregisterChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }

    static final class Pairing {
        final String familyId;
        final String memberId;
        Pairing(String familyId, String memberId) {
            this.familyId = familyId;
            this.memberId = memberId;
        }
    }

    static final class Config {
        String memberId;
        String memberName;
        String language = "en";
        long revision;
        final List<Schedule> schedules = new ArrayList<>();

        static Config fromRemote(String id, Map<String, Object> data) {
            Config config = new Config();
            config.memberId = limit(id, 80);
            config.memberName = clean(text(data.get("name"), "Family member"),
                    60, "Family member");
            config.language = AppLanguage.normalize(text(data.get("language"), "te"));
            config.revision = Math.max(0, number(data.get("revision"), 0).longValue());
            Object raw = data.get("schedules");
            if (!(raw instanceof List)) throw new IllegalArgumentException("Missing schedules");
            int count = 0;
            Set<String> ids = new HashSet<>();
            for (Object value : (List<?>) raw) {
                if (count++ >= 50) break;
                if (!(value instanceof Map)) continue;
                @SuppressWarnings("unchecked") Map<String, Object> item = (Map<String, Object>) value;
                Schedule schedule = Schedule.fromRemote(item);
                if (!ids.add(schedule.id)) throw new IllegalArgumentException("Duplicate reminder id");
                config.schedules.add(schedule);
            }
            return config;
        }

        static Config fromJson(JSONObject json) throws Exception {
            Config config = new Config();
            config.memberId = limit(json.getString("memberId").trim(), 80);
            config.memberName = clean(json.getString("memberName"), 60, "Family member");
            config.language = AppLanguage.normalize(json.optString("language", "te"));
            config.revision = Math.max(0, json.optLong("revision", 0));
            JSONArray items = json.getJSONArray("schedules");
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < Math.min(50, items.length()); i++) {
                Schedule schedule = Schedule.fromJson(items.getJSONObject(i));
                if (!ids.add(schedule.id)) throw new IllegalArgumentException("Duplicate reminder id");
                config.schedules.add(schedule);
            }
            return config;
        }

        JSONObject toJson() throws Exception {
            JSONArray items = new JSONArray();
            for (Schedule item : schedules) items.put(item.toJson());
            return new JSONObject().put("memberId", memberId).put("memberName", memberName)
                    .put("language", language)
                    .put("revision", revision).put("schedules", items);
        }
    }

    static final class Schedule {
        String id;
        String label;
        String category = "medicine";
        String language = "en";
        int hour;
        int minute;
        int days;
        int preMinutes;
        int confirmationMinutes;
        boolean enabled;
        final List<ScriptQuestion> questions = new ArrayList<>();

        static Schedule fromRemote(Map<String, Object> data) {
            Schedule schedule = new Schedule();
            schedule.id = clean(text(data.get("id"), UUID.randomUUID().toString()),
                    80, UUID.randomUUID().toString());
            schedule.label = clean(text(data.get("label"), "Medicine"), 80, "Medicine");
            schedule.category = category(text(data.get("category"),
                    text(data.get("kind"), "medicine")));
            schedule.language = AppLanguage.normalize(text(data.get("language"), "te"));
            schedule.hour = clamp(number(data.get("hour"), 8).intValue(), 0, 23);
            schedule.minute = clamp(number(data.get("minute"), 0).intValue(), 0, 59);
            schedule.days = number(data.get("days"), 0b1111111).intValue() & 0b1111111;
            schedule.preMinutes = clamp(number(data.get("preMinutes"), 0).intValue(), 0, 180);
            schedule.confirmationMinutes = clamp(
                    number(data.get("confirmationMinutes"), 0).intValue(), 0, 180);
            schedule.enabled = !(data.get("enabled") instanceof Boolean) || (Boolean) data.get("enabled");
            Object rawQuestions = data.get("questions");
            if (rawQuestions instanceof List) {
                int count = 0;
                for (Object raw : (List<?>) rawQuestions) {
                    if (count++ >= 10) break;
                    if (raw instanceof Map) {
                        @SuppressWarnings("unchecked") Map<String, Object> question =
                                (Map<String, Object>) raw;
                        ScriptQuestion parsed = ScriptQuestion.fromRemote(question);
                        if (!parsed.prompt.isEmpty()) {
                            schedule.questions.add(parsed);
                        }
                    }
                }
            }
            if (schedule.days == 0) throw new IllegalArgumentException("No scheduled days");
            if (schedule.custom() && schedule.questions.isEmpty()) {
                throw new IllegalArgumentException("Missing custom reminder questions");
            }
            schedule.language = schedule.detectedLanguage();
            return schedule;
        }

        static Schedule fromJson(JSONObject json) throws Exception {
            Schedule schedule = new Schedule();
            schedule.id = clean(json.getString("id"), 80, "");
            schedule.label = clean(json.getString("label"), 80, "");
            schedule.category = category(json.optString("category",
                    json.optString("kind", "medicine")));
            schedule.language = AppLanguage.normalize(json.optString("language", "te"));
            schedule.hour = clamp(json.getInt("hour"), 0, 23);
            schedule.minute = clamp(json.getInt("minute"), 0, 59);
            schedule.days = json.getInt("days") & 0b1111111;
            schedule.preMinutes = clamp(json.optInt("preMinutes", 0), 0, 180);
            schedule.confirmationMinutes = clamp(json.optInt("confirmationMinutes", 0), 0, 180);
            schedule.enabled = json.optBoolean("enabled", true);
            JSONArray questions = json.optJSONArray("questions");
            if (questions != null) {
                for (int i = 0; i < Math.min(10, questions.length()); i++) {
                    JSONObject question = questions.optJSONObject(i);
                    if (question != null) {
                        ScriptQuestion parsed = ScriptQuestion.fromJson(question);
                        if (!parsed.prompt.isEmpty()) {
                            schedule.questions.add(parsed);
                        }
                    }
                }
            }
            if (schedule.id.isEmpty() || schedule.label.isEmpty() || schedule.days == 0) {
                throw new IllegalArgumentException("Invalid reminder schedule");
            }
            if (schedule.custom() && schedule.questions.isEmpty()) {
                throw new IllegalArgumentException("Missing custom reminder questions");
            }
            schedule.language = schedule.detectedLanguage();
            return schedule;
        }

        JSONObject toJson() throws Exception {
            JSONArray items = new JSONArray();
            for (ScriptQuestion question : questions) items.put(question.toJson());
            return new JSONObject().put("id", id).put("label", label)
                    .put("category", category)
                    .put("language", language)
                    .put("hour", hour)
                    .put("minute", minute).put("days", days).put("preMinutes", preMinutes)
                    .put("confirmationMinutes", confirmationMinutes)
                    .put("questions", items)
                    .put("enabled", enabled);
        }

        boolean custom() { return !"medicine".equals(category); }

        String detectedLanguage() {
            StringBuilder text = new StringBuilder(label == null ? "" : label);
            for (ScriptQuestion question : questions) {
                text.append(' ').append(question.prompt);
                for (ScriptAnswer answer : question.answers) {
                    text.append(' ').append(answer.label).append(' ').append(answer.response);
                }
            }
            return AppLanguage.detect(text.toString(), "en");
        }
    }

    static final class ScriptQuestion {
        String prompt = "";
        final List<ScriptAnswer> answers = new ArrayList<>();

        static ScriptQuestion fromRemote(Map<String, Object> data) {
            ScriptQuestion question = new ScriptQuestion();
            question.prompt = clean(text(data.get("prompt"), ""), 300, "");
            Object rawAnswers = data.get("answers");
            if (rawAnswers instanceof List) {
                int count = 0;
                for (Object raw : (List<?>) rawAnswers) {
                    if (count++ >= 4) break;
                    if (raw instanceof Map) {
                        @SuppressWarnings("unchecked") Map<String, Object> answer =
                                (Map<String, Object>) raw;
                        ScriptAnswer parsed = ScriptAnswer.fromRemote(answer);
                        if (!parsed.label.isEmpty()) question.answers.add(parsed);
                    }
                }
            }
            return question;
        }

        static ScriptQuestion fromJson(JSONObject json) {
            ScriptQuestion question = new ScriptQuestion();
            question.prompt = clean(json.optString("prompt", ""), 300, "");
            JSONArray answers = json.optJSONArray("answers");
            if (answers != null) {
                for (int i = 0; i < Math.min(4, answers.length()); i++) {
                    JSONObject answer = answers.optJSONObject(i);
                    if (answer != null) {
                        ScriptAnswer parsed = ScriptAnswer.fromJson(answer);
                        if (!parsed.label.isEmpty()) question.answers.add(parsed);
                    }
                }
            }
            return question;
        }

        JSONObject toJson() throws Exception {
            JSONArray items = new JSONArray();
            for (ScriptAnswer answer : answers) items.put(answer.toJson());
            return new JSONObject().put("prompt", prompt).put("answers", items);
        }
    }

    static final class ScriptAnswer {
        String label = "";
        String response = "";

        static ScriptAnswer fromRemote(Map<String, Object> data) {
            ScriptAnswer answer = new ScriptAnswer();
            answer.label = clean(text(data.get("label"), ""), 40, "");
            answer.response = clean(text(data.get("response"), ""), 300, "");
            return answer;
        }

        static ScriptAnswer fromJson(JSONObject json) {
            ScriptAnswer answer = new ScriptAnswer();
            answer.label = clean(json.optString("label", ""), 40, "");
            answer.response = clean(json.optString("response", ""), 300, "");
            return answer;
        }

        JSONObject toJson() throws Exception {
            return new JSONObject().put("label", label).put("response", response);
        }
    }

    private static String category(String value) {
        if (value == null) return "medicine";
        String normalized = value.trim().toLowerCase(java.util.Locale.US);
        switch (normalized) {
            case "":
            case "medicine":
                return "medicine";
            case "appointment":
            case "payment":
            case "exercise":
            case "meal":
            case "supplement":
            case "drink":
            case "task":
            case "wake_up":
            case "custom":
                return normalized;
            default:
                return "custom";
        }
    }

    private static String text(Object value, String fallback) {
        return value instanceof String ? (String) value : fallback;
    }

    private static Number number(Object value, Number fallback) {
        return value instanceof Number ? (Number) value : fallback;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** Must match the Cloud voice generator's normalization exactly. */
    private static String clean(String value, int maximum, String fallback) {
        if (value == null) return fallback;
        String result = value.replaceAll("[\\x00-\\x1f\\x7f]", " ")
                .replaceAll("[\\s\\p{Z}\\uFEFF]+", " ").trim();
        if (result.isEmpty()) return fallback;
        return result.length() <= maximum
                ? result : result.substring(0, maximum).trim();
    }
}
