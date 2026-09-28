package com.gurthuchey.remindercall;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int BG = Color.rgb(246, 245, 251);
    static final int RAISED = Color.rgb(238, 234, 248);
    static final int RAISED2 = Color.WHITE;
    static final int INK = Color.rgb(39, 36, 61);
    static final int MUTED = Color.rgb(111, 107, 130);
    static final int LINE = Color.rgb(226, 222, 237);
    static final int ACCENT = Color.rgb(102, 82, 204);
    static final int GOLD = ACCENT;
    static final int PRIMARY = Color.rgb(234, 229, 252);
    static final int DANGER = Color.rgb(167, 71, 96);
    static final int NAVY = Color.rgb(74, 56, 161);
    static final int SURFACE = BG;
    static final int WHITE = Color.WHITE;
    static final int ACCEPT = Color.rgb(39, 117, 102);
    static final int ACCEPT_LIGHT = Color.rgb(223, 242, 233);
    static final int REJECT = DANGER;
    static final int AMBER = Color.rgb(161, 111, 43);
    static final int GARDEN_INK = INK;
    static final int GARDEN_MUTED = MUTED;

    private Ui() {}

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context context, String value, float size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(AppLanguage.ui(context, value));
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        view.setLineSpacing(0, 1.12f);
        return view;
    }

    static GradientDrawable rounded(int color, int radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    static GradientDrawable topRounded(int color, int radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        float radius = dp(context, radiusDp);
        drawable.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        return drawable;
    }

    static GradientDrawable roundedWithStroke(int color, int radiusDp,
            int strokeColor, int strokeDp, Context context) {
        GradientDrawable drawable = rounded(color, radiusDp, context);
        drawable.setStroke(dp(context, strokeDp), strokeColor);
        return drawable;
    }

    static Drawable dropdownField(Context context) {
        return new DropdownFieldDrawable(context);
    }

    private static final class DropdownFieldDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;

        DropdownFieldDrawable(Context context) {
            density = context.getResources().getDisplayMetrics().density;
        }

        @Override
        public void draw(Canvas canvas) {
            RectF bounds = new RectF(getBounds());
            float inset = density;
            bounds.inset(inset, inset);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(RAISED);
            canvas.drawRoundRect(bounds, 14 * density, 14 * density, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(density);
            paint.setColor(LINE);
            canvas.drawRoundRect(bounds, 14 * density, 14 * density, paint);

            float centerX = bounds.right - 22 * density;
            float centerY = bounds.centerY();
            paint.setStrokeWidth(2.25f * density);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(INK);
            canvas.drawLine(centerX - 5 * density, centerY - 2 * density,
                    centerX, centerY + 3 * density, paint);
            canvas.drawLine(centerX, centerY + 3 * density,
                    centerX + 5 * density, centerY - 2 * density, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) {
            paint.setColorFilter(filter);
        }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    static Drawable callBackdrop() {
        return new DaylightBackdrop();
    }

    static GradientDrawable glass(Context context, int radiusDp) {
        return roundedWithStroke(WHITE, radiusDp, LINE, 1, context);
    }

    static RippleDrawable actionBackground(Context context, int color, int radiusDp) {
        return new RippleDrawable(
                ColorStateList.valueOf(Color.argb(70, 255, 255, 255)),
                roundedWithStroke(color, radiusDp, Color.argb(72, 255, 255, 255),
                        1, context), null);
    }

    static GradientDrawable circle(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setShape(GradientDrawable.OVAL);
        return drawable;
    }

    static GradientDrawable glassCircle(Context context) {
        GradientDrawable drawable = circle(PRIMARY);
        drawable.setStroke(dp(context, 1), Color.argb(242, 255, 255, 255));
        return drawable;
    }

    static Drawable reminderMark() { return new ReminderMark(); }

    private static final class ReminderMark extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas canvas) {
            RectF area = new RectF(getBounds());
            float size = Math.min(area.width(), area.height());
            float cx = area.centerX(), cy = area.centerY();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ACCENT);
            canvas.drawRoundRect(area, size * .30f, size * .30f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(WHITE);
            paint.setStrokeWidth(size * .065f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawArc(cx - size * .27f, cy - size * .27f,
                    cx + size * .27f, cy + size * .27f, -60, 310, false, paint);
            canvas.drawLine(cx, cy - size * .15f, cx, cy + size * .015f, paint);
            canvas.drawLine(cx, cy + size * .015f, cx + size * .13f, cy + size * .09f, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(246, 182, 144));
            canvas.drawCircle(cx + size * .26f, cy - size * .24f, size * .075f, paint);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** A simple clock orbit keeps reminder screens recognizable and fully offline. */
    private static final class DaylightBackdrop extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas canvas) {
            canvas.drawColor(BG);
            float width = getBounds().width();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1, width * .003f));
            paint.setColor(Color.rgb(231, 225, 247));
            canvas.drawCircle(width * .86f, width * .15f, width * .60f, paint);
            canvas.drawCircle(width * .86f, width * .15f, width * .76f, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(246, 182, 144));
            canvas.drawCircle(width * .29f, width * .33f, width * .014f, paint);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams margins(int width, int height, Context context,
            int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(context, left), dp(context, top),
                dp(context, right), dp(context, bottom));
        return params;
    }

    static View spacer(Context context, int heightDp) {
        View view = new View(context);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(context, heightDp)));
        return view;
    }

    static void safeArea(View view, boolean left, boolean top, boolean right, boolean bottom) {
        int baseLeft = view.getPaddingLeft();
        int baseTop = view.getPaddingTop();
        int baseRight = view.getPaddingRight();
        int baseBottom = view.getPaddingBottom();
        view.setOnApplyWindowInsetsListener((target, windowInsets) -> {
            int insetLeft;
            int insetTop;
            int insetRight;
            int insetBottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets insets = windowInsets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                insetLeft = insets.left;
                insetTop = insets.top;
                insetRight = insets.right;
                insetBottom = insets.bottom;
            } else {
                insetLeft = windowInsets.getSystemWindowInsetLeft();
                insetTop = windowInsets.getSystemWindowInsetTop();
                insetRight = windowInsets.getSystemWindowInsetRight();
                insetBottom = windowInsets.getSystemWindowInsetBottom();
            }
            target.setPadding(
                    baseLeft + (left ? insetLeft : 0),
                    baseTop + (top ? insetTop : 0),
                    baseRight + (right ? insetRight : 0),
                    baseBottom + (bottom ? insetBottom : 0));
            return windowInsets;
        });
        view.requestApplyInsets();
    }
}
