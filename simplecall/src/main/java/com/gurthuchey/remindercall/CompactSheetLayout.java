package com.gurthuchey.remindercall;

import android.content.Context;
import android.widget.LinearLayout;

/** Wraps short forms; limits longer forms so their weighted scroll area can shrink. */
final class CompactSheetLayout extends LinearLayout {
    CompactSheetLayout(Context context) { super(context); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int limit = Math.round(getResources().getDisplayMetrics().heightPixels * .72f);
        if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED)
            limit = Math.min(limit, MeasureSpec.getSize(heightSpec));
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST));
    }
}
