package com.example.myapplication.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.myapplication.models.UserData
import com.google.gson.Gson

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_USER_DATA = "user_data"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_APP_NOTIFICATIONS = "app_notifications_enabled"
        private const val KEY_EMAIL_NOTIFICATIONS = "email_notifications_enabled"
    }

    fun setAppNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_NOTIFICATIONS, enabled).apply()
    }

    fun isAppNotificationsEnabled(): Boolean {
        return prefs.getBoolean(KEY_APP_NOTIFICATIONS, true)
    }

    fun setEmailNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EMAIL_NOTIFICATIONS, enabled).apply()
    }

    fun isEmailNotificationsEnabled(): Boolean {
        return prefs.getBoolean(KEY_EMAIL_NOTIFICATIONS, true)
    }

    fun saveUser(user: UserData) {
        val editor = prefs.edit()
        editor.putString(KEY_USER_DATA, gson.toJson(user))
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.apply()
    }

    fun getUser(): UserData? {
        val userData = prefs.getString(KEY_USER_DATA, null)
        return if (userData != null) {
            gson.fromJson(userData, UserData::class.java)
        } else {
            null
        }
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun logout() {
        val editor = prefs.edit()
        editor.clear()
        editor.apply()
    }
}