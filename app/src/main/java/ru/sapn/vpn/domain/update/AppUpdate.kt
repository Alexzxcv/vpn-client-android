package ru.sapn.vpn.domain.update

/**
 * Описание доступного обновления приложения (последний релиз на GitHub).
 *
 * @param versionName версия из тега релиза без префикса «v» (например, "0.2.0").
 * @param tag         исходный тег релиза (например, "v0.2.0").
 * @param apkUrl      прямой URL .apk-ассета релиза (browser_download_url).
 */
data class AppUpdate(
    val versionName: String,
    val tag: String,
    val apkUrl: String,
)

/** Источник информации об обновлениях (GitHub Releases). */
interface UpdateRepository {
    /**
     * Проверить, есть ли релиз новее текущей версии [currentVersionName].
     * Любые сетевые ошибки/лимиты GitHub — это `Result.success(null)` (тихо
     * пропускаем), сбой не должен ронять приложение.
     */
    suspend fun checkForUpdate(currentVersionName: String): Result<AppUpdate?>
}
