package com.qqmusictv.ui.viewmodel

import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qqmusictv.data.qq.QQCookieStore
import com.qqmusictv.data.qq.QQMusicApi
import com.qqmusictv.data.repository.ProfileAvatarCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val api: QQMusicApi,
    private val cookies: QQCookieStore,
    private val cache: ProfileAvatarCache,
) : ViewModel() {
    private val _avatar = MutableStateFlow<String?>(null)
    val avatar: StateFlow<String?> = _avatar
    private val retry = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val images = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()

    init {
        viewModelScope.launch {
            cookies.accountChanges.map { cookies.uin() }.distinctUntilChanged().collectLatest { uin ->
                _avatar.value = null
                if (uin.isEmpty()) return@collectLatest
                var failures = 0
                while (currentCoroutineContext().isActive && cookies.uin() == uin) {
                    val cached = withContext(Dispatchers.IO) {
                        try { cache.read(uin) } catch (_: Exception) { null }
                    }
                    if (cached != null && cookies.uin() == uin) publish(cached)
                    val age = cached?.let { (System.currentTimeMillis() - it.lastModified()).coerceAtLeast(0) }
                    val wait = if (age != null && age < REFRESH_MS) REFRESH_MS - age else {
                        val bytes = withContext(Dispatchers.IO) { fetch(uin) }
                        ensureActive()
                        when {
                            bytes != null -> {
                                val file = withContext(Dispatchers.IO) {
                                    try { cache.save(uin, bytes) } catch (_: Exception) { null }
                                }
                                if (file != null && cookies.uin() == uin) { publish(file); failures = 0; REFRESH_MS }
                                else retryDelay(failures++)
                            }
                            else -> retryDelay(failures++)
                        }
                    }
                    // A resume or display failure can retry sooner; no failure is shown to the user.
                    withTimeoutOrNull(wait) { retry.first() }
                }
            }
        }
    }

    private suspend fun fetch(uin: String): ByteArray? {
        val url = try {
            withTimeout(10_000) { api.loginUserInfo()?.avatarUrl }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { null } ?: return null
        return download(url)
    }

    private fun publish(file: java.io.File) { _avatar.value = file.toURI().toString() + "#" + file.lastModified() }
    private fun retryDelay(attempt: Int) = listOf(2_000L, 5_000L, 10_000L, 30_000L, 120_000L, 600_000L)[attempt.coerceAtMost(5)]
    private fun download(url: String): ByteArray? = try {
        images.newCall(Request.Builder().url(url).build()).execute().use { response ->
            val body = response.body
            when {
                !response.isSuccessful -> null
                body == null || body.contentLength() > 1_048_576 -> null
                else -> body.byteStream().use { it.readBytesLimited() }.takeIf(::usable)
            }
        }
    } catch (_: Exception) { null }
    private fun usable(bytes: ByteArray): Boolean {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
        val valid = bitmap.width > 0 && bitmap.height > 0
        bitmap.recycle()
        return valid
    }
    private fun java.io.InputStream.readBytesLimited(): ByteArray {
        val result = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) return result.toByteArray()
            check(result.size() + count <= 1_048_576)
            result.write(buffer, 0, count)
        }
    }
    fun onResume() { retry.tryEmit(Unit) }
    fun onAvatarDisplayError() { cache.clear(); retry.tryEmit(Unit) }
    companion object {
        private const val REFRESH_MS = 24 * 60 * 60 * 1000L
    }
}
