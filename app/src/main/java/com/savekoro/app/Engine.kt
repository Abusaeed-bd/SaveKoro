package com.savekoro.app

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * yt-dlp + ffmpeg (+ QuickJS, bundled by youtubedl-android 0.18.1+) engine.
 * First start takes a few seconds (Python gets extracted).
 */
object Engine {
    private val mutex = Mutex()
    @Volatile private var ready = false

    private const val PREFS = "engine"
    private const val KEY_LAST_UPDATE = "last_update"
    private const val DAY_MS = 24L * 60 * 60 * 1000

    suspend fun ensureReady(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!ready) {
                val app = context.applicationContext
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                ready = true
            }
        }
    }

    /** null = still running the yt-dlp that shipped inside the app (never updated) */
    fun version(context: Context): String? =
        runCatching { YoutubeDL.getInstance().version(context.applicationContext) }.getOrNull()

    fun versionLabel(context: Context): String = version(context) ?: "built-in (not updated yet)"

    /**
     * Updates yt-dlp. Tries the stable channel first, then nightly.
     * true = a new version was installed, false = already up to date.
     * Throws IOException with the real reason(s) if every channel fails.
     */
    suspend fun update(context: Context): Boolean = withContext(Dispatchers.IO) {
        ensureReady(context)
        val app = context.applicationContext
        val channels = listOf(
            "stable" to YoutubeDL.UpdateChannel.STABLE,
            "nightly" to YoutubeDL.UpdateChannel.NIGHTLY,
        )
        val errors = mutableListOf<String>()
        for ((name, channel) in channels) {
            try {
                val status = YoutubeDL.getInstance().updateYoutubeDL(app, channel)
                prefs(app).edit().putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply()
                return@withContext status == YoutubeDL.UpdateStatus.DONE
            } catch (e: Exception) {
                errors += "$name: ${describe(e)}"
            }
        }
        throw IOException(errors.joinToString("\n"))
    }

    /**
     * Runs on every app start. If the engine has never been updated (or the last
     * update failed) it tries again right away; otherwise once a day.
     */
    suspend fun autoUpdateIfDue(context: Context) {
        val neverUpdated = version(context) == null
        val last = prefs(context).getLong(KEY_LAST_UPDATE, 0)
        if (!neverUpdated && System.currentTimeMillis() - last < DAY_MS) return
        runCatching { update(context) }
    }

    /** Full error chain, e.g. "failed to update youtube-dl → HTTP 403 rate limit exceeded" */
    fun describe(e: Throwable): String =
        generateSequence(e) { it.cause }
            .map { it.message?.takeIf(String::isNotBlank) ?: it.javaClass.simpleName }
            .distinct()
            .joinToString(" → ")
            .take(400)

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
