package com.savekoro.app

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * yt-dlp + ffmpeg engine. First start takes a few seconds (Python gets extracted).
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

    fun version(context: Context): String =
        runCatching { YoutubeDL.getInstance().version(context.applicationContext) }.getOrNull() ?: "?"

    /** Updates yt-dlp to the latest stable release from GitHub. true = a new version was installed */
    suspend fun update(context: Context): Boolean = withContext(Dispatchers.IO) {
        ensureReady(context)
        val status = YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext)
        prefs(context).edit().putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply()
        status == YoutubeDL.UpdateStatus.DONE
    }

    /**
     * Sites change often, so yt-dlp auto-updates once a day.
     * Silently skipped when offline.
     */
    suspend fun autoUpdateIfDue(context: Context) {
        val last = prefs(context).getLong(KEY_LAST_UPDATE, 0)
        if (System.currentTimeMillis() - last < DAY_MS) return
        runCatching { update(context) }
    }

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
