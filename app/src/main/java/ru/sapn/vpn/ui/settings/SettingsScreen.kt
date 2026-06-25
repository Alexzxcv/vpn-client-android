package ru.sapn.vpn.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.provider.Settings
import ru.sapn.vpn.ui.components.Eyebrow
import ru.sapn.vpn.ui.components.SapnCard
import ru.sapn.vpn.ui.theme.Sapn

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onOpenPerApp: () -> Unit = {}) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium, color = Sapn.Frost)

        // ---- Kill switch / Always-on ----
        SapnCard(Modifier.fillMaxWidth().clickable {
            val i = Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(i) }
        }) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Kill switch (Always-on)", color = Sapn.Frost, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Открыть системные настройки VPN. Включите «Постоянный VPN» и «Блокировать соединения без VPN» — трафик не пойдёт в обход туннеля.",
                        color = Sapn.Mute,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text("›", color = Sapn.Faint, style = MaterialTheme.typography.titleLarge)
            }
        }

        // ---- Приложения через VPN (per-app) ----
        SapnCard(Modifier.fillMaxWidth().clickable(onClick = onOpenPerApp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Приложения через VPN", color = Sapn.Frost, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Выбрать, какие приложения идут через туннель (split tunneling)",
                        color = Sapn.Mute,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text("›", color = Sapn.Faint, style = MaterialTheme.typography.titleLarge)
            }
        }

        // ---- Российские сайты напрямую ----
        SapnCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Российские сайты напрямую", color = Sapn.Frost, style = MaterialTheme.typography.titleMedium)
                    Text(
                        ".ru / .рф / .su идут мимо туннеля",
                        color = Sapn.Mute,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = state.russiaDirect,
                    onCheckedChange = viewModel::setRussiaDirect,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Sapn.Void,
                        checkedTrackColor = Sapn.Ion,
                        uncheckedThumbColor = Sapn.Mute,
                        uncheckedTrackColor = Sapn.Elevated,
                        uncheckedBorderColor = Sapn.Hairline,
                    ),
                )
            }
        }

        // ---- Direct list (split tunnel) ----
        SapnCard(Modifier.fillMaxWidth()) {
            Eyebrow("Direct list (split tunnel)")
            Spacer(Modifier.height(6.dp))
            Text(
                "По одной записи в строке: домены (example.com, .ru) или IP/CIDR (10.0.0.0/8). Идут напрямую, мимо туннеля.",
                color = Sapn.Mute,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.directList,
                onValueChange = viewModel::setDirectList,
                modifier = Modifier.fillMaxWidth().height(140.dp),
                singleLine = false,
                placeholder = { Text(".ru\nexample.com\n10.0.0.0/8", color = Sapn.Faint) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Sapn.Ion,
                    unfocusedBorderColor = Sapn.Hairline,
                    focusedTextColor = Sapn.Frost,
                    unfocusedTextColor = Sapn.Frost,
                    cursorColor = Sapn.Ion,
                ),
            )
        }

        Text(
            "Порт локального прокси на Android не настраивается: туннель полностью на стороне sing-box.",
            color = Sapn.Faint,
            style = MaterialTheme.typography.bodySmall,
        )

        Button(
            onClick = viewModel::save,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Sapn.Ion, contentColor = Sapn.Void),
        ) { Text(if (state.saved) "Сохранено ✓" else "Сохранить") }

        Text(
            "Изменения применяются при следующем подключении.",
            color = Sapn.Faint,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
