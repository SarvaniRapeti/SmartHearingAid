package com.hearing.hearingtest

import android.content.Context

object LiveAudioPrefs {

    private const val PREF_NAME = "live_audio_prefs"
    private const val KEY_AUTO_STOP = "auto_stop_enabled"

    fun isAutoStopEnabled(context: Context): Boolean {
        return context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_STOP, true) // DEFAULT = ENABLED
    }

    fun setAutoStopEnabled(context: Context, enabled: Boolean) {
        context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_STOP, enabled)
            .apply()
    }
}
