package com.gurthuchey.remindercall;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.Calendar;

/** Offline design fixtures; never runs against a member's physical device. */
final class DesignPreview {
    static void run(Instrumentation runner, String language) {
        Bundle result = new Bundle();
        try {
            if (!Build.FINGERPRINT.contains("generic") && !Build.MODEL.contains("sdk"))
                throw new IllegalStateException("Design fixtures require a disposable emulator");
            android.content.Context context = runner.getTargetContext();
            AppLanguage.set(context, language == null ? "en" : language);
            RemoteStore.Config config = new RemoteStore.Config();
            config.memberId = "design-preview";
            config.memberName = "You";
            String[] names = {"Morning stretch", "Drink a glass of water", "Lunch break", "Take a little walk", "Plan tomorrow", "Wind down"};
            String[] categories = {"exercise", "drink", "meal", "exercise", "task", "custom"};
            int[] hours = {7, 10, 13, 17, 20, 21};
            for (int i = 0; i < names.length; i++) {
                RemoteStore.Schedule item = new RemoteStore.Schedule();
                item.id = "preview-" + i;
                item.label = names[i];
                item.category = categories[i];
                item.deliveryMode = i % 2 == 0 ? "call" : "reminder";
                item.days = 127;
                item.hour = hours[i];
                item.minute = i == 3 ? 30 : 0;
                item.enabled = true;
                RemoteStore.ScriptQuestion question = new RemoteStore.ScriptQuestion();
                question.prompt = "Have you finished " + names[i].toLowerCase() + "?";
                RemoteStore.ScriptAnswer answer = new RemoteStore.ScriptAnswer();
                answer.label = "Yes";
                answer.response = "All done.";
                question.answers.add(answer);
                item.questions.add(question);
                config.schedules.add(item);
            }
            Calendar next = Calendar.getInstance();
            next.add(Calendar.MINUTE, 45);
            config.schedules.get(3).hour = next.get(Calendar.HOUR_OF_DAY);
            config.schedules.get(3).minute = next.get(Calendar.MINUTE);
            new RemoteStore(context).save(config);
            DailyCallStatus status = new DailyCallStatus(context);
            for (RemoteStore.Schedule item : config.schedules) status.clearSchedule(item.id);
            status.markCompleted("preview-0", ReminderScheduler.PHASE_MEDICINE);
            status.markSkippedToday("preview-1");
            MainActivity activity = (MainActivity) runner.startActivitySync(new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            invoke(runner, activity, "renderHome", new Class<?>[]{RemoteStore.Config.class}, config);
            verifyFilters(runner, activity, config);
            capture(runner, "design-home.png");
            invoke(runner, activity, "showDailyStatusDialog", new Class<?>[]{RemoteStore.Schedule.class}, config.schedules.get(1));
            capture(runner, "design-status.png");
            AccessibilityNodeInfo root = runner.getUiAutomation().getRootInActiveWindow();
            for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText("Close status editor")) {
                if ("Close status editor".contentEquals(node.getContentDescription())) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    break;
                }
            }
            runner.waitForIdleSync();
            invoke(runner, activity, "showScheduleDialog", new Class<?>[]{RemoteStore.Config.class, RemoteStore.Schedule.class}, config, config.schedules.get(3));
            capture(runner, "design-editor.png");
            result.putString("stream", "PASS: All/Today filters verified; home, status and editor design previews captured.\n");
            runner.finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
            runner.finish(Activity.RESULT_CANCELED, result);
        }
    }
    private static void verifyFilters(Instrumentation runner, MainActivity activity, RemoteStore.Config config) {
        RemoteStore.Schedule otherDay = new RemoteStore.Schedule();
        otherDay.id = "preview-other-day";
        otherDay.label = "Other day fixture";
        otherDay.enabled = true;
        int today = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7;
        otherDay.days = 1 << ((today + 1) % 7);
        config.schedules.add(otherDay);
        invoke(runner, activity, "renderHome", new Class<?>[]{RemoteStore.Config.class}, config);
        runner.runOnMainSync(() -> {
            View root = activity.getWindow().getDecorView();
            if (findText(root, otherDay.label) == null) throw new AssertionError("All reminders includes other days");
            findText(root, AppLanguage.ui(activity, "Today only")).performClick();
            root = activity.getWindow().getDecorView();
            if (findText(root, otherDay.label) != null) throw new AssertionError("Today only excludes other days");
            if (findText(root, "Morning stretch") == null) throw new AssertionError("Today retains today's reminders");
            findText(root, AppLanguage.ui(activity, "All reminders")).performClick();
        });
        config.schedules.remove(otherDay);
        invoke(runner, activity, "renderHome", new Class<?>[]{RemoteStore.Config.class}, config);
    }
    private static TextView findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
    private static void invoke(Instrumentation runner, MainActivity activity, String name, Class<?>[] types, Object... args) {
        runner.runOnMainSync(() -> {
            try {
                Method method = MainActivity.class.getDeclaredMethod(name, types);
                method.setAccessible(true);
                method.invoke(activity, args);
            } catch (Exception error) { throw new RuntimeException(error); }
        });
        runner.waitForIdleSync();
    }
    private static void capture(Instrumentation runner, String name) throws Exception {
        SystemClock.sleep(800);
        Bitmap image = runner.getUiAutomation().takeScreenshot();
        try (FileOutputStream output = new FileOutputStream(new File(runner.getTargetContext().getExternalFilesDir(null), name))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
        image.recycle();
    }
}
