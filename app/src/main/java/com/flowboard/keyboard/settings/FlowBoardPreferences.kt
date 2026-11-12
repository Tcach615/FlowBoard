package com.flowboard.keyboard.settings

import android.content.Context
import android.content.SharedPreferences

/** All settings live in a private SharedPreferences file; nothing leaves the device. */
class FlowBoardPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    /** How long a half-finished chord survives before it is rejected. */
    var syncWindowMs: Int
        get() = prefs.getInt(KEY_SYNC_WINDOW, DEFAULT_SYNC_WINDOW)
        set(value) = prefs.edit().putInt(KEY_SYNC_WINDOW, value.coerceIn(MIN_SYNC_WINDOW, MAX_SYNC_WINDOW)).apply()

    /** Fraction of the pad radius that counts as "no direction yet". */
    var deadZone: Float
        get() = prefs.getFloat(KEY_DEAD_ZONE, DEFAULT_DEAD_ZONE)
        set(value) = prefs.edit().putFloat(KEY_DEAD_ZONE, value.coerceIn(0.10f, 0.50f)).apply()

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    var showCandidateLabels: Boolean
        get() = prefs.getBoolean(KEY_LABELS, true)
        set(value) = prefs.edit().putBoolean(KEY_LABELS, value).apply()

    var personalizationEnabled: Boolean
        get() = prefs.getBoolean(KEY_PERSONALIZATION, true)
        set(value) = prefs.edit().putBoolean(KEY_PERSONALIZATION, value).apply()

    var theme: String
        get() = prefs.getString(KEY_THEME, "dark") ?: "dark"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    var onboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    var chordsTyped: Int
        get() = prefs.getInt(KEY_CHORDS, 0)
        private set(value) = prefs.edit().putInt(KEY_CHORDS, value).apply()

    var corrections: Int
        get() = prefs.getInt(KEY_CORRECTIONS, 0)
        private set(value) = prefs.edit().putInt(KEY_CORRECTIONS, value).apply()

    fun recordChord() {
        chordsTyped += 1
    }

    fun recordCorrection() {
        corrections += 1
    }

    fun resetStatistics() {
        prefs.edit().putInt(KEY_CHORDS, 0).putInt(KEY_CORRECTIONS, 0).apply()
    }

    companion object {
        private const val FILE = "flowboard_prefs"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_SYNC_WINDOW = "sync_window_ms"
        private const val KEY_DEAD_ZONE = "dead_zone"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_LABELS = "candidate_labels"
        private const val KEY_PERSONALIZATION = "personalization"
        private const val KEY_THEME = "theme"
        private const val KEY_ONBOARDING = "onboarding_complete"
        private const val KEY_CHORDS = "stat_chords"
        private const val KEY_CORRECTIONS = "stat_corrections"

        const val DEFAULT_SYNC_WINDOW = 240
        const val MIN_SYNC_WINDOW = 80
        const val MAX_SYNC_WINDOW = 600
        const val DEFAULT_DEAD_ZONE = 0.26f
    }
}
