package ru.sapn.vpn.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.sapn.vpn.ui.theme.Sapn

/** Резкоугольная карточка в духе SAPN: Slate-фон, hairline-граница, без тени. */
@Composable
fun SapnCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = Sapn.Slate,
        border = BorderStroke(1.dp, Sapn.Hairline),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** Маленький uppercase-лейбл секции (eyebrow). */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = Sapn.Faint,
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        modifier = modifier,
    )
}

/** Пара «лейбл сверху / значение снизу» для плотных метрик (tabular). */
@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier, valueColor: androidx.compose.ui.graphics.Color = Sapn.Frost) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Eyebrow(label)
        Text(
            value,
            color = valueColor,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Человекочитаемый размер. Очень большие лимиты (God/Pro ~1ПБ) → «∞». */
fun formatBytes(b: Long): String {
    if (b <= 0) return "0 B"
    if (b >= 100L * 1024 * 1024 * 1024 * 1024) return "∞"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var v = b.toDouble()
    var i = 0
    while (v >= 1024 && i < units.size - 1) {
        v /= 1024; i++
    }
    return if (i == 0) "$b B" else String.format("%.1f %s", v, units[i])
}

/** Строка «ключ — значение» в одну линию. */
@Composable
fun KeyValueRow(key: String, value: String, valueColor: androidx.compose.ui.graphics.Color = Sapn.Frost) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, color = Sapn.Mute, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        Text(value, color = valueColor, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
    }
}
