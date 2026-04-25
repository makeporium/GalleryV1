package com.example.chesh.auth;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.chesh.network.models.UserDto;

public class SessionStore {
    private static final String PREF_NAME      = "chesh_session";
    private static final String KEY_TOKEN      = "access_token";
    private static final String KEY_USER_ID    = "user_id";
    private static final String KEY_USER_NAME  = "user_name";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_AVATAR = "user_avatar_url";
    private static final String KEY_USER_BIO = "user_bio";
    private static final String KEY_USER_PRONOUNS = "user_pronouns";
    private static final String KEY_FIREBASE_UID = "firebase_uid";

    private final SharedPreferences prefs;

    public SessionStore(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // --- Token ---

    public void saveAccessToken(String token) {
        prefs.edit().putString(KEY_TOKEN, token).apply();
    }

    public String getAccessToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    // --- User ---

    public void saveUser(UserDto user) {
        if (user == null) return;
        prefs.edit()
                .putLong(KEY_USER_ID, user.id)
                .putString(KEY_USER_NAME, user.name)
                .putString(KEY_USER_EMAIL, user.email)
                .putString(KEY_USER_AVATAR, user.avatarUrl)
                .putString(KEY_USER_BIO, user.bio)
                .putString(KEY_USER_PRONOUNS, user.pronouns)
                .putString(KEY_FIREBASE_UID, user.firebaseUid)
                .apply();
    }

    public UserDto getUser() {
        long id = prefs.getLong(KEY_USER_ID, 0);
        if (id == 0) return null;
        UserDto u = new UserDto();
        u.id          = id;
        u.name        = prefs.getString(KEY_USER_NAME, null);
        u.email       = prefs.getString(KEY_USER_EMAIL, null);
        u.avatarUrl   = prefs.getString(KEY_USER_AVATAR, null);
        u.bio         = prefs.getString(KEY_USER_BIO, null);
        u.pronouns    = prefs.getString(KEY_USER_PRONOUNS, null);
        u.firebaseUid = prefs.getString(KEY_FIREBASE_UID, null);
        return u;
    }

    public long getUserId() {
        return prefs.getLong(KEY_USER_ID, 0);
    }

    // --- Clear ---

    public void clear() {
        prefs.edit().clear().apply();
    }
}
