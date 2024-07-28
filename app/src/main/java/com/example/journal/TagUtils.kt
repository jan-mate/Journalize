package com.example.journal

import android.content.Context
import android.graphics.Color
import android.widget.Button
import android.widget.LinearLayout

object TagUtils {

    fun initializeTagButtons(context: Context, tagLayout: LinearLayout, tags: List<String>, onClick: (String, Button) -> Unit) {
        tagLayout.removeAllViews()

        for (tag in tags) {
            val tagButton = Button(context).apply {
                text = tag
                setBackgroundColor(Color.parseColor("#AFAFAF"))
                setTextColor(Color.WHITE)
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                setPadding(0, 0, 0, 0)
                minHeight = 0
                height = LinearLayout.LayoutParams.WRAP_CONTENT
                setOnClickListener { onClick(tag, this) }
            }
            tagLayout.addView(tagButton)
        }
    }

    fun updateTagButtons(tagLayout: LinearLayout, tags: List<String>?) {
        for (i in 0 until tagLayout.childCount) {
            val tagButton = tagLayout.getChildAt(i) as Button
            if (tags != null && tags.contains(tagButton.text.toString())) {
                tagButton.setBackgroundColor(Color.parseColor("#999999"))
            } else {
                tagButton.setBackgroundColor(Color.parseColor("#AFAFAF"))
            }
        }
    }

    fun toggleTag(entries: MutableList<EntryEditorActivity.EntryData>, currentEntryId: String?, tag: String, button: Button, updateEntriesJson: () -> Unit) {
        val entryData = entries.find { it.created == currentEntryId }
        entryData?.let {
            if (it.tags.contains(tag)) {
                it.tags.remove(tag)
                button.setBackgroundColor(Color.parseColor("#AFAFAF"))
            } else {
                it.tags.add(tag)
                button.setBackgroundColor(Color.parseColor("#999999"))

                if (it.content.isEmpty() && it.modified == null) {
                    EntryDataUtils.updateModifiedTime(it)
                }
            }
            updateEntriesJson()
        }
    }
}
