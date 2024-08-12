package com.example.journal

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.gson.Gson
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
    private var isIntroEntryCreated: Boolean = false


    companion object {
        var entries = mutableListOf<EntryData>()
        const val REQUEST_CODE_PERMISSIONS = 10
        private const val PREFS_NAME = "MyPrefsFile"
        private const val FIRST_LAUNCH_KEY = "isFirstLaunch"
        private const val INTRO_ID = "intro_json"
    }

    private var currentEntryId: String? = null
    private var isEditMode = true



    override fun onCreate(savedInstanceState: Bundle?) {
        applyCurrentTheme()
        super.onCreate(savedInstanceState)

        // Set up the main content view and initialize views
        setContentView(R.layout.activity_main)
        initializeViews()
        setupButtonListeners()
        setupEditText()

        // Load the entries
        entries = EntryDataUtils.loadEntries(this)

        // Check if this is the first launch
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isFirstLaunch = sharedPreferences.getBoolean(FIRST_LAUNCH_KEY, true)

        if (isFirstLaunch) {
            // Create and open the intro entry
            createAndOpenIntroEntry()

            // Show location permission dialog
            showLocationPermissionDialog()
        } else {
            // Handle the intent normally (opens the last opened entry or creates a new one)
            handleIntent()
        }

        // Load the last known location if permissions are granted
        lastKnownLocation = LocationUtils.getCurrentLocation(this, locationManager)
        lastKnownLocation?.let {
            saveLocationToPreferences(it)
        }

        // Show the keyboard after the entry is opened
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
                // If permissions are already granted, open the image picker
                ImageUtils.selectImage(this)
            } else {
                // Request image permissions
                ImageUtils.requestImagePermissions(this)
            }
        }
    }

    private fun setupEditText() {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (s.isNullOrEmpty()) {
                    // If text becomes empty, delete the current entry and create a new one
                    deleteCurrentEntry()
                    createNewEntry()
                    editText.text.clear()
                    KeyboardUtils.showKeyboard(this@EntryEditorActivity, editText)
                } else {
                    saveEntryContent(s.toString())
                    MarkdownUtils.updateTextStyles(editText)
                }
            }
        })

        editText.setOnKeyListener { _, keyCode, event ->
            MarkdownUtils.handleAutomaticList(editText, keyCode, event)
        }
    }

    override fun onPause() {
        super.onPause()
        AppUsageUtils.onPause(this)
    }

    override fun onResume() {
        super.onResume()

        // Load shared preferences
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isFirstLaunch = sharedPreferences.getBoolean(FIRST_LAUNCH_KEY, true)

        if (isFirstLaunch) {
            // If it's the first launch, create and open the intro entry
            createAndOpenIntroEntry()

            // Update the shared preference to indicate that the app has been launched
            sharedPreferences.edit().putBoolean(FIRST_LAUNCH_KEY, false).apply()
        } else {
            // For subsequent launches, use AppUsageUtils.onResume
            AppUsageUtils.onResume(this) {
                createNewEntry()
                editText.text.clear()
            }
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

        Log.d("EntryOperation", "Created new entry: $currentEntryId")

        LocationUtils.requestSingleLocationUpdate(this, locationManager, locationListener)
        TagUtils.updateTagButtons(tagLayout, newEntryData.tags)
    }


    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            currentLocation = location
            Log.d("LocationUpdate", "Location updated: $location")

            // Update coordinates for all entries that do not have precise coords set
            val entriesToUpdate = entries.filter { it.coords == null }
            entriesToUpdate.forEach {
                it.coords = String.format(Locale.getDefault(), "%.6f %.6f", location.latitude, location.longitude)
                EntryDataUtils.updateEntriesJson(this@EntryEditorActivity, entries)
                Log.d("EntryOperation", "Updated entry with location: ${it.created}")
            }

            // Update last known location for future entries
            saveLocationToPreferences(location)

            locationManager.removeUpdates(this)
        }

        @Deprecated("This method is deprecated in API level 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun saveLocationToPreferences(location: Location) {
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val editor = prefs.edit()
        editor.putString(LocationUtils.PREF_LAST_KNOWN_LAT, location.latitude.toString())
        editor.putString(LocationUtils.PREF_LAST_KNOWN_LON, location.longitude.toString())
        editor.apply()

        // Update lastKnownLocation for immediate use
        lastKnownLocation = location
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
                if (currentNewEntryData!!.last_coords == null) {
                    currentNewEntryData!!.last_coords = String.format(Locale.getDefault(), "%.6f %.6f", lastKnownLocation?.latitude, lastKnownLocation?.longitude)
                }
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
            // Update the content and selection in the EditText
            editText.setText(entryData.content)
            editText.setSelection(editText.text.length)

            // Update the current entry timestamp display
            currentEntryTextView.text = entryData.created

            // Update tag buttons based on the entry's tags
            TagUtils.updateTagButtons(tagLayout, entryData.tags)

            Log.d("EntryOperation", "Opened entry: $id")
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

    private fun requestLocationPermissions() {
        if (LocationUtils.hasLocationPermissions(this)) {
            LocationUtils.getCurrentLocation(this, locationManager)?.let {
                Log.d("LocationPermission", "Location permission already granted, current location is available.")
            }
        } else {
            LocationUtils.requestLocationPermissions(this)
        }
    }

    private fun requestImagePermissions() {
        if (ImageUtils.hasImagePermissions(this)) {
            ImageUtils.selectImage(this)
        } else {
            ImageUtils.requestImagePermissions(this)
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            LocationUtils.REQUEST_CODE_LOCATION_PERMISSIONS -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Location permission granted
                    LocationUtils.getCurrentLocation(this, locationManager)?.let {
                        Log.d("LocationPermission", "Location permission granted, current location is available.")
                    }
                } else {
                    Log.d("LocationPermission", "Location permission denied.")
                }
            }
            ImageUtils.REQUEST_CODE_IMAGE_PERMISSIONS -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Image permission granted, open the image picker
                    ImageUtils.selectImage(this)
                } else {
                    Log.d("ImagePermission", "Image permission denied.")
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
        val nightMode = preferences.getInt("theme_mode", -1) // Default to -1 (no preference set)
        if (nightMode == -1) {
            // No theme has been set before, so default to dark mode
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            preferences.edit().putInt("theme_mode", AppCompatDelegate.MODE_NIGHT_YES).apply()
        } else {
            // Apply the saved theme
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }

    private fun deleteCurrentEntry() {
        currentEntryId?.let { id ->
            entries.removeAll { it.created == id }
            EntryDataUtils.updateEntriesJson(this, entries)
            Log.d("EntryOperation", "Deleted entry: $id")
        }
    }

    private fun showLocationPermissionDialog() {
        // Use the custom AlertDialog style that you defined in your styles.xml
        val builder = AlertDialog.Builder(this, R.style.AlertDialogTheme)

        builder.setTitle("Location Permission Needed")
        builder.setMessage("This app uses your location to log where your journal entries are made. Do you want to enable location permissions?")

        builder.setPositiveButton("Allow") { _, _ ->
            // Request location permissions after the user agrees
            LocationUtils.requestLocationPermissions(this)
        }

        builder.setNegativeButton("Deny") { dialog, _ ->
            // Handle the case where the user denies the location permission
            dialog.dismiss()
        }

        // Create and show the dialog
        val dialog = builder.create()

        dialog.show()
    }

    private fun createAndOpenIntroEntry() {
        val currentTime = EntryDataUtils.getCurrentTimeString()
        Log.d("EntryOperation", "createAndOpenIntroEntry: Creating intro entry at $currentTime")

        // Load the introductory Markdown text from the res/raw folder
        val introText = loadTextFromRawFile(R.raw.intro_content)

        // Create the intro entry data with the current timestamp and loaded text
        val introEntry = EntryData(
            created = currentTime,
            modified = currentTime,
            content = introText,
            coords = "69, 420",
            last_coords = "69420, 42069",
            tags = mutableListOf()
        )

        // Add the entry to the list and save it
        entries.add(introEntry)
        EntryDataUtils.updateEntriesJson(this, entries)
        Log.d("EntryOperation", "createAndOpenIntroEntry: Intro entry created and saved.")

        // Open the intro entry
        openEntry(currentTime)
        Log.d("EntryOperation", "createAndOpenIntroEntry: Intro entry opened.")
    }

    private fun loadTextFromRawFile(resourceId: Int): String {
        val inputStream = resources.openRawResource(resourceId)
        return inputStream.bufferedReader().use { it.readText() }
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
