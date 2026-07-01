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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import ru.sapn.vpn.R
import ru.sapn.vpn.domain.model.CustomServer
import ru.sapn.vpn.domain.model.Location
import ru.sapn.vpn.domain.model.Subscription
import ru.sapn.vpn.domain.vpn.VpnState
import ru.sapn.vpn.ui.components.Eyebrow
import ru.sapn.vpn.ui.components.Metric
import ru.sapn.vpn.ui.components.SapnCard
import ru.sapn.vpn.ui.components.formatBytes
import ru.sapn.vpn.ui.theme.Sapn
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun ConnectionScreen(viewModel: ConnectionViewModel) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val vpnState by viewModel.vpnState.collectAsStateWithLifecycle()
    val vpnError by viewModel.vpnError.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val downloadingUpdate by viewModel.downloadingUpdate.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.load()
        viewModel.checkForUpdate()
    }

    // Живой поллинг трафика/устройств, пока экран виден (STARTED) — иначе цифры
    // на главном замирали до переоткрытия приложения. В фоне не тикает.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(5_000)
                viewModel.refreshUsage()
            }
        }
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

    var showAddDialog by remember { mutableStateOf(false) }

    val connected = vpnState == VpnState.CONNECTED || vpnState == VpnState.CONNECTING
    val selectedLoc = state.locations.firstOrNull { it.id == state.selectedLocationId }
    val selectedCustom = state.customServers.firstOrNull { "custom:${it.id}" == state.selectedLocationId }

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
                        stringResource(R.string.connect_update_available, upd.versionName),
                        color = Sapn.Frost,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { viewModel.dismissUpdate() },
                        enabled = !downloadingUpdate,
                    ) { Text(stringResource(R.string.connect_update_later), color = Sapn.Mute) }
                    TextButton(
                        onClick = { viewModel.applyUpdate() },
                        enabled = !downloadingUpdate,
                    ) {
                        Text(
                            stringResource(
                                if (downloadingUpdate) R.string.update_downloading
                                else R.string.connect_update_now,
                            ),
                            color = if (downloadingUpdate) Sapn.Mute else Sapn.Ion,
                        )
                    }
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
                    Text(stringResource(R.string.connect_ping_ms, selectedLoc.pingMs), color = Sapn.Mute, style = MaterialTheme.typography.labelLarge)
                }
            }
        } else if (selectedCustom != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selectedCustom.name,
                    color = if (vpnState == VpnState.CONNECTED) Sapn.Ok else Sapn.Frost,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Пинг кастомной ноды известен только когда мы к ней подключены.
                if (state.customPingMs > 0) {
                    Text(stringResource(R.string.connect_ping_ms, state.customPingMs), color = Sapn.Mute, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        val shownError = state.error ?: vpnError?.takeIf { vpnState == VpnState.ERROR }
        if (shownError != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.connect_error_prefix, shownError),
                color = Sapn.Alert,
                style = MaterialTheme.typography.bodySmall.copy(color = Sapn.Alert),
            )
        }

        Spacer(Modifier.height(24.dp))

        // --- Метрики подписки ---
        SubscriptionStrip(state.subscription, state.devicesUsed)

        Spacer(Modifier.height(20.dp))

        // --- Локации ---
        Eyebrow(stringResource(R.string.connect_section_location), Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        if (state.locations.isEmpty()) {
            Text(stringResource(R.string.connect_no_locations), color = Sapn.Mute, style = MaterialTheme.typography.bodySmall)
        }
        state.locations.forEach { loc ->
            LocationRow(
                loc = loc,
                selected = loc.id == state.selectedLocationId,
                onClick = { viewModel.selectLocation(loc.id) },
            )
            Spacer(Modifier.height(8.dp))
        }

        // --- Свои конфиги (custom VLESS) ---
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Eyebrow(stringResource(R.string.connect_section_custom))
            Text(
                stringResource(R.string.connect_add),
                color = Sapn.Ion,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable { showAddDialog = true },
            )
        }
        Spacer(Modifier.height(10.dp))
        if (state.customServers.isEmpty()) {
            Text(
                stringResource(R.string.connect_custom_hint),
                color = Sapn.Mute,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        state.customServers.forEach { cs ->
            val isSel = state.selectedLocationId == "custom:${cs.id}"
            CustomRow(
                server = cs,
                selected = isSel,
                // Пинг показываем только у сервера текущей сессии (к нему подключены).
                pingMs = if (isSel && vpnState == VpnState.CONNECTED) state.customPingMs else 0,
                onClick = { viewModel.selectLocation("custom:${cs.id}") },
                onDelete = { viewModel.removeCustomServer(cs.id) },
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showAddDialog) {
        val sizeAtOpen = remember { state.customServers.size }
        AddCustomDialog(
            error = state.customError,
            onAdd = { link -> viewModel.addCustomServer(link) },
            onDismiss = { viewModel.clearCustomError(); showAddDialog = false },
        )
        // Закрываем диалог, как только конфиг успешно добавился (список вырос).
        LaunchedEffect(state.customServers.size) {
            if (state.customServers.size > sizeAtOpen) {
                viewModel.clearCustomError()
                showAddDialog = false
            }
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
            Text(stringResource(stateLabel(state)).uppercase(), color = ring, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SubscriptionStrip(sub: Subscription?, devicesUsed: Int) {
    SapnCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val dash = stringResource(R.string.connect_dash)
            val active = sub?.active == true
            // Тариф: всегда один из FREE / BASIC / STANDARD / PRO. Нет активной
            // платной подписки → FREE.
            val planLabel = if (active) (sub?.plan ?: "").uppercase() else "FREE"
            val planColor = when (planLabel) {
                "BASIC" -> Sapn.Ion       // светло-синий
                "STANDARD" -> Sapn.Ok     // зелёный
                "PRO" -> Sapn.Gold        // золотой
                else -> Sapn.Mute         // FREE — серый
            }
            Metric(
                stringResource(R.string.connect_subscription_plan),
                planLabel,
                valueColor = planColor,
            )
            // Трафик: у платных — лимит тарифа; у free — бесплатный суточный (1ГБ/день).
            val trafficText = when {
                sub == null -> dash
                active -> "${formatBytes(sub.trafficUsedBytes)} / ${formatBytes(sub.trafficLimitBytes)}"
                else -> "${formatBytes(sub.freeDailyUsedBytes)} / ${formatBytes(sub.freeDailyLimitBytes)}"
            }
            Metric(stringResource(R.string.connect_subscription_traffic), trafficText)
            // Устройства: использовано / лимит. Free → 0/0 (платных слотов нет).
            Metric(
                stringResource(R.string.connect_subscription_devices),
                if (active) "$devicesUsed/${sub?.deviceLimit ?: 0}" else "0/0",
            )
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
                if (loc.pingMs > 0) stringResource(R.string.connect_ms, loc.pingMs) else stringResource(R.string.connect_dash),
                color = if (selected) Sapn.Ion else Sapn.Mute,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun CustomRow(server: CustomServer, selected: Boolean, pingMs: Int, onClick: () -> Unit, onDelete: () -> Unit) {
    val border = if (selected) Sapn.Ion else Sapn.Hairline
    val bg = if (selected) Sapn.Ion.copy(alpha = 0.07f) else Sapn.Slate
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (selected) Sapn.Ion else Sapn.Faint))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(server.name, color = Sapn.Frost, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${server.config.host}:${server.config.port}", color = Sapn.Mute, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (pingMs > 0) {
                Text(
                    stringResource(R.string.connect_ms, pingMs),
                    color = Sapn.Mute,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Icon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.custom_delete),
                tint = Sapn.Faint,
                modifier = Modifier.size(20.dp).clickable(onClick = onDelete),
            )
        }
    }
}

@Composable
private fun AddCustomDialog(error: String?, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var link by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val scanPrompt = stringResource(R.string.custom_dialog_scan_prompt)
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { link = it }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Sapn.Slate,
        titleContentColor = Sapn.Frost,
        textContentColor = Sapn.Mute,
        title = { Text(stringResource(R.string.custom_dialog_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.custom_dialog_desc),
                    color = Sapn.Mute,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.custom_dialog_placeholder), color = Sapn.Faint) },
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Sapn.Ion,
                        unfocusedBorderColor = Sapn.Hairline,
                        focusedTextColor = Sapn.Frost,
                        unfocusedTextColor = Sapn.Frost,
                        cursorColor = Sapn.Ion,
                    ),
                )
                Spacer(Modifier.height(6.dp))
                Row {
                    TextButton(onClick = { clipboard.getText()?.text?.let { link = it } }) {
                        Text(stringResource(R.string.custom_dialog_paste), color = Sapn.Ion, style = MaterialTheme.typography.bodySmall.copy(color = Sapn.Ion))
                    }
                    TextButton(onClick = {
                        val opts = ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setOrientationLocked(false)
                            .setBeepEnabled(false)
                            .setPrompt(scanPrompt)
                        scanLauncher.launch(opts)
                    }) {
                        Text(stringResource(R.string.custom_dialog_scan_qr), color = Sapn.Ion, style = MaterialTheme.typography.bodySmall.copy(color = Sapn.Ion))
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(error, color = Sapn.Alert, style = MaterialTheme.typography.bodySmall.copy(color = Sapn.Alert))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (link.isNotBlank()) onAdd(link) }) {
                Text(stringResource(R.string.custom_dialog_add), color = Sapn.Ion)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.custom_dialog_cancel), color = Sapn.Mute) }
        },
    )
}

private fun stateLabel(s: VpnState): Int = when (s) {
    VpnState.DISCONNECTED -> R.string.connect_state_disconnected
    VpnState.CONNECTING -> R.string.connect_state_connecting
    VpnState.CONNECTED -> R.string.connect_state_connected
    VpnState.ERROR -> R.string.connect_state_error
}
