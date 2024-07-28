package com.example.journal

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

object ImageUtils {

    const val PICK_IMAGE_REQUEST = 1
    const val REQUEST_CODE_IMAGE_PERMISSIONS = 10

    fun hasImagePermissions(context: Context): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }

    fun requestImagePermissions(activity: Activity) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        ActivityCompat.requestPermissions(activity, permissions, REQUEST_CODE_IMAGE_PERMISSIONS)
    }

    fun selectImage(activity: Activity) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        activity.startActivityForResult(Intent.createChooser(intent, "Select Images"), PICK_IMAGE_REQUEST)
    }

    fun handleImageUri(context: Context, uri: Uri, editText: EditText, isEditMode: Boolean, renderedTextView: TextView) {
        val imagePath = getPathFromUri(context, uri)
        if (imagePath != null) {
            insertImagePathToMarkdown(editText, imagePath)
            if (!isEditMode) {
                MarkdownUtils.renderMarkdown(context, editText.text.toString(), renderedTextView)
            }
        }
    }

    private fun getPathFromUri(context: Context, uri: Uri): String? {
        var path: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            val name = cursor.getString(nameIndex)
            val file = File(context.filesDir, "${System.currentTimeMillis()}_$name")
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            path = file.absolutePath
        }

        path?.let {
            adjustImageOrientation(it)
        }

        return path
    }

    private fun adjustImageOrientation(imagePath: String) {
        try {
            val exif = ExifInterface(imagePath)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val bitmap = BitmapFactory.decodeFile(imagePath)
            val rotatedBitmap = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                else -> bitmap
            }

            // Save the rotated bitmap back to the file
            FileOutputStream(imagePath).use { out ->
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }

        } catch (e: Exception) {
            Log.e("ImageUtils", "Error adjusting image orientation", e)
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun insertImagePathToMarkdown(editText: EditText, imagePath: String) {
        val markdownImage = "![Image](file://$imagePath)"
        val cursorPosition = editText.selectionStart
        editText.text?.insert(cursorPosition, markdownImage)
    }
}
