package com.qqmusictv.data.auth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AuthIssue { DeveloperUnavailable, UserRejected }

@Singleton
class AuthIssueState @Inject constructor() {
    private val mutable = MutableStateFlow<AuthIssue?>(null)
    val issue = mutable.asStateFlow()
    fun developerUnavailable() { mutable.value = AuthIssue.DeveloperUnavailable }
    fun userRejected() { mutable.value = AuthIssue.UserRejected }
    fun recovered() { mutable.value = null }
}
