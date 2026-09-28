package com.gurthuchey.remindercall;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.function.BooleanSupplier;

/** Offline regression suite. Run only on a disposable emulator with -e regression true. */
final class ReminderModeRegression {
    private final Instrumentation runner;
    private final Context context;
    private final Instrumentation.ActivityMonitor screens;
    private RemoteStore.Config config;
    private RemoteStore.Schedule schedule;

    ReminderModeRegression(Instrumentation runner) {
        this.runner = runner;
        context = runner.getTargetContext();
        screens = runner.addMonitor(CallActivity.class.getName(), null, false);
    }

    void run() {
        Bundle result = new Bundle();
        try {
            check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"),
                    "Regression suite requires a disposable emulator");
            runner.startActivitySync(new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            runner.waitForIdleSync();
            config = new RemoteStore.Config();
            config.memberId = "regression";
            config.memberName = "Test";
            schedule = new RemoteStore.Schedule();
            schedule.id = "regression-reminder";
            schedule.label = "Drink water";
            schedule.category = "task";
            schedule.deliveryMode = "reminder";
            schedule.days = 127;
            schedule.hour = 23;
            schedule.enabled = true;
            RemoteStore.ScriptQuestion first = question("Have you had water?", "Yes", "Good. Next question.");
            schedule.questions.add(first);
            schedule.questions.add(question("Take a short break.", null, null));
            config.schedules.add(schedule);
            new RemoteStore(context).save(config);
            check(new RemoteStore(context).load().schedules.get(0).textReminder(), "Mode survives offline storage");
            check(VoiceClipCache.prepareBlocking(context, null, config), "Text mode needs no voice download");
            start(ReminderScheduler.PHASE_MEDICINE);
            await(() -> session("active") && session("answered"), "Questions open without answering");
            check(session("textReminder"), "Text mode active");
            check("Have you had water?".equals(CallService.session(context).getString("question", "")), "First question visible");
            check(context.getSystemService(AudioManager.class).getMode() == AudioManager.MODE_NORMAL,
                    "Text reminder does not enter voice-call audio mode");
            check(Notification.CATEGORY_ALARM.equals(notification().category), "Alarm notification replaces CallStyle");
            await(() -> find("Have you had water?") != null, "Text question visible on screen");
            capture("reminder-question.png");
            action(CallService.ACTION_OPTION, 0, 0);
            await(() -> session("transitioning"), "Text response shown");
            check(session("showTextContinue"), "Response can be acknowledged");
            action(CallService.ACTION_CONTINUE, -1, 0);
            await(() -> CallService.session(context).getInt("step", -1) == 1, "Next question reached");
            check(session("showTextContinue"), "Information-only text has Okay action");
            action(CallService.ACTION_CONTINUE, -1, 1);
            await(() -> !session("active"), "Information acknowledged and closed");
            check(DailyCallStatus.COMPLETED.equals(new DailyCallStatus(context).display(schedule,
                    System.currentTimeMillis()).kind), "Completion recorded");

            reset();
            start(ReminderScheduler.PHASE_MEDICINE);
            await(() -> session("active"), "Reminder restarted");
            action(CallService.ACTION_DELAY, 1, 0);
            await(() -> session("finalizing"), "Snooze response visible");
            check(!context.getSharedPreferences("pending_call_retries", Context.MODE_PRIVATE)
                    .getAll().isEmpty(), "Snooze persists offline retry");
            action(CallService.ACTION_CONTINUE, -1, 0);
            await(() -> !session("active"), "Snooze closes");
            reset();

            start(ReminderScheduler.PHASE_MEDICINE);
            await(() -> session("active"), "Skip reminder started");
            action(CallService.ACTION_SKIP_TODAY, -1, 0);
            await(() -> !session("active"), "Skipped reminder closes");
            check(new DailyCallStatus(context).isSkippedToday(schedule.id, System.currentTimeMillis()),
                    "Skip recorded");
            reset();

            start(ReminderScheduler.PHASE_MEDICINE);
            await(() -> session("active"), "Timeout reminder started");
            long started = SystemClock.uptimeMillis();
            long timeout = started + 70_000L;
            while (session("active") && SystemClock.uptimeMillis() < timeout) SystemClock.sleep(200);
            check(!session("active"), "Unanswered reminder times out");
            check(SystemClock.uptimeMillis() - started >= 58_000L, "Full answer timeout is respected");
            check(!context.getSharedPreferences("pending_call_retries", Context.MODE_PRIVATE)
                    .getAll().isEmpty(), "Unanswered reminder queues retry");
            reset();

            schedule.deliveryMode = "call";
            new RemoteStore(context).save(config);
            start(ReminderScheduler.PHASE_MEDICINE);
            await(() -> session("active"), "Call starts");
            check(!session("answered") && !session("textReminder"), "Call still requires answering");
            check(Notification.CATEGORY_CALL.equals(notification().category), "Call notification retained");
            action(CallService.ACTION_ANSWER, -1, 0);
            await(() -> session("answered"), "Call can still be answered");
            action(CallService.ACTION_END, -1, 0);
            await(() -> !session("active"), "Call closes");
            reset();

            schedule.deliveryMode = "reminder";
            schedule.category = "medicine";
            schedule.preMinutes = 30;
            new RemoteStore(context).save(config);
            start(ReminderScheduler.PHASE_MEAL);
            await(() -> session("active") && session("showTextContinue"), "Meal text waits for acknowledgment");
            action(CallService.ACTION_CONTINUE, -1, 0);
            await(() -> !session("active"), "Meal text closes after acknowledgment");
            reset();

            schedule.confirmationMinutes = 10;
            check(ReminderScheduler.active(config, schedule.id, ReminderScheduler.PHASE_CONFIRMATION) == null,
                    "Legacy confirmation is disabled");
            schedule.confirmationMinutes = 0;
            schedule.category = "task";
            schedule.preMinutes = 0;
            Calendar due = Calendar.getInstance();
            int today = due.get(Calendar.DAY_OF_YEAR);
            due.add(Calendar.MINUTE, 2);
            if (due.get(Calendar.DAY_OF_YEAR) != today) {
                due = Calendar.getInstance();
                due.set(Calendar.HOUR_OF_DAY, 23);
                due.set(Calendar.MINUTE, 59);
            }
            schedule.hour = due.get(Calendar.HOUR_OF_DAY);
            schedule.minute = due.get(Calendar.MINUTE);
            new RemoteStore(context).save(config);
            MainActivity activity = (MainActivity) runner.startActivitySync(new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            showReminderCards(activity);
            await(() -> find("Pending. Change today's status for Drink water") != null,
                    "Upcoming reminder has a pending tag");
            click("Pending. Change today's status for Drink water");
            await(() -> find("Today's status") != null, "Pending tag opens status sheet");
            check(find("Today's status: Not completed") == null,
                    "Status sheet excludes Not completed");
            check(find("Today's status: Skip for today") != null,
                    "Status sheet offers Skip for today");
            capture("reminder-status-sheet.png");
            click("Today's status: Completed");
            await(() -> DailyCallStatus.COMPLETED.equals(new DailyCallStatus(context).display(schedule,
                    System.currentTimeMillis()).kind), "Completion applies from status sheet");
            showReminderCards(activity);
            click("Completed. Change today's status for Drink water");
            click("Today's status: Pending");
            await(() -> DailyCallStatus.PENDING.equals(new DailyCallStatus(context).display(schedule,
                    System.currentTimeMillis()).kind), "Pending clears completed status");
            showReminderCards(activity);
            click("Pending. Change today's status for Drink water");
            click("Today's status: Skip for today");
            await(() -> new DailyCallStatus(context).isSkippedToday(schedule.id, System.currentTimeMillis()),
                    "Skip applies from status sheet");
            showReminderCards(activity);
            click("Skipped. Change today's status for Drink water");
            click("Today's status: Pending");
            await(() -> DailyCallStatus.PENDING.equals(new DailyCallStatus(context).display(schedule,
                    System.currentTimeMillis()).kind)
                    && !new DailyCallStatus(context).isSkippedToday(schedule.id, System.currentTimeMillis()),
                    "Pending clears skipped status");
            runner.runOnMainSync(() -> {
                try {
                    Method editor = MainActivity.class.getDeclaredMethod("showScheduleDialog",
                            RemoteStore.Config.class, RemoteStore.Schedule.class);
                    editor.setAccessible(true);
                    editor.invoke(activity, config, schedule);
                } catch (Exception error) { throw new RuntimeException(error); }
            });
            check(find("Today's status") == null, "Reminder editor excludes today's status controls");
            capture("reminder-editor.png");
            result.putString("stream", "PASS: offline mode, direct questions, text responses, information acknowledgment, snooze, skip, timeout retry, call compatibility, meal text, retired confirmation, status tags and sheet.\n");
            runner.finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
            runner.finish(Activity.RESULT_CANCELED, result);
        }
    }

    private RemoteStore.ScriptQuestion question(String prompt, String label, String response) {
        RemoteStore.ScriptQuestion question = new RemoteStore.ScriptQuestion();
        question.prompt = prompt;
        if (label != null) {
            RemoteStore.ScriptAnswer answer = new RemoteStore.ScriptAnswer();
            answer.label = label;
            answer.response = response;
            question.answers.add(answer);
        }
        return question;
    }

    private void reset() {
        Activity screen = screens.getLastActivity();
        if (screen != null) await(screen::isDestroyed, "Previous reminder screen closed");
        runner.waitForIdleSync();
        ReminderScheduler.cancelAll(context, config);
        new DailyCallStatus(context).clearSchedule(schedule.id);
        SystemClock.sleep(1500);
    }

    private void showReminderCards(MainActivity activity) {
        runner.runOnMainSync(() -> {
            try {
                LinearLayout body = new LinearLayout(activity);
                body.setOrientation(LinearLayout.VERTICAL);
                Method cards = MainActivity.class.getDeclaredMethod("addSchedules",
                        LinearLayout.class, RemoteStore.Config.class);
                cards.setAccessible(true);
                cards.invoke(activity, body, config);
                activity.setContentView(body);
            } catch (Exception error) { throw new RuntimeException(error); }
        });
        runner.waitForIdleSync();
    }

    private void start(String phase) {
        context.startForegroundService(new Intent(context, CallService.class).setAction(CallService.ACTION_RING)
                .putExtra(ReminderScheduler.EXTRA_ID, schedule.id)
                .putExtra(ReminderScheduler.EXTRA_PHASE, phase)
                .putExtra("label", schedule.label).putExtra("member", "Test"));
    }

    private void action(String action, int option, int step) {
        context.startService(new Intent(context, CallService.class).setAction(action)
                .putExtra(CallService.EXTRA_OPTION, option).putExtra(CallService.EXTRA_OPTION_STEP, step));
    }

    private boolean session(String key) { return CallService.session(context).getBoolean(key, false); }

    private Notification notification() {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        await(() -> manager.getActiveNotifications().length > 0, "Foreground notification posted");
        return manager.getActiveNotifications()[0].getNotification();
    }

    private void await(BooleanSupplier condition, String message) {
        long until = SystemClock.uptimeMillis() + 20000;
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < until) SystemClock.sleep(100);
        check(condition.getAsBoolean(), message);
    }

    private void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }

    private AccessibilityNodeInfo find(String text) {
        AccessibilityNodeInfo root = runner.getUiAutomation().getRootInActiveWindow();
        if (root == null) return null;
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) {
            if (text.contentEquals(node.getText() == null ? "" : node.getText())
                    || text.contentEquals(node.getContentDescription() == null ? "" : node.getContentDescription())) return node;
        }
        return null;
    }

    private void click(String text) {
        AccessibilityNodeInfo node = find(text);
        check(node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Click " + text);
        SystemClock.sleep(250);
    }

    private void capture(String name) throws Exception {
        SystemClock.sleep(500);
        Bitmap bitmap = runner.getUiAutomation().takeScreenshot();
        try (FileOutputStream output = new FileOutputStream(new File(context.getExternalFilesDir(null), name))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
        bitmap.recycle();
    }
}
