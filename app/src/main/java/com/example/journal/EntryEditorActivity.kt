package com.example.journal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var currentEntryTextView: TextView

    companion object {
        var entries = mutableListOf<EntryData>()
    }

    private var currentEntryId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editText = findViewById(R.id.editText)
        viewFilesButton = findViewById(R.id.viewFilesButton)
        newEntryButton = findViewById(R.id.newEntryButton)
        lastEntryButton = findViewById(R.id.lastEntryButton)
        currentEntryTextView = findViewById(R.id.currentEntryTextView)

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

    private fun loadEntries() {
        val jsonFile = File(filesDir, "entries_log.json")
        if (jsonFile.exists()) {
            try {
                val json = FileReader(jsonFile).use { it.readText() }
                val gson = GsonBuilder().create()
                val listType = object : TypeToken<List<EntryData>>() {}.type
                entries = gson.fromJson<List<EntryData>>(json, listType).toMutableList()  // Explicitly specify type
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) {
            data?.getStringExtra("id")?.let { id ->
                openEntry(id)
            }
        }
    }

    data class EntryData(val created: String, var modified: String, var content: String)
}
