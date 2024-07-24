package com.example.journal

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
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
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
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
import android.graphics.Color

class EntryEditorActivity : AppCompatActivity() {

    private lateinit var editText: EditText
    private lateinit var viewFilesButton: Button
    private lateinit var newEntryButton: Button
    private lateinit var lastEntryButton: Button
    private lateinit var renderButton: Button
    private lateinit var mediaButton: Button
    private lateinit var currentEntryTextView: TextView
    private lateinit var renderedTextView: TextView
    private lateinit var locationManager: LocationManager
    private lateinit var tagLayout: LinearLayout
    private var currentLocation: Location? = null
    private var lastKnownLocation: Location? = null

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

        // Initialize views
        editText = findViewById(R.id.editText)
        viewFilesButton = findViewById(R.id.viewFilesButton)
        newEntryButton = findViewById(R.id.newEntryButton)
        lastEntryButton = findViewById(R.id.lastEntryButton)
        renderButton = findViewById(R.id.renderButton)
        mediaButton = findViewById(R.id.mediaButton)
        currentEntryTextView = findViewById(R.id.currentEntryTextView)
        renderedTextView = findViewById(R.id.renderedTextView)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        tagLayout = findViewById(R.id.tagLayout)

        // Set up button click listeners
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

        // Set up text watcher
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!s.isNullOrEmpty()) {
                    saveEntryContent(s.toString())
                    MarkdownUtils.updateTextStyles(editText)
                }
            }
        })

        // Load entries and handle intent
        loadEntries()
        handleIntent()

        // Request location permissions and get the current location
        LocationUtils.requestLocationPermissions(this, locationManager)
        lastKnownLocation = LocationUtils.getLastKnownLocation(this, locationManager) // Ensure we try to get the last known location on app startup

        // Show keyboard initially
        showKeyboard(editText)

        // Initialize tag buttons (example tags list)
        val tagsList = listOf("Tag1", "Tag2", "Tag3", "Tag4", "Tag5")
        initializeTagButtons(tagsList)

        // Update tag buttons if an entry is already opened
        currentEntryId?.let {
            val entryData = entries.find { entry -> entry.created == it }
            entryData?.let { updateTagButtons(it.tags) }
        }
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

        if (currentTime - lastOpenedTime > 60000*5) {
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
                val loadedEntries: List<EntryData> = gson.fromJson(json, listType)

                // Ensure tags field is initialized
                entries = loadedEntries.map { entry ->
                    entry.apply {
                        if (tags == null) {
                            tags = mutableListOf()
                        }
                    }
                }.toMutableList()

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

        val latitude = lastKnownLocation?.latitude
        val longitude = lastKnownLocation?.longitude

        val newEntryData = EntryData(
            currentEntryId!!,
            null,  // Set modified to null initially
            "",
            coords = null,
            last_coords = if (latitude != null && longitude != null) String.format(Locale.getDefault(), "%.6f %.6f", latitude, longitude) else null,
            tags = mutableListOf()
        )
        // Temporarily store the new entry in a variable instead of adding it to the list
        currentNewEntryData = newEntryData

        val coordinatesText = if (latitude != null && longitude != null) {
            "≈ ${String.format(Locale.getDefault(), "%.6f %.6f", latitude, longitude)}"
        } else {
            "updating..."
        }
        findViewById<TextView>(R.id.coordinatesTextView).text = coordinatesText
        findViewById<TextView>(R.id.coordinatesTextView).visibility = View.VISIBLE

        Log.d("EntryOperation", "Created new entry: $currentEntryId")

        // Request the current location to update the entry
        LocationUtils.requestSingleLocationUpdate(this, locationManager, locationListener)

        // Update tag buttons with empty tags list
        updateTagButtons(newEntryData.tags)
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            currentLocation = location
            Log.d("LocationUpdate", "Location updated: $location")

            // Check if there's a temporary new entry that hasn't been saved yet
            if (currentNewEntryData != null) {
                currentNewEntryData!!.coords = String.format(Locale.getDefault(), "%.6f %.6f", location.latitude, location.longitude)
                // Update the coordinates text view
                val coordinatesText = String.format(Locale.getDefault(), "%.6f %.6f", location.latitude, location.longitude)
                findViewById<TextView>(R.id.coordinatesTextView).text = coordinatesText
                Log.d("EntryOperation", "Updated temporary entry with location: $currentEntryId")
            } else {
                // Update the latest entry with the current location only once
                val latestEntry = entries.firstOrNull { it.created == currentEntryId }
                if (latestEntry != null && latestEntry.coords == null) {
                    latestEntry.coords = String.format(Locale.getDefault(), "%.6f %.6f", location.latitude, location.longitude)
                    updateEntriesJson()

                    val coordinatesText = String.format(Locale.getDefault(), "%.6f %.6f", location.latitude, location.longitude)
                    findViewById<TextView>(R.id.coordinatesTextView).text = coordinatesText
                    Log.d("EntryOperation", "Updated entry with location: $currentEntryId")
                } else {
                    Log.e("EntryOperation", "No entry found with id: $currentEntryId or coordinates already set")
                }
            }

            // Remove updates after setting the location
            locationManager.removeUpdates(this)
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private var currentNewEntryData: EntryData? = null

    private fun saveEntryContent(text: String) {
        if (text.isNotEmpty() && currentEntryId != null) {
            val entryData = entries.find { it.created == currentEntryId }
            if (entryData != null) {
                entryData.content = text
                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
                dateFormat.timeZone = TimeZone.getTimeZone("UTC")
                entryData.modified = dateFormat.format(Date())
            } else if (currentNewEntryData != null) {
                currentNewEntryData!!.content = text
                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
                dateFormat.timeZone = TimeZone.getTimeZone("UTC")
                currentNewEntryData!!.modified = dateFormat.format(Date())
                entries.add(currentNewEntryData!!)
                currentNewEntryData = null  // Clear the temporary variable
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

            val coordinatesText = if (entryData.coords != null) {
                entryData.coords
            } else if (entryData.last_coords != null) {
                "≈ ${entryData.last_coords}"
            } else {
                "updating..."
            }
            findViewById<TextView>(R.id.coordinatesTextView).text = coordinatesText
            findViewById<TextView>(R.id.coordinatesTextView).visibility = View.VISIBLE

            Log.d("EntryOperation", "Opened entry: $id")

            // Update tag buttons to reflect the current tags of the entry
            updateTagButtons(entryData.tags)
        } else {
            Log.e("EntryOperation", "Entry data not found for id: $id")
        }
    }

    private fun openPreviousEntry() {
        val nonEmptyEntries = entries.filter { it.content.isNotEmpty() }
        if (nonEmptyEntries.isEmpty()) {
            Log.d("EntryOperation", "No non-empty entries to open.")
            return
        }

        val sortedEntries = nonEmptyEntries.sortedByDescending { it.modified }
        val currentEntryIndex = sortedEntries.indexOfFirst { it.created == currentEntryId }
        val nextEntryIndex = if (currentEntryIndex == -1) 0 else currentEntryIndex + 1

        if (nextEntryIndex < sortedEntries.size) {
            openEntry(sortedEntries[nextEntryIndex].created)
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

    fun updateCurrentLocationUI(location: Location) {
        val coordinatesText = String.format(Locale.getDefault(), "%.6f %.6f", location.latitude, location.longitude)
        findViewById<TextView>(R.id.coordinatesTextView).text = coordinatesText
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                LocationUtils.getCurrentLocation(this, locationManager)?.let {
                    updateCurrentLocationUI(it)
                }
            } else {
                // Permission denied, handle accordingly
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

    private fun initializeTagButtons(tags: List<String>) {
        tagLayout.removeAllViews()  // Clear any existing buttons

        for (tag in tags) {
            val tagButton = Button(this).apply {
                text = tag
                setBackgroundColor(Color.parseColor("#AFAFAF")) // Unselected state background color
                setTextColor(Color.WHITE)
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    0, // Match the bottom buttons layout width
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f // Weight of 1 to distribute evenly
                )
                setPadding(0, 0, 0, 0) // Minimal padding
                minHeight = 0 // Remove minimum height
                height = LinearLayout.LayoutParams.WRAP_CONTENT // Ensure height wraps content
                setOnClickListener { toggleTag(tag, this) }
            }
            tagLayout.addView(tagButton)
        }
    }






    private fun toggleTag(tag: String, button: Button) {
        val entryData = entries.find { it.created == currentEntryId }
        entryData?.let {
            if (it.tags.contains(tag)) {
                it.tags.remove(tag)
                button.setBackgroundColor(Color.parseColor("#AFAFAF")) // Unselected state
            } else {
                it.tags.add(tag)
                button.setBackgroundColor(Color.parseColor("#999999" +
                        "")) // Selected state
            }
            updateEntriesJson()
        }
    }

    private fun updateTagButtons(tags: List<String>?) {
        for (i in 0 until tagLayout.childCount) {
            val tagButton = tagLayout.getChildAt(i) as Button
            if (tags != null && tags.contains(tagButton.text.toString())) {
                tagButton.setBackgroundColor(Color.parseColor("#999999")) // Selected state background color
            } else {
                tagButton.setBackgroundColor(Color.parseColor("#AFAFAF")) // Unselected state background color
            }
        }
    }




    data class EntryData(
        val created: String,
        var modified: String?,
        var content: String,
        var coords: String?,
        var last_coords: String?,
        var tags: MutableList<String> = mutableListOf() // Initialize with an empty list
    )

}
