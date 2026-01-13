package com.hearing.hearingtest

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream

object UploadedFilesManager {

    private const val PREFS = "uploaded_audio_prefs"
    private const val KEY = "files"
    private const val MAX_FILE_SIZE = 20 * 1024 * 1024 // 20 MB

    private val gson = Gson()

    fun save(context: Context, uri: Uri): Boolean {
        val resolver = context.contentResolver
        val pfd = resolver.openFileDescriptor(uri, "r") ?: return false
        val size = pfd.statSize
        pfd.close()

        if (size <= 0 || size > MAX_FILE_SIZE) return false

        val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "audio_${System.currentTimeMillis()}"
        val targetFile = File(context.filesDir, fileName)

        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }

        val list = getAll(context).toMutableList()
        list.add(
            UploadedAudio(
                id = System.currentTimeMillis(),
                name = fileName,
                filePath = targetFile.absolutePath,
                sizeBytes = size,
                timestamp = System.currentTimeMillis()
            )
        )

        persist(context, list)
        return true
    }

    fun getAll(context: Context): List<UploadedAudio> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY, "[]") ?: "[]"
        return gson.fromJson(json, object : TypeToken<List<UploadedAudio>>() {}.type)
    }

    fun delete(context: Context, id: Long) {
        val list = getAll(context).toMutableList()
        val item = list.find { it.id == id } ?: return

        File(item.filePath).delete()
        list.remove(item)
        persist(context, list)
    }

    private fun persist(context: Context, list: List<UploadedAudio>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY, gson.toJson(list)).apply()
    }
}
