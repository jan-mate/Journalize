package com.example.journal

import android.content.Context

object AppUsageUtils {

    private const val PREFS_NAME = "JournalPrefs"
    private const val LAST_OPENED_TIME = "lastOpenedTime"

    fun onPause(context: Context) {
        val currentTime = System.currentTimeMillis()
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putLong(LAST_OPENED_TIME, currentTime)
        editor.apply()
    }

    fun onResume(context: Context, createNewEntry: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastOpenedTime = sharedPreferences.getLong(LAST_OPENED_TIME, 0)

        if (currentTime - lastOpenedTime > 5 * 60 * 1000) { // 5 minutes
            createNewEntry()
        }

        val editor = sharedPreferences.edit()
        editor.putLong(LAST_OPENED_TIME, currentTime)
        editor.apply()
    }
}
