package com.example.journal

import android.app.Activity
import android.content.Context
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
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class FileListActivity : AppCompatActivity() {

    private lateinit var fileListView: ListView
    private lateinit var deleteButton: Button
    private lateinit var exportButton: Button
    private lateinit var viewJsonButton: Button
    private var selectedFiles = mutableListOf<String>()
    private var files = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_list)

        fileListView = findViewById(R.id.fileListView)
        deleteButton = findViewById(R.id.deleteButton)
        exportButton = findViewById(R.id.exportButton)
        viewJsonButton = findViewById(R.id.viewJsonButton)

        loadFileList()

        deleteButton.setOnClickListener {
            deleteSelectedFiles()
        }

        exportButton.setOnClickListener {
            shareJsonFile()
        }

        viewJsonButton.setOnClickListener {
            val intent = Intent(this, ViewJsonActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadFileList() {
        files = filesDir.listFiles()?.map { it.name }
            ?.filter { !it.contains("profileInstalled") && it != "files_log.json" }
            ?.sortedDescending()?.toMutableList() ?: mutableListOf()
        val adapter = FileListAdapter(files)
        fileListView.adapter = adapter
        deleteButton.isEnabled = false
    }

    private fun deleteSelectedFiles() {
        selectedFiles.forEach { filename ->
            val file = File(filesDir, filename)
            if (file.exists()) {
                file.delete()
                updateJsonFile()
            }
        }
        loadFileList()
        selectedFiles.clear()
        deleteButton.isEnabled = false
    }

    private fun updateJsonFile() {
        val fileList = filesDir.listFiles()?.filter {
            !it.name.contains("profileInstalled") && it.name != "files_log.json"
        }?.map { file ->
            FileData(file.name, file.lastModified(), file.readText(Charsets.UTF_8))
        } ?: listOf()

        val gson = GsonBuilder().setPrettyPrinting().create()
        val json = gson.toJson(fileList)

        val jsonFile = File(filesDir, "files_log.json")
        FileWriter(jsonFile).use {
            it.write(json)
        }
    }


    private fun shareJsonFile() {
        updateJsonFile()
        val jsonFile = File(filesDir, "files_log.json")
        val uri: Uri = FileProvider.getUriForFile(this, "com.example.journal.provider", jsonFile)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, "Share JSON file"))
    }

    inner class FileListAdapter(private val files: List<String>) : BaseAdapter() {

        override fun getCount(): Int {
            return files.size
        }

        override fun getItem(position: Int): Any {
            return files[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view: View = convertView ?: layoutInflater.inflate(R.layout.file_list_item, parent, false)
            val fileNameTextView: TextView = view.findViewById(R.id.fileNameTextView)
            val filePreviewTextView: TextView = view.findViewById(R.id.filePreviewTextView)
            val selectCheckBox: CheckBox = view.findViewById(R.id.selectCheckBox)

            val filename = files[position]
            val displayName = filename.removeSuffix(".txt")
            fileNameTextView.text = displayName

            val fileContent = File(filesDir, filename).readText()
            val previewText = createPreviewText(fileContent)
            filePreviewTextView.text = previewText

            view.setOnClickListener {
                val resultIntent = Intent()
                resultIntent.putExtra("filename", filename)
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }

            selectCheckBox.setOnCheckedChangeListener(null)
            selectCheckBox.isChecked = selectedFiles.contains(filename)
            selectCheckBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedFiles.add(filename)
                } else {
                    selectedFiles.remove(filename)
                }
                deleteButton.isEnabled = selectedFiles.isNotEmpty()
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

    data class FileData(val name: String, val lastModified: Long, val content: String)
}
