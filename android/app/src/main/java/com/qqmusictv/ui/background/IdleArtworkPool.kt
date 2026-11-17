package com.qqmusictv.ui.background

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Only recent successfully loaded public cover URLs; no bitmap copies or account data. */
internal object IdleArtworkPool {
    private val _urls = MutableStateFlow<List<String>>(emptyList())
    val urls: StateFlow<List<String>> = _urls

    private val ALLOWED_HOST_SUFFIXES = listOf(
        ".qq.com",       // QQ 音乐封面 CDN（y.qq.com、imgcache.qq.com 等）
        ".qpic.cn",      // 腾讯图片服务
    )

    @Synchronized fun record(data: Any) {
        val url = data as? String ?: return
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return
        if (!url.startsWith("https://")) return
        val ok = ALLOWED_HOST_SUFFIXES.any { host.endsWith(it) }
        if (!ok) return
        _urls.value = (_urls.value.filterNot { it == url } + url).takeLast(64)
    }
}
