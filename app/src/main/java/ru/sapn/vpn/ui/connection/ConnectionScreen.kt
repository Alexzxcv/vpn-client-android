package ru.sapn.vpn.ui.connection

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.sapn.vpn.domain.vpn.VpnState

@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel,
    onLogout: () -> Unit,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val vpnState by viewModel.vpnState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.load() }

    // Системный диалог разрешения VPN.
    val vpnPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onVpnPermissionResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(state.needsVpnPermission) {
        if (state.needsVpnPermission) {
            val intent: Intent? = VpnService.prepare(context)
            if (intent != null) {
                vpnPermLauncher.launch(intent)
            } else {
                // Разрешение уже выдано.
                viewModel.onVpnPermissionResult(true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("SAPN VPN", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Выйти",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onLogout),
            )
        }

        Spacer(Modifier.height(16.dp))

        // Подписка.
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Подписка", style = MaterialTheme.typography.labelLarge)
                val sub = state.subscription
                if (sub == null) {
                    Text("—")
                } else {
                    Text(
                        "${sub.plan} • ${if (sub.active) "активна" else "неактивна"}",
                        fontWeight = FontWeight.SemiBold,
                    )
                    sub.expiresAt?.let { Text("до $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("Локация", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        state.locations.forEach { loc ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = loc.id == state.selectedLocationId,
                        onClick = { viewModel.selectLocation(loc.id) },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = loc.id == state.selectedLocationId,
                    onClick = { viewModel.selectLocation(loc.id) },
                )
                Text("${loc.name} (${loc.location})")
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Статус: ${vpnState.label()}",
            style = MaterialTheme.typography.bodyLarge,
        )

        if (state.error != null) {
            Spacer(Modifier.height(8.dp))
            Text(state.error!!, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(16.dp))

        val connected = vpnState == VpnState.CONNECTED || vpnState == VpnState.CONNECTING
        Button(
            onClick = { if (connected) viewModel.disconnect() else viewModel.connect() },
            enabled = !state.loading && state.selectedLocationId != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.loading || vpnState == VpnState.CONNECTING) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp))
            } else {
                Text(if (connected) "Отключить" else "Подключить")
            }
        }
    }
}

private fun VpnState.label(): String = when (this) {
    VpnState.DISCONNECTED -> "отключено"
    VpnState.CONNECTING -> "подключение…"
    VpnState.CONNECTED -> "подключено"
    VpnState.ERROR -> "ошибка"
}
