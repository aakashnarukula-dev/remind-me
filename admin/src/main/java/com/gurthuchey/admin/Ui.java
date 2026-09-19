package com.gurthuchey.admin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
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
    static final int OK = Color.rgb(77, 131, 118);
    // Compatibility aliases for shared screen components.
    static final int NAVY = INK;
    static final int PAPER = BG;
    static final int WHITE = Color.WHITE;
    static final int CORAL = DANGER;
    static final int TEAL = OK;
    static final int YELLOW = GOLD;
    static final int MINT = Color.rgb(221, 239, 230);

    private Ui() {}

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable shape(int color, float radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    static GradientDrawable topShape(int color, float radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        float radius = dp(context, radiusDp);
        drawable.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        return drawable;
    }

    static GradientDrawable strokedShape(int color, float radiusDp, int strokeColor,
            float strokeDp, Context context) {
        GradientDrawable drawable = shape(color, radiusDp, context);
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

    static TextView text(Context context, String value, float sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(AppLanguage.ui(context, value));
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.08f);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    static Button button(Context context, String label, int background, int foreground) {
        Button button = new Button(context);
        button.setText(AppLanguage.ui(context, label));
        button.setTextSize(14);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(shape(background, 14, context));
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));
        return button;
    }

    static LinearLayout.LayoutParams margins(int width, int height, Context c, int l, int t, int r, int b) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return params;
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
