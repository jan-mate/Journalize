package com.example.journal

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class ViewJsonActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_json)

        val jsonTextView: TextView = findViewById(R.id.jsonTextView)

        val jsonFile = File(filesDir, "files_log.json")
        if (jsonFile.exists()) {
            val jsonContent = jsonFile.readText()
            jsonTextView.text = jsonContent
        } else {
            jsonTextView.text = "JSON file not found."
        }
    }
}
