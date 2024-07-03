package com.example.journal

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import io.noties.markwon.Markwon
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.file.FileSchemeHandler
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface


class EntryEditorActivity : AppCompatActivity() {

    private lateinit var editText: EditText
    private lateinit var viewFilesButton: Button
    private lateinit var newEntryButton: Button
    private lateinit var lastEntryButton: Button
    private lateinit var renderButton: Button
    private lateinit var mediaButton: Button
    private lateinit var currentEntryTextView: TextView
    private lateinit var renderedTextView: TextView

    companion object {
        var entries = mutableListOf<EntryData>()
        const val PREFS_NAME = "JournalPrefs"
        const val LAST_OPENED_TIME = "lastOpenedTime"
        const val REQUEST_CODE_PERMISSIONS = 10
    }

    private var currentEntryId: String? = null
    private var isEditMode = true
    private val PICK_IMAGE_REQUEST = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editText = findViewById(R.id.editText)
        viewFilesButton = findViewById(R.id.viewFilesButton)
        newEntryButton = findViewById(R.id.newEntryButton)
        lastEntryButton = findViewById(R.id.lastEntryButton)
        renderButton = findViewById(R.id.renderButton)
        mediaButton = findViewById(R.id.mediaButton)
        currentEntryTextView = findViewById(R.id.currentEntryTextView)
        renderedTextView = findViewById(R.id.renderedTextView)

        viewFilesButton.setOnClickListener {
            val intent = Intent(this, EntryListActivity::class.java)
            startActivityForResult(intent, 1)
        }

        newEntryButton.setOnClickListener {
            createNewEntry()
            editText.text.clear()
            showKeyboard(editText)
        }

        lastEntryButton.setOnClickListener {
            openPreviousEntry()
        }

        renderButton.setOnClickListener {
            toggleRenderMode()
        }

