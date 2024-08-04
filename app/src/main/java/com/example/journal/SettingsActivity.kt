package com.example.journal

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import android.preference.PreferenceManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var timerTextView: TextView
    private lateinit var timerEditText: EditText
    private lateinit var tagsEditText: EditText
    private lateinit var viewJsonButton: Button
    private lateinit var darkModeButton: Button
    private lateinit var lightModeButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply the saved theme when the activity starts
        applyCurrentTheme()

        setContentView(R.layout.activity_settings)

        timerTextView = findViewById(R.id.timerTextView)
        timerEditText = findViewById(R.id.timerEditText)
        tagsEditText = findViewById(R.id.tagsEditText)
        viewJsonButton = findViewById(R.id.viewJsonButton)
        darkModeButton = findViewById(R.id.darkModeButton)
        lightModeButton = findViewById(R.id.lightModeButton)

        // Load initial timer value
        val initialTimerValue = AppUsageUtils.getTimerDuration(this)
        timerEditText.setText(initialTimerValue.toString())

        // Save timer setting on "Done" key press
        timerEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val newTimerValue = timerEditText.text.toString().toIntOrNull()
                if (newTimerValue != null) {
                    AppUsageUtils.saveTimerDuration(this, newTimerValue)
                }
                true
            } else {
                false
            }
        }

        // Load and display current tags
        val tags = TagUtils.loadTags(this)
        tagsEditText.setText(tags.joinToString(", "))

        // Save tags when the "Enter" or "Done" key is pressed
        tagsEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveTags()
                true
            } else {
                false
            }
        }

        viewJsonButton.setOnClickListener {
            val intent = Intent(this, ViewJsonActivity::class.java)
            startActivity(intent)
        }

        // Set click listeners for theme buttons
        darkModeButton.setOnClickListener {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            saveThemePreference(AppCompatDelegate.MODE_NIGHT_YES)
            recreate() // Recreate activity to apply theme change
        }

        lightModeButton.setOnClickListener {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            saveThemePreference(AppCompatDelegate.MODE_NIGHT_NO)
            recreate() // Recreate activity to apply theme change
        }
    }

    private fun applyCurrentTheme() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val nightMode = preferences.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_NO)
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    private fun saveThemePreference(nightMode: Int) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        preferences.edit().putInt("theme_mode", nightMode).apply()
    }

    private fun saveTags() {
        val newTags = tagsEditText.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        TagUtils.saveTags(this, newTags)
    }
}
