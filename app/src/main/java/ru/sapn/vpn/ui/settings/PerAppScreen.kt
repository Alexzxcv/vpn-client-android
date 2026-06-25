package ru.sapn.vpn.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.sapn.vpn.R
import ru.sapn.vpn.domain.model.AppRoutingMode
import ru.sapn.vpn.ui.components.Eyebrow
import ru.sapn.vpn.ui.theme.Sapn

@Composable
fun PerAppScreen(viewModel: PerAppViewModel, onBack: () -> Unit) {
    val state by viewModel.ui.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.perapp_back),
                tint = Sapn.Frost,
                modifier = Modifier.height(22.dp).clickable(onClick = onBack),
            )
            Spacer(Modifier.fillMaxWidth(0f).height(0.dp))
            Text(
                "  " + stringResource(R.string.perapp_title),
                style = MaterialTheme.typography.titleLarge,
                color = Sapn.Frost,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Режим.
        Eyebrow(stringResource(R.string.perapp_section_mode))
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeRow(stringResource(R.string.perapp_mode_all), state.mode == AppRoutingMode.OFF) { viewModel.setMode(AppRoutingMode.OFF) }
            ModeRow(stringResource(R.string.perapp_mode_include), state.mode == AppRoutingMode.INCLUDE) { viewModel.setMode(AppRoutingMode.INCLUDE) }
            ModeRow(stringResource(R.string.perapp_mode_exclude), state.mode == AppRoutingMode.EXCLUDE) { viewModel.setMode(AppRoutingMode.EXCLUDE) }
        }

        if (state.mode != AppRoutingMode.OFF) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.perapp_search), color = Sapn.Faint) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Sapn.Ion,
                    unfocusedBorderColor = Sapn.Hairline,
                    focusedTextColor = Sapn.Frost,
                    unfocusedTextColor = Sapn.Frost,
                    cursorColor = Sapn.Ion,
                ),
            )
            Spacer(Modifier.height(12.dp))

            if (state.loading) {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = Sapn.Ion, strokeWidth = 2.dp, modifier = Modifier.height(28.dp))
                }
            } else {
                val q = state.query.trim().lowercase()
                val filtered = if (q.isEmpty()) state.apps
                else state.apps.filter { it.label.lowercase().contains(q) || it.packageName.contains(q) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppRow(
                            label = app.label,
                            pkg = app.packageName,
                            checked = state.selected.contains(app.packageName),
                            onToggle = { viewModel.toggle(app.packageName) },
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.perapp_off_hint),
                color = Sapn.Mute,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ModeRow(text: String, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) Sapn.Ion else Sapn.Hairline
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .background(if (selected) Sapn.Ion.copy(alpha = 0.07f) else Sapn.Slate, RoundedCornerShape(6.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = if (selected) Sapn.Ion else Sapn.Frost, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AppRow(label: String, pkg: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Sapn.Frost, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(pkg, color = Sapn.Mute, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = Sapn.Ion, uncheckedColor = Sapn.Faint, checkmarkColor = Sapn.Void),
        )
    }
}
