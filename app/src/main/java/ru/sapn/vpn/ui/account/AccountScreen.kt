package ru.sapn.vpn.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.sapn.vpn.domain.model.Device

@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Аккаунт", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = onBack) { Text("Назад") }
        }

        Spacer(Modifier.height(16.dp))

        // ---- Профиль ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Профиль", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.username,
                    onValueChange = viewModel::onUsernameChange,
                    label = { Text("Логин") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                state.profileError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                if (state.profileSaved) {
                    Spacer(Modifier.height(8.dp))
                    Text("Сохранено", color = MaterialTheme.colorScheme.primary)
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = viewModel::saveProfile,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Сохранить профиль") }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- Смена пароля ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Смена пароля", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.currentPassword,
                    onValueChange = viewModel::onCurrentPasswordChange,
                    label = { Text("Текущий пароль") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.newPassword,
                    onValueChange = viewModel::onNewPasswordChange,
                    label = { Text("Новый пароль") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.newPasswordRepeat,
                    onValueChange = viewModel::onNewPasswordRepeatChange,
                    label = { Text("Повторите новый пароль") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )

                state.passwordError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                if (state.passwordSaved) {
                    Spacer(Modifier.height(8.dp))
                    Text("Пароль изменён", color = MaterialTheme.colorScheme.primary)
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = viewModel::changePassword,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Изменить пароль") }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- Устройства ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Устройства", style = MaterialTheme.typography.titleMedium)
                state.devicesError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                if (state.devices.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Нет привязанных устройств", style = MaterialTheme.typography.bodySmall)
                }
                state.devices.forEachIndexed { index, device ->
                    if (index > 0) HorizontalDivider()
                    DeviceRow(device = device, onRevoke = { viewModel.revokeDevice(device.deviceId) })
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: Device, onRevoke: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                device.name.ifBlank { "Устройство" },
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                buildString {
                    append(device.platform.ifBlank { "—" })
                    if (device.blocked) append(" • заблокировано")
                    device.lastSeenAt?.let { append(" • активно $it") }
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = onRevoke) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Отозвать устройство",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
