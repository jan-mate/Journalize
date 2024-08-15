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
        private const val PREFS_NAME = "MyPrefsFile"
        private const val FIRST_LAUNCH_KEY = "isFirstLaunch"
    }

    private var currentEntryId: String? = null
    private var isEditMode = true



    override fun onCreate(savedInstanceState: Bundle?) {
        applyCurrentTheme()
        super.onCreate(savedInstanceState)


        setContentView(R.layout.activity_main)
        initializeViews()
        setupButtonListeners()
        setupEditText()


        entries = EntryDataUtils.loadEntries(this)


        lastKnownLocation = LocationUtils.getCurrentLocation(this, locationManager)
        lastKnownLocation?.let {
            saveLocationToPreferences(it)
        }

        handleIntent()

        KeyboardUtils.showKeyboard(this, editText)


        val tagsList = TagUtils.loadTags(this)
        TagUtils.initializeTagButtons(this, tagLayout, tagsList) { tag, button ->
            TagUtils.toggleTag(entries, currentEntryId, tag, button) {
                EntryDataUtils.updateEntriesJson(this, entries)
            }
        }


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
                if (s.isNullOrEmpty()) {

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

        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isFirstLaunch = sharedPreferences.getBoolean(FIRST_LAUNCH_KEY, true)

        if (isFirstLaunch) {
            createAndOpenIntroEntry()
            sharedPreferences.edit().putBoolean(FIRST_LAUNCH_KEY, false).apply()
            showLocationPermissionDialog()
        } else {
            Log.d("NewEntryOnResume", "onResume: else clause activated. intro")
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

        if (!isEditMode) {
            isEditMode = MarkdownUtils.toggleRenderMode(this, isEditMode, editText, renderedTextView, renderButton)
        }

        editText.text.clear()
        KeyboardUtils.showKeyboard(this, editText)

        LocationUtils.requestSingleLocationUpdate(this, locationManager, locationListener)
        TagUtils.updateTagButtons(tagLayout, newEntryData.tags)
    }





    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            currentLocation = location
            Log.d("LocationUpdate", "Location updated: $location")


            val entriesToUpdate = entries.filter { it.coords == null }
            entriesToUpdate.forEach {
                it.coords = String.format(Locale.getDefault(), "%.6f %.6f", location.latitude, location.longitude)
                EntryDataUtils.updateEntriesJson(this@EntryEditorActivity, entries)
                Log.d("EntryOperation", "Updated entry with location: ${it.created}")
            }


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
            editText.setText(entryData.content)

            editText.setSelection(editText.text.length)

            // Simulate typing a space and then a backspace, this is a stupid way to correctly adjust the height.
            editText.post {
                val originalText = editText.text.toString()
                editText.append(" ")
                editText.text.delete(editText.text.length - 1, editText.text.length)
            }

            currentEntryTextView.text = entryData.created

            TagUtils.updateTagButtons(tagLayout, entryData.tags)

            Log.d("EntryOperation", "Opened entry: $id, cursor set to end.")
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

            // Force switch to edit mode if currently in render mode
            if (!isEditMode) {
                isEditMode = MarkdownUtils.toggleRenderMode(this, isEditMode, editText, renderedTextView, renderButton)
            }
        } else {
            Log.d("EntryOperation", "No previous entry to open.")
        }
    }



    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            LocationUtils.REQUEST_CODE_LOCATION_PERMISSIONS -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    LocationUtils.getCurrentLocation(this, locationManager)?.let {
                        Log.d("LocationPermission", "Location permission granted, current location is available.")
                    }
                } else {
                    Log.d("LocationPermission", "Location permission denied.")
                }
            }
            ImageUtils.REQUEST_CODE_IMAGE_PERMISSIONS -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
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
        val nightMode = preferences.getInt("theme_mode", -1)
        if (nightMode == -1) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            preferences.edit().putInt("theme_mode", AppCompatDelegate.MODE_NIGHT_YES).apply()
        } else {
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
        val builder = AlertDialog.Builder(this, R.style.AlertDialogTheme)

        builder.setTitle("Location Permission Needed")
        builder.setMessage("This app uses your location to log where your journal entries are made. Do you want to enable location permissions?")

        builder.setPositiveButton("Allow") { _, _ ->
            LocationUtils.requestLocationPermissions(this)
        }

        builder.setNegativeButton("Deny") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = builder.create()

        dialog.show()
    }

    private fun createAndOpenIntroEntry() {
        val currentTime = EntryDataUtils.getCurrentTimeString()
        Log.d("EntryOperation", "createAndOpenIntroEntry: Creating intro entry at $currentTime")


        val introText = loadTextFromRawFile(R.raw.intro_content)

        val introEntry = EntryData(
            created = currentTime,
            modified = currentTime,
            content = introText,
            coords = "41.903290,-84.035225",
            last_coords = "38.362394, -105.093657",
            tags = mutableListOf()
        )

        entries.add(introEntry)
        EntryDataUtils.updateEntriesJson(this, entries)
        Log.d("EntryOperation", "createAndOpenIntroEntry: Intro entry created and saved.")


        openEntry(currentTime)

        editText.post {
            editText.clearFocus()
            editText.setSelection(0)
            editText.isFocusableInTouchMode = true
            editText.requestFocus()
        }

        Log.d("EntryOperation", "createAndOpenIntroEntry: Intro entry opened and scrolled to top.")
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
