package com.example.journal

import android.content.Context
import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object EntryDataUtils {

    fun loadEntries(context: Context): MutableList<EntryEditorActivity.EntryData> {
        val jsonFile = File(context.filesDir, "entries_log.json")
        return if (jsonFile.exists()) {
            try {
                val json = FileReader(jsonFile).use { it.readText() }
                val gson = GsonBuilder().create()
                val listType = object : TypeToken<List<EntryEditorActivity.EntryData>>() {}.type
                val loadedEntries: List<EntryEditorActivity.EntryData> = gson.fromJson(json, listType)

                // Ensure tags field is initialized
                loadedEntries.map { entry ->
                    entry.apply {
                    }
                }.toMutableList()
            } catch (e: Exception) {
                Log.e("EntryOperation", "Error reading JSON file", e)
                mutableListOf()
            }
        } else {
            mutableListOf()
        }
    }

    fun updateEntriesJson(context: Context, entries: List<EntryEditorActivity.EntryData>) {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val nonEmptyEntries = entries.filter { it.content.isNotEmpty() }
        val json = gson.toJson(nonEmptyEntries)
        val jsonFile = File(context.filesDir, "entries_log.json")
        try {
            FileWriter(jsonFile).use {
                it.write(json)
            }
        } catch (e: Exception) {
            Log.e("EntryOperation", "Error writing JSON file", e)
        }
    }

    fun getCurrentTimeString(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        return dateFormat.format(Date())
    }

    fun updateModifiedTime(entryData: EntryEditorActivity.EntryData) {
        entryData.modified = getCurrentTimeString()
    }
}
