package com.gurthuchey.remindercall;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.function.Consumer;

/** Live daily overview. Updating progress never replaces or scrolls the agenda. */
final class DayOverview extends LinearLayout {
    private final DailyCallStatus statuses;
    private final TextView progress;
    private final ProgressRing ring;
    private final TextView nextLabel;
    private final TextView nextTime;
    private final TextView nextTitle;
    private final TextView nextMode;
    private final LinearLayout nextPanel;
    private final Consumer<RemoteStore.Schedule> open;
    private RemoteStore.Schedule next;

    DayOverview(Context context, DailyCallStatus statuses, Consumer<RemoteStore.Schedule> open) {
        super(context);
        this.statuses = statuses;
        this.open = open;
        setOrientation(VERTICAL);
        setPadding(0, 0, 0, Ui.dp(context, 12));
        String language = AppLanguage.current(context);
        TextView date = Ui.text(context, new SimpleDateFormat("EEEE, d MMMM",
                AppLanguage.locale(language)).format(Calendar.getInstance().getTime()), 11, Ui.MUTED, false);
        addView(date);
        LinearLayout heading = new LinearLayout(context);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words = new LinearLayout(context);
        words.setOrientation(HORIZONTAL);
        words.setGravity(Gravity.CENTER_VERTICAL);
        TextView today = Ui.text(context, "Today", 26, Ui.INK, true);
        today.setLetterSpacing(-.035f);
        words.addView(today);
        progress = Ui.text(context, "", 11, Ui.MUTED, false);
        words.addView(progress, Ui.margins(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, context, 12, 0, 0, 0));
        heading.addView(words, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        ring = new ProgressRing(context);
        heading.addView(ring, new LayoutParams(Ui.dp(context, 34), Ui.dp(context, 34)));
        addView(heading, Ui.margins(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                context, 0, 1, 0, 8));

        nextPanel = new LinearLayout(context);
        nextPanel.setOrientation(VERTICAL);
        nextPanel.setPadding(Ui.dp(context, 14), Ui.dp(context, 9), Ui.dp(context, 14), Ui.dp(context, 9));
        nextPanel.setBackground(Ui.rounded(Ui.ACCENT, 16, context));
        nextPanel.setOnClickListener(v -> { if (next != null) open.accept(next); });
        nextLabel = Ui.text(context, "Next up", 10, Ui.WHITE, true);
        LinearLayout caption = new LinearLayout(context);
        caption.setGravity(Gravity.CENTER_VERTICAL);
        caption.addView(nextLabel, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        nextMode = Ui.text(context, "", 10, Ui.WHITE, false);
        caption.addView(nextMode);
        nextPanel.addView(caption);
        LinearLayout details = new LinearLayout(context);
        details.setGravity(Gravity.CENTER_VERTICAL);
        nextTime = Ui.text(context, "", 23, Ui.WHITE, false);
        nextTime.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        nextTime.setSingleLine(true);
        details.addView(nextTime, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        nextTitle = Ui.text(context, "", 15, Ui.WHITE, true);
        nextTitle.setGravity(Gravity.CENTER_VERTICAL);
        nextTitle.setMaxLines(2);
        nextTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LayoutParams titleParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
        titleParams.setMarginStart(Ui.dp(context, 14));
        details.addView(nextTitle, titleParams);
        details.setMinimumHeight(Ui.dp(context, 34));
        nextPanel.addView(details, Ui.margins(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, context, 0, 3, 0, 0));

        addView(nextPanel, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    void bind(RemoteStore.Config config) {
        long now = System.currentTimeMillis();
        int total = 0, done = 0;
        long nextAt = Long.MAX_VALUE;
        next = null;
        if (config != null) for (RemoteStore.Schedule item : config.schedules) {
            if (!item.enabled || !scheduledToday(item, now)) continue;
            total++;
            String kind = statuses.display(item, now).kind;
            if (DailyCallStatus.COMPLETED.equals(kind)) done++;
            if (DailyCallStatus.COMPLETED.equals(kind) || DailyCallStatus.SKIPPED.equals(kind)) continue;
            Calendar at = Calendar.getInstance();
            at.setTimeInMillis(now);
            at.set(Calendar.HOUR_OF_DAY, item.hour);
            at.set(Calendar.MINUTE, item.minute);
            at.set(Calendar.SECOND, 0);
            at.set(Calendar.MILLISECOND, 0);
            if (at.getTimeInMillis() >= now - 60_000L && at.getTimeInMillis() < nextAt) {
                next = item;
                nextAt = at.getTimeInMillis();
            }
        }
        String language = AppLanguage.current(getContext());
        progress.setText(String.format(AppLanguage.locale(language),
                AppLanguage.ui(language, "%1$d of %2$d completed"), done, total));
        ring.update(done, total);
        nextPanel.setEnabled(next != null);
        nextLabel.setText(AppLanguage.ui(language, next == null ? "At your pace" : "Next up"));
        if (next == null) {
            nextTime.setText("✓");
            nextTitle.setText(AppLanguage.ui(language, "No more scheduled today"));
            nextMode.setText(AppLanguage.ui(language, "Your agenda is always here below."));
            nextPanel.setContentDescription(nextTitle.getText());
        } else {
            nextTime.setText(new SimpleDateFormat(
                    android.text.format.DateFormat.is24HourFormat(getContext()) ? "HH:mm" : "h:mm a",
                    AppLanguage.locale(language)).format(nextAt));
            nextTitle.setText(next.label);
            nextMode.setText(AppLanguage.ui(language, next.textReminder() ? "Read & respond" : "Answer & listen"));
            nextPanel.setContentDescription(AppLanguage.ui(language, "Next up") + ": " + next.label + ", " + nextTime.getText());
        }
    }

    static boolean scheduledToday(RemoteStore.Schedule schedule, long now) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        return (schedule.days & (1 << ((calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7))) != 0;
    }

    private static final class ProgressRing extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int done, total;
        ProgressRing(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        void update(int done, int total) { this.done = done; this.total = total; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            float inset = Ui.dp(getContext(), 4);
            RectF oval = new RectF(inset, inset, getWidth() - inset, getHeight() - inset);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Ui.dp(getContext(), 4));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(Ui.PRIMARY);
            canvas.drawOval(oval, paint);
            paint.setColor(Ui.ACCENT);
            if (total > 0) canvas.drawArc(oval, -90, 360f * done / total, false, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            paint.setTextSize(Ui.dp(getContext(), 12));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(Ui.ACCENT);
            canvas.drawText(total == 0 ? "—" : Integer.toString(done), getWidth() / 2f,
                    getHeight() / 2f - (paint.ascent() + paint.descent()) / 2f, paint);
        }
    }
}
