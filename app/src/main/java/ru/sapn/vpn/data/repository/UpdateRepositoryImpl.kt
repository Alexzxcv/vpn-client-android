package ru.sapn.vpn.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.sapn.vpn.data.remote.GitHubReleaseDto
import ru.sapn.vpn.domain.update.AppUpdate
import ru.sapn.vpn.domain.update.UpdateRepository

/**
 * Проверка обновлений через публичный GitHub Releases API (без авторизации).
 *
 * Намеренно не используем Retrofit-клиент приложения: запрос идёт на другой хост
 * (api.github.com), без Bearer-токена нашего бэкенда. Берём отдельный OkHttp.
 *
 * Любая ошибка (сеть, rate-limit 403/429, кривой JSON) трактуется как «обновлений
 * нет» — возвращаем success(null), не роняем приложение.
 */
class UpdateRepositoryImpl(
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val releasesUrl: String = DEFAULT_RELEASES_URL,
) : UpdateRepository {

    override suspend fun checkForUpdate(currentVersionName: String): Result<AppUpdate?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(releasesUrl)
                    .header("Accept", "application/vnd.github+json")
                    .build()

                httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val body = resp.body?.string() ?: return@runCatching null
                    val release = json.decodeFromString(GitHubReleaseDto.serializer(), body)

                    val latest = normalizeVersion(release.tagName) ?: return@runCatching null
                    if (compareVersions(latest, normalizeVersion(currentVersionName).orEmpty()) <= 0) {
                        return@runCatching null
                    }

                    val apk = release.assets
                        .firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                        ?: return@runCatching null

                    AppUpdate(
                        versionName = latest,
                        tag = release.tagName,
                        apkUrl = apk.downloadUrl,
                    )
                }
            }.recover { null } // сетевые/парсинг-ошибки — просто пропускаем
        }

    private companion object {
        const val DEFAULT_RELEASES_URL =
            "https://api.github.com/repos/Alexzxcv/vpn-client-android/releases/latest"
    }
}

/** Убирает ведущий «v» и обрезает суффиксы вида «-rc1». null — если пусто. */
internal fun normalizeVersion(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val v = raw.trim().removePrefix("v").removePrefix("V")
    val core = v.substringBefore('-').substringBefore('+').trim()
    return core.ifBlank { null }
}

/**
 * Семантическое сравнение версий "x.y.z". Возвращает <0, 0, >0.
 * Недостающие компоненты считаются нулями ("1.2" == "1.2.0").
 */
internal fun compareVersions(a: String, b: String): Int {
    val pa = a.split('.')
    val pb = b.split('.')
    val n = maxOf(pa.size, pb.size)
    for (i in 0 until n) {
        val ai = pa.getOrNull(i)?.toIntOrNull() ?: 0
        val bi = pb.getOrNull(i)?.toIntOrNull() ?: 0
        if (ai != bi) return ai.compareTo(bi)
    }
    return 0
}
