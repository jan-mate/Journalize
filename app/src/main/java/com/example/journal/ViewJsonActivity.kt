package com.example.journal

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Locale

class ViewJsonActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_json)

        val jsonEditText: EditText = findViewById(R.id.jsonEditText)
        val applyJsonButton: Button = findViewById(R.id.applyJsonButton)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            jsonEditText.textCursorDrawable =
                ContextCompat.getDrawable(this, R.drawable.cursor_drawable)
        }

        val jsonFile = File(filesDir, "entries.json")
        if (jsonFile.exists()) {
            val jsonContent = jsonFile.readText()
            val sortedJsonContent = sortEntriesByModifiedDate(jsonContent)
            jsonEditText.setText(sortedJsonContent)
        } else {
            jsonEditText.setText("JSON file not found.")
        }

        applyJsonButton.setOnClickListener {
            saveJsonContentIfValid(jsonEditText)
        }
    }

    private fun sortEntriesByModifiedDate(jsonContent: String): String {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val listType = object : TypeToken<List<EntryEditorActivity.EntryData>>() {}.type
        val entries = gson.fromJson<List<EntryEditorActivity.EntryData>>(jsonContent, listType).toMutableList()
        entries.sortByDescending { it.modified }
        return gson.toJson(entries)
    }


    private fun saveJsonContentIfValid(jsonEditText: EditText) {
        val jsonContent = jsonEditText.text.toString()
        val timestampPattern = "yyyy-MM-dd'T'HH:mm:ss.SSS"
        val dateFormat = SimpleDateFormat(timestampPattern, Locale.getDefault())
        dateFormat.isLenient = false

        try {
            // Parse the JSON content to check its validity
            val gson = GsonBuilder().setPrettyPrinting().create()
            val listType = object : TypeToken<List<EntryEditorActivity.EntryData>>() {}.type
            val entries = gson.fromJson<List<EntryEditorActivity.EntryData>>(jsonContent, listType)

            // Check for duplicate 'created' timestamps
            val createdTimestamps = entries.mapNotNull { it.created }
            if (createdTimestamps.size != createdTimestamps.toSet().size) {
                throw IllegalArgumentException("Duplicate 'created' timestamps found.")
            }

            // Check the format of 'created' and 'modified' timestamps
            for (entry in entries) {
                if (!isValidTimestamp(entry.created, dateFormat)) {
                    throw IllegalArgumentException("Invalid 'created' timestamp format found. Expected format: $timestampPattern")
                }
                if (!isValidTimestamp(entry.modified, dateFormat)) {
                    throw IllegalArgumentException("Invalid 'modified' timestamp format found. Expected format: $timestampPattern")
                }
            }

            // If no issues, save the JSON content
            val jsonFile = File(filesDir, "entries.json")
            FileWriter(jsonFile).use {
                it.write(jsonContent)
            }

            AlertDialog.Builder(this)
                .setTitle("Save Successful")
                .setMessage("JSON content is valid and has been saved.")
                .setPositiveButton("OK", null)
                .show()
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Invalid JSON")
                .setMessage("The JSON content is not valid. ${e.message} Please fix it and try again.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun isValidTimestamp(timestamp: String?, dateFormat: SimpleDateFormat): Boolean {
        return try {
            timestamp?.let {
                dateFormat.parse(it)
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }



}
