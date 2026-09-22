package com.gurthuchey.remindercall;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.PopupWindow;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

import com.google.firebase.auth.FirebaseAuth;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class MainActivity extends FragmentActivity {
    private static final String PERMISSION_PREFS = "permission_prompts";
    private static final String NOTIFICATION_PERMISSION_REQUESTED =
            "notification_permission_requested";
    private static final String[] CATEGORY_NAMES = {
            "💊  Medicine", "Supplement", "🥣  Meal", "🥤  Drink",
            "🏃  Exercise", "📅  Appointment", "₹  Bill or payment",
            "✓  Task", "☀  Wake-up", "🔔  Custom"
    };
    private static final String[] CATEGORY_VALUES = {
            "medicine", "supplement", "meal", "drink", "exercise", "appointment",
            "payment", "task", "wake_up", "custom"
    };
    private RemoteSync sync;
    private RemoteStore remoteStore;
    private DailyCallStatus dailyCallStatus;
    private PhoneLogin phoneLogin;
    private String status = "Connecting to Admin…";
    private String loginPhone = "";
    private String loginMessage = "";
    private boolean loginMessageError;
    private boolean otpSent;
    private boolean authBusy;
    private boolean truecallerAutoAttempted;
    private boolean scheduleListenerRegistered;
    private boolean statusListenerRegistered;
    private ScrollView mainScroll;
    private RemoteStore.Config optimisticConfig;
    private final SharedPreferences.OnSharedPreferenceChangeListener scheduleListener =
            (preferences, key) -> {
                if (key == null || "config".equals(key)) {
                    optimisticConfig = null;
                    render();
                }
            };
    private final SharedPreferences.OnSharedPreferenceChangeListener statusListener =
            (preferences, key) -> runOnUiThread(this::render);

    private void applyLightSystemBars(Window window) {
        View decor = window.getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (Build.VERSION.SDK_INT >= 30 && decor.getWindowInsetsController() != null) {
            int appearance = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            decor.getWindowInsetsController().setSystemBarsAppearance(appearance, appearance);
        }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        AppLanguage.syncLauncherLabel(this);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        applyLightSystemBars(getWindow());
        remoteStore = new RemoteStore(this);
        dailyCallStatus = new DailyCallStatus(this);
        sync = new RemoteSync(this);
        phoneLogin = new PhoneLogin(this, "user");
        requestNotificationPermission(false);
        render();
        if (phoneLogin.isSignedInWithPhone()) continueAfterLogin();
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        applyLightSystemBars(getWindow());
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (sync != null) {
            ReminderScheduler.scheduleAll(this, new RemoteStore(this).load());
            render();
        }
    }

    @Override protected void onStart() {
        super.onStart();
        remoteStore.registerChangeListener(scheduleListener);
        scheduleListenerRegistered = true;
        dailyCallStatus.register(statusListener);
        statusListenerRegistered = true;
    }

    @Override protected void onStop() {
        if (scheduleListenerRegistered) {
            remoteStore.unregisterChangeListener(scheduleListener);
            scheduleListenerRegistered = false;
        }
        if (statusListenerRegistered) {
            dailyCallStatus.unregister(statusListener);
            statusListenerRegistered = false;
        }
        super.onStop();
    }

    @Override protected void onDestroy() {
        if (sync != null) sync.stop();
        if (phoneLogin != null) phoneLogin.clear();
        super.onDestroy();
    }

    private void render() {
        RemoteStore store = remoteStore == null ? new RemoteStore(this) : remoteStore;
        RemoteStore.Config config = optimisticConfig == null ? store.load() : optimisticConfig;
        boolean paired = store.pairing() != null;

        if (phoneLogin == null || !phoneLogin.isSignedInWithPhone() || !paired) {
            renderLogin();
            return;
        }
        int previousScrollY = mainScroll == null ? 0 : mainScroll.getScrollY();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Ui.BG);
        // Keep elevated schedule cards inside their scrolling viewport. Without clipping,
        // a card scrolled above the viewport is still drawn over the fixed home header.
        page.setClipChildren(true);
        page.setClipToPadding(true);
        page.addView(buildHomeHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setClipChildren(true);
        scroll.setClipToPadding(true);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 32));
        body.setClipChildren(false);
        body.setClipToPadding(false);
        Ui.safeArea(body, true, false, true, true);

        addPermissionButton(body);
        addSchedules(body, config);

        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        mainScroll = scroll;
        setContentView(page);
        scroll.post(() -> scroll.scrollTo(0, previousScrollY));
    }

    private View buildHomeHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 20), Ui.dp(this, 14));
        header.setBackgroundColor(Ui.BG);
        header.setClipChildren(false);
        header.setClipToPadding(false);
        header.setElevation(Ui.dp(this, 3));
        Ui.safeArea(header, true, true, true, false);
        View profile = profileButton();
        profile.setOnClickListener(this::showProfileMenu);
        LinearLayout.LayoutParams profileParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 44), Ui.dp(this, 44));
        profileParams.setMarginEnd(Ui.dp(this, 10));
        header.addView(profile, profileParams);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(Ui.text(this, AppLanguage.title(currentLanguage()), 20, Ui.INK, true));
        RemoteStore.Config config = optimisticConfig != null
                ? optimisticConfig : remoteStore == null ? null : remoteStore.load();
        header.addView(words, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView add = Ui.text(this, "+", 25, Ui.INK, false);
        add.setGravity(Gravity.CENTER);
        add.setIncludeFontPadding(false);
        add.setPadding(0, 0, 0, 0);
        add.setBackground(Ui.circle(Ui.GOLD));
        add.setClickable(true);
        add.setFocusable(true);
        add.setContentDescription("Add reminder");
        add.setOnClickListener(v -> showScheduleDialog(config, null));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 44), Ui.dp(this, 44));
        addParams.setMarginEnd(Ui.dp(this, 10));
        header.addView(add, addParams);
        return header;
    }

    private String currentLanguage() {
        return AppLanguage.current(this);
    }

    private View profileButton() {
        FrameLayout host = new FrameLayout(this);
        host.setBackground(Ui.roundedWithStroke(Ui.RAISED, 22, Ui.LINE, 1, this));
        host.setElevation(Ui.dp(this, 2));
        host.setContentDescription(AppLanguage.ui(this, "Language"));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int padding = Ui.dp(this, 6);
        icon.setPadding(padding, padding, padding, padding);
        host.addView(icon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return host;
    }

    private void showProfileMenu(View anchor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        card.setBackground(Ui.roundedWithStroke(Ui.RAISED2, 18, Ui.LINE, 1, this));
        PopupWindow popup = new PopupWindow(card, Ui.dp(this, 286),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);

        TextView heading = Ui.text(this, AppLanguage.ui(this, "Language"),
                13, Ui.MUTED, true);
        card.addView(heading, spaced(6, 2, 6, 8));

        String selected = currentLanguage();
        for (int rowIndex = 0; rowIndex < 3; rowIndex++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int column = 0; column < 2; column++) {
                int index = rowIndex * 2 + column;
                Button language = button(AppLanguage.NAMES[index],
                        AppLanguage.CODES[index].equals(selected) ? Ui.GOLD : Ui.RAISED,
                        Ui.INK);
                language.setTextSize(12);
                final String code = AppLanguage.CODES[index];
                language.setOnClickListener(v -> {
                    AppLanguage.set(this, code);
                    popup.dismiss();
                    render();
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        0, Ui.dp(this, 42), 1f);
                if (column == 0) params.setMarginEnd(Ui.dp(this, 6));
                row.addView(language, params);
            }
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (rowIndex > 0) rowParams.topMargin = Ui.dp(this, 6);
            card.addView(row, rowParams);
        }

        Button logout = button("Log out", Ui.RAISED, Ui.DANGER);
        logout.setTextSize(12);
        logout.setOnClickListener(v -> {
            popup.dismiss();
            signOutUser();
        });
        card.addView(logout, spaced(0, 10, 0, 0));
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(Ui.dp(this, 10));
        popup.showAsDropDown(anchor, 0, Ui.dp(this, 6), Gravity.START);
    }

    private void signOutUser() {
        if (sync != null) sync.stop();
        FirebaseAuth.getInstance().signOut();
        otpSent = false;
        loginMessage = "";
        loginMessageError = false;
        render();
    }

    private void renderLogin() {
        mainScroll = null;
        optimisticConfig = null;
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        root.setClipChildren(false);
        root.setClipToPadding(false);

        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setGravity(Gravity.CENTER_HORIZONTAL);
        screen.setPadding(Ui.dp(this, 24), Ui.dp(this, 40), Ui.dp(this, 24), Ui.dp(this, 28));
        screen.setBackgroundColor(Ui.BG);
        screen.setClipChildren(false);
        screen.setClipToPadding(false);
        Ui.safeArea(screen, true, true, true, true);
        root.addView(screen, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView brand = Ui.text(this, AppLanguage.title(currentLanguage()), 30, Ui.INK, true);
        brand.setGravity(Gravity.CENTER);
        screen.addView(brand, Ui.matchWrap());
        TextView intro = Ui.text(this,
                AppLanguage.ui(this, "Your reminders. Your scheduled calls."),
                15, Ui.MUTED, false);
        intro.setGravity(Gravity.CENTER);
        screen.addView(intro, spaced(0, 7, 0, 0));

        View loginSpacer = new View(this);
        screen.addView(loginSpacer, new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20));
        card.setBackground(Ui.roundedWithStroke(Ui.RAISED, 20, Ui.LINE, 1, this));
        card.setElevation(Ui.dp(this, 2));
        card.setClipChildren(false);
        card.setClipToPadding(false);
        screen.addView(card, spaced(2, 24, 2, 4));

        if (phoneLogin != null && phoneLogin.isSignedInWithPhone()) {
            card.addView(Ui.text(this, "Restore reminders", 22, Ui.INK, true));
            card.addView(Ui.text(this,
                    "Signed in as " + phoneLogin.currentPhone()
                            + ". Admin must assign this number to a member.",
                    14, Ui.MUTED, false), spaced(0, 8, 0, 18));
            Button retry = button(authBusy ? "Restoring…" : "Retry restore",
                    Ui.PRIMARY, Ui.INK);
            retry.setEnabled(!authBusy);
            retry.setOnClickListener(v -> restorePhoneAssignment());
            card.addView(retry, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 54)));
            Button logout = button("Use another number", Ui.RAISED2, Ui.INK);
            logout.setOnClickListener(v -> {
                FirebaseAuth.getInstance().signOut();
                loginMessage = "";
                loginMessageError = false;
                otpSent = false;
                render();
            });
            card.addView(logout, spaced(0, 10, 0, 0));
        } else {
            card.addView(Ui.text(this, "Sign in with mobile", 22, Ui.INK, true));
            card.addView(Ui.text(this, "Use the number that Admin assigned to you.",
                    14, Ui.MUTED, false), spaced(0, 7, 0, 18));

            EditText phoneInput = new EditText(this);
            phoneInput.setSingleLine(true);
            phoneInput.setTextColor(Ui.INK);
            phoneInput.setHintTextColor(Ui.MUTED);
            phoneInput.setTextSize(17);
            phoneInput.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
            phoneInput.setBackground(Ui.roundedWithStroke(Ui.RAISED2, 14, Ui.LINE, 1, this));
            phoneInput.setHint("10-digit mobile number");
            phoneInput.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
            phoneInput.setText(localNumber(loginPhone));
            phoneInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String digits = s == null ? "" : s.toString().replaceAll("[^0-9]", "");
                    if (otpSent && !digits.equals(localNumber(loginPhone))) {
                        loginPhone = digits;
                        otpSent = false;
                        loginMessage = "";
                        loginMessageError = false;
                        phoneInput.post(MainActivity.this::renderLogin);
                    }
                }
                @Override public void afterTextChanged(Editable s) {}
            });
            card.addView(phoneInput, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 56)));

            if (otpSent) {
                EditText otpInput = new EditText(this);
                otpInput.setSingleLine(true);
                otpInput.setTextColor(Ui.INK);
                otpInput.setHintTextColor(Ui.MUTED);
                otpInput.setTextSize(17);
                otpInput.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
                otpInput.setBackground(Ui.roundedWithStroke(Ui.RAISED2, 14, Ui.LINE, 1, this));
                otpInput.setHint("6-digit OTP");
                otpInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                otpInput.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                        String code = s == null ? "" : s.toString().replaceAll("[^0-9]", "");
                        if (code.length() == 6 && !authBusy) {
                            authBusy = true;
                            phoneLogin.verifyOtp(code, loginListener());
                        }
                    }
                    @Override public void afterTextChanged(Editable s) {}
                });
                card.addView(otpInput, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 56), this, 0, 12, 0, 0));
            }

            Button action = button(authBusy ? "Please wait…" : otpSent ? "Resend OTP" : "Send OTP",
                    otpSent ? Ui.RAISED2 : Ui.PRIMARY, Ui.INK);
            action.setEnabled(!authBusy);
            action.setTextSize(otpSent ? 13 : 16);
            action.setOnClickListener(v -> {
                authBusy = true;
                loginMessage = "";
                loginMessageError = false;
                loginPhone = phoneInput.getText().toString();
                phoneLogin.sendOtp(loginPhone, loginListener());
                renderLogin();
            });
            LinearLayout.LayoutParams actionParams = Ui.margins(
                    otpSent ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT,
                    Ui.dp(this, otpSent ? 44 : 56), this, 0, 12, 0, 0);
            actionParams.gravity = otpSent ? Gravity.END : Gravity.NO_GRAVITY;
            card.addView(action, actionParams);
        }

        if (!loginMessage.isEmpty()) {
            TextView message = Ui.text(this, loginMessage, 13,
                    loginMessageError ? Ui.DANGER : Ui.NAVY, false);
            message.setGravity(Gravity.CENTER);
            card.addView(message, spaced(8, 16, 8, 0));
        }
        keepLoginCardAboveKeyboard(card);
        if (!truecallerAutoAttempted && phoneLogin != null
                && phoneLogin.isTruecallerInstalled() && phoneLogin.isTruecallerUsable()) {
            View activationLayer = new View(this);
            activationLayer.setBackgroundColor(Color.TRANSPARENT);
            activationLayer.setClickable(true);
            activationLayer.setContentDescription("Continue securely with Truecaller");
            activationLayer.setOnClickListener(v -> {
                truecallerAutoAttempted = true;
                root.removeView(activationLayer);
                tryAutoTruecallerLogin();
            });
            root.addView(activationLayer, new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        setContentView(root);
    }

    private void keepLoginCardAboveKeyboard(View card) {
        if (Build.VERSION.SDK_INT < 30) return;
        card.setOnApplyWindowInsetsListener((view, insets) -> {
            int keyboard = insets.getInsets(WindowInsets.Type.ime()).bottom;
            int navigation = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            int lift = Math.max(0, keyboard - navigation + (keyboard > 0 ? Ui.dp(this, 12) : 0));
            view.setTranslationY(-lift);
            return insets;
        });
        card.requestApplyInsets();
    }

    private void tryAutoTruecallerLogin() {
        if (authBusy || phoneLogin == null || !phoneLogin.isTruecallerInstalled()
                || !phoneLogin.isTruecallerUsable()) return;
        authBusy = true;
        loginMessage = "Opening Truecaller…";
        loginMessageError = false;
        phoneLogin.startTruecaller(loginListener());
    }

    private static String localNumber(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        return digits.length() == 12 && digits.startsWith("91") ? digits.substring(2) : digits;
    }

    private PhoneLogin.Listener loginListener() {
        return new PhoneLogin.Listener() {
            @Override public void onCodeSent(String phone) {
                runOnUiThread(() -> {
                    loginPhone = phone;
                    otpSent = true;
                    authBusy = false;
                    loginMessage = "OTP sent to " + phone;
                    loginMessageError = false;
                    renderLogin();
                });
            }

            @Override public void onComplete(String phone) {
                runOnUiThread(() -> {
                    loginPhone = phone;
                    otpSent = false;
                    authBusy = true;
                    loginMessage = "Restoring reminders…";
                    loginMessageError = false;
                    renderLogin();
                    restorePhoneAssignment();
                });
            }

            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    authBusy = false;
                    loginMessage = message;
                    loginMessageError = true;
                    renderLogin();
                });
            }
        };
    }

    private void continueAfterLogin() {
        if (sync.isPaired()) {
            render();
            sync.begin((success, message) -> runOnUiThread(() -> {
                status = message;
                render();
            }));
        } else {
            restorePhoneAssignment();
        }
    }

    private void restorePhoneAssignment() {
        authBusy = true;
        sync.restoreForSignedInPhone((success, message) -> runOnUiThread(() -> {
            authBusy = false;
            status = message;
            loginMessage = success ? "" : message;
            loginMessageError = !success;
            render();
        }));
    }

    private LinearLayout.LayoutParams spaced(int left, int top, int right, int bottom) {
        return Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, this, left, top, right, bottom);
    }

    private void addPairing(LinearLayout body) {
        TextView title = Ui.text(this, "Connect to Admin", 22, Ui.NAVY, true);
        title.setPadding(0, Ui.dp(this, 26), 0, Ui.dp(this, 6));
        body.addView(title);
        body.addView(Ui.text(this,
                "In Admin, select the family member and tap Connect phone. Enter that 6-digit code here.",
                15, Color.rgb(80, 96, 112), false));
        EditText code = new EditText(this);
        code.setHint("6-digit code");
        code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        code.setTextSize(24);
        code.setTextColor(Ui.INK);
        code.setHintTextColor(Ui.MUTED);
        code.setGravity(Gravity.CENTER);
        code.setBackground(Ui.roundedWithStroke(Ui.WHITE, 12, Ui.LINE, 1, this));
        LinearLayout.LayoutParams codeParams = Ui.matchWrap();
        codeParams.topMargin = Ui.dp(this, 16);
        body.addView(code, codeParams);
        Button connect = button("Connect phone", Ui.PRIMARY, Ui.INK);
        connect.setOnClickListener(v -> {
            String pairingCode = code.getText().toString();
            connect.setEnabled(false);
            status = "Connecting…";
            render();
            sync.pair(pairingCode, (success, message) -> runOnUiThread(() -> {
                status = message;
                render();
            }));
        });
        LinearLayout.LayoutParams buttonParams = Ui.matchWrap();
        buttonParams.topMargin = Ui.dp(this, 12);
        body.addView(connect, buttonParams);
    }

    private void addSchedules(LinearLayout body, RemoteStore.Config config) {
        if (config == null || config.schedules.isEmpty()) {
            body.addView(Ui.text(this, "No reminders yet. Tap + to create one.",
                    16, Ui.MUTED, false));
            return;
        }
        List<RemoteStore.Schedule> visibleSchedules = new ArrayList<>();
        for (RemoteStore.Schedule schedule : config.schedules) {
            if (schedule.enabled) visibleSchedules.add(schedule);
        }
        visibleSchedules.sort(Comparator.comparingInt(
                schedule -> schedule.hour * 60 + schedule.minute));

        String currentPeriod = "";
        for (RemoteStore.Schedule schedule : visibleSchedules) {
            String period = periodIcon(schedule.hour, schedule.minute) + "  "
                    + AppLanguage.timePeriod(currentLanguage(), schedule.hour, schedule.minute);
            if (!period.equals(currentPeriod)) {
                TextView label = Ui.text(this, period, 12, Ui.MUTED, true);
                LinearLayout.LayoutParams labelParams = Ui.matchWrap();
                labelParams.leftMargin = Ui.dp(this, 6);
                labelParams.topMargin = currentPeriod.isEmpty() ? 0 : Ui.dp(this, 8);
                labelParams.bottomMargin = Ui.dp(this, 2);
                body.addView(label, labelParams);
                currentPeriod = period;
            }
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setMinimumHeight(Ui.dp(this, 76));
            card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12),
                    Ui.dp(this, 12), Ui.dp(this, 12));
            card.setBackground(Ui.roundedWithStroke(Ui.RAISED2, 15, Ui.LINE, 1, this));
            card.setElevation(Ui.dp(this, 2));

            View icon = categoryIconView(schedule.category);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    Ui.dp(this, 40), Ui.dp(this, 40));
            iconParams.setMarginEnd(Ui.dp(this, 12));
            card.addView(icon, iconParams);

            LinearLayout words = new LinearLayout(this);
            words.setOrientation(LinearLayout.VERTICAL);
            words.addView(Ui.text(this, schedule.label, 15, Ui.INK, true));
            String meta = categoryName(schedule.category) + " · " + daysText(schedule.days)
                    + ("medicine".equals(schedule.category) && schedule.preMinutes > 0
                    ? " · meal call " + schedule.preMinutes + " min before" : "")
                    + (schedule.confirmationMinutes > 0
                    ? " · confirm after " + schedule.confirmationMinutes + " min" : "");
            words.addView(Ui.text(this, meta, 11, Ui.MUTED, false));
            LinearLayout.LayoutParams wordsParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            wordsParams.setMarginEnd(Ui.dp(this, 8));
            card.addView(words, wordsParams);

            LinearLayout trailing = new LinearLayout(this);
            trailing.setOrientation(LinearLayout.VERTICAL);
            trailing.setGravity(Gravity.END);
            TextView time = Ui.text(this, timeText(schedule), 16, Ui.INK, true);
            time.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            trailing.addView(time);
            DailyCallStatus.Display display = dailyCallStatus.display(
                    schedule, System.currentTimeMillis());
            if (DailyCallStatus.isIncomplete(display.kind)) {
                trailing.addView(statusPill(false, schedule), Ui.margins(
                        ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 22),
                        this, 0, 3, 0, 0));
            } else if (DailyCallStatus.COMPLETED.equals(display.kind)) {
                trailing.addView(statusPill(true, schedule), Ui.margins(
                        ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 22),
                        this, 0, 3, 0, 0));
            } else if (DailyCallStatus.SKIPPED.equals(display.kind)) {
                trailing.addView(skippedPill(schedule), Ui.margins(
                        ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 22),
                        this, 0, 3, 0, 0));
            } else if (DailyCallStatus.NOT_TODAY.equals(display.kind)) {
                trailing.addView(notTodayPill(), Ui.margins(
                        ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 22),
                        this, 0, 3, 0, 0));
            }
            LinearLayout.LayoutParams trailingParams = new LinearLayout.LayoutParams(
                    Ui.dp(this, 92), ViewGroup.LayoutParams.WRAP_CONTENT);
            trailingParams.setMarginEnd(Ui.dp(this, 10));
            card.addView(trailing, trailingParams);

            card.addView(Ui.text(this, "›", 24, Ui.GOLD, false));
            card.setOnClickListener(v -> showScheduleDialog(config, schedule));
            card.setContentDescription("Edit " + schedule.label + " at " + timeText(schedule));
            LinearLayout.LayoutParams params = Ui.matchWrap();
            params.leftMargin = Ui.dp(this, 3);
            params.rightMargin = Ui.dp(this, 3);
            params.topMargin = Ui.dp(this, 4);
            params.bottomMargin = Ui.dp(this, 6);
            body.addView(card, params);
        }
        if (visibleSchedules.isEmpty()) {
            body.addView(Ui.text(this, "No active reminders.",
                    16, Ui.MUTED, false));
        }
    }

    private View statusPill(boolean completed, RemoteStore.Schedule schedule) {
        TextView pill = Ui.text(this,
                completed ? DailyCallStatus.completedLabel(currentLanguage())
                        : DailyCallStatus.incompleteLabel(currentLanguage()),
                9, completed ? Color.rgb(38, 117, 72) : Color.rgb(139, 98, 25), true);
        pill.setGravity(Gravity.CENTER);
        pill.setSingleLine(true);
        pill.setPadding(Ui.dp(this, 7), 0, Ui.dp(this, 7), 0);
        pill.setBackground(Ui.rounded(completed ? Color.rgb(226, 245, 233)
                : Color.rgb(255, 242, 211), 11, this));
        pill.setClickable(true);
        pill.setContentDescription("Change today's status for " + schedule.label);
        pill.setOnClickListener(v -> showDailyStatusDialog(schedule, completed));
        return pill;
    }

    private View skippedPill(RemoteStore.Schedule schedule) {
        TextView pill = Ui.text(this, DailyCallStatus.skippedLabel(currentLanguage()),
                9, Ui.DANGER, true);
        pill.setGravity(Gravity.CENTER);
        pill.setSingleLine(true);
        pill.setPadding(Ui.dp(this, 7), 0, Ui.dp(this, 7), 0);
        pill.setBackground(Ui.rounded(Color.rgb(253, 235, 237), 11, this));
        pill.setClickable(true);
        pill.setContentDescription("Change today's status for " + schedule.label);
        pill.setOnClickListener(v -> showDailyStatusDialog(schedule, false));
        return pill;
    }

    private View notTodayPill() {
        TextView pill = Ui.text(this, DailyCallStatus.notTodayLabel(currentLanguage()),
                9, Color.rgb(83, 99, 116), true);
        pill.setGravity(Gravity.CENTER);
        pill.setSingleLine(true);
        pill.setPadding(Ui.dp(this, 7), 0, Ui.dp(this, 7), 0);
        pill.setBackground(Ui.rounded(Color.rgb(232, 239, 245), 11, this));
        pill.setContentDescription(DailyCallStatus.notTodayLabel(currentLanguage()));
        return pill;
    }

    private void showDailyStatusDialog(RemoteStore.Schedule schedule, boolean completed) {
        boolean skippedToday = dailyCallStatus.isSkippedToday(
                schedule.id, System.currentTimeMillis());
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(Ui.dp(this, 18), Ui.dp(this, 8),
                Ui.dp(this, 18), Ui.dp(this, 16));
        sheet.setBackground(Ui.topRounded(Ui.WHITE, 28, this));
        sheet.setClipChildren(false);
        sheet.setClipToPadding(false);
        Ui.safeArea(sheet, true, false, true, true);
        sheet.addView(draggableSheetHandle(dialog, sheet), Ui.margins(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 24), this, 0, 0, 0, 2));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.text(this, "Today's status", 22, Ui.INK, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = Ui.text(this, "×", 26, Ui.MUTED, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close status editor");
        close.setBackground(Ui.rounded(Ui.RAISED2, 21, this));
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        sheet.addView(header);

        TextView reminder = Ui.text(this, schedule.label, 15, Ui.MUTED, false);
        sheet.addView(reminder, sizedMargins(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 2, 0, 12));

        LinearLayout choices = new LinearLayout(this);
        Button done = button(DailyCallStatus.completedLabel(currentLanguage()),
                Color.rgb(226, 245, 233), Color.rgb(38, 117, 72));
        done.setBackground(Ui.roundedWithStroke(Color.rgb(226, 245, 233), 14,
                completed ? Color.rgb(38, 117, 72) : Ui.LINE, 1, this));
        done.setOnClickListener(v -> {
            ReminderScheduler.cancelRetry(this, schedule.id, ReminderScheduler.PHASE_MEAL);
            ReminderScheduler.cancelRetry(this, schedule.id, ReminderScheduler.PHASE_MEDICINE);
            ReminderScheduler.cancelRetry(this, schedule.id,
                    ReminderScheduler.PHASE_CONFIRMATION);
            dailyCallStatus.markCompleted(schedule.id, ReminderScheduler.PHASE_MEDICINE);
            if (schedule.confirmationMinutes > 0) {
                dailyCallStatus.markCompleted(schedule.id,
                        ReminderScheduler.PHASE_CONFIRMATION);
            }
            dialog.dismiss();
            render();
        });
        choices.addView(done, new LinearLayout.LayoutParams(
                0, Ui.dp(this, 52), 1f));

        Button notDone = button(DailyCallStatus.incompleteLabel(currentLanguage()),
                Color.rgb(255, 242, 211), Color.rgb(139, 98, 25));
        notDone.setBackground(Ui.roundedWithStroke(Color.rgb(255, 242, 211), 14,
                !completed && !skippedToday ? Color.rgb(139, 98, 25) : Ui.LINE, 1, this));
        notDone.setOnClickListener(v -> {
            dailyCallStatus.markIncomplete(schedule.id);
            dialog.dismiss();
            render();
        });
        LinearLayout.LayoutParams notDoneParams = new LinearLayout.LayoutParams(
                0, Ui.dp(this, 52), 1f);
        notDoneParams.setMarginStart(Ui.dp(this, 8));
        choices.addView(notDone, notDoneParams);
        sheet.addView(choices);

        Button skipToday = button(AppLanguage.ui(currentLanguage(), "Skip for today"),
                Color.rgb(253, 235, 237), Ui.DANGER);
        skipToday.setBackground(Ui.roundedWithStroke(Color.rgb(253, 235, 237), 14,
                skippedToday ? Ui.DANGER : Ui.LINE, 1, this));
        skipToday.setOnClickListener(v -> {
            dailyCallStatus.markSkippedToday(schedule.id);
            ReminderScheduler.skipRemainingToday(this, schedule.id);
            if (CallService.isProcessCallActive(schedule.id)) {
                startService(new Intent(this, CallService.class)
                        .setAction(CallService.ACTION_SKIP_TODAY)
                        .putExtra(ReminderScheduler.EXTRA_ID, schedule.id));
            }
            dialog.dismiss();
            render();
        });
        sheet.addView(skipToday, sizedMargins(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 52), 0, 8, 0, 0));

        TextView note = Ui.text(this, "Applies to today only", 12, Ui.MUTED, false);
        note.setGravity(Gravity.CENTER);
        sheet.addView(note, sizedMargins(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 10, 0, 0));

        dialog.setContentView(sheet);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.setDimAmount(0.48f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setNavigationBarColor(Ui.BG);
            applyLightSystemBars(window);
            window.setWindowAnimations(R.style.BottomSheetAnimation);
        }
        dialog.show();
        if (window != null) window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void showScheduleDialog(RemoteStore.Config config, RemoteStore.Schedule existing) {
        if (config == null) return;
        if (existing == null && config.schedules.size() >= 50) {
            Toast.makeText(this, "Maximum 50 reminders", Toast.LENGTH_LONG).show();
            return;
        }
        RemoteStore.Schedule draft = copySchedule(existing);
        boolean[] automaticConversation = {existing == null};
        normalizeReminderTitlePlaceholders(draft);

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(Ui.dp(this, 18), Ui.dp(this, 8),
                Ui.dp(this, 18), Ui.dp(this, 12));
        sheet.setBackground(Ui.topRounded(Ui.WHITE, 28, this));
        sheet.setClipChildren(false);
        sheet.setClipToPadding(false);
        Ui.safeArea(sheet, true, false, true, true);

        sheet.addView(draggableSheetHandle(dialog, sheet), Ui.margins(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 24), this, 0, 0, 0, 2));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClipChildren(false);
        header.setClipToPadding(false);
        header.addView(Ui.text(this, existing == null ? "Add reminder" : "Edit reminder",
                        24, Ui.INK, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (existing != null) {
            Button delete = button("Delete", Ui.RAISED2, Ui.DANGER);
            delete.setTextSize(12);
            delete.setElevation(Ui.dp(this, 3));
            delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Delete reminder?")
                    .setMessage(existing.label + " reminders will stop.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", (ignored, which) -> {
                        ReminderScheduler.clearProgressIfOccurrenceChanged(this, existing, null);
                        config.schedules.remove(existing);
                        dialog.dismiss();
                        saveOwnSchedules(config);
                    }).show());
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    Ui.dp(this, 82), Ui.dp(this, 42));
            deleteParams.topMargin = Ui.dp(this, 4);
            deleteParams.bottomMargin = Ui.dp(this, 4);
            header.addView(delete, deleteParams);
        }
        TextView close = Ui.text(this, "×", 26, Ui.MUTED, false);
        close.setGravity(Gravity.CENTER);
        close.setBackground(Ui.rounded(Ui.RAISED2, 21, this));
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 42), Ui.dp(this, 42));
        closeParams.setMarginStart(Ui.dp(this, 8));
        header.addView(close, closeParams);
        sheet.addView(header);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 6));
        form.setClipChildren(false);
        form.setClipToPadding(false);

        form.addView(fieldLabel("Category"));
        Runnable[] updateCategoryUi = new Runnable[1];
        int[] selectedCategory = {categoryIndex(draft.category)};
        LinearLayout[] categoryChoices = new LinearLayout[CATEGORY_VALUES.length];
        HorizontalScrollView categoryScroll = new HorizontalScrollView(this);
        categoryScroll.setHorizontalScrollBarEnabled(false);
        categoryScroll.setClipToPadding(false);
        categoryScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout categoryRow = new LinearLayout(this);
        categoryRow.setOrientation(LinearLayout.HORIZONTAL);
        categoryRow.setGravity(Gravity.CENTER_VERTICAL);
        for (int index = 0; index < CATEGORY_VALUES.length; index++) {
            final int choice = index;
            LinearLayout option = categoryChoice(CATEGORY_VALUES[index],
                    selectedCategory[0] == index);
            option.setOnClickListener(v -> {
                selectedCategory[0] = choice;
                draft.category = CATEGORY_VALUES[choice];
                for (int i = 0; i < categoryChoices.length; i++) {
                    styleCategoryChoice(categoryChoices[i], i == choice);
                }
                if (automaticConversation[0]) {
                    draft.questions.clear();
                    draft.questions.add(defaultQuestion(draft.category));
                }
                if (updateCategoryUi[0] != null) updateCategoryUi[0].run();
            });
            categoryChoices[index] = option;
            LinearLayout.LayoutParams optionParams = new LinearLayout.LayoutParams(
                    Ui.dp(this, 76), ViewGroup.LayoutParams.WRAP_CONTENT);
            categoryRow.addView(option, optionParams);
        }
        categoryScroll.addView(categoryRow);
        LinearLayout.LayoutParams categoryScrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 70));
        categoryScrollParams.setMargins(-Ui.dp(this, 18), Ui.dp(this, 2),
                -Ui.dp(this, 18), 0);
        form.addView(categoryScroll, categoryScrollParams);

        TextView reminderNameLabel = fieldLabel("Medicine name");
        form.addView(reminderNameLabel, sizedMargins(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8, 0, 0));
        EditText medicine = new EditText(this);
        medicine.setHint("Example: Metformin");
        medicine.setText("medicine".equals(draft.category)
                ? normalizeMedicineName(draft.label) : draft.label);
        medicine.setSingleLine(true);
        medicine.setTextSize(17);
        medicine.setTextColor(Ui.INK);
        medicine.setHintTextColor(Ui.MUTED);
        medicine.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        medicine.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        medicine.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        medicine.setBackground(Ui.roundedWithStroke(Ui.RAISED2, 14, Ui.LINE, 1, this));
        form.addView(medicine, sizedMargins(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 48), 0, 4, 0, 0));

        form.addView(fieldLabel("Call time"), sizedMargins(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 8, 0, 4));
        int[] selectedTime = {draft.hour, draft.minute};
        Button time = button(timeText(draft), Ui.RAISED2, Ui.INK);
        time.setTextSize(20);
        time.setBackground(Ui.roundedWithStroke(Ui.RAISED2, 14, Ui.LINE, 1, this));
        time.setElevation(0);
        time.setTranslationZ(0);
        time.setStateListAnimator(null);
        time.setOnClickListener(v -> new TimePickerDialog(this, (picker, hour, minute) -> {
            selectedTime[0] = hour;
            selectedTime[1] = minute;
            RemoteStore.Schedule shown = copySchedule(draft);
            shown.hour = hour;
            shown.minute = minute;
            time.setText(timeText(shown));
        }, selectedTime[0], selectedTime[1], false).show());
        form.addView(time, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));

        form.addView(fieldLabel("Repeat on"), sizedMargins(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 8, 0, 4));
        String[] dayNames = AppLanguage.shortDays(currentLanguage());
        TextView[] days = new TextView[7];
        LinearLayout dayRow = new LinearLayout(this);
        for (int index = 0; index < dayNames.length; index++) {
            TextView chip = chip(dayNames[index], (draft.days & (1 << index)) != 0);
            days[index] = chip;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, Ui.dp(this, 34), 1f);
            if (index < 6) params.setMarginEnd(Ui.dp(this, 4));
            dayRow.addView(chip, params);
        }
        form.addView(dayRow);

        form.addView(fieldLabel("Confirmation call"), sizedMargins(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 8, 0, 4));
        int[] confirmationValues = {0, 10};
        int[] selectedConfirmation = {draft.confirmationMinutes > 0 ? 10 : 0};
        TextView[] confirmationChips = new TextView[confirmationValues.length];
        LinearLayout confirmationRow = new LinearLayout(this);
        for (int index = 0; index < confirmationValues.length; index++) {
            int choice = index;
            TextView chip = chip(confirmationValues[index] == 0 ? "OFF" : "10 min",
                    selectedConfirmation[0] == confirmationValues[index]);
            chip.setOnClickListener(v -> {
                selectedConfirmation[0] = confirmationValues[choice];
                for (int i = 0; i < confirmationChips.length; i++) {
                    styleChip(confirmationChips[i], i == choice);
                }
            });
            confirmationChips[index] = chip;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, Ui.dp(this, 34), 1f);
            if (index == 0) params.setMarginEnd(Ui.dp(this, 6));
            confirmationRow.addView(chip, params);
        }
        form.addView(confirmationRow);

        LinearLayout medicineOptions = new LinearLayout(this);
        medicineOptions.setOrientation(LinearLayout.VERTICAL);
        medicineOptions.addView(fieldLabel("Meal Reminder"), sizedMargins(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 8, 0, 4));
        int[] leadValues = {0, 15, 30, 45, 60};
        int[] selectedLead = {draft.preMinutes};
        TextView[] leadChips = new TextView[leadValues.length];
        LinearLayout leadRow = new LinearLayout(this);
        for (int index = 0; index < leadValues.length; index++) {
            int choice = index;
            String text = leadValues[index] == 0 ? "OFF" : leadValues[index] + " min";
            TextView chip = chip(text, selectedLead[0] == leadValues[index]);
            chip.setOnClickListener(v -> {
                selectedLead[0] = leadValues[choice];
                for (int i = 0; i < leadChips.length; i++) styleChip(leadChips[i], i == choice);
            });
            leadChips[index] = chip;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, Ui.dp(this, 34), 1f);
            if (index < leadValues.length - 1) params.setMarginEnd(Ui.dp(this, 6));
            leadRow.addView(chip, params);
        }
        medicineOptions.addView(leadRow);
        form.addView(medicineOptions);

        LinearLayout conversation = new LinearLayout(this);
        conversation.setOrientation(LinearLayout.VERTICAL);
        conversation.addView(fieldLabel("Call conversation"), sizedMargins(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 8, 0, 4));
        Button conversationButton = button("", Ui.ACCEPT_LIGHT, Ui.INK);
        conversationButton.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        conversationButton.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        conversationButton.setBackground(Ui.roundedWithStroke(
                Ui.ACCEPT_LIGHT, 14, Ui.ACCEPT, 1, this));
        conversationButton.setElevation(0);
        conversationButton.setTranslationZ(0);
        conversationButton.setStateListAnimator(null);
        conversationButton.setOnClickListener(v -> showQuestionsDialog(draft, () -> {
            automaticConversation[0] = false;
            updateConversationSummary(conversationButton, draft);
        }));
        conversation.addView(conversationButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));
        form.addView(conversation);
        updateConversationSummary(conversationButton, draft);

        updateCategoryUi[0] = () -> {
            boolean isMedicine = "medicine".equals(draft.category);
            reminderNameLabel.setText(isMedicine ? "Medicine name" : "Reminder title");
            medicine.setHint(isMedicine ? "Example: Metformin" : "Example: Pay electricity bill");
            medicineOptions.setVisibility(isMedicine ? View.VISIBLE : View.GONE);
            conversation.setVisibility(View.VISIBLE);
            updateConversationSummary(conversationButton, draft);
        };
        updateCategoryUi[0].run();

        sheet.addView(form, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button save = button(existing == null ? "Add reminder" : "Save changes",
                Ui.GOLD, Ui.INK);
        save.setOnClickListener(v -> {
            boolean medicineReminder = "medicine".equals(draft.category);
            String name = medicineReminder
                    ? normalizeMedicineName(medicine.getText().toString())
                    : medicine.getText().toString().trim();
            int dayMask = 0;
            for (int index = 0; index < days.length; index++) {
                if (Boolean.TRUE.equals(days[index].getTag())) dayMask |= 1 << index;
            }
            if (name.isEmpty()) {
                medicine.setError(medicineReminder ? "Enter medicine name" : "Enter reminder title");
                return;
            }
            if (dayMask == 0) {
                Toast.makeText(this, "Choose at least one day", Toast.LENGTH_SHORT).show();
                return;
            }
            if ((existing == null || !medicineReminder || !draft.questions.isEmpty())
                    && !validConversation(draft)) {
                Toast.makeText(this, "Add at least one question",
                        Toast.LENGTH_LONG).show();
                return;
            }
            normalizeReminderTitlePlaceholders(draft);
            draft.label = name;
            draft.language = scriptLanguage(draft, "en");
            draft.hour = selectedTime[0];
            draft.minute = selectedTime[1];
            draft.days = dayMask;
            draft.preMinutes = medicineReminder ? selectedLead[0] : 0;
            draft.confirmationMinutes = selectedConfirmation[0];
            draft.enabled = true;
            if (existing == null) config.schedules.add(draft);
            else {
                ReminderScheduler.clearProgressIfOccurrenceChanged(this, existing, draft);
                int index = config.schedules.indexOf(existing);
                if (index >= 0) config.schedules.set(index, draft);
            }
            dialog.dismiss();
            saveOwnSchedules(config);
        });
        sheet.addView(save, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));

        dialog.setContentView(sheet);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.setDimAmount(0.48f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            window.setNavigationBarColor(Ui.BG);
            applyLightSystemBars(window);
            window.setWindowAnimations(R.style.BottomSheetAnimation);
        }
        dialog.show();
        if (window != null) window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void saveOwnSchedules(RemoteStore.Config config) {
        optimisticConfig = config;
        render();
        sync.saveOwnSchedules(config, (success, message) -> runOnUiThread(() -> {
            status = message;
            if (!success) optimisticConfig = null;
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            render();
        }));
    }

    private RemoteStore.Schedule copySchedule(RemoteStore.Schedule source) {
        RemoteStore.Schedule copy = new RemoteStore.Schedule();
        if (source == null) {
            copy.id = UUID.randomUUID().toString();
            copy.label = "";
            copy.category = "medicine";
            copy.language = currentLanguage();
            copy.hour = 8;
            copy.minute = 0;
            copy.days = 0b1111111;
            copy.preMinutes = 30;
            copy.confirmationMinutes = 0;
            copy.enabled = true;
            copy.questions.add(defaultQuestion(copy.category));
            return copy;
        }
        copy.id = source.id;
        copy.label = source.label;
        copy.category = source.category;
        copy.language = source.language;
        copy.hour = source.hour;
        copy.minute = source.minute;
        copy.days = source.days;
        copy.preMinutes = source.preMinutes;
        copy.confirmationMinutes = source.confirmationMinutes;
        copy.enabled = source.enabled;
        for (RemoteStore.ScriptQuestion sourceQuestion : source.questions) {
            RemoteStore.ScriptQuestion question = new RemoteStore.ScriptQuestion();
            question.prompt = sourceQuestion.prompt;
            for (RemoteStore.ScriptAnswer sourceAnswer : sourceQuestion.answers) {
                RemoteStore.ScriptAnswer answer = new RemoteStore.ScriptAnswer();
                answer.label = sourceAnswer.label;
                answer.response = sourceAnswer.response;
                question.answers.add(answer);
            }
            copy.questions.add(question);
        }
        return copy;
    }

    private int categoryIndex(String value) {
        for (int index = 0; index < CATEGORY_VALUES.length; index++) {
            if (CATEGORY_VALUES[index].equals(value)) return index;
        }
        return CATEGORY_VALUES.length - 1;
    }

    private String periodIcon(int hour, int minute) {
        int minutesAfterMidnight = hour * 60 + minute;
        if (minutesAfterMidnight >= 5 * 60 && minutesAfterMidnight < 12 * 60) return "🌅";
        if (minutesAfterMidnight >= 12 * 60 && minutesAfterMidnight < 16 * 60) return "☀️";
        if (minutesAfterMidnight >= 16 * 60 && minutesAfterMidnight <= 19 * 60) return "🌇";
        return "🌙";
    }

    private String categoryName(String category) {
        return AppLanguage.category(currentLanguage(), category);
    }

    private String[] categoryNames() {
        String[] names = new String[CATEGORY_VALUES.length];
        for (int index = 0; index < names.length; index++) {
            String icon = "supplement".equals(CATEGORY_VALUES[index]) ? ""
                    : categoryIcon(CATEGORY_VALUES[index]) + "  ";
            names[index] = icon + categoryName(CATEGORY_VALUES[index]);
        }
        return names;
    }

    private View categoryIconView(String category) {
        if ("supplement".equals(category)) {
            ImageView icon = new ImageView(this);
            icon.setImageResource(R.drawable.ic_category_supplement);
            icon.setPadding(Ui.dp(this, 7), Ui.dp(this, 7),
                    Ui.dp(this, 7), Ui.dp(this, 7));
            icon.setBackground(Ui.circle(Ui.RAISED));
            icon.setContentDescription("Supplement");
            return icon;
        }
        TextView icon = Ui.text(this, categoryIcon(category), 18, Ui.NAVY, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(Ui.circle(Ui.RAISED));
        icon.setContentDescription(categoryName(category));
        return icon;
    }

    private LinearLayout categoryChoice(String category, boolean selected) {
        LinearLayout choice = new LinearLayout(this);
        choice.setOrientation(LinearLayout.VERTICAL);
        choice.setGravity(Gravity.CENTER_HORIZONTAL);
        choice.setPadding(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
        choice.setClickable(true);
        choice.setFocusable(true);

        View icon = categoryIconView(category);
        choice.addView(icon, new LinearLayout.LayoutParams(
                Ui.dp(this, 46), Ui.dp(this, 46)));

        TextView name = Ui.text(this, categoryName(category), 11, Ui.MUTED, false);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        choice.addView(name, sizedMargins(ViewGroup.LayoutParams.WRAP_CONTENT,
                Ui.dp(this, 22), 0, 3, 0, 0));
        choice.setTag(new View[]{icon, name});
        styleCategoryChoice(choice, selected);
        return choice;
    }

    private void styleCategoryChoice(LinearLayout choice, boolean selected) {
        if (choice == null || !(choice.getTag() instanceof View[])) return;
        View[] parts = (View[]) choice.getTag();
        View icon = parts[0];
        TextView name = (TextView) parts[1];
        icon.setBackground(Ui.roundedWithStroke(
                selected ? Ui.GOLD : Ui.RAISED2,
                23,
                selected ? Color.rgb(211, 169, 91) : Ui.LINE,
                selected ? 2 : 1,
                this));
        icon.setElevation(selected ? Ui.dp(this, 2) : 0);
        name.setTextColor(selected ? Ui.INK : Ui.MUTED);
        name.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        choice.setContentDescription(name.getText()
                + (selected ? ", selected" : ", not selected"));
        choice.setSelected(selected);
    }

    private void styleCategoryOption(TextView view, int position, boolean dropDown) {
        view.setText(categoryNames()[position]);
        view.setTextSize(17);
        view.setTextColor(Ui.INK);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setMinHeight(Ui.dp(this, 52));
        view.setPadding(Ui.dp(this, 16), dropDown ? Ui.dp(this, 10) : 0,
                Ui.dp(this, dropDown ? 16 : 48), dropDown ? Ui.dp(this, 10) : 0);
        if ("supplement".equals(CATEGORY_VALUES[position])) {
            Drawable tablet = getDrawable(R.drawable.ic_category_supplement);
            int size = Ui.dp(this, 24);
            tablet.setBounds(0, 0, size, size);
            view.setCompoundDrawables(tablet, null, null, null);
            view.setCompoundDrawablePadding(Ui.dp(this, 12));
        } else {
            view.setCompoundDrawables(null, null, null, null);
            view.setCompoundDrawablePadding(0);
        }
    }

    private String categoryIcon(String category) {
        if (category == null) return "💊";
        switch (category) {
            case "medicine": return "💊";
            case "supplement": return "";
            case "meal": return "🥣";
            case "drink": return "🥤";
            case "exercise": return "🏃";
            case "appointment": return "📅";
            case "payment": return "₹";
            case "task": return "✓";
            case "wake_up": return "☀";
            default: return "🔔";
        }
    }

    private void normalizeReminderTitlePlaceholders(RemoteStore.Schedule schedule) {
        if (schedule == null || "medicine".equals(schedule.category)) return;
        String currentTitle = schedule.label == null ? "" : schedule.label.trim();
        for (RemoteStore.ScriptQuestion question : schedule.questions) {
            question.prompt = normalizeReminderTitlePlaceholder(question.prompt, currentTitle);
            for (RemoteStore.ScriptAnswer answer : question.answers) {
                answer.response = normalizeReminderTitlePlaceholder(answer.response, currentTitle);
            }
        }
    }

    private String normalizeReminderTitlePlaceholder(String text, String currentTitle) {
        if (text == null || text.isEmpty()) return text == null ? "" : text;
        String marker = "__GURTHU_REMINDER_TITLE__";
        String normalized = text.replace("[Reminder title]", marker)
                .replace("[Reminder]", marker);
        if (currentTitle != null && !currentTitle.isEmpty()) {
            normalized = java.util.regex.Pattern.compile(
                    java.util.regex.Pattern.quote(currentTitle),
                    java.util.regex.Pattern.CASE_INSENSITIVE
                            | java.util.regex.Pattern.UNICODE_CASE)
                    .matcher(normalized)
                    .replaceAll(java.util.regex.Matcher.quoteReplacement(marker));
        }
        return normalized.replace(marker, "[Reminder title]");
    }

    private RemoteStore.ScriptQuestion defaultQuestion(String category) {
        RemoteStore.ScriptQuestion question = new RemoteStore.ScriptQuestion();
        RemoteStore.ScriptAnswer answer = new RemoteStore.ScriptAnswer();
        question.prompt = ConversationDefaults.prompt(category);
        answer.label = "Okay";
        answer.response = "";
        question.answers.add(answer);
        return question;
    }

    private String scriptLanguage(RemoteStore.Schedule schedule, String fallback) {
        StringBuilder script = new StringBuilder(schedule.label == null ? "" : schedule.label);
        for (RemoteStore.ScriptQuestion question : schedule.questions) {
            script.append(' ').append(question.prompt);
            for (RemoteStore.ScriptAnswer answer : question.answers) {
                script.append(' ').append(answer.label).append(' ').append(answer.response);
            }
        }
        return AppLanguage.detect(script.toString(), fallback);
    }

    private boolean validConversation(RemoteStore.Schedule schedule) {
        if (schedule.questions.isEmpty() || schedule.questions.size() > 10) return false;
        for (RemoteStore.ScriptQuestion question : schedule.questions) {
            if (question.prompt.trim().isEmpty() || question.answers.size() > 4) return false;
            for (RemoteStore.ScriptAnswer answer : question.answers) {
                if (answer.label.trim().isEmpty()) return false;
            }
        }
        return true;
    }

    private void updateConversationSummary(TextView view, RemoteStore.Schedule schedule) {
        int answers = 0;
        for (RemoteStore.ScriptQuestion question : schedule.questions) {
            answers += question.answers.size() + 1;
        }
        view.setText(schedule.questions.isEmpty()
                ? "Add call conversation  ›"
                : schedule.questions.size() + (schedule.questions.size() == 1
                        ? " question · " : " questions · ") + answers
                        + (answers == 0 ? " optional buttons  ›"
                        : answers == 1 ? " answer button  ›" : " answer buttons  ›"));
    }

    private void showQuestionsDialog(RemoteStore.Schedule schedule, Runnable changed) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(Ui.dp(this, 18), Ui.dp(this, 8),
                Ui.dp(this, 18), Ui.dp(this, 14));
        sheet.setBackground(Ui.topRounded(Ui.WHITE, 28, this));
        sheet.setClipChildren(false);
        sheet.setClipToPadding(false);
        Ui.safeArea(sheet, true, false, true, true);
        sheet.addView(draggableSheetHandle(dialog, sheet), Ui.margins(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 24), this, 0, 0, 0, 2));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.text(this, "Call conversation", 24, Ui.INK, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = Ui.text(this, "×", 26, Ui.MUTED, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close conversation editor");
        close.setBackground(Ui.rounded(Ui.RAISED2, 21, this));
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        sheet.addView(header);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        renderQuestionList(list, schedule, changed);
        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.addView(list);
        sheet.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button add = button("+ Add question", Ui.PRIMARY, Ui.INK);
        add.setOnClickListener(v -> {
            if (schedule.questions.size() >= 10) {
                Toast.makeText(this, "Maximum 10 questions", Toast.LENGTH_SHORT).show();
                return;
            }
            showQuestionEditor(schedule, null, () -> {
                renderQuestionList(list, schedule, changed);
                changed.run();
            });
        });
        sheet.addView(add, sizedMargins(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 50), 0, 8, 0, 0));

        dialog.setContentView(sheet);
        showSheet(dialog, 0.76f);
    }

    private void renderQuestionList(LinearLayout list, RemoteStore.Schedule schedule,
            Runnable changed) {
        list.removeAllViews();
        for (int index = 0; index < schedule.questions.size(); index++) {
            RemoteStore.ScriptQuestion question = schedule.questions.get(index);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12),
                    Ui.dp(this, 14), Ui.dp(this, 12));
            card.setBackground(Ui.roundedWithStroke(Ui.RAISED2, 16, Ui.LINE, 1, this));
            card.addView(Ui.text(this, "Question " + (index + 1), 12, Ui.MUTED, true));
            TextView prompt = Ui.text(this, question.prompt, 16, Ui.INK, true);
            prompt.setMaxLines(3);
            card.addView(prompt, sizedMargins(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 0, 5, 0, 0));
            if (!question.answers.isEmpty()) {
                HorizontalScrollView answerScroll = new HorizontalScrollView(this);
                answerScroll.setHorizontalScrollBarEnabled(false);
                answerScroll.setClipToPadding(false);
                LinearLayout answerRow = new LinearLayout(this);
                answerRow.setOrientation(LinearLayout.HORIZONTAL);
                answerRow.setGravity(Gravity.CENTER_VERTICAL);
                for (RemoteStore.ScriptAnswer answer : question.answers) {
                    TextView pill = Ui.text(this, answer.label, 12, Ui.ACCEPT, true);
                    pill.setGravity(Gravity.CENTER);
                    pill.setPadding(Ui.dp(this, 12), Ui.dp(this, 6),
                            Ui.dp(this, 12), Ui.dp(this, 6));
                    pill.setBackground(Ui.roundedWithStroke(Ui.ACCEPT_LIGHT, 18,
                            Ui.ACCEPT, 1, this));
                    answerRow.addView(pill, sizedMargins(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 8, 0));
                }
                TextView later = Ui.text(this,
                        AppLanguage.ui(schedule.language, "Remind me later"),
                        12, Ui.ACCEPT, true);
                later.setGravity(Gravity.CENTER);
                later.setPadding(Ui.dp(this, 12), Ui.dp(this, 6),
                        Ui.dp(this, 12), Ui.dp(this, 6));
                later.setBackground(Ui.roundedWithStroke(Ui.ACCEPT_LIGHT, 18,
                        Ui.ACCEPT, 1, this));
                answerRow.addView(later, sizedMargins(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 8, 0));
                answerScroll.addView(answerRow);
                card.addView(answerScroll, sizedMargins(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 0, 10, 0, 0));
            } else {
                TextView later = Ui.text(this,
                        AppLanguage.ui(schedule.language, "Remind me later"),
                        12, Ui.ACCEPT, true);
                later.setGravity(Gravity.CENTER);
                later.setPadding(Ui.dp(this, 12), Ui.dp(this, 6),
                        Ui.dp(this, 12), Ui.dp(this, 6));
                later.setBackground(Ui.roundedWithStroke(Ui.ACCEPT_LIGHT, 18,
                        Ui.ACCEPT, 1, this));
                card.addView(later, sizedMargins(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 0, 10, 0, 0));
            }
            card.setContentDescription("Edit question " + (index + 1));
            card.setOnClickListener(v -> showQuestionEditor(schedule, question, () -> {
                renderQuestionList(list, schedule, changed);
                changed.run();
            }));
            list.addView(card, sizedMargins(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 8));
        }
        if (schedule.questions.isEmpty()) {
            TextView empty = Ui.text(this,
                    "Add the first question, answer buttons, and Chitti responses.",
                    15, Ui.MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Ui.dp(this, 32), 0, Ui.dp(this, 32));
            list.addView(empty);
        }
    }

    private void showQuestionEditor(RemoteStore.Schedule schedule,
            RemoteStore.ScriptQuestion existing, Runnable saved) {
        RemoteStore.ScriptQuestion working = existing == null
                ? new RemoteStore.ScriptQuestion() : copyQuestion(existing);
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(Ui.dp(this, 18), Ui.dp(this, 8),
                Ui.dp(this, 18), Ui.dp(this, 14));
        sheet.setBackground(Ui.topRounded(Ui.WHITE, 28, this));
        sheet.setClipChildren(true);
        sheet.setClipToPadding(true);
        sheet.setFocusableInTouchMode(true);
        sheet.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        Ui.safeArea(sheet, true, false, true, true);
        sheet.addView(draggableSheetHandle(dialog, sheet), Ui.margins(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 24), this, 0, 0, 0, 2));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.text(this, existing == null ? "Add question" : "Edit question",
                        24, Ui.INK, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (existing != null) {
            Button delete = button("Delete", Ui.RAISED2, Ui.DANGER);
            delete.setOnClickListener(v -> {
                schedule.questions.remove(existing);
                dialog.dismiss();
                saved.run();
            });
            header.addView(delete, new LinearLayout.LayoutParams(Ui.dp(this, 82), Ui.dp(this, 42)));
        }
        TextView close = Ui.text(this, "×", 26, Ui.MUTED, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close question editor");
        close.setBackground(Ui.rounded(Ui.RAISED2, 21, this));
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 42), Ui.dp(this, 42));
        closeParams.setMarginStart(Ui.dp(this, 8));
        header.addView(close, closeParams);
        sheet.addView(header);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        form.addView(fieldLabel("Question spoken by Chitti"));
        EditText prompt = scriptInput("Type question exactly as Chitti should speak", false, 300);
        prompt.setText(working.prompt);
        form.addView(prompt, sizedMargins(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 96), 0, 5, 0, 4));

        TextView answerHelp = Ui.text(this,
                "Answer buttons (optional)\nLeave all buttons empty for an information-only call.",
                13, Ui.MUTED, false);
        answerHelp.setLineSpacing(0, 1.15f);
        form.addView(answerHelp, sizedMargins(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 10, 0, 2));

        EditText[] answerLabels = new EditText[4];
        EditText[] answerResponses = new EditText[4];
        for (int index = 0; index < 4; index++) {
            form.addView(fieldLabel("Answer " + (index + 1) + " button (optional)"),
                    sizedMargins(ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT, 0, 10, 0, 4));
            EditText answer = scriptInput(index == 0
                    ? "Example: Okay — or leave empty" : "Leave empty if unused", true, 40);
            EditText response = scriptInput(
                    "Chitti response after this answer (optional)", false, 300);
            if (index < working.answers.size()) {
                answer.setText(working.answers.get(index).label);
                response.setText(working.answers.get(index).response);
            }
            answerLabels[index] = answer;
            answerResponses[index] = response;
            form.addView(answer, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 50)));
            form.addView(response, sizedMargins(ViewGroup.LayoutParams.MATCH_PARENT,
                    Ui.dp(this, 72), 0, 5, 0, 0));
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setClipChildren(true);
        scroll.setClipToPadding(true);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(form);
        sheet.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button save = button("Save question", Ui.PRIMARY, Ui.INK);
        save.setOnClickListener(v -> {
            String promptValue = prompt.getText().toString().trim();
            if (promptValue.isEmpty()) {
                prompt.setError("Enter question");
                prompt.requestFocus();
                return;
            }
            List<RemoteStore.ScriptAnswer> answers = new ArrayList<>();
            boolean invalidAnswer = false;
            for (int index = 0; index < answerLabels.length; index++) {
                String answerValue = answerLabels[index].getText().toString().trim();
                String responseValue = answerResponses[index].getText().toString().trim();
                if (answerValue.isEmpty()) {
                    if (!responseValue.isEmpty()) {
                        answerLabels[index].setError("Enter button text");
                        invalidAnswer = true;
                    }
                    continue;
                }
                RemoteStore.ScriptAnswer answer = new RemoteStore.ScriptAnswer();
                answer.label = answerValue;
                answer.response = responseValue;
                answers.add(answer);
            }
            if (invalidAnswer) return;
            working.prompt = promptValue;
            working.answers.clear();
            working.answers.addAll(answers);
            if (existing == null) schedule.questions.add(working);
            else {
                int index = schedule.questions.indexOf(existing);
                if (index >= 0) schedule.questions.set(index, working);
            }
            dialog.dismiss();
            saved.run();
        });
        sheet.addView(save, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));
        dialog.setContentView(sheet);
        showSheet(dialog, 0.84f);
        sheet.postDelayed(() -> {
            sheet.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
            prompt.clearFocus();
            sheet.requestFocus();
            scroll.scrollTo(0, 0);
        }, 360L);
    }

    private EditText scriptInput(String hint, boolean singleLine, int maximum) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextColor(Ui.INK);
        input.setHintTextColor(Ui.MUTED);
        input.setTextSize(15);
        input.setSingleLine(singleLine);
        input.setGravity(singleLine ? Gravity.CENTER_VERTICAL : Gravity.TOP);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maximum)});
        input.setPadding(Ui.dp(this, 14), Ui.dp(this, singleLine ? 0 : 10),
                Ui.dp(this, 14), Ui.dp(this, singleLine ? 0 : 10));
        input.setBackground(Ui.roundedWithStroke(Ui.RAISED2, 14, Ui.LINE, 1, this));
        return input;
    }

    private RemoteStore.ScriptQuestion copyQuestion(RemoteStore.ScriptQuestion source) {
        RemoteStore.ScriptQuestion copy = new RemoteStore.ScriptQuestion();
        copy.prompt = source.prompt;
        for (RemoteStore.ScriptAnswer item : source.answers) {
            RemoteStore.ScriptAnswer answer = new RemoteStore.ScriptAnswer();
            answer.label = item.label;
            answer.response = item.response;
            copy.answers.add(answer);
        }
        return copy;
    }

    private void showSheet(Dialog dialog, float heightRatio) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.setDimAmount(0.48f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            window.setNavigationBarColor(Ui.BG);
            applyLightSystemBars(window);
            window.setWindowAnimations(R.style.BottomSheetAnimation);
        }
        dialog.show();
        if (window != null) {
            int height = Math.round(getResources().getDisplayMetrics().heightPixels * heightRatio);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height);
        }
    }

    private TextView fieldLabel(String value) {
        return Ui.text(this, value, 14, Ui.INK, true);
    }

    private TextView chip(String value, boolean selected) {
        TextView chip = Ui.text(this, value, 12, Ui.MUTED, true);
        chip.setGravity(Gravity.CENTER);
        chip.setOnClickListener(v -> styleChip(chip, !Boolean.TRUE.equals(chip.getTag())));
        styleChip(chip, selected);
        return chip;
    }

    private void styleChip(TextView chip, boolean selected) {
        chip.setTag(selected);
        chip.setTextColor(selected ? Ui.INK : Ui.MUTED);
        chip.setBackground(Ui.roundedWithStroke(
                selected ? Ui.ACCEPT_LIGHT : Ui.RAISED2,
                12,
                selected ? Ui.ACCEPT : Ui.LINE,
                1,
                this));
    }

    private LinearLayout.LayoutParams sizedMargins(int width, int height,
            int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(Ui.dp(this, left), Ui.dp(this, top),
                Ui.dp(this, right), Ui.dp(this, bottom));
        return params;
    }

    private String normalizeMedicineName(String value) {
        if (value == null) return "";
        return value.trim()
                .replaceFirst("(?i)(?:[\\s\\u200B-\\u200D\\uFEFF]*tablet[\\s\\u200B-\\u200D\\uFEFF]*)+$", "")
                .replaceFirst("(?:[\\s\\u200B-\\u200D\\uFEFF]*టాబ్లెట్[\\s\\u200B-\\u200D\\uFEFF]*)+$", "").trim();
    }

    private void addPermissionButton(LinearLayout body) {
        if (callPermissionsReady()) return;

        Button permissions = button(missingPermissionLabel(), Ui.RAISED2, Ui.INK);
        permissions.setOnClickListener(v -> openMissingPermission());
        LinearLayout.LayoutParams params = Ui.matchWrap();
        params.topMargin = Ui.dp(this, 22);
        body.addView(permissions, params);
    }

    private Button button(String label, int background, int foreground) {
        Button button = new Button(this);
        button.setText(AppLanguage.ui(this, label));
        button.setTextSize(14);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(Ui.dp(this, 16), Ui.dp(this, 12),
                Ui.dp(this, 16), Ui.dp(this, 12));
        button.setBackground(Ui.rounded(background, 14, this));
        return button;
    }

    private String timeText(RemoteStore.Schedule schedule) {
        Calendar value = Calendar.getInstance();
        value.set(Calendar.HOUR_OF_DAY, schedule.hour);
        value.set(Calendar.MINUTE, schedule.minute);
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(value.getTime());
    }

    private String daysText(int bits) {
        if (bits == 0b1111111) return AppLanguage.ui(this, "Every day");
        if (bits == 0) return AppLanguage.ui(this, "No days");
        String[] names = AppLanguage.shortDays(currentLanguage());
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < names.length; i++) if ((bits & (1 << i)) != 0) {
            if (output.length() > 0) output.append(", ");
            output.append(names[i]);
        }
        return output.toString();
    }

    private void requestNotificationPermission(boolean userInitiated) {
        if (notificationRuntimePermissionReady()) return;
        SharedPreferences prompts = getSharedPreferences(PERMISSION_PREFS, MODE_PRIVATE);
        boolean requestedBefore = prompts.getBoolean(
                NOTIFICATION_PERMISSION_REQUESTED, false);
        boolean canExplain = Build.VERSION.SDK_INT >= 33
                && shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS);
        if (requestedBefore && !canExplain) {
            if (userInitiated) openNotificationSettings();
            return;
        }
        prompts.edit().putBoolean(NOTIFICATION_PERMISSION_REQUESTED, true).apply();
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 50);
    }

    private void openNotificationSettings() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        android.app.NotificationChannel channel = manager == null ? null
                : manager.getNotificationChannel(CallService.CHANNEL_ID);
        Intent settings;
        if (manager != null && manager.areNotificationsEnabled() && channel != null
                && channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
            settings = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                    .putExtra(Settings.EXTRA_CHANNEL_ID, CallService.CHANNEL_ID);
        } else {
            settings = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        }
        try {
            startActivity(settings);
        } catch (RuntimeException unavailable) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    @android.annotation.SuppressLint("InlinedApi")
    private void openMissingPermission() {
        if (!notificationRuntimePermissionReady()) {
            requestNotificationPermission(true);
            return;
        }
        if (!notificationPermissionReady()) {
            openNotificationSettings();
            return;
        }
        if (!fullScreenCallPermissionReady()) {
            Intent settings = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:" + getPackageName()));
            try {
                startActivity(settings);
            } catch (RuntimeException unavailable) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
            return;
        }
        if (Build.VERSION.SDK_INT >= 31
                && !getSystemService(AlarmManager.class).canScheduleExactAlarms()) {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        if (!batteryOptimizationReady()) {
            Intent request = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            try {
                startActivity(request);
            } catch (RuntimeException unavailable) {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
            return;
        }
        if (backgroundRestricted()) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        render();
    }

    private boolean notificationRuntimePermissionReady() {
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean notificationPermissionReady() {
        if (!notificationRuntimePermissionReady()) return false;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null || !manager.areNotificationsEnabled()) return false;
        android.app.NotificationChannel channel =
                manager.getNotificationChannel(CallService.CHANNEL_ID);
        if (channel != null
                && channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
            return false;
        }
        return true;
    }

    private boolean exactAlarmPermissionReady() {
        return Build.VERSION.SDK_INT < 31
                || getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    private boolean fullScreenCallPermissionReady() {
        if (Build.VERSION.SDK_INT < 34) return true;
        NotificationManager manager = getSystemService(NotificationManager.class);
        return manager != null && manager.canUseFullScreenIntent();
    }

    private boolean callPermissionsReady() {
        return notificationPermissionReady()
                && fullScreenCallPermissionReady()
                && exactAlarmPermissionReady()
                && batteryOptimizationReady()
                && !backgroundRestricted();
    }

    private String missingPermissionLabel() {
        if (!notificationPermissionReady()) return "Allow call notifications";
        if (!fullScreenCallPermissionReady()) return "Allow full-screen reminder calls";
        if (!exactAlarmPermissionReady()) return "Allow exact reminder times";
        return "Allow reliable background calls";
    }

    private boolean batteryOptimizationReady() {
        PowerManager power = getSystemService(PowerManager.class);
        return power != null && power.isIgnoringBatteryOptimizations(getPackageName());
    }

    private boolean backgroundRestricted() {
        if (Build.VERSION.SDK_INT < 28) return false;
        ActivityManager activity = getSystemService(ActivityManager.class);
        return activity != null && activity.isBackgroundRestricted();
    }

    private View draggableSheetHandle(Dialog dialog, View sheet) {
        android.widget.FrameLayout dragArea = new android.widget.FrameLayout(this) {
            @Override public boolean performClick() {
                super.performClick();
                return true;
            }
        };
        dragArea.setClickable(true);
        dragArea.setContentDescription("Swipe down to close");
        View bar = new View(this);
        bar.setBackground(Ui.rounded(Ui.LINE, 3, this));
        android.widget.FrameLayout.LayoutParams barParams =
                new android.widget.FrameLayout.LayoutParams(
                        Ui.dp(this, 42), Ui.dp(this, 5), Gravity.CENTER);
        dragArea.addView(bar, barParams);
        dragArea.setOnTouchListener(new View.OnTouchListener() {
            private float startY;
            private long startTime;

            @Override public boolean onTouch(View view, MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    startY = event.getRawY();
                    startTime = event.getEventTime();
                    sheet.animate().cancel();
                    return true;
                }
                if (action == MotionEvent.ACTION_MOVE) {
                    sheet.setTranslationY(Math.max(0f, event.getRawY() - startY));
                    return true;
                }
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    float distance = Math.max(0f, sheet.getTranslationY());
                    long elapsed = Math.max(1L, event.getEventTime() - startTime);
                    boolean dismiss = action == MotionEvent.ACTION_UP
                            && (distance >= Math.max(Ui.dp(MainActivity.this, 72),
                                    sheet.getHeight() * 0.18f)
                            || distance / elapsed >= Ui.dp(MainActivity.this, 1));
                    view.performClick();
                    if (dismiss) {
                        sheet.animate().translationY(Math.max(sheet.getHeight(),
                                        Ui.dp(MainActivity.this, 420)))
                                .setDuration(180).withEndAction(dialog::dismiss).start();
                    } else {
                        sheet.animate().translationY(0f).setDuration(170).start();
                    }
                    return true;
                }
                return false;
            }
        });
        return dragArea;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                     int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 50) render();
    }
}
