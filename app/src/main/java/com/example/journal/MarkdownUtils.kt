package com.example.journal

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.widget.EditText

object MarkdownUtils {

    fun updateTextStyles(editText: EditText) {
        val spannable = editText.text as SpannableStringBuilder
        val lines = spannable.split("\n")

        // Clear previous styles
        val sizeSpans = spannable.getSpans(0, spannable.length, android.text.style.RelativeSizeSpan::class.java)
        for (span in sizeSpans) {
            spannable.removeSpan(span)
        }
        val underlineSpans = spannable.getSpans(0, spannable.length, android.text.style.UnderlineSpan::class.java)
        for (span in underlineSpans) {
            spannable.removeSpan(span)
        }
        val styleSpans = spannable.getSpans(0, spannable.length, android.text.style.StyleSpan::class.java)
        for (span in styleSpans) {
            spannable.removeSpan(span)
        }
        val typefaceSpans = spannable.getSpans(0, spannable.length, android.text.style.TypefaceSpan::class.java)
        for (span in typefaceSpans) {
            spannable.removeSpan(span)
        }
        val strikethroughSpans = spannable.getSpans(0, spannable.length, android.text.style.StrikethroughSpan::class.java)
        for (span in strikethroughSpans) {
            spannable.removeSpan(span)
        }
        val bulletSpans = spannable.getSpans(0, spannable.length, android.text.style.BulletSpan::class.java)
        for (span in bulletSpans) {
            spannable.removeSpan(span)
        }
        val quoteSpans = spannable.getSpans(0, spannable.length, android.text.style.QuoteSpan::class.java)
        for (span in quoteSpans) {
            spannable.removeSpan(span)
        }

        var start = 0

        for (line in lines) {
            val end = start + line.length
            when {
                line.startsWith("# ") -> {
                    spannable.setSpan(
                        android.text.style.RelativeSizeSpan(2f),
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
                line.startsWith("## ") -> {
                    spannable.setSpan(
                        android.text.style.RelativeSizeSpan(1.8f),
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
                line.startsWith("### ") -> spannable.setSpan(
                    android.text.style.RelativeSizeSpan(1.6f),
                    start,
                    end,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                line.startsWith("#### ") -> spannable.setSpan(
                    android.text.style.RelativeSizeSpan(1.4f),
                    start,
                    end,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                line.startsWith("##### ") -> spannable.setSpan(
                    android.text.style.RelativeSizeSpan(1.2f),
                    start,
                    end,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                line.startsWith("###### ") -> spannable.setSpan(
                    android.text.style.RelativeSizeSpan(1.1f),
                    start,
                    end,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                else -> spannable.setSpan(
                    android.text.style.RelativeSizeSpan(1f),
                    start,
                    end,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // Handle bold-italic text within the line (***text***)
            var boldItalicStart = line.indexOf("***", 0)
            while (boldItalicStart != -1) {
                val boldItalicEnd = line.indexOf("***", boldItalicStart + 3)
                if (boldItalicEnd != -1) {
                    spannable.setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.BOLD_ITALIC),
                        start + boldItalicStart,
                        start + boldItalicEnd + 3,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    boldItalicStart = line.indexOf("***", boldItalicEnd + 3)
                } else {
                    boldItalicStart = -1
                }
            }

            // Handle bold text within the line (**text**)
            var boldStart = line.indexOf("**", 0)
            while (boldStart != -1) {
                val boldEnd = line.indexOf("**", boldStart + 2)
                if (boldEnd != -1 && (boldItalicStart == -1 || boldStart < boldItalicStart || boldStart > boldItalicStart + 2)) {
                    spannable.setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        start + boldStart,
                        start + boldEnd + 2,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    boldStart = line.indexOf("**", boldEnd + 2)
                } else {
                    boldStart = -1
                }
            }

            // Handle italic text within the line (*text* and _text_)
            var italicStart = line.indexOf("*", 0)
            while (italicStart != -1) {
                val italicEnd = line.indexOf("*", italicStart + 1)
                if (italicEnd != -1 && (boldStart == -1 || italicStart < boldStart || italicStart > boldStart + 1)) {
                    spannable.setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.ITALIC),
                        start + italicStart,
                        start + italicEnd + 1,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    italicStart = line.indexOf("*", italicEnd + 1)
                } else {
                    italicStart = -1
                }
            }

            italicStart = line.indexOf("_", 0)
            while (italicStart != -1) {
                val italicEnd = line.indexOf("_", italicStart + 1)
                if (italicEnd != -1 && (boldStart == -1 || italicStart < boldStart || italicStart > boldStart + 1)) {
                    spannable.setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.ITALIC),
                        start + italicStart,
                        start + italicEnd + 1,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    italicStart = line.indexOf("_", italicEnd + 1)
                } else {
                    italicStart = -1
                }
            }

            // Handle strikethrough text within the line (~~text~~)
            var strikeStart = line.indexOf("~~", 0)
            while (strikeStart != -1) {
                val strikeEnd = line.indexOf("~~", strikeStart + 2)
                if (strikeEnd != -1) {
                    spannable.setSpan(
                        android.text.style.StrikethroughSpan(),
                        start + strikeStart,
                        start + strikeEnd + 2,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    strikeStart = line.indexOf("~~", strikeEnd + 2)
                } else {
                    strikeStart = -1
                }
            }

            // Handle custom styled text within the line (`text`)
            var codeStart = line.indexOf("`", 0)
            while (codeStart != -1) {
                val codeEnd = line.indexOf("`", codeStart + 1)
                if (codeEnd != -1) {
                    spannable.setSpan(
                        android.text.style.TypefaceSpan("monospace"),
                        start + codeStart,
                        start + codeEnd + 1,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    codeStart = line.indexOf("`", codeEnd + 1)
                } else {
                    codeStart = -1
                }
            }

            // Handle file text within the line (![text](file://path))
            var fileStart = line.indexOf("![", 0)
            while (fileStart != -1) {
                val fileEnd = line.indexOf(")", fileStart + 2)
                if (fileEnd != -1) {
                    spannable.setSpan(
                        ForegroundColorSpan(Color.GRAY),
                        start + fileStart,
                        start + fileEnd + 1,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    fileStart = line.indexOf("![", fileEnd + 1)
                } else {
                    fileStart = -1
                }
            }

            start = end + 1
        }
    }
}

