package ru.sapn.vpn.ui.connection

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.sapn.vpn.domain.model.Location
import ru.sapn.vpn.domain.model.Subscription
import ru.sapn.vpn.domain.vpn.VpnState
import ru.sapn.vpn.ui.components.Eyebrow
import ru.sapn.vpn.ui.components.Metric
import ru.sapn.vpn.ui.components.SapnCard
import ru.sapn.vpn.ui.components.formatBytes
import ru.sapn.vpn.ui.theme.Sapn

@Composable
fun ConnectionScreen(viewModel: ConnectionViewModel) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val vpnState by viewModel.vpnState.collectAsStateWithLifecycle()
    val vpnError by viewModel.vpnError.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.load()
        viewModel.checkForUpdate()
    }

    val vpnPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onVpnPermissionResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(state.needsVpnPermission) {
        if (state.needsVpnPermission) {
            val intent: Intent? = VpnService.prepare(context)
            if (intent != null) vpnPermLauncher.launch(intent)
            else viewModel.onVpnPermissionResult(true)
        }
    }

    val connected = vpnState == VpnState.CONNECTED || vpnState == VpnState.CONNECTING
    val selectedLoc = state.locations.firstOrNull { it.id == state.selectedLocationId }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // --- Бренд-шапка ---
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "SAPN",
                style = MaterialTheme.typography.titleLarge,
                color = Sapn.Frost,
            )
            Text("VPN", style = MaterialTheme.typography.labelLarge, color = Sapn.Faint)
        }

        Spacer(Modifier.height(8.dp))

        // --- Баннер обновления ---
        update?.let { upd ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                color = Sapn.Ion.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, Sapn.Ion.copy(alpha = 0.4f)),
            ) {
                Row(
                    Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Доступно обновление v${upd.versionName}",
                        color = Sapn.Frost,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.dismissUpdate() }) { Text("Позже", color = Sapn.Mute) }
                    TextButton(onClick = {
                        val i = Intent(Intent.ACTION_VIEW, Uri.parse(upd.apkUrl))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(i) }
                    }) { Text("Обновить", color = Sapn.Ion) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(20.dp))

        // --- Диск подключения ---
        ConnectDial(
            state = vpnState,
            enabled = !state.loading && state.selectedLocationId != null,
            onClick = { if (connected) viewModel.disconnect() else viewModel.connect() },
        )

        Spacer(Modifier.height(16.dp))

        // --- Выбранная нода + её пинг ---
        if (selectedLoc != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selectedLoc.name,
                    color = if (vpnState == VpnState.CONNECTED) Sapn.Ok else Sapn.Frost,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selectedLoc.pingMs > 0) {
                    Text("   ·   ${selectedLoc.pingMs} ms", color = Sapn.Mute, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        val shownError = state.error ?: vpnError?.takeIf { vpnState == VpnState.ERROR }
        if (shownError != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Ошибка: $shownError",
                color = Sapn.Alert,
                style = MaterialTheme.typography.bodySmall.copy(color = Sapn.Alert),
            )
        }

        Spacer(Modifier.height(24.dp))

        // --- Метрики подписки ---
        SubscriptionStrip(state.subscription)

        Spacer(Modifier.height(20.dp))

        // --- Локации ---
        Eyebrow("Локация", Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        if (state.locations.isEmpty()) {
            Text("Нет доступных локаций", color = Sapn.Mute, style = MaterialTheme.typography.bodySmall)
        }
        state.locations.forEach { loc ->
            LocationRow(
                loc = loc,
                selected = loc.id == state.selectedLocationId,
                onClick = { viewModel.selectLocation(loc.id) },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ConnectDial(state: VpnState, enabled: Boolean, onClick: () -> Unit) {
    val ring = when (state) {
        VpnState.CONNECTED -> Sapn.Ok
        VpnState.CONNECTING -> Sapn.Ion
        VpnState.ERROR -> Sapn.Alert
        else -> Sapn.Hairline
    }
    Box(
        modifier = Modifier
            .size(196.dp)
            .clip(CircleShape)
            .background(ring.copy(alpha = 0.06f))
            .border(2.dp, ring, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (state == VpnState.CONNECTING) {
                CircularProgressIndicator(color = Sapn.Ion, strokeWidth = 2.dp, modifier = Modifier.size(44.dp))
            } else {
                Icon(
                    Icons.Outlined.PowerSettingsNew,
                    contentDescription = null,
                    tint = if (state == VpnState.CONNECTED) Sapn.Ok else Sapn.Frost,
                    modifier = Modifier.size(52.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(stateLabel(state).uppercase(), color = ring, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SubscriptionStrip(sub: Subscription?) {
    SapnCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val plan = sub?.plan?.replaceFirstChar { it.uppercase() } ?: "—"
            val active = sub?.active == true
            Metric(
                "Тариф",
                if (sub == null) "—" else "$plan",
                valueColor = if (active) Sapn.Frost else Sapn.Warn,
            )
            Metric(
                "Трафик",
                if (sub == null) "—" else "${formatBytes(sub.trafficUsedBytes)} / ${formatBytes(sub.trafficLimitBytes)}",
            )
            Metric(
                "Устройства",
                if (sub == null) "—" else "${sub.deviceLimit}",
            )
        }
        if (sub != null && !sub.active) {
            Spacer(Modifier.height(10.dp))
            Text("Подписка неактивна", color = Sapn.Warn, style = MaterialTheme.typography.bodySmall.copy(color = Sapn.Warn))
        }
    }
}

@Composable
private fun LocationRow(loc: Location, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) Sapn.Ion else Sapn.Hairline
    val bg = if (selected) Sapn.Ion.copy(alpha = 0.07f) else Sapn.Slate
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (selected) Sapn.Ion else Sapn.Faint),
            )
            Spacer(Modifier.height(0.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(loc.name, color = Sapn.Frost, style = MaterialTheme.typography.bodyLarge)
                Text(loc.location, color = Sapn.Mute, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                if (loc.pingMs > 0) "${loc.pingMs} ms" else "—",
                color = if (selected) Sapn.Ion else Sapn.Mute,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private fun stateLabel(s: VpnState): String = when (s) {
    VpnState.DISCONNECTED -> "Отключено"
    VpnState.CONNECTING -> "Подключение"
    VpnState.CONNECTED -> "Подключено"
    VpnState.ERROR -> "Ошибка"
}
