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
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.provider.Settings
import ru.sapn.vpn.R
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
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium, color = Sapn.Frost)

        // ---- Язык / Language ----
        LanguageCard()

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
                    Text(stringResource(R.string.settings_kill_switch_title), color = Sapn.Frost, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.settings_kill_switch_desc),
                        color = Sapn.Mute,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(stringResource(R.string.settings_chevron), color = Sapn.Faint, style = MaterialTheme.typography.titleLarge)
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
                    Text(stringResource(R.string.settings_per_app_title), color = Sapn.Frost, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.settings_per_app_desc),
                        color = Sapn.Mute,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(stringResource(R.string.settings_chevron), color = Sapn.Faint, style = MaterialTheme.typography.titleLarge)
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
                    Text(stringResource(R.string.settings_russia_direct_title), color = Sapn.Frost, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.settings_russia_direct_desc),
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
            Eyebrow(stringResource(R.string.settings_direct_list_title))
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_direct_list_desc),
                color = Sapn.Mute,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.directList,
                onValueChange = viewModel::setDirectList,
                modifier = Modifier.fillMaxWidth().height(140.dp),
                singleLine = false,
                placeholder = { Text(stringResource(R.string.settings_direct_list_placeholder), color = Sapn.Faint) },
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
            stringResource(R.string.settings_proxy_note),
            color = Sapn.Faint,
            style = MaterialTheme.typography.bodySmall,
        )

        Button(
            onClick = viewModel::save,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Sapn.Ion, contentColor = Sapn.Void),
        ) { Text(if (state.saved) stringResource(R.string.settings_saved) else stringResource(R.string.settings_save)) }

        Text(
            stringResource(R.string.settings_apply_note),
            color = Sapn.Faint,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Карточка выбора языка приложения (System / Русский / English). Хранение per-app
 * локали бэкпортится appcompat (AppLocalesMetadataHolderService в манифесте), так
 * что AppCompatActivity не требуется.
 */
@Composable
private fun LanguageCard() {
    var showDialog by remember { mutableStateOf(false) }

    // "" — System (пустой список локалей), иначе тег языка ("ru"/"en").
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')
    val currentTag = when (current) {
        "ru" -> "ru"
        "en" -> "en"
        else -> ""
    }

    SapnCard(Modifier.fillMaxWidth().clickable { showDialog = true }) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_language_title), color = Sapn.Frost, style = MaterialTheme.typography.titleMedium)
                Text(
                    when (currentTag) {
                        "ru" -> stringResource(R.string.settings_language_russian)
                        "en" -> stringResource(R.string.settings_language_english)
                        else -> stringResource(R.string.settings_language_system)
                    },
                    color = Sapn.Mute,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(stringResource(R.string.settings_chevron), color = Sapn.Faint, style = MaterialTheme.typography.titleLarge)
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = Sapn.Slate,
            titleContentColor = Sapn.Frost,
            textContentColor = Sapn.Mute,
            title = { Text(stringResource(R.string.settings_language_dialog_title)) },
            text = {
                Column {
                    LanguageOption(stringResource(R.string.settings_language_system), currentTag == "") {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                        showDialog = false
                    }
                    LanguageOption(stringResource(R.string.settings_language_russian), currentTag == "ru") {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ru"))
                        showDialog = false
                    }
                    LanguageOption(stringResource(R.string.settings_language_english), currentTag == "en") {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                        showDialog = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.custom_dialog_cancel), color = Sapn.Mute)
                }
            },
        )
    }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Sapn.Ion, unselectedColor = Sapn.Faint),
        )
        Text(label, color = Sapn.Frost, style = MaterialTheme.typography.bodyLarge)
    }
}