        mediaButton.setOnClickListener {
            if (hasPermissions()) {
                selectImage()
            } else {
                requestPermissions()
            }
        }

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (!s.isNullOrEmpty()) {
                    saveEntryContent(s.toString())
                }
            }
        })

        loadEntries()
        handleIntent()
        showKeyboard(editText)
    }

    override fun onPause() {
        super.onPause()

        val currentTime = System.currentTimeMillis()
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putLong(LAST_OPENED_TIME, currentTime)
        editor.apply()
    }

    override fun onResume() {
        super.onResume()

        val currentTime = System.currentTimeMillis()

        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastOpenedTime = sharedPreferences.getLong(LAST_OPENED_TIME, 0)

        if (currentTime - lastOpenedTime > 60000) {
            createNewEntry()
            editText.text.clear()
        }

        val editor = sharedPreferences.edit()
        editor.putLong(LAST_OPENED_TIME, currentTime)
        editor.apply()
    }

    private fun loadEntries() {
        val jsonFile = File(filesDir, "entries_log.json")
        if (jsonFile.exists()) {
            try {
                val json = FileReader(jsonFile).use { it.readText() }
                val gson = GsonBuilder().create()
                val listType = object : TypeToken<List<EntryData>>() {}.type
                entries = gson.fromJson<List<EntryData>>(json, listType).toMutableList()
                Log.d("EntryOperation", "Loaded JSON: $entries")
            } catch (e: Exception) {
                Log.e("EntryOperation", "Error reading JSON file", e)
                entries = mutableListOf()
            }
        } else {
            entries = mutableListOf()
        }
    }

    private fun handleIntent() {
        val entryId = intent.getStringExtra("entryId")
        if (entryId != null) {
            openEntry(entryId)
        } else {
            createNewEntry()
            showKeyboard(editText)
        }
    }

    private fun createNewEntry() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val currentDateTime = dateFormat.format(Date())
        currentEntryId = currentDateTime

        currentEntryTextView.text = currentEntryId
        currentEntryTextView.visibility = TextView.VISIBLE

        val newEntryData = EntryData(currentEntryId!!, currentDateTime, "")
        entries.add(0, newEntryData)
        updateEntriesJson()
        Log.d("EntryOperation", "Created new entry: $currentEntryId")
    }

    private fun saveEntryContent(text: String) {
        if (text.isNotEmpty() && currentEntryId != null) {
            val entryData = entries.find { it.created == currentEntryId }
            if (entryData != null) {
                entryData.content = text
                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
                dateFormat.timeZone = TimeZone.getTimeZone("UTC")
                entryData.modified = dateFormat.format(Date())
            } else {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
                dateFormat.timeZone = TimeZone.getTimeZone("UTC")
                val currentDateTime = dateFormat.format(Date())
                val newEntryData = EntryData(currentEntryId!!, currentDateTime, text)
                entries.add(newEntryData)
            }
            updateEntriesJson()

            currentEntryTextView.text = currentEntryId
            currentEntryTextView.visibility = TextView.VISIBLE
            Log.d("EntryOperation", "Saved entry: $currentEntryId")
        }
    }

    private fun updateEntriesJson() {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val nonEmptyEntries = entries.filter { it.content.isNotEmpty() }
        val json = gson.toJson(nonEmptyEntries)
        val jsonFile = File(filesDir, "entries_log.json")
        try {
            FileWriter(jsonFile).use {
                it.write(json)
            }
        } catch (e: Exception) {
            Log.e("EntryOperation", "Error writing JSON file", e)
        }
    }

    private fun openEntry(id: String) {
        currentEntryId = id
        val entryData = entries.find { it.created == id }
        if (entryData != null) {
            editText.setText(entryData.content)
            editText.setSelection(editText.text.length)
            currentEntryTextView.text = id
            currentEntryTextView.visibility = TextView.VISIBLE
            Log.d("EntryOperation", "Opened entry: $id")
        } else {
            Log.e("EntryOperation", "Entry data not found for id: $id")
        }
    }

    private fun openPreviousEntry() {
        val sortedEntries = entries.sortedByDescending { it.modified }
        val lastEditedEntry = sortedEntries.find { it.created != currentEntryId }

        if (lastEditedEntry != null) {
            openEntry(lastEditedEntry.created)
        } else {
            Log.d("EntryOperation", "No previous entry to open.")
        }
    }

    private fun showKeyboard(view: View) {
        view.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun hasPermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_PERMISSIONS)
    }

    private fun selectImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(Intent.createChooser(intent, "Select Images"), PICK_IMAGE_REQUEST)
    }


    private fun toggleRenderMode() {
        if (isEditMode) {
            renderMarkdown()
            renderButton.text = "Edit"
            editText.visibility = View.GONE
            renderedTextView.visibility = View.VISIBLE
            hideKeyboard(renderButton)
        } else {
            editText.visibility = View.VISIBLE
            renderedTextView.visibility = View.GONE
            renderButton.text = "Render"
            showKeyboard(editText)
        }
        isEditMode = !isEditMode
    }

    private fun renderMarkdown() {
        val text = editText.text.toString()
        if (text.isNotEmpty()) {
            val markwon = Markwon.builder(this)
                .usePlugin(ImagesPlugin.create { plugin ->
                    plugin.addSchemeHandler(FileSchemeHandler.create())
                })
                .build()
            markwon.setMarkdown(renderedTextView, text)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                selectImage()
            } else {
                // Permission denied, show a message to the user
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.let { intentData ->
                val clipData = intentData.clipData
                if (clipData != null) {
                    // Multiple images selected
                    for (i in 0 until clipData.itemCount) {
                        val uri = clipData.getItemAt(i).uri
                        handleImageUri(uri)
                    }
                } else {
                    // Single image selected
                    intentData.data?.let { uri ->
                        handleImageUri(uri)
                    }
                }
            }
        } else if (requestCode == 1 && resultCode == RESULT_OK) {
            data?.getStringExtra("id")?.let { id ->
                openEntry(id)
            }
        }
    }

    private fun handleImageUri(uri: Uri) {
        val imagePath = getPathFromUri(uri)
        if (imagePath != null) {
            insertImagePathToMarkdown(imagePath)
        }
    }

    private fun getPathFromUri(uri: Uri): String? {
        var path: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            val name = cursor.getString(nameIndex)
            val file = File(filesDir, "${System.currentTimeMillis()}_$name")
            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            path = file.absolutePath
        }

        path?.let {
            adjustImageOrientation(it)
        }

        return path
    }

    private fun adjustImageOrientation(imagePath: String) {
        try {
            val exif = ExifInterface(imagePath)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val bitmap = BitmapFactory.decodeFile(imagePath)
            val rotatedBitmap = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                else -> bitmap
            }

            // Save the rotated bitmap back to the file
            FileOutputStream(imagePath).use { out ->
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }

        } catch (e: Exception) {
            Log.e("EntryOperation", "Error adjusting image orientation", e)
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun insertImagePathToMarkdown(imagePath: String) {
        val markdownImage = "![Image](file://$imagePath)"
        val cursorPosition = editText.selectionStart
        editText.text?.insert(cursorPosition, markdownImage)
        if (!isEditMode) {
            renderMarkdown()
        }
    }

    data class EntryData(val created: String, var modified: String, var content: String)
}
