package com.example.journal

import android.content.Context

object AppUsageUtils {

    private const val PREFS_NAME = "JournalPrefs"
    private const val LAST_OPENED_TIME = "lastOpenedTime"
    private const val TIMER_DURATION = "timerDuration"
    private const val NEVER_OPEN_ON_REOPEN = "neverOpenOnReopen"

    fun onPause(context: Context) {
        val currentTime = System.currentTimeMillis()
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putLong(LAST_OPENED_TIME, currentTime)
        editor.apply()
    }
    // This doesn't seem to work on android 9 (most likely also lower versions)
    fun onResume(context: Context, createNewEntry: () -> Unit) {
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val last = prefs.getLong(LAST_OPENED_TIME, 0)

        val neverOpen = prefs.getBoolean(NEVER_OPEN_ON_REOPEN, false)
        if (!neverOpen) {
            val timerDurationMs = prefs.getInt(TIMER_DURATION, 300) * 1000 // default 5 minutes
            if (timerDurationMs > 0 && now - last > timerDurationMs) {
                createNewEntry()
            }
        }

        prefs.edit()
            .putLong(LAST_OPENED_TIME, now)
            .apply()
    }

    fun saveTimerDuration(context: Context, seconds: Int) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putInt(TIMER_DURATION, seconds)
        editor.apply()
    }

    fun getTimerDuration(context: Context): Int {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getInt(TIMER_DURATION, 600) // default 600 seconds (10 minutes)
    }

    // NEW: switch state
    fun setNeverOpenOnReopen(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(NEVER_OPEN_ON_REOPEN, enabled)
            .apply()
    }

    fun isNeverOpenOnReopen(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(NEVER_OPEN_ON_REOPEN, false)
    }
}
