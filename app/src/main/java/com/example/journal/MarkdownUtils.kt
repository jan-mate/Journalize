package com.example.journal

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.file.FileSchemeHandler

object MarkdownUtils {

    fun updateTextStyles(editText: EditText) {
        val spannable = editText.text as SpannableStringBuilder
        val lines = spannable.split("\n")

        clearPreviousStyles(spannable)

        var start = 0

        for (line in lines) {
            val end = start + line.length
            when {
                line.startsWith("# ") -> {
                    applyHeaderStyles(spannable, start, end, 1.6f)
                }
                line.startsWith("## ") -> {
                    applyHeaderStyles(spannable, start, end, 1.5f)
                }
                line.startsWith("### ") -> {
                    applyHeaderStyles(spannable, start, end, 1.4f)
                }
                line.startsWith("#### ") -> {
                    applyHeaderStyles(spannable, start, end, 1.3f)
                }
                line.startsWith("##### ") -> {
                    applyHeaderStyles(spannable, start, end, 1.2f)
                }
                line.startsWith("###### ") -> {
                    applyHeaderStyles(spannable, start, end, 1.1f)
                }
                line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ") -> {
                    // No special style application needed, handled like a regular line
                }
                else -> spannable.setSpan(
                    android.text.style.RelativeSizeSpan(1f),
                    start,
                    end,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            applyInlineStyles(line, spannable, start)

            start = end + 1
        }
    }

    private fun clearPreviousStyles(spannable: SpannableStringBuilder) {
        val spans = listOf(
            android.text.style.RelativeSizeSpan::class.java,
            android.text.style.UnderlineSpan::class.java,
            android.text.style.StyleSpan::class.java,
            android.text.style.TypefaceSpan::class.java,
            android.text.style.StrikethroughSpan::class.java,
            android.text.style.BulletSpan::class.java,
            android.text.style.QuoteSpan::class.java
        )

        for (spanClass in spans) {
            val spanInstances = spannable.getSpans(0, spannable.length, spanClass)
            for (span in spanInstances) {
                spannable.removeSpan(span)
            }
        }
    }

    private fun applyHeaderStyles(spannable: SpannableStringBuilder, start: Int, end: Int, size: Float) {
        spannable.setSpan(
            android.text.style.RelativeSizeSpan(size),
            start,
            end,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            android.text.style.UnderlineSpan(),
            start,
            end,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun applyInlineStyles(line: String, spannable: SpannableStringBuilder, start: Int) {
        val boldItalicPattern = Regex("\\*\\*\\*([^*]+)\\*\\*\\*|__\\*([^*]+)\\*__|___([^_]+)___")
        val boldPattern = Regex("\\*\\*([^*]+)\\*\\*|__([^_]+)__")
        val italicPattern = Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)|(?<!_)_([^_]+)_(?!_)")
        val strikethroughPattern = Regex("~~([^~]+)~~")
        val codePattern = Regex("`([^`]+)`")
        val linkPattern = Regex("\\[([^]]+)]\\(([^)]+)\\)")
        val filePattern = Regex("!\\[([^]]+)]\\((file://[^)]+)\\)")

        applyPattern(spannable, line, boldItalicPattern, start) { android.text.style.StyleSpan(android.graphics.Typeface.BOLD_ITALIC) }
        applyPattern(spannable, line, boldPattern, start) { android.text.style.StyleSpan(android.graphics.Typeface.BOLD) }
        applyPattern(spannable, line, italicPattern, start) { android.text.style.StyleSpan(android.graphics.Typeface.ITALIC) }
        applyPattern(spannable, line, strikethroughPattern, start) { android.text.style.StrikethroughSpan() }
        applyPattern(spannable, line, codePattern, start) { android.text.style.TypefaceSpan("monospace") }
        applyPattern(spannable, line, linkPattern, start) { ForegroundColorSpan(Color.GRAY) }
        applyPattern(spannable, line, filePattern, start) { ForegroundColorSpan(Color.GRAY) }
    }


    private fun applyPattern(
        spannable: SpannableStringBuilder,
        line: String,
        pattern: Regex,
        start: Int,
        spanFactory: () -> Any
    ) {
        var matchResult = pattern.find(line)
        while (matchResult != null) {
            val matchStart = start + matchResult.range.first
            val matchEnd = start + matchResult.range.last + 1
            spannable.setSpan(
                spanFactory(),
                matchStart,
                matchEnd,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            matchResult = pattern.find(line, matchResult.range.last + 1)
        }
    }

    fun handleAutomaticList(editText: EditText, keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
            val cursorPosition = editText.selectionStart
            val text = editText.text.toString()
            val start = if (cursorPosition > 0) text.lastIndexOf('\n', cursorPosition - 1) else -1

            val currentLine = if (start == -1) {
                text.substring(0, cursorPosition)
            } else {
                text.substring(start + 1, cursorPosition)
            }

            val bulletOrNumberOnlyRegex = Regex("^(\\d+\\.|[*+-])\\s*$")
            val numberedListContentRegex = Regex("^(\\d+)\\.\\s+.+$")

            if (bulletOrNumberOnlyRegex.matches(currentLine)) {
                if (start == -1) {
                    editText.text.delete(0, cursorPosition)
                } else {
                    editText.text.delete(start + 1, cursorPosition)
                }
                return false
            }

            if ((currentLine.startsWith("- ") || currentLine.startsWith("* ") || currentLine.startsWith("+ ")) && !bulletOrNumberOnlyRegex.matches(currentLine)) {
                val bullet = currentLine.substring(0, 2) // "- ", "* ", or "+ "
                editText.text?.insert(cursorPosition, "\n$bullet")
                return true
            }

            val matchResult = numberedListContentRegex.find(currentLine)
            if (matchResult != null) {
                val number = matchResult.groupValues[1].toInt()
                val nextNumber = number + 1
                editText.text?.insert(cursorPosition, "\n$nextNumber. ")
                return true
            }
        }
        return false
    }

    fun renderMarkdown(context: Context, text: String, renderedTextView: TextView) {
        val markwon = Markwon.builder(context)
            .usePlugin(ImagesPlugin.create { plugin ->
                plugin.addSchemeHandler(FileSchemeHandler.create())
            })
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                    builder.setFactory(org.commonmark.node.Link::class.java) { _, _ ->
                        arrayOf(ForegroundColorSpan(Color.GRAY))
                    }
                }
            })
            .build()

        markwon.setMarkdown(renderedTextView, text)
    }

    fun toggleRenderMode(context: Context, isEditMode: Boolean, editText: EditText, renderedTextView: TextView, renderButton: Button): Boolean {
        return if (isEditMode) {
            renderMarkdown(context, editText.text.toString(), renderedTextView)
            renderButton.text = "Edit"
            editText.visibility = View.GONE
            renderedTextView.visibility = View.VISIBLE
            KeyboardUtils.hideKeyboard(context, renderButton)
            false
        } else {
            editText.visibility = View.VISIBLE
            renderedTextView.visibility = View.GONE
            renderButton.text = "Render"
            KeyboardUtils.showKeyboard(context, editText)
            true
        }
    }
}
