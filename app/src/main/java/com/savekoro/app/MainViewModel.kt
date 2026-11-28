package com.savekoro.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Quality(val label: String, val format: String) {
    BEST("Best", "bv*+ba/b"),
    P1080("1080p", "bv*[height<=1080]+ba/b[height<=1080]/b"),
    P720("720p", "bv*[height<=720]+ba/b[height<=720]/b"),
    P480("480p", "bv*[height<=480]+ba/b[height<=480]/b"),
    AUDIO("MP3 Audio", "ba/b"),
}

data class VideoMeta(
    val title: String,
    val thumbnail: String?,
    val uploader: String?,
    val durationSec: Int,
)

data class SavedItem(val title: String, val uri: Uri?, val isAudio: Boolean)

data class UiState(
    val url: String = "",
    val engineReady: Boolean = false,
    val engineVersion: String = "",
    val fetching: Boolean = false,
    val meta: VideoMeta? = null,
    val quality: Quality = Quality.BEST,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val etaSec: Long = 0,
    val statusLine: String = "",
    val updating: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val saved: List<SavedItem> = emptyList(),
) {
    val platform: Platform? get() = Platform.detect(url)
}

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var job: Job? = null
    @Volatile private var cancelled = false

    /** The YouTube method that worked last time in this session is tried first */
    @Volatile private var preferredYtClient: String? = null

    init {
        viewModelScope.launch {
            try {
                Engine.ensureReady(app)
                _state.update { it.copy(engineReady = true, engineVersion = Engine.versionLabel(app)) }
                Engine.autoUpdateIfDue(app)
                _state.update { it.copy(engineVersion = Engine.versionLabel(app)) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Engine failed to start: ${Engine.describe(e)}") }
            }
        }
    }

    fun onUrlChange(v: String) = _state.update { it.copy(url = v, meta = null, error = null) }

    fun onSharedText(text: String) {
        val url = Platform.extractUrl(text) ?: return
        onUrlChange(url)
        fetchInfo()
    }

    fun setQuality(q: Quality) = _state.update { it.copy(quality = q) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun fetchInfo() {
        val url = _state.value.url.trim()
        if (url.isBlank() || _state.value.fetching) return
        _state.update { it.copy(fetching = true, error = null, meta = null) }
        viewModelScope.launch {
            try {
                Engine.ensureReady(app)
                val info = withYoutubeFallback(url, isCancelled = { false }) { client ->
                    withContext(Dispatchers.IO) {
                        val req = YoutubeDLRequest(url).apply {
                            addOption("--no-playlist")
                            client?.let { addOption("--extractor-args", it) }
                        }
                        YoutubeDL.getInstance().getInfo(req)
                    }
                }
                _state.update {
                    it.copy(
                        meta = VideoMeta(
                            title = info.title ?: "Untitled",
                            thumbnail = info.thumbnail,
                            uploader = info.uploader,
                            durationSec = info.duration ?: 0,
                        )
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = friendlyError(e)) }
            } finally {
                _state.update { it.copy(fetching = false) }
            }
        }
    }

    fun download() {
        val s = _state.value
        val url = s.url.trim()
        if (url.isBlank() || s.downloading) return
        val quality = s.quality
        val isAudio = quality == Quality.AUDIO
        cancelled = false

        job = viewModelScope.launch {
            _state.update {
                it.copy(downloading = true, progress = 0f, etaSec = 0, statusLine = "Starting…", error = null)
            }
            val tmpRoot = File(app.cacheDir, "dl/${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                Engine.ensureReady(app)
                var attempt = 0
                val out = withYoutubeFallback(
                    url,
                    isCancelled = { cancelled },
                    onRetry = { n, total ->
                        _state.update {
                            it.copy(progress = 0f, etaSec = 0, statusLine = "YouTube blocked that method, trying another ($n/$total)…")
                        }
                    },
                ) { client ->
                    attempt++
                    // Fresh folder per attempt so half-finished files don't mix
                    val dir = File(tmpRoot, "a$attempt").apply { mkdirs() }
                    val req = YoutubeDLRequest(url).apply {
                        addOption("--no-playlist")
                        addOption("--no-mtime")
                        addOption("-o", dir.absolutePath + "/%(title).80B.%(ext)s")
                        addOption("-f", quality.format)
                        client?.let { addOption("--extractor-args", it) }
                        if (isAudio) {
                            addOption("-x")
                            addOption("--audio-format", "mp3")
                        } else {
                            addOption("--merge-output-format", "mp4")
                        }
                    }
                    withContext(Dispatchers.IO) {
                        YoutubeDL.getInstance().execute(req, PROCESS_ID, { progress: Float, eta: Long, line: String ->
                            _state.update {
                                it.copy(
                                    progress = (progress / 100f).coerceIn(0f, 1f),
                                    etaSec = eta.coerceAtLeast(0),
                                    statusLine = line,
                                )
                            }
                        })
                    }
                    dir.listFiles()
                        ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
                        ?.maxByOrNull { it.length() }
                        ?: throw IllegalStateException("Download finished, but no file was found")
                }

                _state.update { it.copy(statusLine = "Saving to gallery…", progress = 1f) }
                val uri = MediaSaver.save(app, out, isAudio)
                val title = out.nameWithoutExtension
                _state.update {
                    it.copy(
                        saved = listOf(SavedItem(title, uri, isAudio)) + it.saved,
                        message = if (isAudio) "✅ Saved to Music/SaveKoro" else "✅ Saved to Movies/SaveKoro",
                    )
                }
            } catch (e: Exception) {
                if (cancelled) {
                    _state.update { it.copy(message = "Download cancelled") }
                } else {
                    _state.update { it.copy(error = friendlyError(e)) }
                }
            } finally {
                tmpRoot.deleteRecursively()
                _state.update { it.copy(downloading = false, statusLine = "") }
            }
        }
    }

    /**
     * For YouTube: if one method gets blocked (bot check / 403), silently retry with
     * other YouTube player clients. No login needed. Other sites run once as normal.
     */
    private suspend fun <T> withYoutubeFallback(
        url: String,
        isCancelled: () -> Boolean,
        onRetry: (attempt: Int, total: Int) -> Unit = { _, _ -> },
        block: suspend (extractorArgs: String?) -> T,
    ): T {
        if (Platform.detect(url) != Platform.YOUTUBE) return block(null)

        val order = (listOf(preferredYtClient) + YT_CLIENTS).distinct()
        var lastError: Exception? = null
        for ((i, client) in order.withIndex()) {
            if (i > 0) onRetry(i + 1, order.size)
            try {
                val result = block(client)
                preferredYtClient = client
                return result
            } catch (e: Exception) {
                lastError = e
                if (isCancelled() || !isYoutubeBlock(e)) throw e
            }
        }
        throw lastError ?: IllegalStateException("No download method worked")
    }

    private fun isYoutubeBlock(e: Throwable): Boolean {
        val m = Engine.describe(e).lowercase()
        return listOf(
            "sign in", "not a bot", "403", "forbidden", "po token", "not available on this app",
            "video unavailable", "requested format is not available", "no video formats",
            "only images are available", "http error 4",
        ).any { it in m }
    }

    fun cancel() {
        cancelled = true
        runCatching { YoutubeDL.getInstance().destroyProcessById(PROCESS_ID) }
    }

    fun updateEngine() {
        if (_state.value.updating) return
        _state.update { it.copy(updating = true) }
        viewModelScope.launch {
            try {
                val msg = if (Engine.update(app)) "yt-dlp updated ✅" else "yt-dlp is already up to date ✅"
                _state.update { it.copy(message = msg, error = null) }
            } catch (e: Exception) {
                // Shown in the red card (not a snackbar) so the full reason stays visible
                _state.update { it.copy(error = "Engine update failed:\n${e.message.orEmpty().take(500)}") }
            }
            _state.update { it.copy(updating = false, engineVersion = Engine.versionLabel(app)) }
        }
    }

    override fun onCleared() {
        cancel()
        super.onCleared()
    }

    private fun friendlyError(e: Throwable): String {
        val raw = Engine.describe(e)
        val m = raw.lowercase()
        val isYt = Platform.detect(_state.value.url) == Platform.YOUTUBE
        return when {
            "unsupported url" in m -> "This link is not supported. Please paste a valid video link."
            isYt && isYoutubeBlock(e) ->
                "YouTube blocked this download (bot check). Tap \"Update engine\" below and try again in a few minutes. " +
                    "Switching between Wi-Fi and mobile data can also help."
            "private" in m || "login" in m || "log in" in m || "sign in" in m || "cookies" in m ->
                "This post is private or requires login."
            "unable to download webpage" in m || "timed out" in m || "network" in m || "resolve host" in m ->
                "Please check your internet connection."
            "requested format is not available" in m ->
                "This quality is not available. Try 'Best'."
            raw.isBlank() -> "Something went wrong."
            else -> "Error: " + (raw.lines().lastOrNull { it.contains("ERROR") } ?: raw.lines().first()).take(300)
        }
    }

    companion object {
        private const val PROCESS_ID = "savekoro-download"

        /** YouTube player clients tried in order when one gets blocked. null = yt-dlp default */
        private val YT_CLIENTS = listOf(
            null,
            "youtube:player_client=tv_simply,tv",
            "youtube:player_client=android_vr",
            "youtube:player_client=mweb,web_safari",
            "youtube:player_client=web_embedded",
        )
    }
}
