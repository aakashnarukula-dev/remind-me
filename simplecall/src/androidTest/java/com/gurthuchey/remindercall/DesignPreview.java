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
import android.view.MotionEvent;
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
            RemoteStore.Schedule otherDay = new RemoteStore.Schedule();
            otherDay.id = "preview-other-day";
            otherDay.label = "Other day reminder";
            otherDay.category = "task";
            otherDay.hour = 8;
            otherDay.minute = 30;
            otherDay.enabled = true;
            int today = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7;
            otherDay.days = 1 << ((today + 1) % 7);
            config.schedules.add(otherDay);
            new RemoteStore(context).save(config);
            DailyCallStatus status = new DailyCallStatus(context);
            for (RemoteStore.Schedule item : config.schedules) status.clearSchedule(item.id);
            status.markCompleted("preview-0", ReminderScheduler.PHASE_MEDICINE);
            status.markSkippedToday("preview-1");
            MainActivity activity = (MainActivity) runner.startActivitySync(new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            invoke(runner, activity, "renderHome", new Class<?>[]{RemoteStore.Config.class}, config);
            verifyMinimalHome(runner, activity, otherDay);
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
            result.putString("stream", "PASS: minimal home, disabled status tag, and editable off-day row verified; previews captured.\n");
            runner.finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
            runner.finish(Activity.RESULT_CANCELED, result);
        }
    }
    private static void verifyMinimalHome(Instrumentation runner, MainActivity activity, RemoteStore.Schedule otherDay) {
        View[] targets = new View[2];
        runner.runOnMainSync(() -> {
            View root = activity.getWindow().getDecorView();
            for (String removed : new String[]{"Today only", "All reminders", "Your reminders", "Next up", "Today"}) {
                if (findText(root, AppLanguage.ui(activity, removed)) != null)
                    throw new AssertionError("Removed home section remains: " + removed);
            }
            TextView tag = findText(root, DailyCallStatus.notTodayLabel(AppLanguage.current(activity)));
            if (tag == null || tag.isEnabled()) throw new AssertionError("Off-day tag must be disabled");
            TextView name = findText(root, otherDay.label);
            if (name == null) throw new AssertionError("Off-day reminder remains visible");
            View row = (View) name.getParent().getParent().getParent();
            if (!row.isEnabled() || !row.isClickable() || row.getAlpha() >= 1f)
                throw new AssertionError("Off-day reminder must be dimmed but editable");
            targets[0] = tag;
            targets[1] = name;
        });
        tap(runner, targets[0]);
        if (findDescription(runner, "Close status editor") != null || findDescription(runner, "Close reminder editor") != null)
            throw new AssertionError("Disabled tag must not open any sheet or trigger parent");
        tap(runner, targets[1]);
        AccessibilityNodeInfo close = findDescription(runner, "Close reminder editor");
        if (close == null) throw new AssertionError("Off-day reminder still opens editor");
        close.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        runner.waitForIdleSync();
    }
    private static void tap(Instrumentation runner, View target) {
        int[] point = new int[2];
        runner.runOnMainSync(() -> {
            target.getLocationOnScreen(point);
            point[0] += target.getWidth() / 2;
            point[1] += target.getHeight() / 2;
        });
        long time = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, point[0], point[1], 0);
        MotionEvent up = MotionEvent.obtain(time, time + 40, MotionEvent.ACTION_UP, point[0], point[1], 0);
        runner.sendPointerSync(down);
        runner.sendPointerSync(up);
        down.recycle();
        up.recycle();
        runner.waitForIdleSync();
        SystemClock.sleep(300);
    }
    private static AccessibilityNodeInfo findDescription(Instrumentation runner, String description) {
        AccessibilityNodeInfo root = runner.getUiAutomation().getRootInActiveWindow();
        if (root == null) return null;
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(description)) {
            if (description.contentEquals(node.getContentDescription())) return node;
        }
        return null;
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
