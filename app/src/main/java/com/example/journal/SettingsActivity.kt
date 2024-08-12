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

    private val pickJsonFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            showImportConfirmationDialog(it)
        }
    }

    private val pickSaveDirectoryLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let {
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

        val initialTimerValue = AppUsageUtils.getTimerDuration(this)
        timerEditText.setText(initialTimerValue.toString())

        timerEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val newTimerValue = timerEditText.text.toString().toIntOrNull()
                if (newTimerValue != null && newTimerValue > 0) {
                    AppUsageUtils.saveTimerDuration(this, newTimerValue)
                    Toast.makeText(this, "Inactivity time updated.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Please enter a valid time in seconds.", Toast.LENGTH_SHORT).show()
                }
                true
            } else {
                false
            }
        }


        val tags = TagUtils.loadTags(this)
        tagsEditText.setText(tags.joinToString(", "))

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

        darkModeButton.setOnClickListener {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            saveThemePreference(AppCompatDelegate.MODE_NIGHT_YES)
            recreate()
        }

        lightModeButton.setOnClickListener {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            saveThemePreference(AppCompatDelegate.MODE_NIGHT_NO)
            recreate()
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

        exportJsonButton.setOnClickListener {
            exportJsonContent()
        }

        chooseDirButton.setOnClickListener {
            openDirectoryPicker()
        }
    }

    private fun applyCurrentTheme() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val nightMode = preferences.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_YES)
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
        val jsonFile = File(filesDir, "entries.json")
        try {
            FileWriter(jsonFile).use {
                it.write("[]")
            }
            EntryEditorActivity.entries.clear()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generateFileName(): String {
        val currentDate = SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.getDefault()).format(Date())
        return "$currentDate-Journalize.json"
    }



    private fun shareEntriesJson() {
        val fileName = generateFileName()

        val jsonFile = File(filesDir, "entries.json")
        if (!jsonFile.exists()) {
            Toast.makeText(this, "No entries to share.", Toast.LENGTH_SHORT).show()
            return
        }

        val cacheFile = File(cacheDir, fileName)
        jsonFile.copyTo(cacheFile, overwrite = true)

        val uri: Uri = FileProvider.getUriForFile(this, "com.example.journal.provider", cacheFile)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val resInfoList = packageManager.queryIntentActivities(intent, 0)
        for (resolveInfo in resInfoList) {
            val packageName = resolveInfo.activityInfo.packageName
            grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, "Share JSON file"))
    }



    private fun pickJsonFile() {
        pickJsonFileLauncher.launch(arrayOf("application/json"))
    }

    private fun openDirectoryPicker() {
        pickSaveDirectoryLauncher.launch(null)
    }



    private fun importJsonFile(uri: Uri) {
        val jsonFile = File(filesDir, "entries.json")

        FileWriter(jsonFile).use { it.write("[]") }

        contentResolver.openInputStream(uri)?.use { inputStream ->
            InputStreamReader(inputStream).use { reader ->
                FileOutputStream(jsonFile).use { outputStream ->
                    reader.readLines().forEach { line ->
                        outputStream.write((line + "\n").toByteArray())
                    }
                }
            }
        }
    }


    private fun showImportConfirmationDialog(uri: Uri) {
        val builder = AlertDialog.Builder(this, R.style.AlertDialogTheme)
        builder.setTitle("Confirm Import")
        builder.setMessage("Importing a new JSON file will delete the current entries. Do you want to proceed?")
        builder.setPositiveButton("Yes") { _, _ ->
            importJsonFile(uri)
            Toast.makeText(this, "Entries imported.", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("No") { dialog, _ ->
            dialog.dismiss()
        }
        builder.create().show()
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

        val fileName = generateFileName()
        Log.d("SettingsActivity", "Exporting file with name: $fileName")

        try {
            val jsonFile = File(filesDir, "entries.json")
            val jsonContent = jsonFile.readText()

            val treeDocumentId = DocumentsContract.getTreeDocumentId(directoryUri)
            Log.d("SettingsActivity", "Tree document ID: $treeDocumentId")

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, treeDocumentId)

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
