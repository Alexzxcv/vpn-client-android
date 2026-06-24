package ru.sapn.vpn.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.sapn.vpn.domain.model.Device
import ru.sapn.vpn.ui.components.Eyebrow
import ru.sapn.vpn.ui.components.SapnCard
import ru.sapn.vpn.ui.theme.Sapn

@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    onLogout: () -> Unit,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Аккаунт", style = MaterialTheme.typography.headlineMedium, color = Sapn.Frost)
            TextButton(onClick = onLogout) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = Sapn.Mute, modifier = Modifier.height(18.dp))
                Spacer(Modifier.height(0.dp))
                Text("  Выйти", color = Sapn.Mute)
            }
        }

        // ---- Профиль ----
        SapnCard(Modifier.fillMaxWidth()) {
            Eyebrow("Профиль")
            Spacer(Modifier.height(12.dp))
            Field(state.email, viewModel::onEmailChange, "Email")
            Spacer(Modifier.height(8.dp))
            Field(state.username, viewModel::onUsernameChange, "Логин")
            state.profileError?.let { Note(it, Sapn.Alert) }
            if (state.profileSaved) Note("Сохранено", Sapn.Ok)
            Spacer(Modifier.height(14.dp))
            Primary("Сохранить профиль", enabled = !state.loading, onClick = viewModel::saveProfile)
        }

        // ---- Смена пароля ----
        SapnCard(Modifier.fillMaxWidth()) {
            Eyebrow("Смена пароля")
            Spacer(Modifier.height(12.dp))
            Field(state.currentPassword, viewModel::onCurrentPasswordChange, "Текущий пароль", password = true)
            Spacer(Modifier.height(8.dp))
            Field(state.newPassword, viewModel::onNewPasswordChange, "Новый пароль", password = true)
            Spacer(Modifier.height(8.dp))
            Field(state.newPasswordRepeat, viewModel::onNewPasswordRepeatChange, "Повторите новый пароль", password = true)
            state.passwordError?.let { Note(it, Sapn.Alert) }
            if (state.passwordSaved) Note("Пароль изменён", Sapn.Ok)
            Spacer(Modifier.height(14.dp))
            Primary("Изменить пароль", enabled = !state.loading, onClick = viewModel::changePassword)
        }

        // ---- Устройства ----
        SapnCard(Modifier.fillMaxWidth()) {
            Eyebrow("Устройства")
            state.devicesError?.let { Note(it, Sapn.Alert) }
            if (state.devices.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Нет привязанных устройств", color = Sapn.Mute, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            state.devices.forEachIndexed { index, device ->
                if (index > 0) HorizontalDivider(color = Sapn.Hairline)
                DeviceRow(device = device, onRevoke = { viewModel.revokeDevice(device.deviceId) })
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    password: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Sapn.Ion,
            unfocusedBorderColor = Sapn.Hairline,
            focusedLabelColor = Sapn.Ion,
            unfocusedLabelColor = Sapn.Mute,
            focusedTextColor = Sapn.Frost,
            unfocusedTextColor = Sapn.Frost,
            cursorColor = Sapn.Ion,
        ),
    )
}

@Composable
private fun Primary(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Sapn.Ion, contentColor = Sapn.Void),
    ) { Text(text) }
}

@Composable
private fun Note(text: String, color: androidx.compose.ui.graphics.Color) {
    Spacer(Modifier.height(8.dp))
    Text(text, color = color, style = MaterialTheme.typography.bodySmall.copy(color = color))
}

@Composable
private fun DeviceRow(device: Device, onRevoke: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(device.name.ifBlank { "Устройство" }, color = Sapn.Frost, style = MaterialTheme.typography.bodyLarge)
            Text(
                buildString {
                    append(device.platform.ifBlank { "—" })
                    if (device.blocked) append(" • заблокировано")
                    device.lastSeenAt?.let { append(" • активно $it") }
                },
                color = Sapn.Mute,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = onRevoke) {
            Icon(Icons.Outlined.Delete, contentDescription = "Отозвать устройство", tint = Sapn.Alert)
        }
    }
}
