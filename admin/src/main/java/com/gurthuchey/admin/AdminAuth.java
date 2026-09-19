package com.gurthuchey.admin;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

final class AdminAuth {
    interface Callback { void complete(boolean success, String message); }

    static final String ALLOWED_PHONE = "+919177216132";
    private static final String PREFS = "gurthu_chey_admin_auth";
    private static final String KEY_VERIFIED_UID = "verified_uid";

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final SharedPreferences preferences;

    AdminAuth(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    boolean hasAuthorizedUser() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null && isAllowedPhone(user.getPhoneNumber())
                && user.getUid().equals(preferences.getString(KEY_VERIFIED_UID, ""));
    }

    void clearAnonymousUser() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null && user.isAnonymous()) auth.signOut();
    }

    void verifyCurrent(Callback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || !isAllowedPhone(user.getPhoneNumber())) {
            preferences.edit().remove(KEY_VERIFIED_UID).apply();
            auth.signOut();
            callback.complete(false, "Only +91 91772 16132 can access Admin.");
            return;
        }
        user.getIdToken(true).addOnCompleteListener(token ->
                FirebaseFirestore.getInstance().collection("adminConfig").document("primary")
                        .get(Source.SERVER)
                        .addOnSuccessListener(document -> {
                            if (!document.exists()) {
                                auth.signOut();
                                callback.complete(false, "This mobile number is not an admin.");
                                return;
                            }
                            preferences.edit().putString(KEY_VERIFIED_UID, user.getUid()).apply();
                            callback.complete(true, "");
                        })
                        .addOnFailureListener(error -> {
                            preferences.edit().remove(KEY_VERIFIED_UID).apply();
                            auth.signOut();
                            callback.complete(false,
                                    "This number is not authorized for Admin, or the internet is unavailable.");
                        }));
    }

    void signOut() {
        preferences.edit().remove(KEY_VERIFIED_UID).apply();
        auth.signOut();
    }

    static boolean isAllowedPhone(String phone) {
        return ALLOWED_PHONE.equals(phone == null ? "" : phone.trim());
    }
}
