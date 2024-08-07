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

        // Load the JSON content from the file, if it exists
        val jsonFile = File(filesDir, "entries_log.json")
        if (jsonFile.exists()) {
            val jsonContent = jsonFile.readText()
            val sortedJsonContent = sortEntriesByModifiedDate(jsonContent)
            jsonEditText.setText(sortedJsonContent)
        } else {
            jsonEditText.setText("JSON file not found.")
        }

        // Apply changes to the JSON file
        applyJsonButton.setOnClickListener {
            saveJsonContentIfValid(jsonEditText)
        }
    }

    private fun sortEntriesByModifiedDate(jsonContent: String): String {
        // Sort entries by modified date and return as formatted JSON
        val gson = GsonBuilder().setPrettyPrinting().create()
        val listType = object : TypeToken<List<EntryEditorActivity.EntryData>>() {}.type
        val entries = gson.fromJson<List<EntryEditorActivity.EntryData>>(jsonContent, listType).toMutableList()
        entries.sortByDescending { it.modified }
        return gson.toJson(entries)
    }

    private fun saveJsonContentIfValid(jsonEditText: EditText) {
        val jsonContent = jsonEditText.text.toString()

        try {
            // Validate JSON
            JsonParser.parseString(jsonContent)
            // Save to internal storage
            val jsonFile = File(filesDir, "entries_log.json")
            FileWriter(jsonFile).use {
                it.write(jsonContent)
            }
            AlertDialog.Builder(this)
                .setTitle("Save Successful")
                .setMessage("JSON content is valid and has been saved.")
                .setPositiveButton("OK", null)
                .show()
        } catch (e: Exception) {
            // Show error message if JSON is invalid
            AlertDialog.Builder(this)
                .setTitle("Invalid JSON")
                .setMessage("The JSON content is not valid. Please fix it and try again.")
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
