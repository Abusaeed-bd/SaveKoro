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
    BEST("সেরা", "bv*+ba/b"),
    P1080("1080p", "bv*[height<=1080]+ba/b[height<=1080]/b"),
    P720("720p", "bv*[height<=720]+ba/b[height<=720]/b"),
    P480("480p", "bv*[height<=480]+ba/b[height<=480]/b"),
    AUDIO("MP3 অডিও", "ba/b"),
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

    init {
        viewModelScope.launch {
            try {
                Engine.ensureReady(app)
                _state.update { it.copy(engineReady = true, engineVersion = Engine.version(app)) }
                Engine.autoUpdateIfDue(app)
                _state.update { it.copy(engineVersion = Engine.version(app)) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "ইঞ্জিন চালু হয়নি: ${e.message}") }
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
                val info = withContext(Dispatchers.IO) {
                    val req = YoutubeDLRequest(url).apply { addOption("--no-playlist") }
                    YoutubeDL.getInstance().getInfo(req)
                }
                _state.update {
                    it.copy(
                        meta = VideoMeta(
                            title = info.title ?: "শিরোনাম নেই",
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
                it.copy(downloading = true, progress = 0f, etaSec = 0, statusLine = "শুরু হচ্ছে…", error = null)
            }
            val tmp = File(app.cacheDir, "dl/${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                Engine.ensureReady(app)
                val req = YoutubeDLRequest(url).apply {
                    addOption("--no-playlist")
                    addOption("--no-mtime")
                    addOption("-o", tmp.absolutePath + "/%(title).80B.%(ext)s")
                    addOption("-f", quality.format)
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
                val out = tmp.listFiles()
                    ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
                    ?.maxByOrNull { it.length() }
                    ?: throw IllegalStateException("ডাউনলোড শেষ, কিন্তু ফাইল পাওয়া যায়নি")

                _state.update { it.copy(statusLine = "গ্যালারিতে সেভ হচ্ছে…", progress = 1f) }
                val uri = MediaSaver.save(app, out, isAudio)
                val title = out.nameWithoutExtension
                _state.update {
                    it.copy(
                        saved = listOf(SavedItem(title, uri, isAudio)) + it.saved,
                        message = if (isAudio) "✅ Music/SaveKoro তে সেভ হয়েছে" else "✅ Movies/SaveKoro তে সেভ হয়েছে",
                    )
                }
            } catch (e: Exception) {
                if (cancelled) {
                    _state.update { it.copy(message = "ডাউনলোড বাতিল করা হয়েছে") }
                } else {
                    _state.update { it.copy(error = friendlyError(e)) }
                }
            } finally {
                tmp.deleteRecursively()
                _state.update { it.copy(downloading = false, statusLine = "") }
            }
        }
    }

    fun cancel() {
        cancelled = true
        runCatching { YoutubeDL.getInstance().destroyProcessById(PROCESS_ID) }
    }

    fun updateEngine() {
        if (_state.value.updating) return
        _state.update { it.copy(updating = true) }
        viewModelScope.launch {
            val msg = try {
                if (Engine.update(app)) "yt-dlp আপডেট হয়েছে ✅" else "yt-dlp আগে থেকেই সর্বশেষ ভার্সনে আছে"
            } catch (e: Exception) {
                "আপডেট হয়নি: ${e.message?.take(120)}"
            }
            _state.update { it.copy(updating = false, message = msg, engineVersion = Engine.version(app)) }
        }
    }

    override fun onCleared() {
        cancel()
        super.onCleared()
    }

    private fun friendlyError(e: Throwable): String {
        val raw = e.message.orEmpty()
        val m = raw.lowercase()
        return when {
            "unsupported url" in m -> "এই লিংকটা সাপোর্টেড নয়। ঠিক ভিডিওর লিংক দিন।"
            "sign in to confirm" in m || "not a bot" in m ->
                "YouTube বট-চেক চাইছে। একটু পরে চেষ্টা করুন বা নিচে থেকে yt-dlp আপডেট করুন।"
            "private" in m || "login" in m || "log in" in m || "cookies" in m ->
                "এই ভিডিও প্রাইভেট অথবা দেখতে লগইন লাগে।"
            "unable to download webpage" in m || "timed out" in m || "network" in m || "resolve host" in m ->
                "ইন্টারনেট সংযোগ চেক করুন।"
            "requested format is not available" in m ->
                "এই কোয়ালিটি পাওয়া যায়নি। 'সেরা' দিয়ে চেষ্টা করুন।"
            raw.isBlank() -> "কিছু একটা সমস্যা হয়েছে।"
            else -> "সমস্যা: " + (raw.lines().lastOrNull { it.contains("ERROR") } ?: raw.lines().first()).take(250)
        }
    }

    companion object {
        private const val PROCESS_ID = "savekoro-download"
    }
}
