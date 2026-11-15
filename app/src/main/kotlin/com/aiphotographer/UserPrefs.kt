package com.aiphotographer

import android.content.Context
import com.aiphotographer.intelligence.ShootingInterest

/** The two things ShotMuse remembers about you: that you've seen the intro, and what you shoot. */
class UserPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("shotmuse", Context.MODE_PRIVATE)

    var onboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()

    /** In the order picked, so the first pick's tip is the one shown. */
    var interests: List<ShootingInterest>
        get() = prefs.getString(KEY_INTERESTS, "").orEmpty()
            .split(',').filter { it.isNotBlank() }
            .let { names -> names.mapNotNull { n -> ShootingInterest.entries.firstOrNull { it.name == n } } }
        set(value) = prefs.edit().putString(KEY_INTERESTS, value.joinToString(",") { it.name }).apply()

    private companion object {
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_INTERESTS = "interests"
    }
}
