package ru.sapn.vpn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Палитра SAPN — строгая «приборная» тёмная тема (cold blue-black), единая с
 * веб-фронтами (см. docs/DESIGN_SYSTEM.md). Резкие углы, hairline-границы,
 * почти без теней. Доступна как [Sapn] из любого composable.
 */
object Sapn {
    val Void = Color(0xFF0A0C10)      // фон приложения
    val Slate = Color(0xFF12151C)     // карточки/поверхности
    val Elevated = Color(0xFF161B24)  // приподнятые поверхности (bottom bar)
    val Hairline = Color(0xFF222936)  // тонкие границы
    val Frost = Color(0xFFE6E8EC)     // основной текст
    val Mute = Color(0xFF8A93A6)      // вторичный текст
    val Faint = Color(0xFF55607A)     // очень тихий текст/иконки

    val Ion = Color(0xFF3DA9FC)       // акцент / тариф Basic
    val Ok = Color(0xFF36D399)        // подключено / успех / тариф Standard
    val Warn = Color(0xFFFBBD23)      // предупреждение
    val Alert = Color(0xFFF2555A)     // ошибка / отключить
    val Gold = Color(0xFFF0A030)      // тариф Pro (золото ближе к оранжевому)
}

private val SapnDark = darkColorScheme(
    primary = Sapn.Ion,
    onPrimary = Color(0xFF04121F),
    secondary = Sapn.Ion,
    background = Sapn.Void,
    onBackground = Sapn.Frost,
    surface = Sapn.Slate,
    onSurface = Sapn.Frost,
    surfaceVariant = Sapn.Elevated,
    onSurfaceVariant = Sapn.Mute,
    outline = Sapn.Hairline,
    error = Sapn.Alert,
    onError = Color.White,
)

private val SapnTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp, color = Sapn.Mute),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.sp),
)

/** Тема приложения — всегда тёмная (бренд-требование, без light/dynamic). */
@Composable
fun SapnTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SapnDark,
        typography = SapnTypography,
        content = content,
    )
}
