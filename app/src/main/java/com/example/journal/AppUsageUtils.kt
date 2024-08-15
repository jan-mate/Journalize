package com.example.journal

import android.content.Context

object AppUsageUtils {

    private const val PREFS_NAME = "JournalPrefs"
    private const val LAST_OPENED_TIME = "lastOpenedTime"
    private const val TIMER_DURATION = "timerDuration"

    fun onPause(context: Context) {
        val currentTime = System.currentTimeMillis()
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putLong(LAST_OPENED_TIME, currentTime)
        editor.apply()
    }
    // This doesn't seem to work on android 9 (most likely also lower versions)
    fun onResume(context: Context, createNewEntry: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastOpenedTime = sharedPreferences.getLong(LAST_OPENED_TIME, 0)
        val timerDuration = sharedPreferences.getInt(TIMER_DURATION, 300) * 1000 // default 300 seconds (5 minutes)

        if (currentTime - lastOpenedTime > timerDuration) {
            createNewEntry()
        }

        val editor = sharedPreferences.edit()
        editor.putLong(LAST_OPENED_TIME, currentTime)
        editor.apply()
    }

    fun saveTimerDuration(context: Context, seconds: Int) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putInt(TIMER_DURATION, seconds)
        editor.apply()
    }

    fun getTimerDuration(context: Context): Int {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getInt(TIMER_DURATION, 300) // default 300 seconds (5 minutes)
    }
}
