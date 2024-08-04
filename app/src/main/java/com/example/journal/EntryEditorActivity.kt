package com.example.journal

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

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
        const val REQUEST_CODE_PERMISSIONS = 10
    }

    private var currentEntryId: String? = null
    private var isEditMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply the saved theme before calling super.onCreate
        applyCurrentTheme()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupButtonListeners()
        setupEditText()

        // Change cursor color (if applicable)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            editText.textCursorDrawable = ContextCompat.getDrawable(this, R.drawable.cursor_drawable)
        }

        // Load entries and handle intent
        entries = EntryDataUtils.loadEntries(this)
        handleIntent()

        // Request location permissions and get the current location
        LocationUtils.requestLocationPermissions(this, locationManager)
        lastKnownLocation = LocationUtils.getLastKnownLocation(this, locationManager)

        // Show keyboard initially
        KeyboardUtils.showKeyboard(this, editText)

        // Load and initialize tags
        val tagsList = TagUtils.loadTags(this)
        TagUtils.initializeTagButtons(this, tagLayout, tagsList) { tag, button ->
            TagUtils.toggleTag(entries, currentEntryId, tag, button) {
                EntryDataUtils.updateEntriesJson(this, entries)
            }
        }

        // Update tag buttons based on the current entry
        currentEntryId?.let {
            val entryData = entries.find { entry -> entry.created == it }
            entryData?.let { TagUtils.updateTagButtons(tagLayout, it.tags) }
        }
    }


    private fun initializeViews() {
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
    }

    private fun setupButtonListeners() {
        viewFilesButton.setOnClickListener {
            val intent = Intent(this, EntryListActivity::class.java)
            startActivityForResult(intent, 1)
        }

        newEntryButton.setOnClickListener {
            createNewEntry()
            editText.text.clear()
            KeyboardUtils.showKeyboard(this, editText)
        }

        lastEntryButton.setOnClickListener {
            openPreviousEntry()
        }

        renderButton.setOnClickListener {
            isEditMode = MarkdownUtils.toggleRenderMode(this, isEditMode, editText, renderedTextView, renderButton)
        }

        mediaButton.setOnClickListener {
            if (ImageUtils.hasImagePermissions(this)) {
                ImageUtils.selectImage(this)
            } else {
                ImageUtils.requestImagePermissions(this)
            }
        }
    }

    private fun setupEditText() {
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

        editText.setOnKeyListener { v, keyCode, event ->
            MarkdownUtils.handleAutomaticList(editText, keyCode, event)
        }
    }

    override fun onPause() {
        super.onPause()
        AppUsageUtils.onPause(this)
    }

    override fun onResume() {
        super.onResume()
        AppUsageUtils.onResume(this) {
            createNewEntry()
            editText.text.clear()
        }
    }

    private fun handleIntent() {
        val entryId = intent.getStringExtra("entryId")
        if (entryId != null) {
            openEntry(entryId)
        } else {
            createNewEntry()
            KeyboardUtils.showKeyboard(this, editText)
        }
    }

    private fun createNewEntry() {
        currentEntryId = EntryDataUtils.getCurrentTimeString()
        currentEntryTextView.text = currentEntryId
        currentEntryTextView.visibility = TextView.VISIBLE

        val latitude = lastKnownLocation?.latitude
        val longitude = lastKnownLocation?.longitude

        val newEntryData = EntryData(
            currentEntryId!!,
            null,
            "",
            coords = null,
            last_coords = if (latitude != null && longitude != null) String.format(Locale.getDefault(), "%.6f %.6f", latitude, longitude) else null,
            tags = mutableListOf()
        )
        entries.add(newEntryData)
        currentNewEntryData = newEntryData

        updateCoordinatesTextView(latitude, longitude, isApproximate = true)

        Log.d("EntryOperation", "Created new entry: $currentEntryId")

        LocationUtils.requestSingleLocationUpdate(this, locationManager, locationListener)
        TagUtils.updateTagButtons(tagLayout, newEntryData.tags)
    }

    fun updateCoordinatesTextView(latitude: Double?, longitude: Double?, isApproximate: Boolean) {
        val coordinatesText = if (latitude != null && longitude != null) {
            if (isApproximate) {
                "≈ ${String.format(Locale.getDefault(), "%.6f %.6f", latitude, longitude)}"
            } else {
                String.format(Locale.getDefault(), "%.6f %.6f", latitude, longitude)
            }
        } else {
            "updating..."
        }
        findViewById<TextView>(R.id.coordinatesTextView).text = coordinatesText
        findViewById<TextView>(R.id.coordinatesTextView).visibility = View.VISIBLE
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            currentLocation = location
            Log.d("LocationUpdate", "Location updated: $location")

            if (currentNewEntryData != null) {
                currentNewEntryData!!.coords = String.format(Locale.getDefault(), "%.6f %.6f", location.latitude, location.longitude)
                updateCoordinatesTextView(location.latitude, location.longitude, isApproximate = false)
                Log.d("EntryOperation", "Updated temporary entry with location: $currentEntryId")
            } else {
                val latestEntry = entries.firstOrNull { it.created == currentEntryId }
                if (latestEntry != null && latestEntry.coords == null) {
                    latestEntry.coords = String.format(Locale.getDefault(), "%.6f %.6f", location.latitude, location.longitude)
                    EntryDataUtils.updateEntriesJson(this@EntryEditorActivity, entries)
                    updateCoordinatesTextView(location.latitude, location.longitude, isApproximate = false)
                    Log.d("EntryOperation", "Updated entry with location: $currentEntryId")
                } else {
                    Log.e("EntryOperation", "No entry found with id: $currentEntryId or coordinates already set")
                }
            }

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
                EntryDataUtils.updateModifiedTime(entryData)
            } else if (currentNewEntryData != null) {
                currentNewEntryData!!.content = text
                EntryDataUtils.updateModifiedTime(currentNewEntryData!!)
                entries.add(currentNewEntryData!!)
                currentNewEntryData = null
            }
            EntryDataUtils.updateEntriesJson(this, entries)
            currentEntryTextView.text = currentEntryId
            currentEntryTextView.visibility = TextView.VISIBLE
            Log.d("EntryOperation", "Saved entry: $currentEntryId")
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
            TagUtils.updateTagButtons(tagLayout, entryData.tags)
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == ImageUtils.REQUEST_CODE_IMAGE_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                ImageUtils.selectImage(this)
            }
        } else if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                LocationUtils.getCurrentLocation(this, locationManager)?.let {
                    updateCoordinatesTextView(it.latitude, it.longitude, isApproximate = false)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ImageUtils.PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.let { intentData ->
                val clipData = intentData.clipData
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        val uri = clipData.getItemAt(i).uri
                        ImageUtils.handleImageUri(this, uri, editText, isEditMode, renderedTextView)
                    }
                } else {
                    intentData.data?.let { uri ->
                        ImageUtils.handleImageUri(this, uri, editText, isEditMode, renderedTextView)
                    }
                }
            }
        } else if (requestCode == 1 && resultCode == RESULT_OK) {
            data?.getStringExtra("id")?.let { id ->
                openEntry(id)
            }
        }
    }

    private fun applyCurrentTheme() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val nightMode = preferences.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_NO)
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }


    data class EntryData(
        val created: String,
        var modified: String?,
        var content: String,
        var coords: String?,
        var last_coords: String?,
        var tags: MutableList<String> = mutableListOf()
    )
}
