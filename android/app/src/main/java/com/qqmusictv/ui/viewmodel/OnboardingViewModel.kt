package com.qqmusictv.ui.viewmodel

import com.qqmusictv.i18n.tr
import com.qqmusictv.R

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qqmusictv.data.AccountPreferences
import com.qqmusictv.data.OnboardingPreferences
import com.qqmusictv.media.InAppWebServer
import com.qqmusictv.media.PhoneServerPhase
import com.qqmusictv.media.phoneSetupHintId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingState(
    val phoneUrl: String = "",
    val hasMut: Boolean = false,
    val checking: Boolean = false,
    val tokenStored: Boolean = false,
    val tokenError: String? = null,
    val phoneServerUnavailable: Boolean = false,
    val phoneSetupMessage: Int? = null,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboarding: OnboardingPreferences,
    private val cookies: com.qqmusictv.data.qq.QQCookieStore,
    private val webServer: InAppWebServer,
) : ViewModel() {
    private val _tokenState = MutableStateFlow(OnboardingState(phoneUrl = qqLoginUrl()))
    val state: StateFlow<OnboardingState> = combine(_tokenState, webServer.endpoint) { token, endpoint ->
        token.copy(phoneUrl = qqLoginUrl(), phoneServerUnavailable =
            endpoint.phase == PhoneServerPhase.FAILED || endpoint.phase == PhoneServerPhase.STOPPED,
            phoneSetupMessage = phoneSetupHintId(endpoint))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, OnboardingState())
    private var mutPollJob: Job? = null

    private fun qqLoginUrl(): String {
        val base = webServer.serverUrl()
        return if (base.isEmpty()) "" else base.trimEnd('/') + "/qq"
    }

    /** The phone page writes the cookie; poll until the account shows up, then let the user start. */
    fun startMutPolling() {
        if (mutPollJob?.isActive == true) return
        mutPollJob = viewModelScope.launch {
            while (isActive) {
                val loggedIn = cookies.hasLogin()
                _tokenState.value = OnboardingState(
                    phoneUrl = qqLoginUrl(),
                    hasMut = loggedIn,
                    tokenStored = loggedIn,
                )
                delay(1_500)
            }
        }
    }

    fun stopMutPolling() { mutPollJob?.cancel(); mutPollJob = null }

    fun finish() {
        stopMutPolling()
        onboarding.markCompleted()
    }
}
