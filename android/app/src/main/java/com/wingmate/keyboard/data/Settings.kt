package com.wingmate.keyboard.data

import android.content.Context
import android.content.SharedPreferences
import com.wingmate.keyboard.BuildConfig

enum class Tone(val wire: String, val label: String) {
    ANY("any", "Mix"),
    PLAYFUL("playful", "😜 Playful"),
    FLIRTY("flirty", "😏 Flirty"),
    SINCERE("sincere", "🙂 Sincere"),
    CURIOUS("curious", "🤔 Curious");

    companion object {
        fun fromWire(value: String?): Tone = entries.firstOrNull { it.wire == value } ?: ANY
    }
}

/** Package names of chat apps the reader may look at. Users can add or remove entries. */
val DEFAULT_ALLOWED_APPS = linkedMapOf(
    "com.tinder" to "Tinder",
    "com.bumble.app" to "Bumble",
    "co.hinge.app" to "Hinge",
    "com.okcupid.okcupid" to "OkCupid",
    "com.whatsapp" to "WhatsApp",
    "com.instagram.android" to "Instagram",
    "com.facebook.orca" to "Messenger",
    "org.telegram.messenger" to "Telegram",
    "com.google.android.apps.messaging" to "Google Messages",
)

/** Small wrapper around SharedPreferences shared by the app, keyboard and reader (same process). */
class Settings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("wingmate", Context.MODE_PRIVATE)

    var consentGiven: Boolean
        get() = prefs.getBoolean(KEY_CONSENT, false)
        set(v) = prefs.edit().putBoolean(KEY_CONSENT, v).apply()

    var tone: Tone
        get() = Tone.fromWire(prefs.getString(KEY_TONE, null))
        set(v) = prefs.edit().putString(KEY_TONE, v.wire).apply()

    var aboutMe: String
        get() = prefs.getString(KEY_ABOUT, "") ?: ""
        set(v) = prefs.edit().putString(KEY_ABOUT, v.take(500)).apply()

    var backendUrl: String
        get() = prefs.getString(KEY_URL, null) ?: BuildConfig.DEFAULT_BACKEND_URL
        set(v) = prefs.edit().putString(KEY_URL, v.trim().trimEnd('/')).apply()

    var appToken: String
        get() = prefs.getString(KEY_TOKEN, null) ?: BuildConfig.DEFAULT_APP_TOKEN
        set(v) = prefs.edit().putString(KEY_TOKEN, v.trim()).apply()

    var allowedApps: Set<String>
        get() = prefs.getStringSet(KEY_APPS, null) ?: DEFAULT_ALLOWED_APPS.keys
        set(v) = prefs.edit().putStringSet(KEY_APPS, v).apply()

    private companion object {
        const val KEY_CONSENT = "consent"
        const val KEY_TONE = "tone"
        const val KEY_ABOUT = "about_me"
        const val KEY_URL = "backend_url"
        const val KEY_TOKEN = "app_token"
        const val KEY_APPS = "allowed_apps"
    }
}
