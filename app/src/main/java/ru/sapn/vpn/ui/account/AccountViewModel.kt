package ru.sapn.vpn.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.sapn.vpn.data.remote.ApiException
import ru.sapn.vpn.domain.model.Device
import ru.sapn.vpn.domain.model.User
import ru.sapn.vpn.domain.repository.AccountRepository

data class AccountUiState(
    val loading: Boolean = false,
    val user: User? = null,
    val devices: List<Device> = emptyList(),
    // Поля редактирования профиля.
    val email: String = "",
    val username: String = "",
    // Поля смены пароля.
    val currentPassword: String = "",
    val newPassword: String = "",
    val newPasswordRepeat: String = "",
    // Сообщения.
    val profileError: String? = null,
    val profileSaved: Boolean = false,
    val passwordError: String? = null,
    val passwordSaved: Boolean = false,
    val devicesError: String? = null,
)

/**
 * Экран «Аккаунт»: профиль (email/username), смена пароля, список/отзыв устройств.
 * Различает 409 (конфликт — email/username занят) и 401 (текущий пароль неверный).
 */
class AccountViewModel(
    private val repo: AccountRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(AccountUiState())
    val ui: StateFlow<AccountUiState> = _ui.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, devicesError = null) }
            repo.me().onSuccess { user ->
                _ui.update {
                    it.copy(
                        loading = false,
                        user = user,
                        email = user.email,
                        username = user.username.orEmpty(),
                    )
                }
            }.onFailure { e ->
                _ui.update { it.copy(loading = false, profileError = e.userMessage()) }
            }
            refreshDevices()
        }
    }

    fun refreshDevices() {
        viewModelScope.launch {
            repo.devices()
                .onSuccess { list -> _ui.update { it.copy(devices = list, devicesError = null) } }
                .onFailure { e -> _ui.update { it.copy(devicesError = e.userMessage()) } }
        }
    }

    fun onEmailChange(v: String) = _ui.update { it.copy(email = v, profileError = null, profileSaved = false) }
    fun onUsernameChange(v: String) = _ui.update { it.copy(username = v, profileError = null, profileSaved = false) }

    fun onCurrentPasswordChange(v: String) = _ui.update { it.copy(currentPassword = v, passwordError = null, passwordSaved = false) }
    fun onNewPasswordChange(v: String) = _ui.update { it.copy(newPassword = v, passwordError = null, passwordSaved = false) }
    fun onNewPasswordRepeatChange(v: String) = _ui.update { it.copy(newPasswordRepeat = v, passwordError = null, passwordSaved = false) }

    fun saveProfile() {
        val s = _ui.value
        val email = s.email.trim()
        val username = s.username.trim()
        if (email.isBlank() && username.isBlank()) {
            _ui.update { it.copy(profileError = "Заполните хотя бы одно поле") }
            return
        }
        if (email.isNotBlank() && !email.contains("@")) {
            _ui.update { it.copy(profileError = "Некорректный email") }
            return
        }
        // Шлём только реально изменившиеся поля.
        val newEmail = email.takeIf { it.isNotBlank() && it != s.user?.email }
        val newUsername = username.takeIf { it.isNotBlank() && it != s.user?.username }
        if (newEmail == null && newUsername == null) {
            _ui.update { it.copy(profileError = "Нет изменений") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, profileError = null, profileSaved = false) }
            repo.updateProfile(newEmail, newUsername)
                .onSuccess { user ->
                    _ui.update {
                        it.copy(
                            loading = false,
                            user = user,
                            email = user.email,
                            username = user.username.orEmpty(),
                            profileSaved = true,
                        )
                    }
                }
                .onFailure { e ->
                    val msg = when ((e as? ApiException)?.code) {
                        409 -> "Email или логин уже заняты"
                        401 -> "Сессия истекла, войдите снова"
                        else -> e.userMessage()
                    }
                    _ui.update { it.copy(loading = false, profileError = msg) }
                }
        }
    }

    fun changePassword() {
        val s = _ui.value
        if (s.currentPassword.isBlank() || s.newPassword.isBlank()) {
            _ui.update { it.copy(passwordError = "Заполните все поля") }
            return
        }
        if (s.newPassword.length < 8) {
            _ui.update { it.copy(passwordError = "Новый пароль — минимум 8 символов") }
            return
        }
        if (s.newPassword != s.newPasswordRepeat) {
            _ui.update { it.copy(passwordError = "Пароли не совпадают") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, passwordError = null, passwordSaved = false) }
            repo.changePassword(s.currentPassword, s.newPassword)
                .onSuccess {
                    _ui.update {
                        it.copy(
                            loading = false,
                            passwordSaved = true,
                            currentPassword = "",
                            newPassword = "",
                            newPasswordRepeat = "",
                        )
                    }
                }
                .onFailure { e ->
                    val msg = when ((e as? ApiException)?.code) {
                        401, 403 -> "Текущий пароль неверный"
                        else -> e.userMessage()
                    }
                    _ui.update { it.copy(loading = false, passwordError = msg) }
                }
        }
    }

    fun revokeDevice(deviceId: String) {
        viewModelScope.launch {
            repo.revokeDevice(deviceId)
                .onSuccess { refreshDevices() }
                .onFailure { e -> _ui.update { it.copy(devicesError = e.userMessage()) } }
        }
    }

    private fun Throwable.userMessage(): String =
        (this as? ApiException)?.let { ex ->
            when (ex.code) {
                0 -> "Нет соединения"
                401 -> "Сессия истекла, войдите снова"
                else -> ex.message ?: "Ошибка"
            }
        } ?: (message ?: "Ошибка")

    class Factory(private val repo: AccountRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AccountViewModel(repo) as T
    }
}
