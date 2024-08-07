package com.example.journal

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import android.preference.PreferenceManager
import androidx.core.content.FileProvider
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var timerTextView: TextView
    private lateinit var timerEditText: EditText
    private lateinit var tagsEditText: EditText
    private lateinit var viewJsonButton: Button
    private lateinit var darkModeButton: Button
    private lateinit var lightModeButton: Button
    private lateinit var deleteJsonButton: Button
    private lateinit var shareJsonButton: Button
    private lateinit var importJsonButton: Button
    private lateinit var exportJsonButton: Button
    private lateinit var chooseDirButton: Button

    // Activity result launchers
    private val pickJsonFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            importJsonFile(it)
        }
    }

    private val pickSaveDirectoryLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let {
            // Take persistable URI permission
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            saveDirectoryPath(uri)
            Toast.makeText(this, "Save directory selected.", Toast.LENGTH_SHORT).show()
        }
    }

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
        deleteJsonButton = findViewById(R.id.deleteJsonButton)
        shareJsonButton = findViewById(R.id.shareJsonButton)
        importJsonButton = findViewById(R.id.importJsonButton)
        exportJsonButton = findViewById(R.id.exportJsonButton)
        chooseDirButton = findViewById(R.id.chooseDirButton)

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

        // Set click listeners for JSON operations
        deleteJsonButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        shareJsonButton.setOnClickListener {
            shareEntriesJson()
        }

        importJsonButton.setOnClickListener {
            pickJsonFile()
        }

        exportJsonButton.setOnClickListener {
            exportJsonContent()
        }

        chooseDirButton.setOnClickListener {
            openDirectoryPicker()
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

    private fun showDeleteConfirmationDialog() {
        val builder = AlertDialog.Builder(this, R.style.AlertDialogTheme)
        builder.setTitle("Confirm Delete")
        builder.setMessage("Are you sure you want to delete the JSON file?")
        builder.setPositiveButton("Yes") { dialog, which ->
            clearJsonFile()
            Toast.makeText(this, "JSON file cleared.", Toast.LENGTH_SHORT).show()
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
            EntryEditorActivity.entries.clear()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generateFileName(): String {
        // Get the current date and time and format it as YYYY-MM-DD-HH-MM
        val currentDate = SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.getDefault()).format(Date())
        return "$currentDate-Journalize.json"
    }



    private fun shareEntriesJson() {
        // Generate the file name using the current date and time
        val fileName = generateFileName()

        // Original file location
        val jsonFile = File(filesDir, "entries_log.json")
        if (!jsonFile.exists()) {
            Toast.makeText(this, "No entries to share.", Toast.LENGTH_SHORT).show()
            return
        }

        // Create a temporary file in the cache directory
        val cacheFile = File(cacheDir, fileName)
        jsonFile.copyTo(cacheFile, overwrite = true)

        // Obtain the URI for the temporary cache file
        val uri: Uri = FileProvider.getUriForFile(this, "com.example.journal.provider", cacheFile)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Grant URI permission to the target apps that can handle the share intent
        val resInfoList = packageManager.queryIntentActivities(intent, 0)
        for (resolveInfo in resInfoList) {
            val packageName = resolveInfo.activityInfo.packageName
            grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Start the chooser activity for sharing
        startActivity(Intent.createChooser(intent, "Share JSON file"))
    }



    private fun pickJsonFile() {
        pickJsonFileLauncher.launch(arrayOf("application/json"))
    }

    private fun openDirectoryPicker() {
        pickSaveDirectoryLauncher.launch(null)
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
        Toast.makeText(this, "JSON file imported.", Toast.LENGTH_SHORT).show()
    }

    private fun exportJsonContent() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val directoryUriString = preferences.getString("save_directory_uri", null)

        if (directoryUriString == null) {
            Toast.makeText(this, "Please choose a save directory first.", Toast.LENGTH_SHORT).show()
            return
        }

        val directoryUri = Uri.parse(directoryUriString)
        Log.d("SettingsActivity", "Using directory URI: $directoryUri")

        // Generate the file name
        val fileName = generateFileName()
        Log.d("SettingsActivity", "Exporting file with name: $fileName")

        try {
            val jsonFile = File(filesDir, "entries_log.json")
            val jsonContent = jsonFile.readText()

            // Retrieve the document ID of the selected directory
            val treeDocumentId = DocumentsContract.getTreeDocumentId(directoryUri)
            Log.d("SettingsActivity", "Tree document ID: $treeDocumentId")

            // Build the URI for the directory's children
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, treeDocumentId)

            // Create a new document in the selected directory
            val newFileUri = DocumentsContract.createDocument(
                contentResolver,
                childrenUri,
                "application/json",
                fileName
            )

            Log.d("SettingsActivity", "New file URI: $newFileUri")

            if (newFileUri != null) {
                contentResolver.openOutputStream(newFileUri)?.use { outputStream ->
                    outputStream.write(jsonContent.toByteArray())
                    Log.d("SettingsActivity", "File successfully written to: $newFileUri")
                    AlertDialog.Builder(this)
                        .setTitle("Export Successful")
                        .setMessage("JSON file has been saved to the selected directory as $fileName.")
                        .setPositiveButton("OK", null)
                        .show()
                } ?: run {
                    Log.e("SettingsActivity", "Failed to open output stream for JSON file")
                    showErrorDialog("Export Failed", "Failed to open output stream for the JSON file.")
                }
            } else {
                Log.e("SettingsActivity", "Failed to create JSON file in selected directory")
                showErrorDialog("Export Failed", "Failed to create JSON file in the selected directory.")
            }
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Exception during export: ${e.message}")
            e.printStackTrace()
            showErrorDialog("Export Failed", "An error occurred while exporting the JSON file.")
        }
    }

    private fun showErrorDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun saveDirectoryPath(uri: Uri) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        preferences.edit().putString("save_directory_uri", uri.toString()).apply()
        Log.d("SettingsActivity", "Saved directory URI: $uri")
    }
}
