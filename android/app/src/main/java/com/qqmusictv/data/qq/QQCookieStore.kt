package com.qqmusictv.data.qq

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the QQ Music web cookie pasted from the phone page. It is kept on device only —
 * never uploaded anywhere. Cookie contains `uin` and `qqmusic_key` (or legacy `qm_keyst`),
 * which are both required to resolve a playable stream URL.
 */
@Singleton
class QQCookieStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("qq_music_tv", Context.MODE_PRIVATE)

    /** Emits whenever the stored cookie changes so screens can re-check the sign-in state. */
    val accountChanges: Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "cookie") trySend(Unit)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun cookie(): String = prefs.getString("cookie", "").orEmpty().trim()

    fun setCookie(raw: String) {
        val cleaned = raw.trim().replace("\r", "").replace("\n", "")
        prefs.edit { putString("cookie", cleaned) }
    }

    fun clear() = prefs.edit { remove("cookie") }

    /** Numeric QQ uin, with the leading `o` some cookies carry stripped off. */
    fun uin(): String {
        val raw = param("uin") ?: param("wxuin") ?: return ""
        return raw.filter { it.isDigit() }
    }

    fun key(): String = param("qqmusic_key") ?: param("qm_keyst") ?: ""

    fun hasLogin(): Boolean = uin().isNotEmpty() && key().isNotEmpty()

    fun cookieHeader(): String = cookie()

    private fun param(name: String): String? = cookie()
        .split(';')
        .asSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("$name=") }
        ?.substringAfter('=')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}
