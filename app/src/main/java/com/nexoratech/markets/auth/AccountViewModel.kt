package com.nexoratech.markets.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nexoratech.markets.NexoraApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Account state machine for the whole app shell.
 * Launch: CheckingSession -> (SignedIn -> markets | SignedOut -> welcome)
 */
class AccountViewModel(private val app: NexoraApp) : ViewModel() {

    sealed class AuthState {
        data object CheckingSession : AuthState()
        data object SignedOut : AuthState()
        data class SignedIn(val user: AccountUser) : AuthState()
    }

    private val _state = MutableStateFlow<AuthState>(AuthState.CheckingSession)
    val state: StateFlow<AuthState> = _state

    /** null = still checking, false = questionnaire needed, true = profile on file. */
    private val _profileComplete = MutableStateFlow<Boolean?>(null)
    val profileComplete: StateFlow<Boolean?> = _profileComplete

    init {
        restoreSession()
    }

    private fun restoreSession() {
        val store = app.accountStore
        val token = store.sessionToken
        if (token.isBlank()) {
            _state.value = AuthState.SignedOut
            return
        }
        viewModelScope.launch {
            when (val result = app.accountService.validateSession(token)) {
                is AccountService.AccountResult.Success -> {
                    // Keep the existing token; refresh the profile fields.
                    store.userEmail = result.user.email
                    store.userDisplayName = result.user.displayName
                    _state.value = AuthState.SignedIn(result.user)
                    checkProfile()
                }
                is AccountService.AccountResult.Failure -> {
                    store.clear()
                    _state.value = AuthState.SignedOut
                }
            }
        }
    }

    fun register(displayName: String, email: String, password: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            when (val result = app.accountService.register(displayName, email, password)) {
                is AccountService.AccountResult.Success -> {
                    persistAndEnter(result)
                    onDone(null)
                }
                is AccountService.AccountResult.Failure -> onDone(result.message)
            }
        }
    }

    fun login(email: String, password: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            when (val result = app.accountService.login(email, password)) {
                is AccountService.AccountResult.Success -> {
                    persistAndEnter(result)
                    onDone(null)
                }
                is AccountService.AccountResult.Failure -> onDone(result.message)
            }
        }
    }

    fun signOut(onDone: () -> Unit) {
        val token = app.accountStore.sessionToken
        viewModelScope.launch {
            app.accountService.logout(token)
            app.accountStore.clear()
            _state.value = AuthState.SignedOut
            onDone()
        }
    }

    private fun persistAndEnter(result: AccountService.AccountResult.Success) {
        app.accountStore.saveSession(
            token = result.token.orEmpty(),
            email = result.user.email,
            displayName = result.user.displayName,
        )
        _state.value = AuthState.SignedIn(result.user)
        checkProfile()
    }

    private fun checkProfile() {
        viewModelScope.launch {
            val token = app.accountStore.sessionToken
            when (val result = app.accountService.getProfile(token)) {
                is AccountService.ProfileResult.Loaded ->
                    _profileComplete.value = result.profile?.isComplete == true
                is AccountService.ProfileResult.Failure ->
                    // Backend unreachable: let the user through rather than
                    // trapping them at a spinner — they can retry from Settings.
                    _profileComplete.value = true
            }
        }
    }

    fun onProfileSaved() {
        _profileComplete.value = true
    }

    fun saveProfile(
        experienceLevel: String,
        primaryGoal: String,
        capitalUsd: Double,
        instruments: String,
        tradingStyle: String,
        riskTolerance: String,
        onDone: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            val token = app.accountStore.sessionToken
            when (
                val result = app.accountService.saveProfile(
                    token, experienceLevel, primaryGoal, capitalUsd,
                    instruments, tradingStyle, riskTolerance,
                )
            ) {
                is AccountService.ProfileResult.Loaded -> onDone(null)
                is AccountService.ProfileResult.Failure -> onDone(result.message)
            }
        }
    }

    class Factory(private val app: NexoraApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AccountViewModel(app) as T
    }
}
