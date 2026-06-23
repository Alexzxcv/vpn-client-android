# CLAUDE.md — SAPN VPN Android client

Правила и контекст для работы над этим репозиторием. Читать перед любой задачей.

## Что это

Android-клиент платного VPN-сервиса **SAPN**. Пользователь логинится, видит подписку,
выбирает локацию, подключается. Туннель — VLESS Reality через встроенный движок
(Xray/sing-box). Сырая VLESS-ссылка наружу не выдаётся: конфиг приходит с control-plane.

Это **отдельный публичный репозиторий**. Backend (control-plane) и node-agent живут в
другом репозитории (Go). Бэкенд: `https://bot.niffty.ru/api`, ноды — Xray VLESS Reality.

## Правила репозитория

- **Публичный репо.** Никаких секретов в коде/истории: ключей, паролей, токенов,
  приватных адресов, Reality private keys. Адрес API control-plane — публичный, ок.
- Базовый URL API — только через `BuildConfig.API_BASE_URL`, не хардкодить по коду.
- Не логировать: пароли, JWT, `uuid`/`publicKey`/`shortId` из конфига, любые ключи.
- `device_id` отдаём как хеш `ANDROID_ID`, не сырым.
- Бренд — **SAPN**. Применять в названиях/строках/теме.
- Язык общения с пользователем — **русский**. Комментарии в коде — русские, по делу.

## Стек

Kotlin · Gradle Kotlin DSL (+ version catalog `gradle/libs.versions.toml`) ·
Jetpack Compose + Material3 · Retrofit/OkHttp + kotlinx.serialization · DataStore ·
Android VpnService + бинарный движок Xray/sing-box (AAR). minSdk 26, target/compile 35,
JDK 17.

## Архитектура

Чистая архитектура, слои `data / domain / ui`, зависимости направлены внутрь к `domain`.

- `domain/` — модели, порты репозиториев (`AuthRepository`, `VpnRepository`),
  интерфейс `VpnEngine`. Без Android-зависимостей по возможности.
- `data/remote` — Retrofit API + DTO + `AuthInterceptor` (Bearer, refresh на 401).
- `data/local` — `TokenStore` (DataStore), `DeviceIdProvider`.
- `data/repository` — реализации портов + мапперы DTO→domain.
- `vpn/` — `XrayVpnService` (VpnService), `StubVpnEngine`, `VpnController` (мост к UI).
- `ui/` — Compose-экраны + ViewModel (auth, connection), тема.
- `di/AppContainer` — Service Locator (легко заменить на Hilt/Koin позже).

Соглашения: новый источник данных — порт в `domain`, реализация в `data`, провязка в
`AppContainer`. SQL/сеть — только в `data`. Бизнес-логика — в ViewModel/репозиториях,
не в Compose.

## VPN-движок (заглушка)

Бинарный движок не подключён. Абстракция — `domain/vpn/VpnEngine`, заглушка —
`vpn/StubVpnEngine` (поднимает tun через VpnService, но трафик не заворачивает).
Как подключить реальный AAR — пошагово в KDoc `StubVpnEngine` и в README.
Не тащить реальный AAR без отдельной задачи.

## Сборка

`./gradlew assembleDebug`. Нужен Android SDK + JDK 17. Если нет
`gradle/wrapper/gradle-wrapper.jar` — сгенерировать `gradle wrapper` или открыть в
Android Studio.

## Git

Коммитить/пушить только по явной просьбе. `.env`, keystore, AAR-движок — в `.gitignore`.
