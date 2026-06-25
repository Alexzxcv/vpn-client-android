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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.sapn.vpn.R
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
            Text(stringResource(R.string.account_title), style = MaterialTheme.typography.headlineMedium, color = Sapn.Frost)
            TextButton(onClick = onLogout) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = Sapn.Mute, modifier = Modifier.height(18.dp))
                Spacer(Modifier.height(0.dp))
                Text("  " + stringResource(R.string.account_logout), color = Sapn.Mute)
            }
        }

        // ---- Профиль ----
        SapnCard(Modifier.fillMaxWidth()) {
            Eyebrow(stringResource(R.string.account_section_profile))
            Spacer(Modifier.height(12.dp))
            Field(state.email, viewModel::onEmailChange, stringResource(R.string.account_field_email))
            Spacer(Modifier.height(8.dp))
            Field(state.username, viewModel::onUsernameChange, stringResource(R.string.account_field_username))
            state.profileError?.let { Note(it, Sapn.Alert) }
            if (state.profileSaved) Note(stringResource(R.string.account_saved), Sapn.Ok)
            Spacer(Modifier.height(14.dp))
            Primary(stringResource(R.string.account_save_profile), enabled = !state.loading, onClick = viewModel::saveProfile)
        }

        // ---- Смена пароля ----
        SapnCard(Modifier.fillMaxWidth()) {
            Eyebrow(stringResource(R.string.account_section_password))
            Spacer(Modifier.height(12.dp))
            Field(state.currentPassword, viewModel::onCurrentPasswordChange, stringResource(R.string.account_field_current_password), password = true)
            Spacer(Modifier.height(8.dp))
            Field(state.newPassword, viewModel::onNewPasswordChange, stringResource(R.string.account_field_new_password), password = true)
            Spacer(Modifier.height(8.dp))
            Field(state.newPasswordRepeat, viewModel::onNewPasswordRepeatChange, stringResource(R.string.account_field_repeat_password), password = true)
            state.passwordError?.let { Note(it, Sapn.Alert) }
            if (state.passwordSaved) Note(stringResource(R.string.account_password_changed), Sapn.Ok)
            Spacer(Modifier.height(14.dp))
            Primary(stringResource(R.string.account_change_password), enabled = !state.loading, onClick = viewModel::changePassword)
        }

        // ---- Устройства ----
        SapnCard(Modifier.fillMaxWidth()) {
            Eyebrow(stringResource(R.string.account_section_devices))
            state.devicesError?.let { Note(it, Sapn.Alert) }
            if (state.devices.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.account_no_devices), color = Sapn.Mute, style = MaterialTheme.typography.bodySmall)
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
        val sep = stringResource(R.string.account_device_separator)
        val dash = stringResource(R.string.connect_dash)
        val unnamed = stringResource(R.string.account_device_unnamed)
        val blockedLabel = stringResource(R.string.account_device_blocked)
        val lastSeenLabel = device.lastSeenAt?.let { stringResource(R.string.account_device_last_seen, it) }
        Column(Modifier.weight(1f)) {
            Text(device.name.ifBlank { unnamed }, color = Sapn.Frost, style = MaterialTheme.typography.bodyLarge)
            Text(
                buildString {
                    append(device.platform.ifBlank { dash })
                    if (device.blocked) append(sep + blockedLabel)
                    lastSeenLabel?.let { append(sep + it) }
                },
                color = Sapn.Mute,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = onRevoke) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.account_revoke_device), tint = Sapn.Alert)
        }
    }
}
