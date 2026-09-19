package com.gurthuchey.admin;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class AdminStore {
    private static final String PREFS = "maathra_admin";
    private static final String KEY_MEMBERS = "members";
    private static final String KEY_DELETED_MEMBERS = "deleted_members";
    private final SharedPreferences preferences;

    AdminStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    List<Models.Member> load() {
        List<Models.Member> members = new ArrayList<>();
        String raw = preferences.getString(KEY_MEMBERS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) members.add(Models.Member.fromJson(array.getJSONObject(i)));
        } catch (Exception ignored) {
            // A malformed local preference should not prevent the admin from opening.
        }
        return members;
    }

    @SuppressLint("ApplySharedPref") // Admin edits must survive an immediate process stop.
    void save(List<Models.Member> members) {
        JSONArray array = new JSONArray();
        try {
            for (Models.Member member : members) array.put(member.toJson());
            preferences.edit().putString(KEY_MEMBERS, array.toString()).commit();
        } catch (Exception ignored) {
            // Every field is controlled by this app, so serialization should always succeed.
        }
    }

    Set<String> loadDeletedMembers() {
        return new HashSet<>(preferences.getStringSet(KEY_DELETED_MEMBERS, java.util.Collections.emptySet()));
    }

    @SuppressLint("ApplySharedPref") // The tombstone must outlive an offline/process-stop window.
    void markMemberDeleted(String memberId) {
        Set<String> deleted = loadDeletedMembers();
        deleted.add(memberId);
        preferences.edit().putStringSet(KEY_DELETED_MEMBERS, deleted).commit();
    }

    @SuppressLint("ApplySharedPref") // Clear only after Firebase confirms the deletion.
    void clearMemberDeleted(String memberId) {
        Set<String> deleted = loadDeletedMembers();
        deleted.remove(memberId);
        preferences.edit().putStringSet(KEY_DELETED_MEMBERS, deleted).commit();
    }

}
