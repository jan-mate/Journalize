package com.example.journal

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.InputStreamReader

class ViewJsonActivity : AppCompatActivity() {

    private val PICK_JSON_FILE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_json)

        val jsonEditText: EditText = findViewById(R.id.jsonEditText)
        val deleteJsonButton: Button = findViewById(R.id.deleteJsonButton)
        val shareJsonButton: Button = findViewById(R.id.shareJsonButton)
        val importJsonButton: Button = findViewById(R.id.importJsonButton)
        val saveJsonButton: Button = findViewById(R.id.saveJsonButton)
        val applyJsonButton: Button = findViewById(R.id.applyJsonButton)

        val jsonFile = File(filesDir, "entries_log.json")
        if (jsonFile.exists()) {
            val jsonContent = jsonFile.readText()
            jsonEditText.setText(jsonContent)
        } else {
            jsonEditText.setText("JSON file not found.")
        }

        deleteJsonButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        shareJsonButton.setOnClickListener {
            shareEntriesJson()
        }

        importJsonButton.setOnClickListener {
            pickJsonFile()
        }

        saveJsonButton.setOnClickListener {
            downloadJsonContent()
        }

        applyJsonButton.setOnClickListener {
            saveJsonContentIfValid()
        }
    }

    private fun showDeleteConfirmationDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Confirm Delete")
        builder.setMessage("Are you sure you want to delete the JSON file?")
        builder.setPositiveButton("Yes") { dialog, which ->
            clearJsonFile()
            findViewById<EditText>(R.id.jsonEditText).setText("JSON file cleared.")
        }
        builder.setNegativeButton("No") { dialog, which ->
            dialog.dismiss()
        }
        builder.create().show()
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

    private fun shareEntriesJson() {
        val jsonFile = File(filesDir, "entries_log.json")
        val uri: Uri = FileProvider.getUriForFile(this, "com.example.journal.provider", jsonFile)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, "Share JSON file"))
    }

    private fun pickJsonFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "application/json"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, PICK_JSON_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_JSON_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.also { uri ->
                importJsonFile(uri)
            }
        }
    }

    private fun importJsonFile(uri: Uri) {
        val jsonFile = File(filesDir, "entries_log.json")
        contentResolver.openInputStream(uri)?.use { inputStream ->
            InputStreamReader(inputStream).use { reader ->
                FileOutputStream(jsonFile).use { outputStream ->
                    reader.readLines().forEach { line ->
                        outputStream.write((line + "\n").toByteArray())
                    }
                }
            }
        }

        // Update the EditText with the new content
        val newJsonContent = jsonFile.readText()
        findViewById<EditText>(R.id.jsonEditText).setText(newJsonContent)
    }

    private fun saveJsonContentIfValid() {
        val jsonEditText: EditText = findViewById(R.id.jsonEditText)
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

    private fun downloadJsonContent() {
        val jsonEditText: EditText = findViewById(R.id.jsonEditText)
        val jsonContent = jsonEditText.text.toString()

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "entries_log.json")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/")
        }

        val uri: Uri? = contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
        uri?.let {
            contentResolver.openOutputStream(it)?.use { outputStream ->
                outputStream.write(jsonContent.toByteArray())
                AlertDialog.Builder(this)
                    .setTitle("Download Successful")
                    .setMessage("JSON file has been saved to Documents/entries_log.json")
                    .setPositiveButton("OK", null)
                    .show()
            } ?: run {
                AlertDialog.Builder(this)
                    .setTitle("Download Failed")
                    .setMessage("Failed to save JSON file.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        } ?: run {
            AlertDialog.Builder(this)
                .setTitle("Download Failed")
                .setMessage("Failed to create JSON file.")
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
