package com.example.journal

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter

class EntryListActivity : AppCompatActivity() {

    private lateinit var entryListView: ListView
    private lateinit var deleteButton: Button
    private lateinit var exportButton: Button
    private lateinit var viewJsonButton: Button
    private var selectedEntries = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)

        entryListView = findViewById(R.id.fileListView)
        deleteButton = findViewById(R.id.deleteButton)
        exportButton = findViewById(R.id.exportButton)
        viewJsonButton = findViewById(R.id.viewJsonButton)

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
    }

    private fun loadEntries() {
        val jsonFile = File(filesDir, "entries_log.json")
        if (jsonFile.exists()) {
            try {
                val json = FileReader(jsonFile).use { it.readText() }
                val gson = GsonBuilder().create()
                val listType = object : TypeToken<List<EntryEditorActivity.EntryData>>() {}.type
                EntryEditorActivity.entries = gson.fromJson<List<EntryEditorActivity.EntryData>>(json, listType).toMutableList()  // Explicitly specify type
                EntryEditorActivity.entries.sortByDescending { it.modified }
            } catch (e: Exception) {
                Log.e("EntryOperation", "Error reading JSON file", e)
                EntryEditorActivity.entries = mutableListOf()
            }
        } else {
            EntryEditorActivity.entries = mutableListOf()
        }

        val adapter = EntryListAdapter(EntryEditorActivity.entries)
        entryListView.adapter = adapter
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

    inner class EntryListAdapter(private val entries: List<EntryEditorActivity.EntryData>) : BaseAdapter() {

        override fun getCount(): Int {
            return entries.size
        }

        override fun getItem(position: Int): Any {
            return entries[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view: View = convertView ?: layoutInflater.inflate(R.layout.list_item, parent, false)
            val entryNameTextView: TextView = view.findViewById(R.id.fileNameTextView)
            val entryPreviewTextView: TextView = view.findViewById(R.id.filePreviewTextView)
            val selectCheckBox: CheckBox = view.findViewById(R.id.selectCheckBox)

            val entryData = entries[position]
            val createdText = entryData.created ?: "Unknown"
            entryNameTextView.text = createdText
            entryPreviewTextView.text = createPreviewText(entryData.content)

            view.setOnClickListener {
                val intent = Intent()
                intent.putExtra("id", createdText)
                setResult(Activity.RESULT_OK, intent)
                finish()
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
            val lines = content.split("\n").take(2)
            val preview = lines.joinToString("\n").take(100)
            return if (content.length > 100 || lines.size > 2) "$preview..." else preview
        }
    }
}
