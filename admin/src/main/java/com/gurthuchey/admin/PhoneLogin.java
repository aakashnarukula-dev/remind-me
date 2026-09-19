package com.gurthuchey.admin;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.truecaller.android.sdk.oAuth.CodeVerifierUtil;
import com.truecaller.android.sdk.oAuth.TcOAuthCallback;
import com.truecaller.android.sdk.oAuth.TcOAuthData;
import com.truecaller.android.sdk.oAuth.TcOAuthError;
import com.truecaller.android.sdk.oAuth.TcSdk;
import com.truecaller.android.sdk.oAuth.TcSdkOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

final class PhoneLogin implements TcOAuthCallback {
    private static final String TAG = "GurthuTruecaller";
    interface Listener {
        void onCodeSent(String phone);
        void onComplete(String phone);
        void onError(String message);
    }

    private final FragmentActivity activity;
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final String role;
    private final ActivityResultLauncher<Intent> truecallerLauncher;
    private Listener listener;
    private String verificationId = "";
    private String pendingPhone = "";
    private String oauthState = "";
    private String codeVerifier = "";
    private ListenerRegistration truecallerResultListener;

    PhoneLogin(FragmentActivity activity, String role) {
        this.activity = activity;
        this.role = role;
        truecallerLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    try {
                        TcSdk.getInstance().onActivityResultObtained(
                                activity, result.getResultCode(), result.getData());
                    } catch (RuntimeException error) {
                        fail("Truecaller could not finish. Use SMS OTP instead.");
                    }
                });
        try {
            TcSdkOptions options = new TcSdkOptions.Builder(activity, this)
                    .sdkOptions(TcSdkOptions.OPTION_VERIFY_ALL_USERS)
                    .footerType(TcSdkOptions.FOOTER_TYPE_ANOTHER_METHOD)
                    .consentHeadingOption(TcSdkOptions.SDK_CONSENT_HEADING_LOG_IN_TO)
                    .ctaText(TcSdkOptions.CTA_TEXT_CONTINUE)
                    .buttonShapeOptions(TcSdkOptions.BUTTON_SHAPE_ROUNDED)
                    .buttonColor(Ui.GOLD)
                    .buttonTextColor(Ui.INK)
                    .consentMode(TcSdkOptions.CONSENT_MODE_BOTTOMSHEET)
                    .build();
            TcSdk.init(options);
        } catch (RuntimeException error) {
            Log.e(TAG, "Truecaller SDK initialization failed", error);
        }
    }

    boolean isSignedInWithPhone() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null && user.getPhoneNumber() != null && !user.getPhoneNumber().isEmpty();
    }

    String currentPhone() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null || user.getPhoneNumber() == null ? "" : user.getPhoneNumber();
    }

    boolean isTruecallerUsable() {
        try {
            boolean usable = TcSdk.getInstance().isOAuthFlowUsable();
            Log.d(TAG, "Truecaller OAuth usable=" + usable);
            return usable;
        } catch (RuntimeException error) {
            Log.e(TAG, "Truecaller usability check failed", error);
            return false;
        }
    }

    boolean isTruecallerInstalled() {
        try {
            activity.getPackageManager().getPackageInfo("com.truecaller", 0);
            Log.d(TAG, "Truecaller package installed");
            return true;
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            Log.d(TAG, "Truecaller package missing");
            return false;
        }
    }

    void sendOtp(String rawPhone, Listener listener) {
        String phone = normalizeIndianPhone(rawPhone);
        if (phone.isEmpty()) {
            listener.onError("Enter a valid 10-digit Indian mobile number.");
            return;
        }
        if ("admin".equals(role) && !AdminAuth.isAllowedPhone(phone)) {
            listener.onError("Only +91 91772 16132 can access Admin.");
            return;
        }
        this.listener = listener;
        this.pendingPhone = phone;
        PhoneAuthProvider.OnVerificationStateChangedCallbacks callbacks =
                new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                        finishCredential(credential);
                    }

                    @Override public void onVerificationFailed(@NonNull FirebaseException error) {
                        String message = error.getLocalizedMessage();
                        fail(message == null || message.trim().isEmpty()
                                ? "Could not send OTP. Check the number and connection."
                                : message);
                    }

                    @Override public void onCodeSent(@NonNull String id,
                            @NonNull PhoneAuthProvider.ForceResendingToken token) {
                        verificationId = id;
                        if (PhoneLogin.this.listener != null) {
                            PhoneLogin.this.listener.onCodeSent(pendingPhone);
                        }
                    }
                };
        PhoneAuthProvider.verifyPhoneNumber(PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build());
    }

    void verifyOtp(String rawCode, Listener listener) {
        String code = rawCode == null ? "" : rawCode.replaceAll("[^0-9]", "");
        if (verificationId.isEmpty() || code.length() != 6) {
            listener.onError("Enter the 6-digit OTP.");
            return;
        }
        this.listener = listener;
        try {
            finishCredential(PhoneAuthProvider.getCredential(verificationId, code));
        } catch (RuntimeException error) {
            listener.onError("That OTP is not valid. Try again.");
        }
    }

    void startTruecaller(Listener listener) {
        this.listener = listener;
        try {
            TcSdk sdk = TcSdk.getInstance();
            if (!sdk.isOAuthFlowUsable()) {
                fail("Truecaller login is unavailable on this phone. Use SMS OTP.");
                return;
            }
            oauthState = UUID.randomUUID().toString().replace("-", "");
            codeVerifier = CodeVerifierUtil.Companion.generateRandomCodeVerifier();
            sdk.setOAuthState(oauthState);
            sdk.setOAuthScopes(new String[]{"openid", "phone"});
            sdk.setCodeChallenge(CodeVerifierUtil.Companion.getCodeChallenge(codeVerifier));
            sdk.getAuthorizationCode(activity, truecallerLauncher);
        } catch (RuntimeException error) {
            fail("Truecaller login is unavailable. Use SMS OTP.");
        }
    }

    @Override public void onSuccess(@NonNull TcOAuthData data) {
        if (!oauthState.equals(data.getState()) || data.getAuthorizationCode().isEmpty()) {
            fail("Truecaller security check failed. Please try again.");
            return;
        }
        exchangeTruecaller(data.getAuthorizationCode());
    }

    @Override public void onFailure(@NonNull TcOAuthError error) {
        Log.w(TAG, "OAuth failed " + error.getErrorCode() + ": " + error.getErrorMessage());
        String detail = error.getErrorMessage();
        fail(detail == null || detail.trim().isEmpty()
                ? "Truecaller login failed (" + error.getErrorCode() + "). Use SMS OTP."
                : "Truecaller: " + detail.trim() + " (" + error.getErrorCode() + ")");
    }

    @Override public void onVerificationRequired(@NonNull TcOAuthError error) {
        Log.w(TAG, "OAuth verification required " + error.getErrorCode()
                + ": " + error.getErrorMessage());
        fail("Use SMS OTP to verify this mobile number.");
    }

    void clear() {
        if (truecallerResultListener != null) truecallerResultListener.remove();
        truecallerResultListener = null;
        try { TcSdk.clear(); } catch (RuntimeException ignored) {}
    }

    private void finishCredential(AuthCredential credential) {
        FirebaseUser current = auth.getCurrentUser();
        if (current != null && (current.getPhoneNumber() == null || current.getPhoneNumber().isEmpty())) {
            current.linkWithCredential(credential)
                    .addOnSuccessListener(result -> complete(result.getUser()))
                    .addOnFailureListener(error -> {
                        if (error instanceof FirebaseAuthInvalidCredentialsException) {
                            fail("That OTP is incorrect or expired.");
                        } else if (error instanceof FirebaseAuthUserCollisionException) {
                            auth.signInWithCredential(credential)
                                    .addOnSuccessListener(result -> complete(result.getUser()))
                                    .addOnFailureListener(signInError -> fail(
                                            "Could not verify this number. Please request a new OTP."));
                        } else {
                            fail("Could not link this number. Check the connection and try again.");
                        }
                    });
        } else {
            auth.signInWithCredential(credential)
                    .addOnSuccessListener(result -> complete(result.getUser()))
                    .addOnFailureListener(error -> fail("That OTP is incorrect or expired."));
        }
    }

    private void exchangeTruecaller(String code) {
        FirebaseUser current = auth.getCurrentUser();
        if (current != null) {
            submitTruecallerRequest(current, code);
            return;
        }
        auth.signInAnonymously()
                .addOnSuccessListener(result -> submitTruecallerRequest(result.getUser(), code))
                .addOnFailureListener(error -> fail(
                        "Could not start secure Truecaller login. Use SMS OTP instead."));
    }

    private void submitTruecallerRequest(FirebaseUser current, String code) {
        if (current == null) {
            fail("Could not start secure Truecaller login. Use SMS OTP instead.");
            return;
        }
        String uid = current.getUid();
        String state = oauthState;
        DocumentReference request = firestore.collection("truecallerRequests").document(uid);
        DocumentReference result = firestore.collection("truecallerResults").document(uid);
        if (truecallerResultListener != null) truecallerResultListener.remove();
        truecallerResultListener = result.addSnapshotListener((snapshot, error) -> {
            if (error != null || snapshot == null || !snapshot.exists()
                    || !state.equals(snapshot.getString("state"))) return;
            String token = snapshot.getString("firebaseToken");
            String phone = snapshot.getString("phone");
            if (token == null || token.isEmpty()) {
                if (truecallerResultListener != null) truecallerResultListener.remove();
                truecallerResultListener = null;
                result.delete();
                fail("Truecaller verification failed. Use SMS OTP instead.");
                return;
            }
            pendingPhone = phone == null ? "" : phone;
            if (truecallerResultListener != null) truecallerResultListener.remove();
            truecallerResultListener = null;
            result.delete().addOnCompleteListener(unused -> auth.signInWithCustomToken(token)
                    .addOnSuccessListener(authResult -> complete(authResult.getUser()))
                    .addOnFailureListener(signInError -> fail("Could not finish Truecaller login.")));
        });

        Map<String, Object> value = new HashMap<>();
        value.put("role", role);
        value.put("code", code);
        value.put("codeVerifier", codeVerifier);
        value.put("state", state);
        value.put("createdAt", FieldValue.serverTimestamp());
        request.delete().addOnCompleteListener(unused -> request.set(value)
                .addOnFailureListener(error -> {
                    if (truecallerResultListener != null) truecallerResultListener.remove();
                    truecallerResultListener = null;
                    fail("Could not verify with Truecaller. Use SMS OTP instead.");
                }));
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (state.equals(oauthState) && truecallerResultListener != null) {
                truecallerResultListener.remove();
                truecallerResultListener = null;
                fail("Truecaller took too long. Please try again or use SMS OTP.");
            }
        }, 35000L);
    }

    private void complete(FirebaseUser user) {
        if (user == null) {
            fail("Phone sign-in did not complete.");
            return;
        }
        String phone = user.getPhoneNumber();
        if (phone == null || phone.isEmpty()) phone = pendingPhone;
        Listener callback = listener;
        if (callback != null) callback.onComplete(phone == null ? "" : phone);
    }

    private void fail(String message) {
        Listener callback = listener;
        if (callback != null) callback.onError(message);
    }

    static String normalizeIndianPhone(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (digits.length() == 10 && digits.charAt(0) >= '6') return "+91" + digits;
        if (digits.length() == 12 && digits.startsWith("91") && digits.charAt(2) >= '6') {
            return "+" + digits;
        }
        return "";
    }
}
