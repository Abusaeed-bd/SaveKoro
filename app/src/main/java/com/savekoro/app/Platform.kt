package com.savekoro.app

import android.net.Uri

enum class Platform(val label: String, val color: Long) {
    YOUTUBE("YouTube", 0xFFFF0033),
    FACEBOOK("Facebook", 0xFF1877F2),
    INSTAGRAM("Instagram", 0xFFE1306C),
    TIKTOK("TikTok", 0xFF111111),
    PINTEREST("Pinterest", 0xFFE60023),
    THREADS("Threads", 0xFF000000),
    OTHER("অন্যান্য", 0xFF607D8B);

    companion object {
        val supported = entries.filter { it != OTHER }

        fun detect(url: String): Platform? {
            val host = runCatching { Uri.parse(url.trim()).host?.lowercase() }.getOrNull()
                ?: return null
            fun h(vararg d: String) = d.any { host == it || host.endsWith(".$it") }
            return when {
                h("youtube.com", "youtu.be") -> YOUTUBE
                h("facebook.com", "fb.watch", "fb.com") -> FACEBOOK
                h("instagram.com") -> INSTAGRAM
                h("tiktok.com") -> TIKTOK
                h("pin.it") || host.contains("pinterest.") -> PINTEREST
                h("threads.net", "threads.com") -> THREADS
                else -> OTHER
            }
        }

        private val urlRegex = Regex("""https?://[^\s<>"']+""")

        /** শেয়ার করা টেক্সট থেকে (যেমন "দেখো এই ভিডিও https://...") লিংক বের করে */
        fun extractUrl(text: String): String? =
            urlRegex.find(text)?.value?.trimEnd('.', ',', ')', ']', '!', '?')
    }
}
