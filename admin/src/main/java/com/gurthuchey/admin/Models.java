package com.gurthuchey.admin;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class Models {

    private Models() {}

    static final class Member {
        String id = UUID.randomUUID().toString();
        String name = "";
        String phoneE164 = "";
        String relation = "generic";
        String language = "en";
        final List<Schedule> schedules = new ArrayList<>();

        JSONObject toJson() throws JSONException {
            JSONArray items = new JSONArray();
            for (Schedule schedule : schedules) items.put(schedule.toJson());
            return new JSONObject()
                    .put("id", id)
                    .put("name", name)
                    .put("phoneE164", phoneE164)
                    .put("relation", relation)
                    .put("language", language)
                    .put("schedules", items);
        }

        static Member fromJson(JSONObject json) throws JSONException {
            Member member = new Member();
            member.id = clean(json.optString("id", member.id), 80, member.id);
            member.name = clean(json.optString("name", "Family member"), 60, "Family member");
            member.phoneE164 = cleanOptional(json.optString("phoneE164", ""), 20);
            member.relation = clean(json.optString("relation", "generic"), 20, "generic");
            member.language = AppLanguage.normalize(json.optString("language",
                    defaultLanguage(member.phoneE164)));
            JSONArray items = json.optJSONArray("schedules");
            if (items != null) {
                for (int i = 0; i < Math.min(50, items.length()); i++) {
                    member.schedules.add(Schedule.fromJson(items.getJSONObject(i)));
                }
            }
            return member;
        }

        static Member fromRemote(String id, Map<String, Object> value) {
            Member member = new Member();
            member.id = clean(id, 80, member.id);
            member.name = clean(text(value.get("name"), "Family member"), 60, "Family member");
            member.phoneE164 = cleanOptional(text(value.get("phoneE164"), ""), 20);
            member.relation = clean(text(value.get("relation"), "generic"), 20, "generic");
            member.language = AppLanguage.normalize(text(value.get("language"),
                    defaultLanguage(member.phoneE164)));
            Object rawSchedules = value.get("schedules");
            if (rawSchedules instanceof List) {
                int count = 0;
                for (Object raw : (List<?>) rawSchedules) {
                    if (count++ >= 50) break;
                    if (raw instanceof Map) {
                        @SuppressWarnings("unchecked") Map<String, Object> schedule = (Map<String, Object>) raw;
                        member.schedules.add(Schedule.fromRemote(schedule));
                    }
                }
            }
            return member;
        }

        private static String defaultLanguage(String phone) {
            return phone != null && phone.replaceAll("[^0-9]", "")
                    .endsWith("9177216132") ? "en" : "te";
        }
    }

    static final class Schedule {
        String id = UUID.randomUUID().toString();
        String label = "Medicine";
        String category = "medicine";
        String language = "en";
        int hour = 8;
        int minute = 0;
        int days = 0b1111111;
        int preMinutes = 30;
        int confirmationMinutes;
        boolean enabled = true;
        String medicineKey = "generic";
        final List<ScriptQuestion> questions = new ArrayList<>();

        JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("id", id)
                    .put("label", label)
                    .put("category", category)
                    .put("language", language)
                    .put("hour", hour)
                    .put("minute", minute)
                    .put("days", days)
                    .put("preMinutes", preMinutes)
                    .put("confirmationMinutes", confirmationMinutes)
                    .put("medicineKey", medicineKey)
                    .put("questions", questionsJson())
                    .put("enabled", enabled);
        }

        private JSONArray questionsJson() throws JSONException {
            JSONArray items = new JSONArray();
            for (ScriptQuestion question : questions) items.put(question.toJson());
            return items;
        }

        static Schedule fromJson(JSONObject json) {
            Schedule item = new Schedule();
            item.id = clean(json.optString("id", item.id), 80, item.id);
            item.label = clean(json.optString("label", "Medicine"), 80, "Medicine");
            item.category = category(json.optString("category",
                    json.optString("kind", "medicine")));
            item.language = AppLanguage.normalize(json.optString("language", "te"));
            item.hour = Math.max(0, Math.min(23, json.optInt("hour", 8)));
            item.minute = Math.max(0, Math.min(59, json.optInt("minute", 0)));
            item.days = json.optInt("days", 0b1111111) & 0b1111111;
            item.preMinutes = Math.max(0, Math.min(180, json.optInt("preMinutes", 30)));
            item.confirmationMinutes = Math.max(0, Math.min(180,
                    json.optInt("confirmationMinutes", 0)));
            item.medicineKey = clean(json.optString("medicineKey", "generic"), 24, "generic");
            item.enabled = json.optBoolean("enabled", true);
            JSONArray questions = json.optJSONArray("questions");
            if (questions != null) {
                for (int i = 0; i < Math.min(10, questions.length()); i++) {
                    JSONObject question = questions.optJSONObject(i);
                    if (question != null) item.questions.add(ScriptQuestion.fromJson(question));
                }
            }
            item.language = item.detectedLanguage();
            return item;
        }

        static Schedule fromRemote(Map<String, Object> value) {
            Schedule item = new Schedule();
            item.id = clean(text(value.get("id"), item.id), 80, item.id);
            item.label = clean(text(value.get("label"), "Medicine"), 80, "Medicine");
            item.category = category(text(value.get("category"),
                    text(value.get("kind"), "medicine")));
            item.language = AppLanguage.normalize(text(value.get("language"), "te"));
            item.hour = Math.max(0, Math.min(23, number(value.get("hour"), 8)));
            item.minute = Math.max(0, Math.min(59, number(value.get("minute"), 0)));
            item.days = number(value.get("days"), 0b1111111) & 0b1111111;
            item.preMinutes = Math.max(0, Math.min(180, number(value.get("preMinutes"), 30)));
            item.confirmationMinutes = Math.max(0, Math.min(180,
                    number(value.get("confirmationMinutes"), 0)));
            item.medicineKey = clean(text(value.get("medicineKey"), "generic"), 24, "generic");
            Object active = value.get("enabled");
            item.enabled = !(active instanceof Boolean) || (Boolean) active;
            Object rawQuestions = value.get("questions");
            if (rawQuestions instanceof List) {
                int count = 0;
                for (Object raw : (List<?>) rawQuestions) {
                    if (count++ >= 10) break;
                    if (raw instanceof Map) {
                        @SuppressWarnings("unchecked") Map<String, Object> question =
                                (Map<String, Object>) raw;
                        item.questions.add(ScriptQuestion.fromRemote(question));
                    }
                }
            }
            item.language = item.detectedLanguage();
            return item;
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

        String timeText() {
            int shownHour = hour % 12;
            if (shownHour == 0) shownHour = 12;
            return String.format(Locale.US, "%d:%02d %s", shownHour, minute, hour < 12 ? "AM" : "PM");
        }

        String daysText() {
            if (days == 0b1111111) return "Every day";
            String[] labels = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
            StringBuilder value = new StringBuilder();
            for (int i = 0; i < labels.length; i++) {
                if ((days & (1 << i)) != 0) {
                    if (value.length() > 0) value.append(" · ");
                    value.append(labels[i]);
                }
            }
            return value.length() == 0 ? "No days" : value.toString();
        }
    }

    static final class ScriptQuestion {
        String prompt = "";
        final List<ScriptAnswer> answers = new ArrayList<>();

        JSONObject toJson() throws JSONException {
            JSONArray items = new JSONArray();
            for (ScriptAnswer answer : answers) items.put(answer.toJson());
            return new JSONObject().put("prompt", prompt).put("answers", items);
        }

        static ScriptQuestion fromJson(JSONObject json) {
            ScriptQuestion question = new ScriptQuestion();
            question.prompt = clean(json.optString("prompt", ""), 300, "");
            JSONArray answers = json.optJSONArray("answers");
            if (answers != null) {
                for (int i = 0; i < Math.min(4, answers.length()); i++) {
                    JSONObject answer = answers.optJSONObject(i);
                    if (answer != null) question.answers.add(ScriptAnswer.fromJson(answer));
                }
            }
            return question;
        }

        static ScriptQuestion fromRemote(Map<String, Object> value) {
            ScriptQuestion question = new ScriptQuestion();
            question.prompt = clean(text(value.get("prompt"), ""), 300, "");
            Object rawAnswers = value.get("answers");
            if (rawAnswers instanceof List) {
                int count = 0;
                for (Object raw : (List<?>) rawAnswers) {
                    if (count++ >= 4) break;
                    if (raw instanceof Map) {
                        @SuppressWarnings("unchecked") Map<String, Object> answer =
                                (Map<String, Object>) raw;
                        question.answers.add(ScriptAnswer.fromRemote(answer));
                    }
                }
            }
            return question;
        }
    }

    static final class ScriptAnswer {
        String label = "";
        String response = "";

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("label", label).put("response", response);
        }

        static ScriptAnswer fromJson(JSONObject json) {
            ScriptAnswer answer = new ScriptAnswer();
            answer.label = clean(json.optString("label", ""), 40, "");
            answer.response = clean(json.optString("response", ""), 300, "");
            return answer;
        }

        static ScriptAnswer fromRemote(Map<String, Object> value) {
            ScriptAnswer answer = new ScriptAnswer();
            answer.label = clean(text(value.get("label"), ""), 40, "");
            answer.response = clean(text(value.get("response"), ""), 300, "");
            return answer;
        }
    }

    private static String category(String value) {
        if (value == null) return "medicine";
        String normalized = value.trim().toLowerCase(Locale.US);
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
                // Unknown explicit categories are generic reminders, never medicine.
                // This keeps future Admin versions from making older builds ask about tablets.
                return "custom";
        }
    }

    private static String text(Object value, String fallback) {
        return value instanceof String ? (String) value : fallback;
    }

    private static String clean(String value, int maximum, String fallback) {
        if (value == null) return fallback;
        String result = value.replaceAll("[\\x00-\\x1f\\x7f]", " ")
                .replaceAll("[\\s\\p{Z}\\uFEFF]+", " ").trim();
        if (result.isEmpty()) return fallback;
        return result.length() <= maximum ? result : result.substring(0, maximum).trim();
    }

    private static String cleanOptional(String value, int maximum) {
        if (value == null) return "";
        String result = value.replaceAll("[^+0-9]", "").trim();
        return result.length() <= maximum ? result : result.substring(0, maximum);
    }

    private static int number(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }
}
