package com.gurthuchey.admin;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Editable;
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
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MainActivity extends FragmentActivity {
    private static final String[] CATEGORY_NAMES = {
            "💊  Medicine", "Supplement", "🥣  Meal", "🥤  Drink",
            "🏃  Exercise", "📅  Appointment", "₹  Bill or payment",
            "✓  Task", "☀  Wake-up", "🔔  Custom"
    };
    private static final String[] CATEGORY_VALUES = {
            "medicine", "supplement", "meal", "drink", "exercise", "appointment",
            "payment", "task", "wake_up", "custom"
    };
    private final List<Models.Member> members = new ArrayList<>();
    private final List<AdminFirebase.PhoneDevice> phones = new ArrayList<>();
    private final Set<String> cleanupRequested = new HashSet<>();
    private final Set<String> pendingMemberDeletes = new HashSet<>();
    private final Set<String> memberDeleteRequests = new HashSet<>();
    private AdminStore store;
    private AdminAuth adminAuth;
    private PhoneLogin phoneLogin;
    private AdminFirebase firebase;
    private LinearLayout page;
    private ScrollView mainScroll;
    private boolean firebaseReady;
    private boolean backendListenersStarted;
    private boolean adminStarted;
    private boolean signingIn;
    private String signInMessage = "";
    private String loginPhone = "";
    private boolean otpSent;
    private boolean truecallerAutoAttempted;
    private String selectedMemberId;

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

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLanguage.syncLauncherLabel(this);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.PAPER);
        applyLightSystemBars(getWindow());
        store = new AdminStore(this);
        adminAuth = new AdminAuth(this);
        adminAuth.clearAnonymousUser();
        phoneLogin = new PhoneLogin(this, "admin");
        if (adminAuth.hasAuthorizedUser()) startAdmin();
        else {
            render();
            if (phoneLogin.isSignedInWithPhone()) verifyAdminAfterLogin();
        }
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.PAPER);
        applyLightSystemBars(getWindow());
        render();
    }

    private void startAdmin() {
        if (adminStarted) return;
        adminStarted = true;
        members.clear();
        members.addAll(store.load());
        pendingMemberDeletes.clear();
        pendingMemberDeletes.addAll(store.loadDeletedMembers());
        members.removeIf(member -> pendingMemberDeletes.contains(member.id));
        firebase = new AdminFirebase(this);
        render();
        firebase.start(members, (status, ready) -> runOnUiThread(() -> {
            firebaseReady = ready;
            if (ready && !backendListenersStarted) {
                backendListenersStarted = true;
                firebase.listenToMembers(updated -> runOnUiThread(() -> {
                    updated.removeIf(member -> pendingMemberDeletes.contains(member.id));
                    members.clear();
                    members.addAll(updated);
                    store.save(members);
                    render();
                }));
                firebase.listenToDevices(updated -> runOnUiThread(() -> {
                    phones.clear();
                    phones.addAll(updated);
                    cleanupSupersededPhones();
                    render();
                }));
            }
            if (ready) flushPendingMemberDeletes();
        }));
    }

    private void render() {
        if (adminAuth == null || !adminAuth.hasAuthorizedUser()) {
            renderSignIn();
            return;
        }
        int previousScrollY = mainScroll == null ? 0 : mainScroll.getScrollY();
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.PAPER);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        Ui.safeArea(root, false, false, false, true);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Ui.PAPER);
        // Header lives outside scrolling viewport. Clip viewport so overscroll and
        // elevated member cards cannot draw over fixed header.
        shell.setClipChildren(true);
        shell.setClipToPadding(true);
        root.addView(shell, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        shell.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipChildren(true);
        scroll.setClipToPadding(true);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, 0, 0, Ui.dp(this, 28));
        scroll.addView(page, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        shell.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (!members.isEmpty()) buildMemberSection();
        mainScroll = scroll;
        setContentView(root);
        scroll.post(() -> scroll.scrollTo(0, previousScrollY));
    }

    private void renderSignIn() {
        mainScroll = null;
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        Ui.safeArea(root, true, true, true, true);

        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setGravity(Gravity.CENTER_HORIZONTAL);
        screen.setPadding(Ui.dp(this, 24), Ui.dp(this, 34),
                Ui.dp(this, 24), Ui.dp(this, 28));
        root.addView(screen, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView eyebrow = Ui.text(this, "Admin", 13, Ui.NAVY, true);
        screen.addView(eyebrow);

        TextView title = Ui.text(this, AppLanguage.title(this), 30, Ui.INK, true);
        title.setGravity(Gravity.CENTER);
        screen.addView(title, Ui.margins(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, this, 0, 10, 0, 0));

        TextView intro = Ui.text(this,
                "Sign in once to restore and manage your family’s reminders on any phone.",
                16, Ui.MUTED, false);
        intro.setGravity(Gravity.CENTER);
        screen.addView(intro, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, this, 18, 12, 18, 0));

        View loginSpacer = new View(this);
        screen.addView(loginSpacer, new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 22), Ui.dp(this, 24),
                Ui.dp(this, 22), Ui.dp(this, 22));
        card.setBackground(Ui.strokedShape(Ui.RAISED, 22, Ui.LINE, 1, this));
        card.setElevation(Ui.dp(this, 2));
        screen.addView(card, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, this, 0, 24, 0, 0));

        TextView access = Ui.text(this, "Private admin access", 20, Ui.INK, true);
        card.addView(access);
        String help = "Use the authorized admin mobile number: +91 91772 16132.";
        card.addView(Ui.text(this, help, 14, Ui.MUTED, false),
                Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, this, 0, 7, 0, 20));

        EditText phoneInput = new EditText(this);
        phoneInput.setSingleLine(true);
        phoneInput.setTextColor(Ui.INK);
        phoneInput.setHintTextColor(Ui.MUTED);
        phoneInput.setTextSize(17);
        phoneInput.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        phoneInput.setBackground(Ui.strokedShape(Ui.RAISED2, 14, Ui.LINE, 1, this));
        phoneInput.setHint("10-digit mobile number");
        phoneInput.setInputType(InputType.TYPE_CLASS_PHONE);
        phoneInput.setText(localNumber(loginPhone));
        phoneInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String digits = s == null ? "" : s.toString().replaceAll("[^0-9]", "");
                if (otpSent && !digits.equals(localNumber(loginPhone))) {
                    loginPhone = digits;
                    otpSent = false;
                    signInMessage = "";
                    phoneInput.post(MainActivity.this::renderSignIn);
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
            otpInput.setBackground(Ui.strokedShape(Ui.RAISED2, 14, Ui.LINE, 1, this));
            otpInput.setHint("6-digit OTP");
            otpInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            otpInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String code = s == null ? "" : s.toString().replaceAll("[^0-9]", "");
                    if (code.length() == 6 && !signingIn) {
                        signingIn = true;
                        phoneLogin.verifyOtp(code, phoneListener());
                    }
                }
                @Override public void afterTextChanged(Editable s) {}
            });
            card.addView(otpInput, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                    Ui.dp(this, 56), this, 0, 12, 0, 0));
        }

        Button signIn = Ui.button(this,
                signingIn ? "Please wait…" : otpSent ? "Resend OTP" : "Send OTP",
                otpSent ? Ui.RAISED2 : Ui.PRIMARY, Ui.INK);
        signIn.setEnabled(!signingIn);
        signIn.setTextSize(otpSent ? 13 : 16);
        signIn.setOnClickListener(v -> {
            signingIn = true;
            signInMessage = "";
            loginPhone = phoneInput.getText().toString();
            phoneLogin.sendOtp(loginPhone, phoneListener());
            renderSignIn();
        });
        LinearLayout.LayoutParams signInParams = Ui.margins(
                otpSent ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, otpSent ? 44 : 56), this, 0, 12, 0, 0);
        signInParams.gravity = otpSent ? Gravity.END : Gravity.NO_GRAVITY;
        card.addView(signIn, signInParams);

        if (!signInMessage.isEmpty()) {
            TextView error = Ui.text(this, signInMessage, 13, Ui.DANGER, false);
            error.setGravity(Gravity.CENTER);
            card.addView(error, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, this, 2, 16, 2, 0));
        }

        keepLoginCardAboveKeyboard(card);

        TextView note = Ui.text(this,
                "Members, schedules, and connected parent phones stay in Firebase and return after sign-in.",
                12, Ui.MUTED, false);
        note.setGravity(Gravity.CENTER);
        screen.addView(note, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, this, 16, 24, 16, 0));
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
            root.addView(activationLayer, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        setContentView(root);
    }

    private void keepLoginCardAboveKeyboard(View card) {
        if (android.os.Build.VERSION.SDK_INT < 30) return;
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
        if (signingIn || phoneLogin == null || !phoneLogin.isTruecallerInstalled()
                || !phoneLogin.isTruecallerUsable()) return;
        signingIn = true;
        signInMessage = "Opening Truecaller…";
        phoneLogin.startTruecaller(phoneListener());
    }

    private static String localNumber(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        return digits.length() == 12 && digits.startsWith("91") ? digits.substring(2) : digits;
    }

    private PhoneLogin.Listener phoneListener() {
        return new PhoneLogin.Listener() {
            @Override public void onCodeSent(String phone) {
                runOnUiThread(() -> {
                    loginPhone = phone;
                    otpSent = true;
                    signingIn = false;
                    signInMessage = "OTP sent to " + phone;
                    renderSignIn();
                });
            }

            @Override public void onComplete(String phone) {
                runOnUiThread(() -> {
                    loginPhone = phone;
                    otpSent = false;
                    verifyAdminAfterLogin();
                });
            }

            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    signingIn = false;
                    signInMessage = message;
                    renderSignIn();
                });
            }
        };
    }

    private void verifyAdminAfterLogin() {
        signingIn = true;
        signInMessage = "Checking admin access…";
        renderSignIn();
        adminAuth.verifyCurrent((success, message) -> runOnUiThread(() -> {
            signingIn = false;
            if (success) startAdmin();
            else {
                signInMessage = message;
                renderSignIn();
            }
        }));
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 20), Ui.dp(this, 14));
        header.setBackgroundColor(Ui.BG);
        header.setClipChildren(false);
        header.setClipToPadding(false);
        header.setElevation(Ui.dp(this, 8));
        header.setTranslationZ(Ui.dp(this, 8));
        Ui.safeArea(header, true, true, true, false);

        View profile = profileButton();
        profile.setOnClickListener(this::showProfileMenu);
        LinearLayout.LayoutParams profileParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 44), Ui.dp(this, 44));
        profileParams.setMarginEnd(Ui.dp(this, 10));
        header.addView(profile, profileParams);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(Ui.text(this, AppLanguage.title(this), 20, Ui.INK, true));
        words.addView(Ui.text(this, AppLanguage.count(this, members.size(), "member", "members"),
                12, Ui.MUTED, false));
        header.addView(words, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button addMember = Ui.button(this, "Add Member", Ui.GOLD, Ui.INK);
        addMember.setTextSize(12);
        addMember.setOnClickListener(v -> showMemberDialog());
        header.addView(addMember, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 44)));
        return header;
    }

    private View profileButton() {
        FrameLayout host = new FrameLayout(this);
        host.setBackground(Ui.strokedShape(Ui.RAISED, 22, Ui.LINE, 1, this));
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
        card.setBackground(Ui.strokedShape(Ui.RAISED2, 18, Ui.LINE, 1, this));
        PopupWindow popup = new PopupWindow(card, Ui.dp(this, 286),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);

        TextView heading = Ui.text(this, AppLanguage.ui(this, "Language"),
                13, Ui.MUTED, true);
        card.addView(heading, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, this, 6, 2, 6, 8));

        String selected = AppLanguage.current(this);
        for (int rowIndex = 0; rowIndex < 3; rowIndex++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int column = 0; column < 2; column++) {
                int index = rowIndex * 2 + column;
                Button language = Ui.button(this, AppLanguage.NAMES[index],
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

        Button logout = Ui.button(this, AppLanguage.ui(this, "Log out"), Ui.RAISED, Ui.DANGER);
        logout.setTextSize(12);
        logout.setOnClickListener(v -> {
            popup.dismiss();
            signOutAdmin();
        });
        card.addView(logout, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 44), this, 0, 10, 0, 0));
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(Ui.dp(this, 10));
        popup.showAsDropDown(anchor, 0, Ui.dp(this, 6), Gravity.START);
    }

    private void signOutAdmin() {
        if (firebase != null) firebase.stop();
        adminAuth.signOut();
        adminStarted = false;
        backendListenersStarted = false;
        firebaseReady = false;
        members.clear();
        phones.clear();
        render();
    }

    private AdminFirebase.PhoneDevice connectedPhone(String memberId) {
        Set<String> activeMemberIds = new HashSet<>();
        for (Models.Member member : members) activeMemberIds.add(member.id);
        return PhoneDeviceReconciler.connectedPhone(memberId, phones, activeMemberIds);
    }

    private boolean isSuperseded(AdminFirebase.PhoneDevice candidate) {
        Set<String> activeMemberIds = new HashSet<>();
        for (Models.Member member : members) activeMemberIds.add(member.id);
        return PhoneDeviceReconciler.isSuperseded(candidate, phones, activeMemberIds);
    }

    private void cleanupSupersededPhones() {
        for (AdminFirebase.PhoneDevice phone : phones) {
            if (isSuperseded(phone) && cleanupRequested.add(phone.uid)) firebase.deleteDevice(phone.uid);
        }
    }

    private void buildMemberSection() {
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(Ui.dp(this, 22), Ui.dp(this, 24), Ui.dp(this, 22), Ui.dp(this, 6));
        TextView section = Ui.text(this, AppLanguage.ui(this, "Members"), 21, Ui.INK, true);
        titleRow.addView(section, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView count = Ui.text(this,
                AppLanguage.count(this, members.size(), "member", "members"),
                13, Ui.MUTED, false);
        titleRow.addView(count);
        page.addView(titleRow);

        page.addView(memberCarousel(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private View memberCarousel() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setClipChildren(false);

        int swipeThreshold = Ui.dp(this, 24);
        List<View> dots = new ArrayList<>();

        class MemberPager extends FrameLayout {
            private float downX;
            private float downY;
            private int selectedPage;

            MemberPager(Context context) {
                super(context);
                setClipChildren(false);
                setClipToPadding(false);
            }

            void showPage(int requestedPage, boolean animate) {
                int target = Math.max(0, Math.min(members.size() - 1, requestedPage));
                if (getChildCount() > 0 && target == selectedPage) return;

                int direction = target >= selectedPage ? 1 : -1;
                View incoming = memberCard(members.get(target));
                removeAllViews();
                addView(incoming, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                selectedPage = target;
                selectedMemberId = members.get(target).id;
                styleCarouselDots(dots, selectedPage);
                requestLayout();

                if (animate && getWidth() > 0) {
                    incoming.setTranslationX(direction * Ui.dp(MainActivity.this, 54));
                    incoming.setAlpha(0.72f);
                    incoming.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(170)
                            .start();
                }
            }

            @Override public boolean onInterceptTouchEvent(MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    downX = event.getX();
                    downY = event.getY();
                    return false;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    float horizontal = Math.abs(event.getX() - downX);
                    float vertical = Math.abs(event.getY() - downY);
                    if (horizontal >= swipeThreshold && horizontal > vertical) return true;
                }
                return false;
            }

            @Override public boolean onTouchEvent(MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    downX = event.getX();
                    downY = event.getY();
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP
                        || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    float distance = event.getX() - downX;
                    if (Math.abs(distance) >= swipeThreshold) {
                        showPage(selectedPage + (distance < 0 ? 1 : -1), true);
                    } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                        performClick();
                    }
                    return true;
                }
                return true;
            }

            @Override public boolean performClick() {
                super.performClick();
                return true;
            }
        }

        MemberPager pager = new MemberPager(this);
        container.addView(pager, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, this, 18, 10, 18, 0));

        LinearLayout indicators = new LinearLayout(this);
        indicators.setOrientation(LinearLayout.HORIZONTAL);
        indicators.setGravity(Gravity.CENTER);
        for (int index = 0; index < members.size(); index++) {
            View dot = new View(this);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                    Ui.dp(this, 7), Ui.dp(this, 7));
            dotParams.setMarginStart(Ui.dp(this, 4));
            dotParams.setMarginEnd(Ui.dp(this, 4));
            indicators.addView(dot, dotParams);
            dots.add(dot);
            final int target = index;
            dot.setOnClickListener(v -> pager.showPage(target, true));
        }
        container.addView(indicators, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 18), this, 0, 10, 0, 8));

        int initialPage = 0;
        if (selectedMemberId != null) {
            for (int index = 0; index < members.size(); index++) {
                if (selectedMemberId.equals(members.get(index).id)) {
                    initialPage = index;
                    break;
                }
            }
        }
        pager.showPage(initialPage, false);
        return container;
    }

    private void styleCarouselDots(List<View> dots, int selected) {
        for (int index = 0; index < dots.size(); index++) {
            dots.get(index).setBackground(Ui.shape(
                    index == selected ? Ui.GOLD : Ui.LINE,
                    4, this));
        }
    }

    private View memberCard(Models.Member member) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 16));
        card.setBackground(Ui.strokedShape(Ui.RAISED, 20, Ui.LINE, 1, this));
        card.setElevation(Ui.dp(this, 2));

        LinearLayout identity = new LinearLayout(this);
        identity.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.text(this, member.name, 20, Ui.INK, true);
        names.addView(name);
        int reminderCount = member.schedules.size();
        String count = reminderCount + (reminderCount == 1 ? " reminder" : " reminders");
        names.addView(Ui.text(this, count, 13, Ui.MUTED, false), Ui.margins(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, this, 0, 3, 0, 0));
        identity.addView(names, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button delete = Ui.button(this, "Remove", Ui.RAISED2, Ui.DANGER);
        delete.setTextSize(12);
        delete.setContentDescription("Remove " + member.name);
        delete.setOnClickListener(v -> confirmMemberDelete(member));
        identity.addView(delete);
        card.addView(identity);

        AdminFirebase.PhoneDevice connectedPhone = connectedPhone(member.id);
        TextView phoneState = Ui.text(this,
                member.phoneE164 == null || member.phoneE164.isEmpty()
                        ? "Phone number required"
                        : member.phoneE164 + (connectedPhone == null ? " · not signed in" : " · connected"),
                12, connectedPhone == null ? Ui.MUTED : Ui.TEAL, connectedPhone != null);
        phoneState.setPadding(0, Ui.dp(this, 12), 0, 0);
        card.addView(phoneState);

        if (member.schedules.isEmpty()) {
            TextView empty = Ui.text(this, "No reminders yet.", 14, Ui.MUTED, false);
            empty.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 8));
            card.addView(empty);
        } else {
            String currentPeriod = "";
            for (Models.Schedule schedule : sortedSchedules(member)) {
                String period = periodIcon(schedule.hour, schedule.minute) + "  "
                        + AppLanguage.timePeriod(this, schedule.hour, schedule.minute);
                if (!period.equals(currentPeriod)) {
                    TextView label = Ui.text(this, period, 12, Ui.MUTED, true);
                    card.addView(label, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT, this, 4,
                            currentPeriod.isEmpty() ? 14 : 12, 0, 0));
                    currentPeriod = period;
                }
                card.addView(medicineCard(member, schedule),
                        Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT, this, 0, 6, 0, 0));
            }
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button connect = Ui.button(this,
                member.phoneE164 == null || member.phoneE164.isEmpty() ? "Add phone" : "Change phone",
                Ui.GOLD, Ui.INK);
        connect.setTextSize(12);
        connect.setContentDescription("Set login phone number for " + member.name);
        connect.setOnClickListener(v -> showPhoneDialog(member));
        LinearLayout.LayoutParams connectParams = new LinearLayout.LayoutParams(
                0, Ui.dp(this, 50), 1f);
        connectParams.setMarginEnd(Ui.dp(this, 8));
        actions.addView(connect, connectParams);
        Button addMedicine = Ui.button(this, "+ Add reminder", Ui.PRIMARY, Ui.INK);
        addMedicine.setTextSize(12);
        addMedicine.setOnClickListener(v -> showScheduleDialog(member, null));
        actions.addView(addMedicine, new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1f));
        card.addView(actions, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, this, 0, 16, 0, 0));
        return card;
    }

    private void showPairingCode(Models.Member member, boolean reconnecting) {
        AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle(reconnecting ? "Creating new connection code…"
                        : "Creating connection code…")
                .setMessage("Making a fresh one-time code for " + member.name + ".")
                .setCancelable(false)
                .create();
        progress.show();
        firebase.createPairingCode(member, (success, code, message) -> runOnUiThread(() -> {
            progress.dismiss();
            if (!success) {
                new AlertDialog.Builder(this)
                        .setTitle("Could not create code")
                        .setMessage(message)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Try again",
                                (dialog, which) -> showPairingCode(member, reconnecting))
                        .show();
                return;
            }

            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(Ui.dp(this, 24), Ui.dp(this, 8), Ui.dp(this, 24), 0);
            TextView explanation = Ui.text(this,
                    reconnecting
                            ? "Open the newly installed Reminder Call app on " + member.name
                                    + "’s phone and enter this code. Their reminders and times will return automatically."
                            : "Open Reminder Call on " + member.name
                                    + "’s phone and enter this code.",
                    15, Ui.MUTED, false);
            content.addView(explanation);

            String displayedCode = code.substring(0, 3) + "  " + code.substring(3);
            TextView codeView = Ui.text(this, displayedCode, 34, Ui.NAVY, true);
            codeView.setGravity(Gravity.CENTER);
            codeView.setLetterSpacing(0.11f);
            codeView.setPadding(Ui.dp(this, 12), Ui.dp(this, 22), Ui.dp(this, 12), Ui.dp(this, 22));
            codeView.setBackground(Ui.shape(Color.rgb(255, 245, 211), 18, this));
            content.addView(codeView, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, this, 0, 18, 0, 14));
            content.addView(Ui.text(this, "Expires in 10 minutes · works once", 13, Ui.CORAL, true));

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle((reconnecting ? "Reconnect " : "Connect ")
                            + member.name + "’s phone")
                    .setView(content)
                    .setNegativeButton("Done", null)
                    .setPositiveButton("Copy code", null)
                    .create();
            dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("Gurthu Chey connection code", code));
                Toast.makeText(this, "Code copied", Toast.LENGTH_SHORT).show();
            }));
            dialog.show();
        }));
    }

    private Map<String, List<Models.Schedule>> medicineGroups(Models.Member member) {
        Map<String, List<Models.Schedule>> groups = new LinkedHashMap<>();
        for (Models.Schedule schedule : member.schedules) {
            String key = schedule.label.trim().toLowerCase(Locale.US);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(schedule);
        }
        for (List<Models.Schedule> times : groups.values()) {
            times.sort(Comparator.comparingInt(value -> value.hour * 60 + value.minute));
        }
        return groups;
    }

    private List<Models.Schedule> sortedSchedules(Models.Member member) {
        List<Models.Schedule> schedules = new ArrayList<>(member.schedules);
        schedules.sort(Comparator
                .comparingInt((Models.Schedule value) -> value.hour * 60 + value.minute)
                .thenComparing(value -> value.label, String.CASE_INSENSITIVE_ORDER));
        return schedules;
    }

    private View medicineCard(Models.Member member, Models.Schedule schedule) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(this, 76));
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        row.setBackground(Ui.strokedShape(Ui.RAISED2, 15, Ui.LINE, 1, this));
        row.setElevation(Ui.dp(this, 1));

        View icon = categoryIconView(schedule.category);
        row.addView(icon, Ui.margins(Ui.dp(this, 40), Ui.dp(this, 40),
                this, 0, 0, 12, 0));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(Ui.text(this, schedule.label, 15, Ui.INK, true));
        String meta = categoryName(schedule.category) + " · " + localizedDaysText(schedule.days)
                + ("medicine".equals(schedule.category) && schedule.preMinutes > 0
                ? " · meal call " + schedule.preMinutes + " min before" : "")
                + (schedule.confirmationMinutes > 0
                ? " · confirm after " + schedule.confirmationMinutes + " min" : "")
                + (!schedule.enabled ? " · Paused" : "");
        words.addView(Ui.text(this, meta, 11,
                schedule.enabled ? Ui.MUTED : Ui.CORAL, !schedule.enabled));
        row.addView(words, Ui.margins(0, ViewGroup.LayoutParams.WRAP_CONTENT,
                this, 0, 0, 8, 0));
        ((LinearLayout.LayoutParams) words.getLayoutParams()).weight = 1f;

        TextView time = Ui.text(this, schedule.timeText(), 16,
                schedule.enabled ? Ui.NAVY : Ui.MUTED, true);
        time.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 78), ViewGroup.LayoutParams.WRAP_CONTENT);
        timeParams.setMarginEnd(Ui.dp(this, 12));
        row.addView(time, timeParams);

        TextView arrow = Ui.text(this, "›", 24, Ui.GOLD, false);
        row.addView(arrow);
        row.setOnClickListener(v -> showScheduleDialog(member, schedule));
        row.setContentDescription("Edit " + schedule.label + " at " + schedule.timeText());
        return row;
    }

    private void showMemberDialog() {
        if (members.size() >= 100) {
            Toast.makeText(this, "This family already has the maximum 100 people", Toast.LENGTH_LONG).show();
            return;
        }

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(Ui.dp(this, 20), Ui.dp(this, 10),
                Ui.dp(this, 20), Ui.dp(this, 18));
        sheet.setBackground(Ui.topShape(Ui.WHITE, 28, this));
        Ui.safeArea(sheet, true, false, true, true);

        sheet.addView(draggableSheetHandle(dialog, sheet), Ui.margins(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 28), this, 0, 0, 0, 4));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.text(this, "Add family member", 24, Ui.INK, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = Ui.text(this, "×", 26, Ui.MUTED, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close member editor");
        close.setBackground(Ui.shape(Ui.RAISED2, 21, this));
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(
                Ui.dp(this, 42), Ui.dp(this, 42)));
        sheet.addView(header);

        TextView helper = Ui.text(this,
                "Chitti will use this name during reminder calls.",
                14, Ui.MUTED, false);
        sheet.addView(helper, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, this, 0, 6, 0, 22));

        sheet.addView(label("Member name"));
        EditText input = new EditText(this);
        input.setHint("Example: Nanna or Lakshmi");
        input.setSingleLine(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(60)});
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setTextColor(Ui.INK);
        input.setHintTextColor(Ui.MUTED);
        input.setTextSize(17);
        input.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        input.setBackground(Ui.strokedShape(Ui.RAISED2, 14, Ui.LINE, 1, this));
        sheet.addView(input, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 58), this, 0, 7, 0, 16));

        sheet.addView(label("Mobile number"));
        EditText phone = new EditText(this);
        phone.setHint("10-digit Indian mobile number");
        phone.setSingleLine(true);
        phone.setFilters(new InputFilter[]{new InputFilter.LengthFilter(14)});
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        phone.setTextColor(Ui.INK);
        phone.setHintTextColor(Ui.MUTED);
        phone.setTextSize(17);
        phone.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        phone.setBackground(Ui.strokedShape(Ui.RAISED2, 14, Ui.LINE, 1, this));
        sheet.addView(phone, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 58), this, 0, 7, 0, 22));

        Button add = Ui.button(this, "Add member", Ui.PRIMARY, Ui.INK);
        add.setTextSize(16);
        add.setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                input.setError("Enter a name");
                input.requestFocus();
                return;
            }
            String phoneE164 = normalizeIndianPhone(phone.getText().toString());
            if (phoneE164.isEmpty()) {
                phone.setError("Enter a valid 10-digit Indian mobile number");
                phone.requestFocus();
                return;
            }
            if (phoneUsedByOther(phoneE164, null)) {
                phone.setError("This number is already assigned to another member");
                phone.requestFocus();
                return;
            }
            Models.Member member = new Models.Member();
            member.name = name;
            member.phoneE164 = phoneE164;
            member.relation = "generic"; // Kept only for compatibility with older Parent APKs.
            members.add(member);
            persistAndRender();
            dialog.dismiss();
        });
        sheet.addView(add, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 56)));

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
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showPhoneDialog(Models.Member member) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), Ui.dp(this, 14));
        sheet.setBackground(Ui.topShape(Ui.WHITE, 28, this));
        sheet.setClipChildren(false);
        sheet.setClipToPadding(false);
        Ui.safeArea(sheet, true, false, true, true);

        sheet.addView(draggableSheetHandle(dialog, sheet), Ui.margins(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 28), this, 0, 0, 0, 4));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleGroup = new LinearLayout(this);
        titleGroup.setOrientation(LinearLayout.VERTICAL);
        titleGroup.addView(Ui.text(this, member.name + "’s login number", 23, Ui.INK, true));
        titleGroup.addView(Ui.text(this, "Used for secure sign-in and reminder restore.",
                13, Ui.MUTED, false), Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, this, 0, 4, 0, 0));
        header.addView(titleGroup, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = Ui.text(this, "×", 26, Ui.MUTED, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close phone editor");
        close.setBackground(Ui.shape(Ui.RAISED, 21, this));
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 42), Ui.dp(this, 42));
        closeParams.setMarginStart(Ui.dp(this, 12));
        header.addView(close, closeParams);
        sheet.addView(header);

        sheet.addView(Ui.text(this, "Mobile number", 13, Ui.INK, true),
                Ui.margins(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, this, 0, 22, 0, 7));

        LinearLayout phoneField = new LinearLayout(this);
        phoneField.setOrientation(LinearLayout.HORIZONTAL);
        phoneField.setGravity(Gravity.CENTER_VERTICAL);
        phoneField.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 8), 0);
        phoneField.setBackground(Ui.strokedShape(Ui.RAISED, 16, Ui.LINE, 1, this));
        TextView prefix = Ui.text(this, "+91", 17, Ui.INK, true);
        prefix.setGravity(Gravity.CENTER_VERTICAL);
        phoneField.addView(prefix, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        EditText input = new EditText(this);
        input.setHint("10-digit mobile number");
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        input.setSingleLine(true);
        input.setTextColor(Ui.INK);
        input.setHintTextColor(Ui.MUTED);
        input.setTextSize(17);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10)});
        if (member.phoneE164 != null && !member.phoneE164.isEmpty()) {
            input.setText(localIndianPhone(member.phoneE164));
        }
        input.setSelectAllOnFocus(true);
        input.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
        phoneField.addView(input, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        sheet.addView(phoneField, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 58)));

        Button save = Ui.button(this, "Save number", Ui.PRIMARY, Ui.INK);
        save.setTextSize(16);
        save.setOnClickListener(v -> {
            String normalized = normalizeIndianPhone(input.getText().toString());
            if (normalized.isEmpty()) {
                input.setError("Enter a valid 10-digit Indian mobile number");
                input.requestFocus();
                return;
            }
            if (phoneUsedByOther(normalized, member.id)) {
                input.setError("This number is already assigned to another member");
                input.requestFocus();
                return;
            }
            member.phoneE164 = normalized;
            persistAndRender();
            dialog.dismiss();
        });
        sheet.addView(save, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 56), this, 0, 18, 0, 0));

        dialog.setContentView(sheet);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.setDimAmount(0.42f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            window.setNavigationBarColor(Ui.BG);
            applyLightSystemBars(window);
            window.setWindowAnimations(R.style.BottomSheetAnimation);
        }
        dialog.show();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private String localIndianPhone(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        return digits.length() == 12 && digits.startsWith("91") ? digits.substring(2) : digits;
    }

    private FrameLayout shadowHost(View child, int childHeightDp) {
        FrameLayout host = new FrameLayout(this);
        host.setClipChildren(false);
        host.setClipToPadding(false);
        int space = Ui.dp(this, 4);
        FrameLayout.LayoutParams childParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, childHeightDp));
        childParams.setMargins(space, space, space, space);
        host.addView(child, childParams);
        return host;
    }

    private String normalizeIndianPhone(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (digits.length() == 10 && digits.charAt(0) >= '6') return "+91" + digits;
        if (digits.length() == 12 && digits.startsWith("91") && digits.charAt(2) >= '6') {
            return "+" + digits;
        }
        return "";
    }

    private boolean phoneUsedByOther(String phone, String memberId) {
        for (Models.Member candidate : members) {
            if ((memberId == null || !memberId.equals(candidate.id))
                    && phone.equals(candidate.phoneE164)) return true;
        }
        return false;
    }

    private void showScheduleDialog(Models.Member member, Models.Schedule existing) {
        if (existing == null && member.schedules.size() >= 50) {
            Toast.makeText(this, "This person already has the maximum 50 reminders",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Models.Schedule draft = existing == null ? new Models.Schedule() : Models.Schedule.fromJson(safeJson(existing));
        if (existing == null) {
            draft.label = "";
            draft.language = "en";
            draft.questions.add(defaultQuestion(draft.category));
        }
        boolean[] automaticConversation = {existing == null};
        normalizeReminderTitlePlaceholders(draft);

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(Ui.dp(this, 18), Ui.dp(this, 8), Ui.dp(this, 18), Ui.dp(this, 12));
        sheet.setBackground(Ui.topShape(Ui.WHITE, 28, this));
        sheet.setClipChildren(false);
        sheet.setClipToPadding(false);
        Ui.safeArea(sheet, true, false, true, true);

        sheet.addView(draggableSheetHandle(dialog, sheet), Ui.margins(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 24), this, 0, 0, 0, 2));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClipChildren(false);
        header.setClipToPadding(false);
        String titleText = existing != null ? "Edit reminder" : "Add reminder";
        header.addView(Ui.text(this, titleText, 24, Ui.INK, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (existing != null) {
            Button delete = Ui.button(this, "Delete", Ui.RAISED2, Ui.DANGER);
            delete.setTextSize(12);
            delete.setElevation(Ui.dp(this, 3));
            delete.setOnClickListener(v -> {
                dialog.dismiss();
                confirmScheduleDelete(member, existing);
            });
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    Ui.dp(this, 82), Ui.dp(this, 42));
            deleteParams.topMargin = Ui.dp(this, 4);
            deleteParams.bottomMargin = Ui.dp(this, 4);
            header.addView(delete, deleteParams);
        }
        TextView close = Ui.text(this, "×", 26, Ui.MUTED, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close reminder editor");
        close.setBackground(Ui.shape(Ui.RAISED2, 21, this));
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

        form.addView(label("Category"));
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

        TextView reminderNameLabel = label("Medicine name");
        form.addView(reminderNameLabel, Ui.margins(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, this, 0, 8, 0, 0));
        EditText medicine = new EditText(this);
        medicine.setHint("Example: Metformin");
        medicine.setText("medicine".equals(draft.category)
                ? normalizeMedicineName(draft.label) : draft.label);
        medicine.setTextColor(Ui.INK);
        medicine.setHintTextColor(Ui.MUTED);
        medicine.setTextSize(17);
        medicine.setSingleLine(true);
        medicine.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        medicine.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        medicine.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        medicine.setBackground(Ui.strokedShape(Ui.RAISED2, 14, Ui.LINE, 1, this));
        form.addView(medicine, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 48), this, 0, 4, 0, 0));

        form.addView(label("Call time"), Ui.margins(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, this, 0, 8, 0, 4));
        final int[] selectedTime = {draft.hour, draft.minute};
        Button timeButton = Ui.button(this, timeText(selectedTime[0], selectedTime[1]),
                Ui.RAISED2, Ui.INK);
        timeButton.setTextSize(21);
        timeButton.setBackground(Ui.strokedShape(Ui.RAISED2, 14, Ui.LINE, 1, this));
        timeButton.setElevation(0);
        timeButton.setTranslationZ(0);
        timeButton.setStateListAnimator(null);
        timeButton.setOnClickListener(v -> new TimePickerDialog(this, (picker, hour, minute) -> {
            selectedTime[0] = hour;
            selectedTime[1] = minute;
            timeButton.setText(timeText(hour, minute));
        }, selectedTime[0], selectedTime[1], false).show());
        form.addView(timeButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));

        form.addView(label("Repeat on"), Ui.margins(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, this, 0, 8, 0, 4));
        String[] dayLabels = AppLanguage.shortDays(this);
        TextView[] dayChips = new TextView[7];
        LinearLayout dayRow = new LinearLayout(this);
        dayRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int index = 0; index < dayLabels.length; index++) {
            boolean selected = (draft.days & (1 << index)) != 0;
            TextView chip = dayChip(dayLabels[index], selected);
            chip.setTextSize(12);
            dayChips[index] = chip;
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    0, Ui.dp(this, 34), 1f);
            if (index < dayLabels.length - 1) chipParams.setMarginEnd(Ui.dp(this, 4));
            dayRow.addView(chip, chipParams);
        }
        form.addView(dayRow);

        form.addView(label("Confirmation call"), Ui.margins(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                this, 0, 8, 0, 4));
        int[] confirmationValues = {0, 10};
        int[] selectedConfirmation = {draft.confirmationMinutes > 0 ? 10 : 0};
        TextView[] confirmationChips = new TextView[confirmationValues.length];
        LinearLayout confirmationRow = new LinearLayout(this);
        confirmationRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int index = 0; index < confirmationValues.length; index++) {
            final int choice = index;
            TextView chip = dayChip(confirmationValues[index] == 0 ? "OFF" : "10 min",
                    selectedConfirmation[0] == confirmationValues[index]);
            chip.setOnClickListener(v -> {
                selectedConfirmation[0] = confirmationValues[choice];
                for (int i = 0; i < confirmationChips.length; i++) {
                    styleDayChip(confirmationChips[i], i == choice);
                }
            });
            confirmationChips[index] = chip;
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    0, Ui.dp(this, 34), 1f);
            if (index == 0) chipParams.setMarginEnd(Ui.dp(this, 7));
            confirmationRow.addView(chip, chipParams);
        }
        form.addView(confirmationRow);

        LinearLayout medicineOptions = new LinearLayout(this);
        medicineOptions.setOrientation(LinearLayout.VERTICAL);
        medicineOptions.addView(label("Meal Reminder"), Ui.margins(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                this, 0, 8, 0, 4));
        int[] leadValues = {0, 15, 30, 45, 60};
        int[] selectedLead = {draft.preMinutes};
        TextView[] leadChips = new TextView[leadValues.length];
        LinearLayout leadRow = new LinearLayout(this);
        leadRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int index = 0; index < leadValues.length; index++) {
            final int selectedIndex = index;
            String leadLabel = leadValues[index] == 0 ? "OFF" : leadValues[index] + " min";
            TextView chip = dayChip(leadLabel,
                    selectedLead[0] == leadValues[index]);
            chip.setTextSize(12);
            chip.setOnClickListener(v -> {
                selectedLead[0] = leadValues[selectedIndex];
                for (int i = 0; i < leadChips.length; i++) {
                    styleDayChip(leadChips[i], i == selectedIndex);
                }
            });
            leadChips[index] = chip;
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    0, Ui.dp(this, 34), 1f);
            if (index < leadValues.length - 1) chipParams.setMarginEnd(Ui.dp(this, 7));
            leadRow.addView(chip, chipParams);
        }
        medicineOptions.addView(leadRow);
        form.addView(medicineOptions);

        LinearLayout conversation = new LinearLayout(this);
        conversation.setOrientation(LinearLayout.VERTICAL);
        conversation.addView(label("Call conversation"), Ui.margins(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                this, 0, 8, 0, 4));
        Button conversationButton = Ui.button(this, "", Ui.MINT, Ui.INK);
        conversationButton.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        conversationButton.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        conversationButton.setBackground(
                Ui.strokedShape(Ui.MINT, 14, Ui.OK, 1, this));
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

        String saveLabel = existing != null ? "Save changes" : "Add reminder";
        Button save = Ui.button(this, saveLabel, Ui.GOLD, Ui.INK);
        save.setTextSize(16);
        save.setOnClickListener(v -> {
            int dayMask = 0;
            for (int i = 0; i < dayChips.length; i++) {
                if (Boolean.TRUE.equals(dayChips[i].getTag())) dayMask |= 1 << i;
            }
            boolean isMedicine = "medicine".equals(draft.category);
            String value = isMedicine ? normalizeMedicineName(medicine.getText().toString())
                    : medicine.getText().toString().trim();
            if (value.isEmpty()) {
                medicine.setError(isMedicine ? "Enter medicine name" : "Enter reminder title");
                medicine.requestFocus();
                return;
            }
            if (dayMask == 0) {
                Toast.makeText(this, "Choose at least one day", Toast.LENGTH_SHORT).show();
                return;
            }
            normalizeReminderTitlePlaceholders(draft);
            draft.label = value;
            draft.hour = selectedTime[0];
            draft.minute = selectedTime[1];
            draft.days = dayMask;
            draft.preMinutes = isMedicine ? selectedLead[0] : 0;
            draft.confirmationMinutes = selectedConfirmation[0];
            if ((existing == null || !isMedicine || !draft.questions.isEmpty())
                    && !validConversation(draft)) {
                Toast.makeText(this, "Add at least one question",
                        Toast.LENGTH_LONG).show();
                return;
            }
            draft.language = scriptLanguage(draft, "en");
            // The entered medicine name is exactly what Chitti speaks. Keep the
            // legacy key only so older Parent APKs can still read this schedule.
            draft.medicineKey = "generic";
            draft.enabled = true;
            if (existing == null) member.schedules.add(draft);
            else {
                int index = member.schedules.indexOf(existing);
                if (index >= 0) member.schedules.set(index, draft);
            }
            dialog.dismiss();
            persistAndRender();
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

    private int categoryIndex(String value) {
        for (int index = 0; index < CATEGORY_VALUES.length; index++) {
            if (CATEGORY_VALUES[index].equals(value)) return index;
        }
        // Unknown explicit categories are generic reminders. Never reopen one as
        // Medicine, because saving it would silently add tablet-specific behavior.
        return CATEGORY_VALUES.length - 1;
    }

    private String periodIcon(int hour, int minute) {
        int minutesAfterMidnight = hour * 60 + minute;
        if (minutesAfterMidnight >= 5 * 60 && minutesAfterMidnight < 12 * 60) return "🌅";
        if (minutesAfterMidnight >= 12 * 60 && minutesAfterMidnight < 16 * 60) return "☀️";
        if (minutesAfterMidnight >= 16 * 60 && minutesAfterMidnight <= 19 * 60) return "🌇";
        return "🌙";
    }

    private String localizedDaysText(int bits) {
        if (bits == 0b1111111) return AppLanguage.ui(this, "Every day");
        if (bits == 0) return AppLanguage.ui(this, "No days");
        String[] names = AppLanguage.shortDays(this);
        StringBuilder output = new StringBuilder();
        for (int index = 0; index < names.length; index++) {
            if ((bits & (1 << index)) == 0) continue;
            if (output.length() > 0) output.append(", ");
            output.append(names[index]);
        }
        return output.toString();
    }

    private String categoryName(String category) {
        return AppLanguage.category(this, category);
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
            icon.setBackground(Ui.shape(Ui.RAISED, 20, this));
            icon.setContentDescription("Supplement");
            return icon;
        }
        TextView icon = Ui.text(this, categoryIcon(category), 18, Ui.NAVY, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(Ui.shape(Ui.RAISED, 20, this));
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
        choice.addView(name, Ui.margins(ViewGroup.LayoutParams.WRAP_CONTENT,
                Ui.dp(this, 22), this, 0, 3, 0, 0));
        choice.setTag(new View[]{icon, name});
        styleCategoryChoice(choice, selected);
        return choice;
    }

    private void styleCategoryChoice(LinearLayout choice, boolean selected) {
        if (choice == null || !(choice.getTag() instanceof View[])) return;
        View[] parts = (View[]) choice.getTag();
        View icon = parts[0];
        TextView name = (TextView) parts[1];
        icon.setBackground(Ui.strokedShape(
                selected ? Ui.GOLD : Ui.RAISED2,
                23,
                selected ? Color.rgb(211, 169, 91) : Ui.LINE,
                selected ? 2 : 1,
                this));
        icon.setElevation(selected ? Ui.dp(this, 2) : 0);
        name.setTextColor(selected ? Ui.INK : Ui.MUTED);
        name.setTypeface(null, selected ? android.graphics.Typeface.BOLD
                : android.graphics.Typeface.NORMAL);
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

    private void normalizeReminderTitlePlaceholders(Models.Schedule schedule) {
        if (schedule == null || "medicine".equals(schedule.category)) return;
        String currentTitle = schedule.label == null ? "" : schedule.label.trim();
        for (Models.ScriptQuestion question : schedule.questions) {
            question.prompt = normalizeReminderTitlePlaceholder(question.prompt, currentTitle);
            for (Models.ScriptAnswer answer : question.answers) {
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

    private Models.ScriptQuestion defaultQuestion(String category) {
        Models.ScriptQuestion question = new Models.ScriptQuestion();
        Models.ScriptAnswer answer = new Models.ScriptAnswer();
        question.prompt = ConversationDefaults.prompt(category);
        answer.label = "Okay";
        answer.response = "";
        question.answers.add(answer);
        return question;
    }

    private String scriptLanguage(Models.Schedule schedule, String fallback) {
        StringBuilder script = new StringBuilder(schedule.label == null ? "" : schedule.label);
        for (Models.ScriptQuestion question : schedule.questions) {
            script.append(' ').append(question.prompt);
            for (Models.ScriptAnswer answer : question.answers) {
                script.append(' ').append(answer.label).append(' ').append(answer.response);
            }
        }
        return AppLanguage.detect(script.toString(), fallback);
    }

    private boolean validConversation(Models.Schedule schedule) {
        if (schedule.questions.isEmpty() || schedule.questions.size() > 10) return false;
        for (Models.ScriptQuestion question : schedule.questions) {
            if (question.prompt.trim().isEmpty() || question.answers.size() > 4) return false;
            for (Models.ScriptAnswer answer : question.answers) {
                if (answer.label.trim().isEmpty()) return false;
            }
        }
        return true;
    }

    private void updateConversationSummary(TextView view, Models.Schedule schedule) {
        int answers = 0;
        for (Models.ScriptQuestion question : schedule.questions) {
            answers += question.answers.size() + 1;
        }
        view.setText(schedule.questions.isEmpty()
                ? "Add call conversation  ›"
                : schedule.questions.size() + (schedule.questions.size() == 1
                        ? " question · " : " questions · ") + answers
                        + (answers == 0 ? " optional buttons  ›"
                        : answers == 1 ? " answer button  ›" : " answer buttons  ›"));
    }

    private void showQuestionsDialog(Models.Schedule schedule, Runnable changed) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(Ui.dp(this, 18), Ui.dp(this, 8), Ui.dp(this, 18), Ui.dp(this, 14));
        sheet.setBackground(Ui.topShape(Ui.WHITE, 28, this));
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
        close.setBackground(Ui.shape(Ui.RAISED2, 21, this));
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        sheet.addView(header);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        renderQuestionList(list, schedule, dialog, changed);
        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.addView(list);
        sheet.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button add = Ui.button(this, "+ Add question", Ui.PRIMARY, Ui.INK);
        add.setOnClickListener(v -> {
            if (schedule.questions.size() >= 10) {
                Toast.makeText(this, "Maximum 10 questions", Toast.LENGTH_SHORT).show();
                return;
            }
            showQuestionEditor(schedule, null, () -> {
                renderQuestionList(list, schedule, dialog, changed);
                changed.run();
            });
        });
        sheet.addView(add, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 50), this, 0, 8, 0, 0));

        dialog.setContentView(sheet);
        showSheet(dialog, 0.76f);
    }

    private void renderQuestionList(LinearLayout list, Models.Schedule schedule,
            Dialog parent, Runnable changed) {
        list.removeAllViews();
        for (int index = 0; index < schedule.questions.size(); index++) {
            Models.ScriptQuestion question = schedule.questions.get(index);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12),
                    Ui.dp(this, 14), Ui.dp(this, 12));
            card.setBackground(Ui.strokedShape(Ui.RAISED2, 16, Ui.LINE, 1, this));
            card.addView(Ui.text(this, "Question " + (index + 1), 12, Ui.MUTED, true));
            TextView prompt = Ui.text(this, question.prompt, 16, Ui.INK, true);
            prompt.setMaxLines(3);
            card.addView(prompt, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, this, 0, 5, 0, 0));
            if (!question.answers.isEmpty()) {
                HorizontalScrollView answerScroll = new HorizontalScrollView(this);
                answerScroll.setHorizontalScrollBarEnabled(false);
                answerScroll.setClipToPadding(false);
                LinearLayout answerRow = new LinearLayout(this);
                answerRow.setOrientation(LinearLayout.HORIZONTAL);
                answerRow.setGravity(Gravity.CENTER_VERTICAL);
                for (Models.ScriptAnswer answer : question.answers) {
                    TextView pill = Ui.text(this, answer.label, 12, Ui.OK, true);
                    pill.setGravity(Gravity.CENTER);
                    pill.setPadding(Ui.dp(this, 12), Ui.dp(this, 6),
                            Ui.dp(this, 12), Ui.dp(this, 6));
                    pill.setBackground(Ui.strokedShape(Ui.MINT, 18,
                            Ui.OK, 1, this));
                    answerRow.addView(pill, Ui.margins(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT, this, 0, 0, 8, 0));
                }
                TextView later = Ui.text(this,
                        AppLanguage.ui(schedule.language, "Remind me later"),
                        12, Ui.OK, true);
                later.setGravity(Gravity.CENTER);
                later.setPadding(Ui.dp(this, 12), Ui.dp(this, 6),
                        Ui.dp(this, 12), Ui.dp(this, 6));
                later.setBackground(Ui.strokedShape(Ui.MINT, 18, Ui.OK, 1, this));
                answerRow.addView(later, Ui.margins(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, this, 0, 0, 8, 0));
                answerScroll.addView(answerRow);
                card.addView(answerScroll, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, this, 0, 10, 0, 0));
            } else {
                TextView later = Ui.text(this,
                        AppLanguage.ui(schedule.language, "Remind me later"),
                        12, Ui.OK, true);
                later.setGravity(Gravity.CENTER);
                later.setPadding(Ui.dp(this, 12), Ui.dp(this, 6),
                        Ui.dp(this, 12), Ui.dp(this, 6));
                later.setBackground(Ui.strokedShape(Ui.MINT, 18, Ui.OK, 1, this));
                card.addView(later, Ui.margins(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, this, 0, 10, 0, 0));
            }
            card.setOnClickListener(v -> showQuestionEditor(schedule, question, () -> {
                renderQuestionList(list, schedule, parent, changed);
                changed.run();
            }));
            list.addView(card, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, this, 0, 0, 0, 8));
        }
        if (schedule.questions.isEmpty()) {
            TextView empty = Ui.text(this,
                    "Add first question, answer buttons, and Chitti responses.",
                    15, Ui.MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Ui.dp(this, 32), 0, Ui.dp(this, 32));
            list.addView(empty);
        }
    }

    private void showQuestionEditor(Models.Schedule schedule, Models.ScriptQuestion existing,
            Runnable saved) {
        Models.ScriptQuestion working = existing == null
                ? new Models.ScriptQuestion() : copyQuestion(existing);
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(Ui.dp(this, 18), Ui.dp(this, 8), Ui.dp(this, 18), Ui.dp(this, 14));
        sheet.setBackground(Ui.topShape(Ui.WHITE, 28, this));
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
            Button delete = Ui.button(this, "Delete", Ui.RAISED2, Ui.DANGER);
            delete.setOnClickListener(v -> {
                schedule.questions.remove(existing);
                dialog.dismiss();
                saved.run();
            });
            header.addView(delete, new LinearLayout.LayoutParams(Ui.dp(this, 82), Ui.dp(this, 42)));
        }
        TextView close = Ui.text(this, "×", 26, Ui.MUTED, false);
        close.setGravity(Gravity.CENTER);
        close.setBackground(Ui.shape(Ui.RAISED2, 21, this));
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 42), Ui.dp(this, 42));
        closeParams.setMarginStart(Ui.dp(this, 8));
        header.addView(close, closeParams);
        sheet.addView(header);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        form.addView(label("Question spoken by Chitti"));
        EditText prompt = scriptInput("Type question exactly as Chitti should speak", false, 300);
        prompt.setText(working.prompt);
        form.addView(prompt, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 96), this, 0, 5, 0, 4));

        TextView answerHelp = Ui.text(this,
                "Answer buttons (optional)\nLeave all buttons empty for an information-only call.",
                13, Ui.MUTED, false);
        answerHelp.setLineSpacing(0, 1.15f);
        form.addView(answerHelp, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, this, 0, 10, 0, 2));

        EditText[] answerLabels = new EditText[4];
        EditText[] answerResponses = new EditText[4];
        for (int index = 0; index < 4; index++) {
            form.addView(label("Answer " + (index + 1) + " button (optional)"),
                    Ui.margins(ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT, this, 0, 10, 0, 4));
            EditText answer = scriptInput(index == 0 ? "Example: Okay — or leave empty" : "Leave empty if unused",
                    true, 40);
            EditText response = scriptInput("Chitti response after this answer (optional)",
                    false, 300);
            if (index < working.answers.size()) {
                answer.setText(working.answers.get(index).label);
                response.setText(working.answers.get(index).response);
            }
            answerLabels[index] = answer;
            answerResponses[index] = response;
            form.addView(answer, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 50)));
            form.addView(response, Ui.margins(ViewGroup.LayoutParams.MATCH_PARENT,
                    Ui.dp(this, 72), this, 0, 5, 0, 0));
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setClipChildren(true);
        scroll.setClipToPadding(true);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(form);
        sheet.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button save = Ui.button(this, "Save question", Ui.PRIMARY, Ui.INK);
        save.setOnClickListener(v -> {
            String promptValue = prompt.getText().toString().trim();
            if (promptValue.isEmpty()) {
                prompt.setError("Enter question");
                prompt.requestFocus();
                return;
            }
            List<Models.ScriptAnswer> answers = new ArrayList<>();
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
                Models.ScriptAnswer answer = new Models.ScriptAnswer();
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
        input.setBackground(Ui.strokedShape(Ui.RAISED2, 14, Ui.LINE, 1, this));
        return input;
    }

    private Models.ScriptQuestion copyQuestion(Models.ScriptQuestion source) {
        Models.ScriptQuestion copy = new Models.ScriptQuestion();
        copy.prompt = source.prompt;
        for (Models.ScriptAnswer item : source.answers) {
            Models.ScriptAnswer answer = new Models.ScriptAnswer();
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

    private TextView dayChip(String value, boolean selected) {
        TextView chip = Ui.text(this, value, 14, Ui.MUTED, true);
        chip.setGravity(Gravity.CENTER);
        chip.setOnClickListener(v -> styleDayChip(chip,
                !Boolean.TRUE.equals(chip.getTag())));
        styleDayChip(chip, selected);
        return chip;
    }

    private String normalizeMedicineName(String value) {
        if (value == null) return "";
        return value.trim()
                .replaceFirst("(?i)(?:[\\s\\u200B-\\u200D\\uFEFF]*tablet[\\s\\u200B-\\u200D\\uFEFF]*)+$", "")
                .replaceFirst("(?:[\\s\\u200B-\\u200D\\uFEFF]*టాబ్లెట్[\\s\\u200B-\\u200D\\uFEFF]*)+$", "").trim();
    }

    private void styleDayChip(TextView chip, boolean selected) {
        chip.setTag(selected);
        chip.setTextColor(selected ? Ui.INK : Ui.MUTED);
        chip.setBackground(Ui.strokedShape(selected ? Ui.MINT : Ui.RAISED2,
                12, selected ? Ui.OK : Ui.LINE, 1, this));
        chip.setContentDescription(chip.getText() + (selected ? ", selected" : ", not selected"));
    }

    private LinearLayout settingRow(String text, Switch control) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 15), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        row.setBackground(Ui.strokedShape(Ui.RAISED2, 14, Ui.LINE, 1, this));
        row.addView(Ui.text(this, text, 15, Ui.INK, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(control);
        return row;
    }

    private String timeText(int hour, int minute) {
        int shownHour = hour % 12;
        if (shownHour == 0) shownHour = 12;
        return String.format(Locale.US, "%d:%02d %s", shownHour, minute, hour < 12 ? "AM" : "PM");
    }

    private TextView label(String value) {
        TextView label = Ui.text(this, value, 13, Ui.NAVY, true);
        label.setLetterSpacing(0.04f);
        return label;
    }

    private org.json.JSONObject safeJson(Models.Schedule schedule) {
        try { return schedule.toJson(); }
        catch (Exception ignored) { return new org.json.JSONObject(); }
    }

    private void confirmMemberDelete(Models.Member member) {
        new AlertDialog.Builder(this)
                .setTitle("Remove " + member.name + "?")
                .setMessage("This deletes the member, all reminder schedules, and their connected phone. Reminder calls will stop after the phone receives the update.")
                .setNegativeButton("Keep", null)
                .setPositiveButton("Remove member", (dialog, which) -> {
                    pendingMemberDeletes.add(member.id);
                    store.markMemberDeleted(member.id);
                    members.remove(member);
                    persistAndRender();
                    flushPendingMemberDeletes();
                })
                .show();
    }

    private void flushPendingMemberDeletes() {
        if (firebase == null || !firebaseReady) return;
        for (String memberId : new HashSet<>(pendingMemberDeletes)) {
            if (!memberDeleteRequests.add(memberId)) continue;
            firebase.deleteMember(memberId, success -> runOnUiThread(() -> {
                memberDeleteRequests.remove(memberId);
                if (success) {
                    pendingMemberDeletes.remove(memberId);
                    store.clearMemberDeleted(memberId);
                }
            }));
        }
    }

    private void confirmScheduleDelete(Models.Member member, Models.Schedule schedule) {
        new AlertDialog.Builder(this)
                .setTitle("Delete this time?")
                .setMessage(schedule.label + " at " + schedule.timeText())
                .setNegativeButton("Keep", null)
                .setPositiveButton("Delete", (dialog, which) -> { member.schedules.remove(schedule); persistAndRender(); })
                .show();
    }

    private View draggableSheetHandle(Dialog dialog, View sheet) {
        FrameLayout dragArea = new FrameLayout(this) {
            @Override public boolean performClick() {
                super.performClick();
                return true;
            }
        };
        dragArea.setClickable(true);
        dragArea.setContentDescription("Swipe down to close");
        View bar = new View(this);
        bar.setBackground(Ui.shape(Ui.LINE, 3, this));
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
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

    private void persistAndRender() {
        store.save(members);
        if (firebase != null) firebase.publishAll(members);
        render();
    }

    @Override protected void onDestroy() {
        if (firebase != null) firebase.stop();
        if (phoneLogin != null) phoneLogin.clear();
        super.onDestroy();
    }
}
