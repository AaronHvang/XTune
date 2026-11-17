package com.qqmusictv.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val LEGACY_PREFS_NAME = "apple_music_tv"
private const val APP_PREFS_NAME = "qq_music_tv"
private const val MIGRATION_FLAG = "_migrated_from_legacy_prefs"

/** One-time copy of settings written by the original Apple Music TV build. */
internal fun Context.appPrefs(): SharedPreferences {
    val prefs = getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)
    if (prefs.getBoolean(MIGRATION_FLAG, false)) return prefs
    val legacy = getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit {
        for ((key, value) in legacy.all) when (value) {
            is String -> putString(key, value)
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
        }
        putBoolean(MIGRATION_FLAG, true)
    }
    return prefs
}

/** Persists the account token used as the "signed in" state. */
@Singleton
class AccountPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.appPrefs()
    val accountChanges: kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.callbackFlow {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "mut") trySend(Unit)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun getMUT(): String = prefs.getString("mut", "") ?: ""
    @Volatile private var verifiedToken: String? = null
    private var authorizationGeneration = 0L
    private val _authorizationRevision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val authorizationRevision: kotlinx.coroutines.flow.StateFlow<Long> = _authorizationRevision
    @Synchronized fun markVerified(token: String) {
        if (getMUT() == token && token.isNotEmpty() && verifiedToken != token) {
            verifiedToken = token
            _authorizationRevision.value = ++authorizationGeneration
        }
    }
    fun isVerified(token: String) = token.isNotEmpty() && token == getMUT() && token == verifiedToken
    @Synchronized fun setMUT(token: String) {
        if (getMUT() != token) { verifiedToken = null; _authorizationRevision.value = 0L }
        prefs.edit { putString("mut", token) }
    }
    fun hasMUT(): Boolean = getMUT().isNotEmpty()
}
