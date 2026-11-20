package com.savekoro.app

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume

/**
 * Saves the downloaded file to the phone gallery:
 * video → Movies/SaveKoro, audio → Music/SaveKoro
 */
object MediaSaver {
    private const val FOLDER = "SaveKoro"

    suspend fun save(context: Context, file: File, isAudio: Boolean): Uri? =
        withContext(Dispatchers.IO) {
            val mime = mimeOf(file, isAudio)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveScoped(context, file, isAudio, mime)
            } else {
                saveLegacy(context, file, isAudio, mime)
            }
        }

    private fun saveScoped(context: Context, file: File, isAudio: Boolean, mime: String): Uri {
        val resolver = context.contentResolver
        val collection = if (isAudio) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val baseDir = if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$baseDir/$FOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: throw IOException("Could not create file in gallery")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: throw IOException("Could not write to gallery")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    @Suppress("DEPRECATION")
    private suspend fun saveLegacy(context: Context, file: File, isAudio: Boolean, mime: String): Uri? {
        val base = Environment.getExternalStoragePublicDirectory(
            if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        )
        val dir = File(base, FOLDER).apply { mkdirs() }
        val dest = uniqueFile(dir, file.name)
        file.copyTo(dest)
        return suspendCancellableCoroutine { cont ->
            MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf(mime)) { _, uri ->
                if (cont.isActive) cont.resume(uri)
            }
        }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        var i = 1
        while (f.exists()) {
            f = File(dir, "${name.substringBeforeLast('.')} ($i).${name.substringAfterLast('.', "mp4")}")
            i++
        }
        return f
    }

    private fun mimeOf(file: File, isAudio: Boolean): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: if (isAudio) "audio/mpeg" else "video/mp4"
}
