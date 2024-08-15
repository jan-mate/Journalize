package com.example.journal

import android.annotation.SuppressLint
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
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class EntryListActivity : AppCompatActivity() {

    private lateinit var entryListView: ListView
    private lateinit var overflowMenu: ImageView
    private lateinit var searchView: EditText
    private lateinit var tagLayout: LinearLayout
    private lateinit var tagToggleButton: ImageButton
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
        tagToggleButton = findViewById(R.id.tagToggleButton)

        loadEntries()
        loadTags()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            searchView.textCursorDrawable =
                ContextCompat.getDrawable(this, R.drawable.cursor_drawable)
        }

        overflowMenu.setOnClickListener { showPopupMenu(overflowMenu) }

        tagToggleButton.setOnClickListener {
            if (tagLayout.visibility == View.VISIBLE) {
                tagLayout.visibility = View.GONE
            } else {
                tagLayout.visibility = View.VISIBLE
            }
        }

        searchView.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {}

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                entryListAdapter?.filter(s.toString())
            }
        })

        searchView.requestFocus()
        searchView.postDelayed({
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchView, InputMethodManager.SHOW_IMPLICIT)
        }, 200)

        searchView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                KeyboardUtils.showKeyboard(this, searchView)
            }
        }
    }

    private fun loadTags() {
        val tagsList = TagUtils.loadTags(this)
        if (tagsList.isNotEmpty()) {
            tagLayout.visibility = View.GONE
        }
        populateTagButtons(tagsList)
    }

    private fun populateTagButtons(tagsList: List<String>) {
        tagLayout.removeAllViews()
        for (tag in tagsList) {
            val tagButton = Button(this)
            tagButton.text = tag

            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.weight = 1.0f

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
            tagButton.setBackgroundColor(ContextCompat.getColor(this, R.color.highlightColor))
        }
        filterEntriesByTags()
    }

    private fun filterEntriesByTags() {
        entryListAdapter?.filterByTags(selectedTags)
    }

    private fun showPopupMenu(view: View) {

        val popupMenu = PopupMenu(this, view)
        val inflater: MenuInflater = popupMenu.menuInflater
        inflater.inflate(R.menu.popup_menu, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.new_entry -> {
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
        val visibleEntries = entryListAdapter?.getVisibleEntries() ?: emptyList()

        val allSelected = visibleEntries.all { selectedEntries.contains(it.created) }

        if (allSelected) {
            visibleEntries.forEach { entry ->
                entry.created.let { selectedEntries.remove(it) }
            }
        } else {
            visibleEntries.forEach { entry ->
                entry.created.let { selectedEntries.add(it) }
            }
        }

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
        val jsonFile = File(filesDir, "entries.json")
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
        val jsonFile = File(filesDir, "entries.json")
        try {
            FileWriter(jsonFile).use {
                it.write(json)
            }
        } catch (e: Exception) {
            Log.e("EntryOperation", "Error writing JSON file", e)
        }
    }

    inner class EntryListAdapter(private var entries: List<EntryEditorActivity.EntryData>) :
        BaseAdapter() {

        private var filteredEntries: List<EntryEditorActivity.EntryData> = entries
        private var currentQuery: String? = null
        private var currentSelectedTags: Set<String> = emptySet()

        override fun getCount(): Int {
            return filteredEntries.size
        }

        override fun getItem(position: Int): Any {
            return filteredEntries[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view: View =
                convertView ?: layoutInflater.inflate(R.layout.list_item, parent, false)

            val entryModifiedDateTextView: TextView = view.findViewById(R.id.entryModifiedDateTextView)
            val entryTagsTextView: TextView = view.findViewById(R.id.entryTagsTextView)
            val entryContentTextView: TextView = view.findViewById(R.id.entryContentTextView)
            val selectCheckBox: CheckBox = view.findViewById(R.id.selectCheckBox)

            val imageViews = listOf(
                view.findViewById<ImageView>(R.id.entryImageView1),
                view.findViewById(R.id.entryImageView2),
                view.findViewById(R.id.entryImageView3),
                view.findViewById(R.id.entryImageView4)
            )

            val entryData = filteredEntries[position]
            val createdText = entryData.created
            val modifiedText = entryData.modified?.let { formatTimeDifference(it) } ?: "Unknown"

            val coordsText = when {
                !entryData.coords.isNullOrEmpty() -> entryData.coords
                !entryData.last_coords.isNullOrEmpty() -> "≈ ${entryData.last_coords}"
                else -> ""
            }

            entryModifiedDateTextView.text = "$modifiedText | ${formatDateWithoutSeconds(createdText)} | $coordsText".trimEnd()

            if (entryData.tags.isNotEmpty()) {
                entryTagsTextView.text = entryData.tags.joinToString(", ")
                entryTagsTextView.visibility = View.VISIBLE
            } else {
                entryTagsTextView.visibility = View.GONE
            }

            entryContentTextView.text = createPreviewText(entryData.content)

            imageViews.forEach { it.visibility = View.GONE }

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
                intent.putExtra("fromMenu", true)  // Adjust this according to context
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
            val regex = Regex("!\\[.*?]\\((file://.*?)\\)")
            return content.replace(regex, "").trim()
        }

        @SuppressLint("DefaultLocale")
        private fun formatTimeDifference(modifiedDate: String): String {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = dateFormat.parse(modifiedDate)
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val diff = Date().time - date!!.time

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
            return outputFormat.format(date!!)
        }

        private fun extractImageUrls(content: String): List<String> {
            val regex = Regex("!\\[.*?]\\((file://.*?)\\)")
            return regex.findAll(content).map { it.groups[1]?.value ?: "" }
                .filter { it.isNotEmpty() }.toList()
        }

        fun filter(query: String?) {
            currentQuery = query
            applyFilters()
        }

        fun filterByTags(selectedTags: Set<String>) {
            currentSelectedTags = selectedTags
            applyFilters()
        }

        private fun applyFilters() {
            filteredEntries = entries.filter { entry ->
                val matchesQuery = currentQuery.isNullOrEmpty() || entry.content.lowercase().let { content ->
                    val normalizedQuery = currentQuery!!
                        .replace("∧", "AND")
                        .replace("∨", "OR")
                        .trim()

                    val orSegments = normalizedQuery.split("OR").map { it.trim() }

                    orSegments.any { segment ->
                        val andTerms = segment.split("AND").map { it.trim() }

                        andTerms.all { term ->
                            content.contains(term.lowercase())
                        }
                    }
                }

                val matchesTags = currentSelectedTags.isEmpty() || entry.tags.any { it in currentSelectedTags }

                matchesQuery && matchesTags
            }
            notifyDataSetChanged()
        }

        fun getVisibleEntries(): List<EntryEditorActivity.EntryData> {
            return filteredEntries
        }
    }

}
