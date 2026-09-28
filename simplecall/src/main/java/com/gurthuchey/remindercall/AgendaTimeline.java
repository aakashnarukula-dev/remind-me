package com.gurthuchey.remindercall;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** One continuous rail across reminder rows and intervening period headings. */
final class AgendaTimeline extends LinearLayout {
    static final int TIME_WIDTH = 66;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<View> rows = new ArrayList<>();
    private final List<Integer> times = new ArrayList<>();
    private final List<Boolean> inactive = new ArrayList<>();

    AgendaTimeline(Context context) {
        super(context);
        setOrientation(VERTICAL);
    }

    void addReminder(View row, int minute, boolean notToday) {
        rows.add(row);
        times.add(minute);
        inactive.add(notToday);
        addView(row, Ui.matchWrap());
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        if (!rows.isEmpty()) {
            Calendar now = Calendar.getInstance();
            int current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
            int[] minutes = new int[times.size()];
            for (int i = 0; i < minutes.length; i++) minutes[i] = times.get(i);
            float position = TimelineProgress.position(minutes, current);
            float x = Ui.dp(getContext(), TIME_WIDTH + 6);
            float first = center(0), last = center(rows.size() - 1);
            paint.setStrokeWidth(Ui.dp(getContext(), 2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(Ui.LINE);
            canvas.drawLine(x, first, x, last, paint);
            if (position >= 0f) {
                int index = (int) position;
                float end = center(index);
                if (index + 1 < rows.size()) end += (center(index + 1) - end) * (position - index);
                paint.setColor(Ui.ACCENT);
                canvas.drawLine(x, first, x, end, paint);
            }
            for (int i = 0; i < rows.size(); i++) {
                paint.setColor(!inactive.get(i) && minutes[i] <= current ? Ui.ACCENT : Ui.LINE);
                canvas.drawCircle(x, center(i), Ui.dp(getContext(), 7) / 2f, paint);
            }
        }
        super.dispatchDraw(canvas);
    }

    private float center(int index) {
        View row = rows.get(index);
        return row.getTop() + row.getHeight() / 2f;
    }
}
