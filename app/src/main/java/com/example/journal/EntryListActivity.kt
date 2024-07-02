package com.example.journal

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.appcompat.widget.SearchView
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
    private lateinit var deleteButton: Button
    private lateinit var exportButton: Button
    private lateinit var viewJsonButton: Button
    private lateinit var searchView: SearchView
    private var selectedEntries = mutableListOf<String>()
    private var entryListAdapter: EntryListAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)

        entryListView = findViewById(R.id.fileListView)
        deleteButton = findViewById(R.id.deleteButton)
        exportButton = findViewById(R.id.exportButton)
        viewJsonButton = findViewById(R.id.viewJsonButton)
        searchView = findViewById(R.id.searchView)

        loadEntries()

        deleteButton.setOnClickListener {
            deleteSelectedEntries()
        }

        exportButton.setOnClickListener {
            shareEntriesJson()
        }

        viewJsonButton.setOnClickListener {
            val intent = Intent(this, ViewJsonActivity::class.java)
            startActivity(intent)
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                entryListAdapter?.filter(query)
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                entryListAdapter?.filter(newText)
                return false
            }
        })

        // Show keyboard by default when SearchView is focused
        searchView.setOnClickListener {
            showKeyboard(searchView)
        }

        // Request focus on SearchView and show keyboard by default
        searchView.requestFocus()
        showKeyboard(searchView)
    }

    private fun showKeyboard(view: View) {
        view.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
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
        deleteButton.isEnabled = false
    }

    private fun deleteSelectedEntries() {
        EntryEditorActivity.entries.removeAll { selectedEntries.contains(it.created) }
        updateEntriesJson()
        loadEntries()
        selectedEntries.clear()
        deleteButton.isEnabled = false
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

    inner class EntryListAdapter(private var entries: List<EntryEditorActivity.EntryData>) : BaseAdapter() {

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
            val view: View = convertView ?: layoutInflater.inflate(R.layout.list_item, parent, false)
            val entryModifiedDateTextView: TextView = view.findViewById(R.id.entryModifiedDateTextView)
            val entryCreatedDateTextView: TextView = view.findViewById(R.id.entryCreatedDateTextView)
            val entryContentTextView: TextView = view.findViewById(R.id.entryContentTextView)
            val selectCheckBox: CheckBox = view.findViewById(R.id.selectCheckBox)

            val entryData = filteredEntries[position]
            val createdText = entryData.created ?: "Unknown"
            val modifiedText = entryData.modified?.let { formatTimeDifference(it) } ?: "Unknown"

            entryModifiedDateTextView.text = modifiedText
            entryCreatedDateTextView.text = formatDateWithoutSeconds(createdText)
            entryContentTextView.text = createPreviewText(entryData.content)

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
                deleteButton.isEnabled = selectedEntries.isNotEmpty()
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
            val lines = content.split("\n").take(4)
            val preview = lines.joinToString("\n").take(120)
            return if (content.length > 150 || lines.size > 3) "$preview…" else preview
        }

        private fun formatTimeDifference(modifiedDate: String): String {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
            val date = dateFormat.parse(modifiedDate)
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
            val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val date = inputFormat.parse(dateStr)
            return outputFormat.format(date)
        }

        fun filter(query: String?) {
            if (query.isNullOrEmpty()) {
                filteredEntries = entries
            } else {
                val conditions = query.split(" ")
                val filteredList = entries.filter { entry ->
                    var match = true
                    var useAnd = true
                    for (condition in conditions) {
                        when (condition.uppercase()) {
                            "AND", "∧", "\\LAND" -> useAnd = true
                            "OR", "∨", "\\LOR" -> useAnd = false
                            else -> {
                                val contains = entry.content.contains(condition, ignoreCase = true)
                                match = if (useAnd) match && contains else match || contains
                            }
                        }
                    }
                    match
                }
                filteredEntries = filteredList
            }
            notifyDataSetChanged()
        }
    }

}
