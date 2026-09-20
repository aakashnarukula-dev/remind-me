package com.gurthuchey.remindercall;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class CallActivity extends android.app.Activity {
    private final BroadcastReceiver updates = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { render(); }
    };
    private boolean registered;
    private boolean optionSubmitting;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        configureCallWindow();
        handleLaunchAction(getIntent());
        render();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleLaunchAction(intent);
    }

    private void handleLaunchAction(Intent intent) {
        if (intent == null || !CallService.ACTION_ANSWER.equals(intent.getAction())) return;
        intent.setAction(null);
        send(CallService.ACTION_ANSWER, -1);
    }

    private void configureCallWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND
                | WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        View decor = window.getDecorView();
        int lightSystemBars = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        }
        // Window#getInsetsController can throw on some Samsung builds before the decor view is
        // attached. The decor flags are available on every supported Android version and avoid
        // that launch-time crash while preserving light system-bar icons.
        decor.setSystemUiVisibility(lightSystemBars);
        if (Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(CallService.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(updates, filter, RECEIVER_NOT_EXPORTED);
        else registerReceiver(updates, filter);
        registered = true;
        render();
    }

    @Override protected void onStop() {
        if (registered) {
            unregisterReceiver(updates);
            registered = false;
        }
        super.onStop();
    }

    @Override protected void onResume() {
        super.onResume();
        send(CallService.ACTION_CALL_SCREEN_VISIBLE, -1);
        render();
    }

    @Override protected void onPause() {
        if (CallService.session(this).getBoolean("active", false)) {
            send(CallService.ACTION_CALL_SCREEN_HIDDEN, -1);
        }
        super.onPause();
    }

    @Override public void onBackPressed() {
        moveTaskToBack(true);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && (key == KeyEvent.KEYCODE_VOLUME_UP
                || key == KeyEvent.KEYCODE_VOLUME_DOWN
                || key == KeyEvent.KEYCODE_VOLUME_MUTE)) {
            send(CallService.ACTION_SILENCE, -1);
        }
        return super.dispatchKeyEvent(event);
    }

    private void render() {
        optionSubmitting = false;
        SharedPreferences session = CallService.session(this);
        if (!session.getBoolean("active", false)) {
            finishAndRemoveTask();
            return;
        }
        if (session.getBoolean("answered", false)) {
            renderOngoing(session.getInt("step", 0), session.getInt("branch", 0));
        }
        else renderIncoming();
    }

    private void renderIncoming() {
        String language = callLanguage();
        String reminderTitle = reminderTitle(language);
        LinearLayout page = basePage();
        page.setPadding(Ui.dp(this, 22), Ui.dp(this, 18),
                Ui.dp(this, 22), Ui.dp(this, 24));
        Ui.safeArea(page, true, true, true, true);
        page.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView status = centered("●  " + reminderTitle,
                14, Ui.GARDEN_INK, true);
        status.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        status.setBackground(Ui.glass(this, 22));
        page.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 42)));

        page.addView(Ui.spacer(this, 58));
        page.addView(medicineMark(126), new LinearLayout.LayoutParams(
                Ui.dp(this, 126), Ui.dp(this, 126)));

        TextView caller = centered(AppLanguage.caller(language), 34, Ui.GARDEN_INK, true);
        caller.setPadding(Ui.dp(this, 12), Ui.dp(this, 30), Ui.dp(this, 12), 0);
        page.addView(caller, Ui.matchWrap());
        TextView incoming = centered(reminderTitle, 17,
                Ui.GARDEN_MUTED, false);
        incoming.setPadding(0, Ui.dp(this, 7), 0, 0);
        page.addView(incoming, Ui.matchWrap());

        View space = new View(this);
        page.addView(space, new LinearLayout.LayoutParams(1, 0, 1));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(Ui.dp(this, 14), Ui.dp(this, 18),
                Ui.dp(this, 14), Ui.dp(this, 16));
        actions.setBackground(Ui.glass(this, 28));
        actions.setClipChildren(false);
        actions.setClipToPadding(false);
        LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        actions.addView(callControl("☎", "Reject", Ui.REJECT, CallService.ACTION_REJECT),
                controlParams);
        actions.addView(callControl("☎", "Answer", Ui.ACCEPT, CallService.ACTION_ANSWER),
                controlParams);
        page.addView(actions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(page);
    }

    private String reminderTitle(String language) {
        return CallService.session(this).getString("reminderTitle",
                AppLanguage.ui(language, "Reminder call"));
    }

    private void renderOngoing(int step, int branch) {
        SharedPreferences session = CallService.session(this);
        String language = callLanguage();
        LinearLayout page = basePage();
        page.setPadding(Ui.dp(this, 20), Ui.dp(this, 16),
                Ui.dp(this, 20), Ui.dp(this, 22));
        Ui.safeArea(page, true, true, true, true);

        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 54));
        headerParams.gravity = Gravity.CENTER_HORIZONTAL;
        page.addView(connectedHeader(AppLanguage.caller(language),
                session.getBoolean("speakerOn", false)), headerParams);

        View space = new View(this);
        page.addView(space, new LinearLayout.LayoutParams(1, 0, 1));

        LinearLayout interactionSheet = new LinearLayout(this);
        interactionSheet.setOrientation(LinearLayout.VERTICAL);
        interactionSheet.setPadding(Ui.dp(this, 22), Ui.dp(this, 26),
                Ui.dp(this, 22), Ui.dp(this, 10));
        interactionSheet.setBackground(Ui.glass(this, 34));
        interactionSheet.setElevation(Ui.dp(this, 5));

        String questionText = session.getString("question", "Reminder call");
        String answerA = session.getString("answerA", "Yes");
        String answerB = session.getString("answerB", "No");
        String answerC = session.getString("answerC", "");
        String answerD = session.getString("answerD", "");
        String remindLaterLabel = session.getString("remindLaterLabel",
                SpeechText.answerLater(language));
        boolean showRemindLater = session.getBoolean("showRemindLater", false);
        int customAnswerCount = session.getInt("customAnswerCount", 0);
        boolean customCall = session.getBoolean("customCall", false);
        TextView question = Ui.text(this, "", 25, Ui.GARDEN_INK, true);
        question.setText(highlightButtonLabels(questionText, customCall,
                answerA, answerB, answerC, answerD,
                showRemindLater ? remindLaterLabel : ""));
        question.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        question.setLineSpacing(0, 1.18f);
        interactionSheet.addView(question, Ui.matchWrap());
        interactionSheet.addView(Ui.spacer(this, 24));

        if (session.getBoolean("showDelayOptions", false)
                && !session.getBoolean("finalizing", false)) {
            interactionSheet.addView(delayOptions(step), optionParams());
        } else {
            String[] answers = {answerA, answerB, answerC, answerD};
            for (int index = 0; index < answers.length; index++) {
                if (answers[index] != null && !answers[index].isEmpty()) {
                    interactionSheet.addView(option(answers[index], index, step, customCall),
                            optionParams());
                }
            }
            if (showRemindLater) {
                interactionSheet.addView(remindLaterOption(remindLaterLabel,
                        customAnswerCount, step), optionParams());
            }
        }
        page.addView(interactionSheet, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        page.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
        setContentView(page);
    }

    private CharSequence highlightButtonLabels(String question, boolean customCall,
            String... answers) {
        SpannableString highlighted = new SpannableString(question);
        for (int index = 0; index < answers.length; index++) {
            highlightLabel(highlighted, question, answers[index],
                    customCall ? Ui.NAVY : index == 0 ? Ui.ACCEPT : Ui.REJECT);
        }
        return highlighted;
    }

    private void highlightLabel(SpannableString highlighted, String question,
            String buttonLabel, int color) {
        if (buttonLabel == null) return;
        String label = buttonLabel.replaceFirst("^[✓✕×]\\s*", "").trim();
        if (label.isEmpty()) return;
        int start = question.indexOf(label);
        while (start >= 0) {
            int end = start + label.length();
            highlighted.setSpan(new ForegroundColorSpan(color), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            highlighted.setSpan(new BackgroundColorSpan(Color.argb(28,
                            Color.red(color), Color.green(color), Color.blue(color))),
                    start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            start = question.indexOf(label, end);
        }
    }

    private LinearLayout basePage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setBackground(Ui.callBackdrop());
        page.setClipChildren(false);
        page.setClipToPadding(false);
        return page;
    }

    private ImageView medicineMark(int sizeDp) {
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.mipmap.ic_launcher);
        int padding = Ui.dp(this, Math.max(8, Math.round(sizeDp * 0.18f)));
        mark.setPadding(padding, padding, padding, padding);
        mark.setBackground(Ui.glassCircle(this));
        mark.setElevation(Ui.dp(this, 3));
        mark.setContentDescription("Reminder call");
        return mark;
    }

    private LinearLayout connectedHeader(String member, boolean speakerOn) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(this, 16), Ui.dp(this, 9),
                Ui.dp(this, 16), Ui.dp(this, 9));
        header.setBackground(Ui.glass(this, 24));

        TextView dot = centered("●", 13, Color.rgb(48, 155, 105), true);
        header.addView(dot, new LinearLayout.LayoutParams(Ui.dp(this, 22),
                LinearLayout.LayoutParams.MATCH_PARENT));
        header.addView(Ui.text(this, member, 16, Ui.GARDEN_INK, true));

        ImageView speaker = new ImageView(this);
        speaker.setImageResource(R.drawable.ic_speaker);
        speaker.setColorFilter(speakerOn ? Ui.ACCEPT : Ui.GARDEN_MUTED);
        speaker.setPadding(Ui.dp(this, 8), Ui.dp(this, 8),
                Ui.dp(this, 8), Ui.dp(this, 8));
        speaker.setBackground(Ui.actionBackground(this,
                speakerOn ? Ui.ACCEPT_LIGHT : Color.argb(105, 255, 255, 255), 18));
        speaker.setContentDescription(AppLanguage.ui(callLanguage(),
                speakerOn ? "Turn speaker off" : "Turn speaker on"));
        speaker.setOnClickListener(view -> send(CallService.ACTION_TOGGLE_SPEAKER, -1));
        LinearLayout.LayoutParams speakerParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 36), Ui.dp(this, 36));
        speakerParams.setMarginStart(Ui.dp(this, 12));
        header.addView(speaker, speakerParams);
        return header;
    }

    private TextView centered(String value, float size, int color, boolean bold) {
        TextView text = Ui.text(this, value, size, color, bold);
        text.setGravity(Gravity.CENTER);
        return text;
    }

    private LinearLayout callControl(String icon, String label, int color, String action) {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setGravity(Gravity.CENTER);
        holder.setClipChildren(false);
        holder.setClipToPadding(false);
        TextView circle = centered(icon, 31, Color.WHITE, true);
        circle.setRotation(CallService.ACTION_REJECT.equals(action) ? 135 : 0);
        circle.setBackground(Ui.actionBackground(this, color, 40));
        circle.setElevation(Ui.dp(this, 3));
        boolean reject = CallService.ACTION_REJECT.equals(action);
        circle.setContentDescription(AppLanguage.ui(callLanguage(),
                reject ? "Swipe to reject" : "Swipe to answer"));
        enableCallSwipe(circle, action);
        holder.addView(circle, new LinearLayout.LayoutParams(Ui.dp(this, 78), Ui.dp(this, 78)));
        TextView caption = centered(AppLanguage.ui(callLanguage(),
                        reject ? "Swipe to reject" : "Swipe to answer"),
                15, Ui.GARDEN_INK, true);
        caption.setPadding(0, Ui.dp(this, 10), 0, 0);
        holder.addView(caption);
        return holder;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void enableCallSwipe(View control, String action) {
        final float[] downX = new float[1];
        final float[] downY = new float[1];
        final boolean[] completed = new boolean[1];
        final float maximum = Ui.dp(this, 58);
        final float threshold = Ui.dp(this, 38);
        control.setOnTouchListener((view, event) -> {
            if (completed[0]) return true;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX[0] = event.getRawX();
                    downY[0] = event.getRawY();
                    view.animate().cancel();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float deltaX = event.getRawX() - downX[0];
                    float deltaY = event.getRawY() - downY[0];
                    float distance = (float) Math.hypot(deltaX, deltaY);
                    float limited = Math.min(maximum, distance);
                    float ratio = distance == 0f ? 0f : limited / distance;
                    view.setTranslationX(deltaX * ratio);
                    view.setTranslationY(deltaY * ratio);
                    float progress = limited / maximum;
                    view.setScaleX(1f + .05f * progress);
                    view.setScaleY(1f + .05f * progress);
                    return true;
                case MotionEvent.ACTION_UP:
                    float travelledX = event.getRawX() - downX[0];
                    float travelledY = event.getRawY() - downY[0];
                    float travelled = (float) Math.hypot(travelledX, travelledY);
                    if (travelled >= threshold) {
                        completed[0] = true;
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        float endX = travelledX / travelled * maximum;
                        float endY = travelledY / travelled * maximum;
                        view.animate().translationX(endX).translationY(endY).alpha(0f)
                                .setDuration(120L)
                                .withEndAction(() -> send(action, -1)).start();
                    } else {
                        resetSwipeControl(view);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    resetSwipeControl(view);
                    return true;
                default:
                    return false;
            }
        });
    }

    private void resetSwipeControl(View view) {
        view.animate().translationX(0f).translationY(0f)
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(180L).start();
    }

    private TextView option(String label, int index, int expectedStep, boolean customCall) {
        TextView option = centered(label, 18, customCall ? Ui.INK : Color.WHITE, true);
        option.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        option.setBackground(Ui.actionBackground(this,
                customCall ? Ui.PRIMARY : index == 0 ? Ui.ACCEPT : Ui.REJECT, 22));
        option.setElevation(Ui.dp(this, 3));
        option.setOnClickListener(v -> submitOption(v, index, expectedStep));
        return option;
    }

    private TextView remindLaterOption(String label, int index, int expectedStep) {
        TextView option = centered(label, 18, Ui.GARDEN_INK, true);
        option.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        option.setBackground(Ui.actionBackground(this, Ui.ACCEPT_LIGHT, 22));
        option.setElevation(Ui.dp(this, 2));
        option.setOnClickListener(v -> submitOption(v, index, expectedStep));
        return option;
    }

    private LinearLayout.LayoutParams optionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 64));
        params.bottomMargin = Ui.dp(this, 14);
        return params;
    }

    private LinearLayout delayOptions(int expectedStep) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = new String[CallService.DELAY_MINUTES.length];
        for (int index = 0; index < labels.length; index++) {
            labels[index] = CallService.DELAY_MINUTES[index] + " " + minuteUnit(callLanguage());
        }
        for (int index = 0; index < labels.length; index++) {
            TextView button = centered(labels[index], 12, Ui.GARDEN_INK, true);
            button.setBackground(Ui.actionBackground(this, Ui.ACCEPT_LIGHT, 18));
            final int optionIndex = index;
            button.setOnClickListener(v -> selectDelayOption(button, optionIndex, expectedStep));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            if (index < labels.length - 1) params.setMarginEnd(Ui.dp(this, 7));
            row.addView(button, params);
        }
        return row;
    }

    private void selectDelayOption(TextView button, int option, int expectedStep) {
        if (optionSubmitting) return;
        optionSubmitting = true;
        button.setTextColor(Color.WHITE);
        button.setBackground(Ui.actionBackground(this, Ui.ACCEPT, 18));
        button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        button.postDelayed(() -> dispatchOption(option, expectedStep), 140L);
    }

    private void submitOption(View view, int option, int expectedStep) {
        if (optionSubmitting) return;
        optionSubmitting = true;
        view.setEnabled(false);
        dispatchOption(option, expectedStep);
    }

    private void dispatchOption(int option, int expectedStep) {
        Intent intent = new Intent(this, CallService.class).setAction(CallService.ACTION_OPTION)
                .putExtra(CallService.EXTRA_OPTION, option)
                .putExtra(CallService.EXTRA_OPTION_STEP, expectedStep);
        startService(intent);
    }

    private void send(String action, int option) {
        Intent intent = new Intent(this, CallService.class).setAction(action);
        if (option >= 0) intent.putExtra(CallService.EXTRA_OPTION, option);
        startService(intent);
    }

    private String callLanguage() {
        return AppLanguage.normalize(CallService.session(this)
                .getString("language", AppLanguage.current(this)));
    }

    private String minuteUnit(String language) {
        switch (AppLanguage.normalize(language)) {
            case "te": return "నిమి"; case "hi": return "मिनट"; case "ta": return "நிமி";
            case "kn": return "ನಿಮಿ"; case "ml": return "മിനിറ്റ്"; default: return "min";
        }
    }
}
