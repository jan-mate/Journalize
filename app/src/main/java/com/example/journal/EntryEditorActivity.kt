package com.example.journal

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
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
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan

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
                    updateTextStyles()
                }
            }
        })

        // Load entries and handle intent
        loadEntries()
        handleIntent()

        // Request location permissions and get the current location
        requestLocationPermissions()
        getLastKnownLocation()  // Ensure we try to get the last known location on app startup

        // Show keyboard initially
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

        val latitude = lastKnownLocation?.latitude
        val longitude = lastKnownLocation?.longitude

        val newEntryData = EntryData(
            currentEntryId!!,
            currentDateTime,
            "",
            coords = null,
            last_coords = if (latitude != null && longitude != null) String.format(Locale.getDefault(), "%.6f %.6f", latitude, longitude) else null
        )
        entries.add(0, newEntryData)
        updateEntriesJson()

        val coordinatesText = if (latitude != null && longitude != null) {
            String.format(Locale.getDefault(), "%+.6f %+.6f", latitude, longitude)
        } else {
            "updating..."
        }
        findViewById<TextView>(R.id.coordinatesTextView).text = coordinatesText
        findViewById<TextView>(R.id.coordinatesTextView).visibility = View.VISIBLE

        Log.d("EntryOperation", "Created new entry: $currentEntryId")

        // Request the current location to update the entry
        requestSingleLocationUpdate()
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            // Use the most recent known location
            if (gpsLocation != null && networkLocation != null) {
                currentLocation = if (gpsLocation.time > networkLocation.time) gpsLocation else networkLocation
            } else if (gpsLocation != null) {
                currentLocation = gpsLocation
            } else if (networkLocation != null) {
                currentLocation = networkLocation
            }

            if (currentLocation != null) {
                updateCurrentLocationUI(currentLocation!!)
            }
        } else {
            Log.e("LocationUpdate", "Location permission not granted")
        }
    }

    private fun getLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            // Use the most recent known location
            if (gpsLocation != null && networkLocation != null) {
                lastKnownLocation = if (gpsLocation.time > networkLocation.time) gpsLocation else networkLocation
            } else if (gpsLocation != null) {
                lastKnownLocation = gpsLocation
            } else if (networkLocation != null) {
                lastKnownLocation = networkLocation
            }
        } else {
            Log.e("LocationUpdate", "Location permission not granted")
        }
    }

    private fun requestSingleLocationUpdate() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, locationListener, null)
            locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, locationListener, null)
        } else {
            Log.e("LocationUpdate", "Location permission not granted")
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            currentLocation = location
            Log.d("LocationUpdate", "Location updated: $location")

            // Update the latest entry with the current location only once
            val latestEntry = entries.firstOrNull { it.created == currentEntryId }
            if (latestEntry != null && latestEntry.coords == null) {
                latestEntry.coords = String.format(Locale.getDefault(), "%.6f %.6f", location.latitude, location.longitude)
                updateEntriesJson()

                val coordinatesText = String.format(
                    Locale.getDefault(),
                    "%.6f %.6f",
                    location.latitude,
                    location.longitude
                )
                findViewById<TextView>(R.id.coordinatesTextView).text = coordinatesText
                Log.d("EntryOperation", "Updated entry with location: $currentEntryId")
            } else {
                Log.e("EntryOperation", "No entry found with id: $currentEntryId or coordinates already set")
            }

            // Remove updates after setting the location
            locationManager.removeUpdates(this)
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }


    private fun updateCurrentLocationUI(location: Location) {
        val coordinatesText = String.format(Locale.getDefault(), "%.6f %.6f", location.latitude, location.longitude)
        findViewById<TextView>(R.id.coordinatesTextView).text = coordinatesText
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
                val newEntryData = EntryData(
                    currentEntryId!!,
                    currentDateTime,
                    text,
                    coords = null,
                    last_coords = if (lastKnownLocation != null) String.format("%+.6f %+.6f", lastKnownLocation!!.latitude, lastKnownLocation!!.longitude) else null
                )
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

    private fun updateTextStyles() {
        val spannable = editText.text as SpannableStringBuilder
        val lines = spannable.split("\n")

        // Clear previous styles
        val sizeSpans = spannable.getSpans(0, spannable.length, android.text.style.RelativeSizeSpan::class.java)
        for (span in sizeSpans) {
            spannable.removeSpan(span)
        }
        val underlineSpans = spannable.getSpans(0, spannable.length, android.text.style.UnderlineSpan::class.java)
        for (span in underlineSpans) {
            spannable.removeSpan(span)
        }
        val styleSpans = spannable.getSpans(0, spannable.length, android.text.style.StyleSpan::class.java)
        for (span in styleSpans) {
            spannable.removeSpan(span)
        }
        val typefaceSpans = spannable.getSpans(0, spannable.length, android.text.style.TypefaceSpan::class.java)
        for (span in typefaceSpans) {
            spannable.removeSpan(span)
        }
        val strikethroughSpans = spannable.getSpans(0, spannable.length, android.text.style.StrikethroughSpan::class.java)
        for (span in strikethroughSpans) {
            spannable.removeSpan(span)
        }
        val bulletSpans = spannable.getSpans(0, spannable.length, android.text.style.BulletSpan::class.java)
        for (span in bulletSpans) {
            spannable.removeSpan(span)
        }
        val quoteSpans = spannable.getSpans(0, spannable.length, android.text.style.QuoteSpan::class.java)
        for (span in quoteSpans) {
            spannable.removeSpan(span)
        }

        var start = 0

        for (line in lines) {
            val end = start + line.length
            when {
                line.startsWith("# ") -> {
                    spannable.setSpan(
                        android.text.style.RelativeSizeSpan(2f),
                        start,
                        end,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spannable.setSpan(
                        android.text.style.UnderlineSpan(),
                        start,
                        end,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                line.startsWith("## ") -> {
                    spannable.setSpan(
                        android.text.style.RelativeSizeSpan(1.8f),
                        start,
                        end,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spannable.setSpan(
                        android.text.style.UnderlineSpan(),
                        start,
                        end,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                line.startsWith("### ") -> spannable.setSpan(
                    android.text.style.RelativeSizeSpan(1.6f),
                    start,
                    end,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                line.startsWith("#### ") -> spannable.setSpan(
                    android.text.style.RelativeSizeSpan(1.4f),
                    start,
                    end,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                line.startsWith("##### ") -> spannable.setSpan(
                    android.text.style.RelativeSizeSpan(1.2f),
                    start,
                    end,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                line.startsWith("###### ") -> spannable.setSpan(
                    android.text.style.RelativeSizeSpan(1.1f),
                    start,
                    end,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                else -> spannable.setSpan(
                    android.text.style.RelativeSizeSpan(1f),
                    start,
                    end,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // Handle bold-italic text within the line (***text***)
            var boldItalicStart = line.indexOf("***", 0)
            while (boldItalicStart != -1) {
                val boldItalicEnd = line.indexOf("***", boldItalicStart + 3)
                if (boldItalicEnd != -1) {
                    spannable.setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.BOLD_ITALIC),
                        start + boldItalicStart,
                        start + boldItalicEnd + 3,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    boldItalicStart = line.indexOf("***", boldItalicEnd + 3)
                } else {
                    boldItalicStart = -1
                }
            }

            // Handle bold text within the line (**text**)
            var boldStart = line.indexOf("**", 0)
            while (boldStart != -1) {
                val boldEnd = line.indexOf("**", boldStart + 2)
                if (boldEnd != -1 && (boldItalicStart == -1 || boldStart < boldItalicStart || boldStart > boldItalicStart + 2)) {
                    spannable.setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        start + boldStart,
                        start + boldEnd + 2,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    boldStart = line.indexOf("**", boldEnd + 2)
                } else {
                    boldStart = -1
                }
            }

            // Handle italic text within the line (*text* and _text_)
            var italicStart = line.indexOf("*", 0)
            while (italicStart != -1) {
                val italicEnd = line.indexOf("*", italicStart + 1)
                if (italicEnd != -1 && (boldStart == -1 || italicStart < boldStart || italicStart > boldStart + 1)) {
                    spannable.setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.ITALIC),
                        start + italicStart,
                        start + italicEnd + 1,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    italicStart = line.indexOf("*", italicEnd + 1)
                } else {
                    italicStart = -1
                }
            }

            italicStart = line.indexOf("_", 0)
            while (italicStart != -1) {
                val italicEnd = line.indexOf("_", italicStart + 1)
                if (italicEnd != -1 && (boldStart == -1 || italicStart < boldStart || italicStart > boldStart + 1)) {
                    spannable.setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.ITALIC),
                        start + italicStart,
                        start + italicEnd + 1,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    italicStart = line.indexOf("_", italicEnd + 1)
                } else {
                    italicStart = -1
                }
            }

            // Handle strikethrough text within the line (~~text~~)
            var strikeStart = line.indexOf("~~", 0)
            while (strikeStart != -1) {
                val strikeEnd = line.indexOf("~~", strikeStart + 2)
                if (strikeEnd != -1) {
                    spannable.setSpan(
                        android.text.style.StrikethroughSpan(),
                        start + strikeStart,
                        start + strikeEnd + 2,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    strikeStart = line.indexOf("~~", strikeEnd + 2)
                } else {
                    strikeStart = -1
                }
            }

            // Handle custom styled text within the line (`text`)
            var codeStart = line.indexOf("`", 0)
            while (codeStart != -1) {
                val codeEnd = line.indexOf("`", codeStart + 1)
                if (codeEnd != -1) {
                    spannable.setSpan(
                        android.text.style.TypefaceSpan("monospace"),
                        start + codeStart,
                        start + codeEnd + 1,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    codeStart = line.indexOf("`", codeEnd + 1)
                } else {
                    codeStart = -1
                }
            }

            // Handle file text within the line (![text](file://path))
            var fileStart = line.indexOf("![", 0)
            while (fileStart != -1) {
                val fileEnd = line.indexOf(")", fileStart + 2)
                if (fileEnd != -1) {
                    spannable.setSpan(
                        ForegroundColorSpan(Color.GRAY),
                        start + fileStart,
                        start + fileEnd + 1,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    fileStart = line.indexOf("![", fileEnd + 1)
                } else {
                    fileStart = -1
                }
            }

            start = end + 1
        }
    }

    private fun requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_CODE_PERMISSIONS)
        } else {
            getCurrentLocation()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
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

    data class EntryData(
        val created: String,
        var modified: String,
        var content: String,
        var coords: String?,
        var last_coords: String?
    )
}
