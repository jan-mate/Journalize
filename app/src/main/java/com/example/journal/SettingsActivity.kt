package com.example.journal

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var timerTextView: TextView
    private lateinit var timerEditText: EditText
    private lateinit var tagsEditText: EditText
    private lateinit var viewJsonButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        timerTextView = findViewById(R.id.timerTextView)
        timerEditText = findViewById(R.id.timerEditText)
        tagsEditText = findViewById(R.id.tagsEditText)
        viewJsonButton = findViewById(R.id.viewJsonButton)

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
    }

    private fun saveTags() {
        val newTags = tagsEditText.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        TagUtils.saveTags(this, newTags)
    }
}
