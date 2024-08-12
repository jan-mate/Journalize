package com.example.journal

import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object TagUtils {

    private val defaultTags = listOf("Do", "φ", "“…“", "Book", "Grace")

    fun initializeTagButtons(
        context: Context,
        tagLayout: LinearLayout,
        tags: List<String>,
        onClick: (String, Button) -> Unit
    ) {
        tagLayout.removeAllViews()

        val buttonColor = ContextCompat.getColor(context, R.color.buttonColor)
        val buttonTextColor = ContextCompat.getColor(context, R.color.buttonTextColor)

        for (tag in tags) {
            val tagButton = Button(context).apply {
                text = tag
                setBackgroundColor(buttonColor)
                setTextColor(buttonTextColor)
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f
                )
                setPadding(8, 2, 8, 2)
                minHeight = 36
                setOnClickListener { onClick(tag, this) }
            }
            tagLayout.addView(tagButton)
        }
    }

    fun updateTagButtons(tagLayout: LinearLayout, tags: List<String>?) {
        val buttonColor = ContextCompat.getColor(tagLayout.context, R.color.buttonColor)
        val tagButtonSelectedColor = ContextCompat.getColor(tagLayout.context, R.color.colorAccent)

        for (i in 0 until tagLayout.childCount) {
            val tagButton = tagLayout.getChildAt(i) as Button
            if (tags != null && tags.contains(tagButton.text.toString())) {
                tagButton.setBackgroundColor(tagButtonSelectedColor)
            } else {
                tagButton.setBackgroundColor(buttonColor)
            }
            tagButton.alpha = 1.0f
        }
    }

    fun toggleTag(
        entries: MutableList<EntryEditorActivity.EntryData>,
        currentEntryId: String?,
        tag: String,
        button: Button,
        updateEntriesJson: () -> Unit
    ) {
        val buttonColor = ContextCompat.getColor(button.context, R.color.buttonColor)
        val tagButtonSelectedColor = ContextCompat.getColor(button.context, R.color.colorAccent)

        val entryData = entries.find { it.created == currentEntryId }
        entryData?.let {
            if (it.tags.contains(tag)) {
                it.tags.remove(tag)
                button.setBackgroundColor(buttonColor)
            } else {
                it.tags.add(tag)
                button.setBackgroundColor(tagButtonSelectedColor)

                if (it.content.isEmpty() && it.modified == null) {
                    EntryDataUtils.updateModifiedTime(it)
                }
            }
            button.alpha = 1.0f
            updateEntriesJson()
        }
    }

    fun loadTags(context: Context): List<String> {
        val sharedPreferences = context.getSharedPreferences("com.example.journal", Context.MODE_PRIVATE)
        val tagsJson = sharedPreferences.getString("tags", null)

        return if (tagsJson.isNullOrEmpty()) {
            defaultTags
        } else {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(tagsJson, type)
        }
    }

    fun saveTags(context: Context, tags: List<String>) {
        val sharedPreferences = context.getSharedPreferences("com.example.journal", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val tagsJson = Gson().toJson(tags)
        editor.putString("tags", tagsJson)
        editor.apply()
    }
}
