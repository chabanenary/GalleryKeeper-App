package com.example.gallerykeeper.Utils;

import android.content.Context;
import android.content.SharedPreferences;

public class UserPrefs {

    private static final String PREFS_NAME = "user_prefs";
    private static final String KEY_USER_EMAIL = "user_email";

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void setUserEmail(Context context, String email) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (email == null) {
            editor.remove(KEY_USER_EMAIL);
        } else {
            editor.putString(KEY_USER_EMAIL, email);
        }
        editor.apply();
    }

    public static String getUserEmail(Context context) {
        return prefs(context).getString(KEY_USER_EMAIL, null);
    }
}
