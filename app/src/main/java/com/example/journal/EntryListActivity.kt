package com.example.journal

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class EntryListActivity : AppCompatActivity() {

    private lateinit var entryListView: ListView
    private lateinit var overflowMenu: ImageView
    private lateinit var searchView: EditText
    private lateinit var tagLayout: LinearLayout
    private lateinit var tagToggleButton: ImageButton // Add a reference for the tag toggle button
    private var selectedEntries = mutableSetOf<String>()
    private var selectedTags = mutableSetOf<String>()
    private var entryListAdapter: EntryListAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)

        entryListView = findViewById(R.id.fileListView)
        overflowMenu = findViewById(R.id.overflowMenu)
        searchView = findViewById(R.id.searchView)
        tagLayout = findViewById(R.id.tagLayout)
        tagToggleButton = findViewById(R.id.tagToggleButton) // Initialize the tag toggle button

        loadEntries()
        loadTags()  // Load and display tags

        // Set the custom cursor drawable for the search EditText
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            searchView.textCursorDrawable =
                ContextCompat.getDrawable(this, R.drawable.cursor_drawable)
        }

        overflowMenu.setOnClickListener { showPopupMenu(overflowMenu) }

        // Toggle the visibility of the tag layout when the "T" button is clicked
        tagToggleButton.setOnClickListener {
            if (tagLayout.visibility == View.VISIBLE) {
                tagLayout.visibility = View.GONE
            } else {
                tagLayout.visibility = View.VISIBLE
            }
        }

        // Add text change listener to searchView
        searchView.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {}

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                entryListAdapter?.filter(s.toString())
            }
        })

        // Show keyboard when activity starts
        searchView.requestFocus()
        searchView.postDelayed({
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchView, InputMethodManager.SHOW_IMPLICIT)
        }, 200)

        // Optionally, handle focus change to show the keyboard again
        searchView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                showKeyboard(searchView)
            }
        }
    }

    private fun loadTags() {
        val tagsList = TagUtils.loadTags(this) // Assume you have this utility to load tags
        if (tagsList.isNotEmpty()) {
            tagLayout.visibility = View.GONE // Start with the tag layout hidden
        }
        populateTagButtons(tagsList)
    }

    private fun populateTagButtons(tagsList: List<String>) {
        tagLayout.removeAllViews()  // Clear existing buttons
        for (tag in tagsList) {
            val tagButton = Button(this)
            tagButton.text = tag

            // Set the layout parameters to make the button fill the available space
            val params = LinearLayout.LayoutParams(
                0,  // Width set to 0dp
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.weight = 1.0f  // Equal weight to distribute space equally among buttons

            tagButton.layoutParams = params

            tagButton.setOnClickListener { onTagClicked(tagButton) }
            tagLayout.addView(tagButton)
        }
    }


    private fun onTagClicked(tagButton: Button) {
        val tag = tagButton.text.toString()
        if (selectedTags.contains(tag)) {
            selectedTags.remove(tag)
            tagButton.setBackgroundColor(ContextCompat.getColor(this, R.color.buttonColor))
        } else {
            selectedTags.add(tag)
            tagButton.setBackgroundColor(ContextCompat.getColor(this, R.color.buttonDisabledColor))
        }
        filterEntriesByTags()
    }

    private fun filterEntriesByTags() {
        entryListAdapter?.filterByTags(selectedTags)
    }

    private fun showPopupMenu(view: View) {
        // Create a PopupMenu
        val popupMenu = PopupMenu(this, view)
        val inflater: MenuInflater = popupMenu.menuInflater
        inflater.inflate(R.menu.popup_menu, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.new_entry -> { // Handle the new entry case
                    createNewEntry()
                    true
                }
                R.id.select_all_shown -> {
                    toggleSelectAllShownEntries()
                    true
                }
                R.id.delete_selected -> {
                    deleteSelectedEntries()
                    true
                }
                R.id.settings -> {
                    val intent = Intent(this, SettingsActivity::class.java)
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun createNewEntry() {
        val intent = Intent(this, EntryEditorActivity::class.java)
        intent.putExtra("new_entry", true)
        startActivity(intent)
    }

    private fun toggleSelectAllShownEntries() {
        // Get the list of currently visible entries
        val visibleEntries = entryListAdapter?.getVisibleEntries() ?: emptyList()

        // Check if all visible entries are selected
        val allSelected = visibleEntries.all { selectedEntries.contains(it.created) }

        if (allSelected) {
            // If all are selected, unselect them
            visibleEntries.forEach { entry ->
                entry.created?.let { selectedEntries.remove(it) }
            }
        } else {
            // If not all are selected, select them all
            visibleEntries.forEach { entry ->
                entry.created?.let { selectedEntries.add(it) }
            }
        }

        // Notify the adapter to update the checkboxes
        entryListAdapter?.notifyDataSetChanged()
    }

    override fun onPause() {
        super.onPause()
        AppUsageUtils.onPause(this)
    }

    override fun onResume() {
        super.onResume()
        AppUsageUtils.onResume(this) {
            val intent = Intent(this, EntryEditorActivity::class.java)
            intent.putExtra("new_entry", true)
            startActivity(intent)
            finish()
        }
        loadEntries()
    }

    private fun loadEntries() {
        val jsonFile = File(filesDir, "entries_log.json")
        if (jsonFile.exists()) {
            try {
                val json = FileReader(jsonFile).use { it.readText() }
                val gson = GsonBuilder().create()
                val listType = object : TypeToken<List<EntryEditorActivity.EntryData>>() {}.type
                EntryEditorActivity.entries = gson.fromJson<List<EntryEditorActivity.EntryData>>(json, listType).toMutableList()
                EntryEditorActivity.entries.sortByDescending { it.modified }
            } catch (e: Exception) {
                Log.e("EntryOperation", "Error reading JSON file", e)
                EntryEditorActivity.entries = mutableListOf()
            }
        } else {
            EntryEditorActivity.entries = mutableListOf()
        }

        entryListAdapter = EntryListAdapter(EntryEditorActivity.entries)
        entryListView.adapter = entryListAdapter
    }

    private fun deleteSelectedEntries() {
        EntryEditorActivity.entries.removeAll { selectedEntries.contains(it.created) }
        updateEntriesJson()
        loadEntries()
        selectedEntries.clear()
    }

    private fun updateEntriesJson() {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val json = gson.toJson(EntryEditorActivity.entries)
        val jsonFile = File(filesDir, "entries_log.json")
        try {
            FileWriter(jsonFile).use {
                it.write(json)
            }
        } catch (e: Exception) {
            Log.e("EntryOperation", "Error writing JSON file", e)
        }
    }

    private fun shareEntriesJson() {
        updateEntriesJson()
        val jsonFile = File(filesDir, "entries_log.json")
        val uri: Uri = FileProvider.getUriForFile(this, "com.example.journal.provider", jsonFile)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, "Share JSON file"))
    }

    private fun showKeyboard(view: View) {
        view.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    inner class EntryListAdapter(private var entries: List<EntryEditorActivity.EntryData>) :
        BaseAdapter() {

        private var filteredEntries: List<EntryEditorActivity.EntryData> = entries

        override fun getCount(): Int {
            return filteredEntries.size
        }

        override fun getItem(position: Int): Any {
            return filteredEntries[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view: View =
                convertView ?: layoutInflater.inflate(R.layout.list_item, parent, false)

            val entryModifiedDateTextView: TextView = view.findViewById(R.id.entryModifiedDateTextView)
            val entryTagsTextView: TextView = view.findViewById(R.id.entryTagsTextView)
            val entryContentTextView: TextView = view.findViewById(R.id.entryContentTextView)
            val selectCheckBox: CheckBox = view.findViewById(R.id.selectCheckBox)

            val imageViews = listOf(
                view.findViewById<ImageView>(R.id.entryImageView1),
                view.findViewById<ImageView>(R.id.entryImageView2),
                view.findViewById<ImageView>(R.id.entryImageView3),
                view.findViewById<ImageView>(R.id.entryImageView4)
            )

            val entryData = filteredEntries[position]
            val createdText = entryData.created ?: "Unknown"
            val modifiedText = entryData.modified?.let { formatTimeDifference(it) } ?: "Unknown"

            // Determine coordinates text
            val coordsText = when {
                !entryData.coords.isNullOrEmpty() -> entryData.coords
                !entryData.last_coords.isNullOrEmpty() -> "≈ ${entryData.last_coords}"
                else -> ""
            }

            // Set combined text for Modified Date, Created Date, and Coordinates
            entryModifiedDateTextView.text = "$modifiedText | ${formatDateWithoutSeconds(createdText)} | $coordsText".trimEnd()

            // Set tags as a comma-separated string if there are any
            if (entryData.tags.isNotEmpty()) {
                entryTagsTextView.text = entryData.tags.joinToString(", ")
                entryTagsTextView.visibility = View.VISIBLE
            } else {
                entryTagsTextView.visibility = View.GONE
            }

            entryContentTextView.text = createPreviewText(entryData.content)

            // Clear all ImageViews initially
            imageViews.forEach { it.visibility = View.GONE }

            // Extract and display up to 4 images
            val imageUrls = extractImageUrls(entryData.content).take(4)
            imageUrls.forEachIndexed { index, imageUrl ->
                imageViews[index].visibility = View.VISIBLE
                Glide.with(view.context)
                    .load(Uri.parse(imageUrl))
                    .into(imageViews[index])
            }

            view.setOnClickListener {
                val intent = Intent(this@EntryListActivity, EntryEditorActivity::class.java)
                intent.putExtra("entryId", createdText)
                startActivity(intent)
            }

            selectCheckBox.setOnCheckedChangeListener(null)
            selectCheckBox.isChecked = selectedEntries.contains(createdText)
            selectCheckBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedEntries.add(createdText)
                } else {
                    selectedEntries.remove(createdText)
                }
            }

            selectCheckBox.setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    v.performClick()
                }
                true
            }

            return view
        }

        private fun createPreviewText(content: String): String {
            val cleanedContent = removeImageMarkdown(content).trimEnd()
            val lines = cleanedContent.split("\n").take(4)
            var preview = lines.joinToString("\n").take(120)

            preview = preview.trimEnd()

            val originalLength = cleanedContent.length
            val previewLength = preview.length
            if (originalLength > previewLength) {
                preview = preview.trimEnd().removeSuffix("\n") + "…"
            }

            return preview
        }

        private fun removeImageMarkdown(content: String): String {
            val regex = Regex("!\\[.*?\\]\\((file://.*?)\\)")
            return content.replace(regex, "").trim()
        }

        private fun formatTimeDifference(modifiedDate: String): String {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = dateFormat.parse(modifiedDate)
            val localDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val diff = Date().time - date.time

            val days = TimeUnit.MILLISECONDS.toDays(diff)
            val hours = TimeUnit.MILLISECONDS.toHours(diff) % 24
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60

            return if (days > 0) {
                String.format("%d days ago", days)
            } else {
                String.format("%02d:%02d ago", hours, minutes)
            }
        }

        private fun formatDateWithoutSeconds(dateStr: String): String {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val date = inputFormat.parse(dateStr)
            outputFormat.timeZone = TimeZone.getDefault()
            return outputFormat.format(date)
        }

        private fun extractImageUrls(content: String): List<String> {
            val regex = Regex("!\\[.*?\\]\\((file://.*?)\\)")
            return regex.findAll(content).map { it.groups[1]?.value ?: "" }
                .filter { it.isNotEmpty() }.toList()
        }

        fun filter(query: String?) {
            if (query.isNullOrEmpty()) {
                filteredEntries = entries
            } else {
                filteredEntries = entries.filter { entry ->
                    entry.content.contains(query, ignoreCase = true)
                }
            }
            notifyDataSetChanged()
        }

        fun filterByTags(selectedTags: Set<String>) {
            if (selectedTags.isEmpty()) {
                filteredEntries = entries
            } else {
                filteredEntries = entries.filter { entry ->
                    entry.tags.any { it in selectedTags }
                }
            }
            notifyDataSetChanged()
        }

        fun getVisibleEntries(): List<EntryEditorActivity.EntryData> {
            return filteredEntries
        }
    }

}
