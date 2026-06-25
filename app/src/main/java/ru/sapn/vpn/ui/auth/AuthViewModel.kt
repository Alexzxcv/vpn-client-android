package ru.sapn.vpn.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.sapn.vpn.R
import ru.sapn.vpn.domain.repository.AuthRepository

data class AuthUiState(
    val login: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

class AuthViewModel(
    app: Application,
    private val authRepository: AuthRepository,
) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    val isLoggedIn: StateFlow<Boolean> = authRepository.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onLoginChange(value: String) = _ui.update { it.copy(login = value, error = null) }
    fun onPasswordChange(value: String) = _ui.update { it.copy(password = value, error = null) }

    fun login() {
        val state = _ui.value
        if (state.login.isBlank() || state.password.isBlank()) {
            _ui.update { it.copy(error = getApplication<Application>().getString(R.string.auth_error_enter_credentials)) }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            authRepository.login(state.login.trim(), state.password)
                .onSuccess { _ui.update { s -> s.copy(loading = false, password = "") } }
                .onFailure { e ->
                    _ui.update { s -> s.copy(loading = false, error = e.message ?: getApplication<Application>().getString(R.string.auth_error_login_failed)) }
                }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    private inline fun MutableStateFlow<AuthUiState>.update(block: (AuthUiState) -> AuthUiState) {
        value = block(value)
    }

    class Factory(
        private val app: Application,
        private val authRepository: AuthRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AuthViewModel(app, authRepository) as T
    }
}
