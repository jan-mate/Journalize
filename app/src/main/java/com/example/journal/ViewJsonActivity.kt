package com.example.journal

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileWriter

class ViewJsonActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_json)

        val jsonTextView: TextView = findViewById(R.id.jsonTextView)
        val clearJsonButton: Button = findViewById(R.id.clearJsonButton)

        val jsonFile = File(filesDir, "entries_log.json")
        if (jsonFile.exists()) {
            val jsonContent = jsonFile.readText()
            jsonTextView.text = jsonContent
        } else {
            jsonTextView.text = "JSON file not found."
        }

        clearJsonButton.setOnClickListener {
            clearJsonFile()
            jsonTextView.text = "JSON file cleared."
        }
    }

    private fun clearJsonFile() {
        val jsonFile = File(filesDir, "entries_log.json")
        try {
            FileWriter(jsonFile).use {
                it.write("[]")  // Write an empty JSON array
            }
            // Clear the entries list in EntryEditorActivity
            EntryEditorActivity.entries.clear()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
