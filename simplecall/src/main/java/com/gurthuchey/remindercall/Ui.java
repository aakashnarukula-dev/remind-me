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
    static final int BG = Color.rgb(247, 250, 252);
    static final int RAISED = Color.rgb(234, 244, 248);
    static final int RAISED2 = Color.rgb(255, 255, 255);
    static final int INK = Color.rgb(36, 51, 74);
    static final int MUTED = Color.rgb(107, 123, 143);
    static final int LINE = Color.rgb(214, 227, 236);
    static final int GOLD = Color.rgb(240, 210, 154);
    static final int PRIMARY = Color.rgb(185, 221, 240);
    static final int DANGER = Color.rgb(186, 89, 104);
    static final int NAVY = Color.rgb(49, 94, 122);
    static final int SURFACE = BG;
    static final int WHITE = Color.WHITE;
    static final int ACCEPT = Color.rgb(63, 128, 111);
    static final int ACCEPT_LIGHT = Color.rgb(213, 237, 227);
    static final int REJECT = Color.rgb(199, 95, 109);
    static final int AMBER = Color.rgb(216, 168, 77);
    static final int GARDEN_INK = Color.rgb(23, 55, 47);
    static final int GARDEN_MUTED = Color.rgb(91, 113, 105);

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
        view.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
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
        return new GardenBackdrop();
    }

    static GradientDrawable glass(Context context, int radiusDp) {
        return roundedWithStroke(Color.argb(220, 249, 252, 246), radiusDp,
                Color.argb(242, 255, 255, 255), 1, context);
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
        GradientDrawable drawable = circle(Color.argb(205, 249, 252, 246));
        drawable.setStroke(dp(context, 1), Color.argb(242, 255, 255, 255));
        return drawable;
    }

    /** A quiet botanical backdrop drawn locally, so the call screen remains fully offline. */
    private static final class GardenBackdrop extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();

        @Override public void draw(Canvas canvas) {
            int width = getBounds().width();
            int height = getBounds().height();
            paint.setShader(new LinearGradient(0, 0, width, height,
                    new int[]{Color.rgb(247, 250, 244), Color.rgb(232, 241, 228),
                            Color.rgb(218, 233, 216)},
                    new float[]{0f, .52f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(getBounds(), paint);

            paint.setShader(new RadialGradient(width * .2f, height * .18f, width * .62f,
                    new int[]{Color.argb(185, 255, 255, 255), Color.TRANSPARENT},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRect(getBounds(), paint);
            paint.setShader(null);

            drawLeaf(canvas, width * -.12f, height * .30f, width * .70f,
                    height * .13f, -24f, Color.argb(23, 52, 112, 77));
            drawLeaf(canvas, width * .58f, height * .46f, width * .62f,
                    height * .12f, 29f, Color.argb(19, 44, 105, 69));
            drawLeaf(canvas, width * -.08f, height * .67f, width * .50f,
                    height * .10f, 18f, Color.argb(13, 68, 126, 83));
        }

        private void drawLeaf(Canvas canvas, float left, float top, float width,
                float height, float rotation, int color) {
            canvas.save();
            canvas.rotate(rotation, left + width / 2f, top + height / 2f);
            oval.set(left, top, left + width, top + height);
            paint.setColor(color);
            canvas.drawOval(oval, paint);
            paint.setColor(Color.argb(15, 30, 80, 52));
            paint.setStrokeWidth(Math.max(1f, width * .008f));
            canvas.drawLine(left + width * .13f, top + height * .70f,
                    left + width * .87f, top + height * .30f, paint);
            canvas.restore();
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }
        @SuppressWarnings("deprecation")
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
