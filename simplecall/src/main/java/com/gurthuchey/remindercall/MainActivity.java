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
import android.os.Handler;
import android.os.Looper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private DayOverview dayOverview;
    private RemoteStore.Config agendaConfig;
    private boolean todayOnly;
    private final Map<TextView, RemoteStore.Schedule> statusPills = new HashMap<>();
    private RemoteStore.Config optimisticConfig;
    private final Handler dayRolloverHandler = new Handler(Looper.getMainLooper());
    private final Runnable overviewTick = new Runnable() {
        @Override public void run() {
            if (dayOverview != null) dayOverview.bind(agendaConfig);
            dayRolloverHandler.postDelayed(this, 60_000L);
        }
    };
    private final Runnable dayRollover = () -> {
        ReminderScheduler.scheduleAll(this, new RemoteStore(this).load());
        render();
        scheduleDayRollover();
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener scheduleListener =
            (preferences, key) -> {
                if (key == null || "config".equals(key)) {
                    optimisticConfig = null;
                    render();
                }
            };
    private final SharedPreferences.OnSharedPreferenceChangeListener statusListener =
            (preferences, key) -> runOnUiThread(() -> {
                refreshDailyStatusPills();
                scheduleDayRollover();
            });

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
        dayRolloverHandler.removeCallbacks(overviewTick);
        dayRolloverHandler.postDelayed(overviewTick, 60_000L);
        scheduleDayRollover();
        if (sync != null) {
            ReminderScheduler.scheduleAll(this, new RemoteStore(this).load());
            render();
        }
    }

    @Override protected void onPause() {
        dayRolloverHandler.removeCallbacks(dayRollover);
        dayRolloverHandler.removeCallbacks(overviewTick);
        super.onPause();
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
        dayRolloverHandler.removeCallbacks(dayRollover);
        dayRolloverHandler.removeCallbacks(overviewTick);
        if (sync != null) sync.stop();
        if (phoneLogin != null) phoneLogin.clear();
        super.onDestroy();
    }

    private void scheduleDayRollover() {
        dayRolloverHandler.removeCallbacks(dayRollover);
        long now = System.currentTimeMillis();
        DailyCallStatus statuses = dailyCallStatus == null
                ? new DailyCallStatus(this) : dailyCallStatus;
        long delay = Math.max(1_000L, statuses.nextRolloverAt(now) - now + 1_000L);
        dayRolloverHandler.postDelayed(dayRollover, delay);
    }

    private void render() {
        RemoteStore store = remoteStore == null ? new RemoteStore(this) : remoteStore;
        RemoteStore.Config config = optimisticConfig == null ? store.load() : optimisticConfig;
        boolean paired = store.pairing() != null;

        if (phoneLogin == null || !phoneLogin.isSignedInWithPhone() || !paired) {
            renderLogin();
            return;
        }
        renderHome(config);
    }

    private void renderHome(RemoteStore.Config config) {
        statusPills.clear();
        agendaConfig = config;
        int previousScrollY = mainScroll == null ? 0 : mainScroll.getScrollY();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Ui.BG);
        page.setClipChildren(true);
        page.addView(buildHomeHeader(), Ui.matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 18), Ui.dp(this, 4), Ui.dp(this, 18), Ui.dp(this, 12));
        Ui.safeArea(body, true, false, true, false);
        dayOverview = new DayOverview(this, dailyCallStatus, reminder -> showScheduleDialog(config, reminder));
        dayOverview.bind(config);
        body.addView(dayOverview, Ui.matchWrap());
        addPermissionButton(body);

        body.addView(Ui.text(this, "Your reminders", 17, Ui.INK, true));
        LinearLayout filters = new LinearLayout(this);
        filters.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        filters.setBackground(Ui.rounded(Ui.RAISED, 14, this));
        for (int i = 0; i < 2; i++) {
            final boolean onlyToday = i == 1;
            TextView filter = Ui.text(this, onlyToday ? "Today only" : "All reminders", 13,
                    todayOnly == onlyToday ? Ui.ACCENT : Ui.MUTED, true);
            filter.setGravity(Gravity.CENTER);
            filter.setSelected(todayOnly == onlyToday);
            filter.setBackground(Ui.rounded(todayOnly == onlyToday ? Ui.WHITE : Color.TRANSPARENT, 11, this));
            filter.setOnClickListener(v -> { todayOnly = onlyToday; renderHome(config); });
            filters.addView(filter, new LinearLayout.LayoutParams(0, Ui.dp(this, 34), 1));
        }
        body.addView(filters, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, this, 0, 7, 0, 12));
        addSchedules(body, config);
        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        mainScroll = scroll;

        LinearLayout dock = new LinearLayout(this);
        dock.setPadding(Ui.dp(this, 18), Ui.dp(this, 6), Ui.dp(this, 18), Ui.dp(this, 6));
        dock.setBackgroundColor(Ui.BG);
        Ui.safeArea(dock, true, false, true, true);
        Button add = button("+  " + AppLanguage.ui(this, "Add reminder"), Ui.ACCENT, Ui.WHITE);
        add.setContentDescription(AppLanguage.ui(this, "Add reminder"));
        add.setOnClickListener(v -> showScheduleDialog(config, null));
        dock.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 46)));
        page.addView(dock);
        setContentView(page);
        scroll.post(() -> { if (mainScroll == scroll) scroll.scrollTo(0, previousScrollY); });
    }

    private View buildHomeHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(this, 18), Ui.dp(this, 4), Ui.dp(this, 18), Ui.dp(this, 4));
        Ui.safeArea(header, true, true, true, false);
        View mark = new View(this);
        mark.setBackground(Ui.reminderMark());
        header.addView(mark, new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30)));
        TextView brand = Ui.text(this, AppLanguage.title(currentLanguage()), 18, Ui.INK, true);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        brandParams.setMarginStart(Ui.dp(this, 10));
        header.addView(brand, brandParams);
        View profile = profileButton();
        profile.setOnClickListener(this::showProfileMenu);
        header.addView(profile, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        return header;
    }

    private String currentLanguage() {
        return AppLanguage.current(this);
    }

    private View profileButton() {
        TextView profile = Ui.text(this, "•••", 16, Ui.ACCENT, true);
        profile.setGravity(Gravity.CENTER);
        profile.setBackground(Ui.rounded(Ui.PRIMARY, 16, this));
        profile.setContentDescription(AppLanguage.ui(this, "Language and account"));
        return profile;
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
        dayOverview = null;
        agendaConfig = null;
        statusPills.clear();
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

        View identity = new View(this);
        identity.setBackground(Ui.reminderMark());
        screen.addView(identity, new LinearLayout.LayoutParams(Ui.dp(this, 68), Ui.dp(this, 68)));
        screen.addView(Ui.spacer(this, 22));
        TextView brand = Ui.text(this, AppLanguage.title(currentLanguage()), 32, Ui.INK, true);
        brand.setGravity(Gravity.CENTER);
        screen.addView(brand, Ui.matchWrap());
        TextView intro = Ui.text(this,
                AppLanguage.ui(this, "A calmer way to remember."),
                15, Ui.MUTED, false);
        intro.setGravity(Gravity.CENTER);
        screen.addView(intro, spaced(0, 7, 0, 0));

        View loginSpacer = new View(this);
        screen.addView(loginSpacer, new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20));
        card.setBackground(Ui.rounded(Ui.WHITE, 26, this));
        card.setElevation(0);
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
        List<RemoteStore.Schedule> visible = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (config != null) for (RemoteStore.Schedule item : config.schedules) {
            if (item.enabled && (!todayOnly || DayOverview.scheduledToday(item, now))) visible.add(item);
        }
        visible.sort(Comparator.comparingInt(item -> item.hour * 60 + item.minute));
        if (visible.isEmpty()) {
            TextView empty = Ui.text(this, todayOnly ? "No reminders scheduled today." : "No reminders yet. Tap + to create one.",
                    16, Ui.MUTED, false);
            empty.setPadding(0, Ui.dp(this, 28), 0, Ui.dp(this, 32));
            body.addView(empty);
            return;
        }
        String period = "";
        for (RemoteStore.Schedule schedule : visible) {
            String nextPeriod = AppLanguage.timePeriod(currentLanguage(), schedule.hour, schedule.minute);
            if (!nextPeriod.equals(period)) {
                TextView section = Ui.text(this, nextPeriod, 12, Ui.ACCENT, true);
                body.addView(section, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, this, 0, period.isEmpty() ? 0 : 12, 0, 4));
                period = nextPeriod;
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(true);
            row.setOnClickListener(v -> showScheduleDialog(config, schedule));
            row.setContentDescription("Edit " + schedule.label + " at " + timeText(schedule));
            LinearLayout when = new LinearLayout(this);
            when.setOrientation(LinearLayout.VERTICAL);
            when.setGravity(Gravity.CENTER_VERTICAL);
            Calendar time = Calendar.getInstance();
            time.set(Calendar.HOUR_OF_DAY, schedule.hour);
            time.set(Calendar.MINUTE, schedule.minute);
            boolean clock24 = android.text.format.DateFormat.is24HourFormat(this);
            TextView hour = Ui.text(this, new java.text.SimpleDateFormat(clock24 ? "HH:mm" : "h:mm",
                    AppLanguage.locale(currentLanguage())).format(time.getTime()), 17, Ui.INK, true);
            hour.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            hour.setSingleLine(true);
            hour.setAutoSizeTextTypeUniformWithConfiguration(12, 17, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
            when.addView(hour);
            if (!clock24) when.addView(Ui.text(this, new java.text.SimpleDateFormat("a",
                    AppLanguage.locale(currentLanguage())).format(time.getTime()).toLowerCase(AppLanguage.locale(currentLanguage())),
                    11, Ui.MUTED, false));
            row.addView(when, new LinearLayout.LayoutParams(Ui.dp(this, 46), ViewGroup.LayoutParams.MATCH_PARENT));
            FrameLayout rail = new FrameLayout(this);
            View line = new View(this);
            line.setBackgroundColor(Ui.LINE);
            FrameLayout.LayoutParams lineParams = new FrameLayout.LayoutParams(Ui.dp(this, 1), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL);
            rail.addView(line, lineParams);
            View dot = new View(this);
            dot.setBackground(Ui.circle(Ui.ACCENT));
            FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(Ui.dp(this, 7), Ui.dp(this, 7), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            dotParams.topMargin = Ui.dp(this, 27);
            rail.addView(dot, dotParams);
            row.addView(rail, new LinearLayout.LayoutParams(Ui.dp(this, 12), ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.HORIZONTAL);
            content.setGravity(Gravity.CENTER_VERTICAL);
            content.setMinimumHeight(Ui.dp(this, 66));
            content.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), 0, Ui.dp(this, 8));
            LinearLayout words = new LinearLayout(this);
            words.setOrientation(LinearLayout.VERTICAL);
            TextView name = Ui.text(this, schedule.label, 15, Ui.INK, true);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            words.addView(name);
            String metadata = categoryName(schedule.category) + " · "
                    + AppLanguage.ui(this, schedule.textReminder() ? "Reminder" : "Call");
            TextView detail = Ui.text(this, metadata, 10, Ui.MUTED, false);
            detail.setSingleLine(true);
            detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
            words.addView(detail);
            if (schedule.days != 127) words.addView(Ui.text(this, daysText(schedule.days), 10, Ui.MUTED, false));
            content.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView tag = (TextView) statusPill(schedule, dailyCallStatus.display(schedule, now).kind);
            tag.setSingleLine(false);
            tag.setMaxLines(2);
            tag.setMaxWidth(Ui.dp(this, 116));
            tag.setEllipsize(android.text.TextUtils.TruncateAt.END);
            content.addView(tag, Ui.margins(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 40), this, 6, 0, 0, 0));
            row.addView(content, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            body.addView(row, Ui.matchWrap());
        }
    }

    private View statusPill(RemoteStore.Schedule schedule, String kind) {
        TextView pill = Ui.text(this, "", 11, Ui.MUTED, true);
        pill.setGravity(Gravity.CENTER);
        pill.setSingleLine(true);
        pill.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        pill.setClickable(true);
        pill.setOnClickListener(v -> showDailyStatusDialog(schedule));
        updateStatusPill(pill, schedule, kind);
        statusPills.put(pill, schedule);
        return pill;
    }

    private void refreshDailyStatusPills() {
        long now = System.currentTimeMillis();
        if (dayOverview != null) dayOverview.bind(agendaConfig);
        for (Map.Entry<TextView, RemoteStore.Schedule> entry : statusPills.entrySet()) {
            RemoteStore.Schedule schedule = entry.getValue();
            updateStatusPill(entry.getKey(), schedule, dailyCallStatus.display(schedule, now).kind);
        }
    }

    private void updateStatusPill(TextView pill, RemoteStore.Schedule schedule, String kind) {
        boolean completed = DailyCallStatus.COMPLETED.equals(kind);
        boolean skipped = DailyCallStatus.SKIPPED.equals(kind);
        String label = completed ? DailyCallStatus.completedLabel(currentLanguage())
                : skipped ? DailyCallStatus.skippedLabel(currentLanguage())
                : DailyCallStatus.NOT_TODAY.equals(kind)
                ? DailyCallStatus.notTodayLabel(currentLanguage())
                : DailyCallStatus.pendingLabel(currentLanguage());
        int ink = completed ? Ui.ACCEPT
                : skipped ? Ui.DANGER
                : Ui.MUTED;
        int background = completed ? Ui.ACCEPT_LIGHT
                : skipped ? Color.rgb(253, 235, 237)
                : Ui.RAISED;
        pill.setText(label);
        pill.setTextColor(ink);
        pill.setBackground(Ui.rounded(background, 10, this));
        pill.setContentDescription(label + ". Change today's status for " + schedule.label);
    }

    private void showDailyStatusDialog(RemoteStore.Schedule schedule) {
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

        sheet.addView(dailyStatusControls(schedule, () -> {
            dialog.dismiss();
            refreshDailyStatusPills();
        }));

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

    private View dailyStatusControls(RemoteStore.Schedule schedule, Runnable changed) {
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        String[] labels = {DailyCallStatus.pendingLabel(currentLanguage()),
                DailyCallStatus.completedLabel(currentLanguage()),
                AppLanguage.ui(currentLanguage(), "Skip for today")};
        String[] hints = {"Keep this on today's list", "Mark today's reminder done", "Resume on the next scheduled day"};
        int[] colors = {Ui.ACCENT, Ui.ACCEPT, Ui.DANGER};
        int[] backgrounds = {Ui.PRIMARY, Ui.ACCEPT_LIGHT, Color.rgb(251, 234, 239)};
        LinearLayout[] controls = new LinearLayout[3];
        TextView[] checks = new TextView[3];
        Runnable update = () -> {
            String kind = dailyCallStatus.display(schedule, System.currentTimeMillis()).kind;
            int selected = DailyCallStatus.COMPLETED.equals(kind) ? 1
                    : DailyCallStatus.SKIPPED.equals(kind) ? 2
                    : DailyCallStatus.NOT_TODAY.equals(kind) ? -1 : 0;
            for (int i = 0; i < controls.length; i++) {
                controls[i].setSelected(i == selected);
                controls[i].setBackground(Ui.roundedWithStroke(i == selected ? backgrounds[i] : Ui.BG,
                        16, i == selected ? colors[i] : Ui.BG, 1, this));
                checks[i].setText(i == selected ? "✓" : "○");
            }
        };
        for (int index = 0; index < controls.length; index++) {
            int choice = index;
            LinearLayout control = new LinearLayout(this);
            control.setGravity(Gravity.CENTER_VERTICAL);
            control.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
            control.setMinimumHeight(Ui.dp(this, 66));
            control.setContentDescription("Today's status: " + labels[index]);
            LinearLayout words = new LinearLayout(this);
            words.setOrientation(LinearLayout.VERTICAL);
            words.addView(Ui.text(this, labels[index], 15, colors[index], true));
            words.addView(Ui.text(this, hints[index], 12, Ui.MUTED, false));
            control.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView check = Ui.text(this, "○", 23, colors[index], true);
            check.setGravity(Gravity.CENTER);
            control.addView(check, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
            checks[index] = check;
            control.setOnClickListener(v -> {
                if (choice == 0) {
                    dailyCallStatus.markPending(schedule.id);
                    ReminderScheduler.scheduleAll(this, new RemoteStore(this).load());
                } else if (choice == 1) {
                    ReminderScheduler.cancelRetry(this, schedule.id, ReminderScheduler.PHASE_MEAL);
                    ReminderScheduler.cancelRetry(this, schedule.id, ReminderScheduler.PHASE_MEDICINE);
                    ReminderScheduler.cancelRetry(this, schedule.id, ReminderScheduler.PHASE_CONFIRMATION);
                    dailyCallStatus.markCompleted(schedule.id, ReminderScheduler.PHASE_MEDICINE);
                    if (CallService.isProcessCallActive(schedule.id)) {
                        startService(new Intent(this, CallService.class)
                                .setAction(CallService.ACTION_COMPLETE_TODAY)
                                .putExtra(ReminderScheduler.EXTRA_ID, schedule.id));
                    }
                } else {
                    dailyCallStatus.markSkippedToday(schedule.id);
                    ReminderScheduler.skipRemainingToday(this, schedule.id);
                    if (CallService.isProcessCallActive(schedule.id)) {
                        startService(new Intent(this, CallService.class)
                                .setAction(CallService.ACTION_SKIP_TODAY)
                                .putExtra(ReminderScheduler.EXTRA_ID, schedule.id));
                    }
                }
                update.run();
                changed.run();
            });
            controls[index] = control;
            choices.addView(control, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, this, 0, index == 0 ? 0 : 8, 0, 0));
        }
        update.run();
        return choices;
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
            delete.setElevation(0);
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
        close.setContentDescription("Close reminder editor");
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 42), Ui.dp(this, 42));
        closeParams.setMarginStart(Ui.dp(this, 8));
        header.addView(close, closeParams);
        sheet.addView(header);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 24));
        form.setClipChildren(false);
        form.setClipToPadding(false);

        TextView categoryLabel = fieldLabel("Category");
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
        categoryRow.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
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
                    ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 42));
            optionParams.setMarginEnd(Ui.dp(this, 8));
            categoryRow.addView(option, optionParams);
        }
        categoryScroll.addView(categoryRow);
        LinearLayout.LayoutParams categoryScrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48));
        categoryScrollParams.setMargins(-Ui.dp(this, 18), Ui.dp(this, 2),
                -Ui.dp(this, 18), 0);
        categoryScroll.post(() -> categoryScroll.scrollTo(
                Math.max(0, categoryChoices[selectedCategory[0]].getLeft() - Ui.dp(this, 18)), 0));

        TextView reminderNameLabel = fieldLabel("Medicine name");
        form.addView(reminderNameLabel, sizedMargins(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0));
        EditText medicine = new EditText(this);
        medicine.setHint("Example: Metformin");
        medicine.setText("medicine".equals(draft.category)
                ? normalizeMedicineName(draft.label) : draft.label);
        medicine.setSingleLine(true);
        medicine.setTextSize(23);
        medicine.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        medicine.setTextColor(Ui.INK);
        medicine.setHintTextColor(Ui.MUTED);
        medicine.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        medicine.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        medicine.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        medicine.setBackground(Ui.roundedWithStroke(Ui.RAISED2, 14, Ui.LINE, 1, this));
        form.addView(medicine, sizedMargins(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 64), 0, 6, 0, 0));
        form.addView(categoryLabel, sizedMargins(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 18, 0, 6));
        form.addView(categoryScroll, categoryScrollParams);

        form.addView(fieldLabel("Time & mode"), sizedMargins(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 18, 0, 8));
        int[] selectedTime = {draft.hour, draft.minute};
        Button time = button(timeText(draft), Ui.RAISED2, Ui.INK);
        time.setTextSize(23);
        time.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
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
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setGravity(Gravity.CENTER_VERTICAL);
        modeRow.addView(time, new LinearLayout.LayoutParams(0, Ui.dp(this, 60), 1.3f));
        TextView[] modeChips = new TextView[2];
        String[] modes = {"call", "reminder"};
        for (int index = 0; index < modes.length; index++) {
            int choice = index;
            TextView option = chip(index == 0 ? "Call" : "Reminder",
                    modes[index].equals(draft.deliveryMode));
            option.setContentDescription(index == 0 ? "Mode: Call" : "Mode: Reminder");
            option.setOnClickListener(v -> {
                draft.deliveryMode = modes[choice];
                for (int i = 0; i < modeChips.length; i++) {
                    styleChip(modeChips[i], i == choice);
                }
            });
            modeChips[index] = option;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, Ui.dp(this, 60), 1f);
            params.setMarginStart(Ui.dp(this, 6));
            modeRow.addView(option, params);
        }
        form.addView(modeRow);

        form.addView(fieldLabel("Repeat on"), sizedMargins(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 18, 0, 8));
        String[] dayNames = AppLanguage.shortDays(currentLanguage());
        TextView[] days = new TextView[7];
        LinearLayout dayRow = new LinearLayout(this);
        for (int index = 0; index < dayNames.length; index++) {
            TextView chip = chip(dayNames[index], (draft.days & (1 << index)) != 0);
            chip.setSingleLine(true);
            chip.setAutoSizeTextTypeUniformWithConfiguration(8, 12, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
            days[index] = chip;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, Ui.dp(this, 42), 1f);
            if (index < 6) params.setMarginEnd(Ui.dp(this, 4));
            dayRow.addView(chip, params);
        }
        form.addView(dayRow);

        LinearLayout medicineOptions = new LinearLayout(this);
        medicineOptions.setOrientation(LinearLayout.VERTICAL);
        medicineOptions.addView(fieldLabel("Meal Reminder"), sizedMargins(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 18, 0, 8));
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
                    0, Ui.dp(this, 42), 1f);
            if (index < leadValues.length - 1) params.setMarginEnd(Ui.dp(this, 6));
            leadRow.addView(chip, params);
        }
        medicineOptions.addView(leadRow);
        form.addView(medicineOptions);

        LinearLayout conversation = new LinearLayout(this);
        conversation.setOrientation(LinearLayout.VERTICAL);
        conversation.addView(fieldLabel("Questions & answers"), sizedMargins(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 18, 0, 8));
        Button conversationButton = button("", Ui.PRIMARY, Ui.ACCENT);
        conversationButton.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        conversationButton.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        conversationButton.setBackground(Ui.roundedWithStroke(
                Ui.PRIMARY, 14, Ui.PRIMARY, 1, this));
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
            reminderNameLabel.setText(AppLanguage.ui(this, isMedicine ? "Medicine name" : "Reminder title"));
            medicine.setHint(AppLanguage.ui(this, isMedicine ? "Example: Metformin" : "Example: Pay electricity bill"));
            medicineOptions.setVisibility(isMedicine ? View.VISIBLE : View.GONE);
            conversation.setVisibility(View.VISIBLE);
            updateConversationSummary(conversationButton, draft);
        };
        updateCategoryUi[0].run();

        ScrollView editorScroll = new ScrollView(this);
        editorScroll.setVerticalScrollBarEnabled(false);
        editorScroll.setFillViewport(false);
        editorScroll.addView(form);
        sheet.addView(editorScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

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
            draft.confirmationMinutes = 0;
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
        showSheet(dialog, .92f);
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
        copy.confirmationMinutes = 0;
        copy.deliveryMode = source.deliveryMode;
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
        choice.setGravity(Gravity.CENTER_VERTICAL);
        choice.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        choice.setClickable(true);
        choice.setFocusable(true);
        TextView name = Ui.text(this, categoryName(category), 12, Ui.MUTED, true);
        choice.addView(name);
        choice.setTag(name);
        styleCategoryChoice(choice, selected);
        return choice;
    }

    private void styleCategoryChoice(LinearLayout choice, boolean selected) {
        TextView name = (TextView) choice.getTag();
        choice.setBackground(Ui.rounded(selected ? Ui.ACCENT : Ui.RAISED, 12, this));
        name.setTextColor(selected ? Ui.WHITE : Ui.MUTED);
        choice.setContentDescription(name.getText() + (selected ? ", selected" : ", not selected"));
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
        header.addView(Ui.text(this, "Questions & answers", 24, Ui.INK, true),
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
        chip.setTextColor(selected ? Ui.ACCENT : Ui.MUTED);
        chip.setBackground(Ui.roundedWithStroke(
                selected ? Ui.PRIMARY : Ui.RAISED2,
                12,
                selected ? Ui.ACCENT : Ui.LINE,
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
        button.setTextColor(background == Ui.ACCENT ? Ui.WHITE : foreground);
        button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setStateListAnimator(null);
        button.setElevation(0);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(Ui.dp(this, 16), Ui.dp(this, 12),
                Ui.dp(this, 16), Ui.dp(this, 12));
        button.setBackground(Ui.actionBackground(this, background, 18));
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
