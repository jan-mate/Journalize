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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var editText: EditText
    private lateinit var viewFilesButton: Button
    private lateinit var newFileButton: Button
    private lateinit var lastFileButton: Button
    private lateinit var currentFileTextView: TextView

    private var currentFilename: String? = null
    private val recentFiles = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editText = findViewById(R.id.editText)
        viewFilesButton = findViewById(R.id.viewFilesButton)
        newFileButton = findViewById(R.id.newFileButton)
        lastFileButton = findViewById(R.id.lastFileButton)
        currentFileTextView = findViewById(R.id.currentFileTextView)

        viewFilesButton.setOnClickListener {
            val intent = Intent(this, FileListActivity::class.java)
            startActivityForResult(intent, 1)
        }

        newFileButton.setOnClickListener {
            createNewFile()
            editText.text.clear()
        }

        lastFileButton.setOnClickListener {
            openPreviousFile()
        }

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                saveTextToFile(s.toString())
            }
        })

        createNewFile()
        showKeyboard(editText)
    }

    private fun createNewFile() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
        val currentDateTime = dateFormat.format(Date())
        currentFilename = "$currentDateTime.txt"
        currentFileTextView.text = "Editing: $currentFilename"
        currentFileTextView.visibility = TextView.VISIBLE
        Log.d("FileOperation", "Created new file: $currentFilename")
    }

    private fun saveTextToFile(text: String) {
        if (text.isNotEmpty()) {
            val filename = currentFilename ?: createNewFileName()
            Log.d("FileOperation", "Saving to file: $filename")
            openFileOutput(filename, Context.MODE_PRIVATE).use {
                it.write(text.toByteArray(Charsets.UTF_8))
            }
            if (!recentFiles.contains(filename)) {
                recentFiles.add(0, filename) // Add to the start of the list
                if (recentFiles.size > 10) {
                    recentFiles.removeAt(10) // Keep the list to the last 10 files
                }
            }
            currentFilename = filename
            currentFileTextView.text = "Editing: $filename"
            currentFileTextView.visibility = TextView.VISIBLE
        }
    }

    private fun createNewFileName(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
        return "${dateFormat.format(Date())}.txt"
    }

    private fun openFile(filename: String) {
        currentFilename = filename
        val file = File(filesDir, filename)
        val text = file.readText(Charsets.UTF_8)
        editText.setText(text)
        currentFileTextView.text = "Editing: $filename"
        currentFileTextView.visibility = TextView.VISIBLE
        Log.d("FileOperation", "Opened file: $filename")
    }

    private fun openPreviousFile() {
        if (recentFiles.size > 1) {
            val lastEditedFile = recentFiles[1]
            openFile(lastEditedFile)
            recentFiles.removeAt(1) // Move it to the top of the list
            recentFiles.add(0, lastEditedFile)
        } else if (recentFiles.size == 1) {
            openFile(recentFiles[0])
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
            data?.getStringExtra("filename")?.let { filename ->
                openFile(filename)
                if (!recentFiles.contains(filename)) {
                    recentFiles.add(0, filename)
                    if (recentFiles.size > 10) {
                        recentFiles.removeAt(10)
                    }
                }
            }
        }
    }
}
