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
    }

    class Factory(private val app: NexoraApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AccountViewModel(app) as T
    }
}
