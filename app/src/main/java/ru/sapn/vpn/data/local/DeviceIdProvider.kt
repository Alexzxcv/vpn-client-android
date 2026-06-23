package ru.sapn.vpn.data.local

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

/**
 * Стабильный идентификатор устройства для привязки (POST /devices).
 *
 * Берём ANDROID_ID и хешируем (SHA-256), чтобы не отдавать сырой идентификатор
 * на сервер. ANDROID_ID стабилен в пределах установки приложения и обычно
 * переживает обновления. На сброс к заводским/переустановку он меняется — это
 * приемлемо: устройство просто привяжется заново в рамках лимита тарифа.
 */
object DeviceIdProvider {

    @SuppressLint("HardwareIds")
    fun deviceId(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        ) ?: "unknown"
        return sha256(androidId).take(32)
    }

    /** Человекочитаемое имя устройства для UI/привязки. */
    fun deviceName(): String =
        listOf(android.os.Build.MANUFACTURER, android.os.Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android device" }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
