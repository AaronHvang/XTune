package com.qqmusictv.data.auth

import com.qqmusictv.data.network.AuthStatus
import com.qqmusictv.data.qq.QQCookieStore
import javax.inject.Inject
import javax.inject.Singleton

/** Single source of truth for "is the QQ Music account connected". */
@Singleton
class QQAuthState @Inject constructor(private val cookies: QQCookieStore) {

    val isLoggedIn: Boolean get() = cookies.hasLogin()

    fun status(): AuthStatus = AuthStatus(
        hasMUT = cookies.hasLogin(),
        mutSetAt = null,
        hasBearer = cookies.hasLogin(),
    )
}
