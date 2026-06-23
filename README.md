# SAPN VPN — Android client

Клиент платного VPN-сервиса SAPN под Android. Пользователь логинится, видит свою
подписку, выбирает локацию и подключается. Сырая VLESS-ссылка наружу не выдаётся:
конфиг приходит с control-plane по API и используется встроенным VPN-движком.

> Это публичный репозиторий. Секретов (ключей, токенов, паролей) в коде нет и быть
> не должно. Адрес control-plane — публичный.

## Стек

- **Kotlin**, Android, Gradle (Kotlin DSL), version catalog (`gradle/libs.versions.toml`).
- **Jetpack Compose** + Material3 — UI.
- **Retrofit + OkHttp** + `kotlinx.serialization` — сетевой слой.
- **DataStore (Preferences)** — хранение JWT-токенов.
- **VpnService** (Android) + бинарный движок Xray/sing-box — туннель VLESS Reality.
- minSdk 26, targetSdk/compileSdk 35.

## Архитектура

Чистая архитектура, разделение на слои:

```
app/src/main/java/ru/sapn/vpn/
  domain/            # модели, интерфейсы репозиториев, интерфейс VpnEngine
    model/           #   User, Subscription, Location, VlessConfig
    repository/      #   AuthRepository, VpnRepository (порты)
    vpn/             #   VpnEngine, VpnState
  data/
    remote/          # Retrofit API, DTO, AuthInterceptor (Bearer + refresh на 401)
    local/           # TokenStore (DataStore), DeviceIdProvider (хеш ANDROID_ID)
    repository/      # реализации репозиториев + мапперы DTO→domain
  vpn/               # XrayVpnService (VpnService), StubVpnEngine (заглушка), VpnController
  ui/
    auth/            # AuthViewModel + LoginScreen
    connection/      # ConnectionViewModel + ConnectionScreen
    theme/           # тёмная тема SAPN
  di/                # AppContainer (Service Locator)
  MainActivity.kt    # навигация login <-> connection по состоянию сессии
  SapnApp.kt         # Application, держит AppContainer
```

Зависимости направлены внутрь: UI и data зависят от интерфейсов из `domain`.
Замена Service Locator на Hilt/Koin не затронет доменный слой.

## API control-plane

База — `BuildConfig.API_BASE_URL` (по умолчанию `https://bot.niffty.ru/api/`,
переопределяется в `app/build.gradle.kts`). Авторизация — Bearer JWT, проставляется
`AuthInterceptor`; при 401 он один раз обновляет токен через `/auth/refresh`.

| Метод | Путь | Назначение |
|------|------|-----------|
| POST | `auth/login` | вход по login/email + password → токены |
| POST | `auth/refresh` | обновление токенов |
| GET  | `me` | профиль |
| GET  | `subscription` | подписка |
| GET  | `vpn/locations` | список локаций |
| POST | `devices` | привязка устройства (обязательна до конфига) |
| POST | `vpn/config` | VLESS Reality конфиг |

## Сборка

```bash
./gradlew assembleDebug
```

APK появится в `app/build/outputs/apk/debug/`.

> Нужен Android SDK (через Android Studio или `sdkmanager`) и JDK 17.
> **Gradle wrapper JAR** (`gradle/wrapper/gradle-wrapper.jar`) в репозитории —
> бинарный; если его нет, сгенерируй командой `gradle wrapper` (нужен локально
> установленный Gradle) или открой проект в Android Studio, который доустановит его.

Запуск тестов / линта добавляются стандартными gradle-таскам (`test`, `lint`).

## Что заглушено: VPN-движок

Туннель VLESS Reality поднимает бинарный движок (Xray-core или sing-box mobile),
подключаемый как AAR. В скелете он абстрагирован интерфейсом
`domain/vpn/VpnEngine` и реализован заглушкой `vpn/StubVpnEngine`:

- `XrayVpnService` (наследник `VpnService`) реально создаёт tun-интерфейс,
  показывает foreground-уведомление, ведёт состояние через `VpnController`.
- `StubVpnEngine.start()` принимает tun-дескриптор и `VlessConfig`, но **трафик
  не заворачивает** — только логирует факт старта (без чувствительных полей).

### Как подключить реальный движок

1. Положи AAR движка в `app/libs/` и подключи в `app/build.gradle.kts`
   (`implementation(files("libs/libxray.aar"))`) либо как Maven-артефакт.
2. Реализуй `VpnEngine` поверх движка: из `VlessConfig` собери JSON-конфиг
   outbound'а VLESS Reality (host/port/uuid/flow/publicKey/shortId/sni/fingerprint)
   и inbound типа tun; передай движку дескриптор tun из `VpnService`.
3. Замени `StubVpnEngine` на реальную реализацию в `XrayVpnService`
   (или провяжи через `AppContainer`/DI).
4. Настрой DNS, маршруты и при необходимости прямой маршрут до адреса ноды
   (handshake движка) в `XrayVpnService.buildTun` / конфиге движка.

Подробные пошаговые TODO — в KDoc `vpn/StubVpnEngine.kt`.

## Безопасность

- Токены в DataStore (для прод — дошифровать через Keystore, см. TODO в `TokenStore`).
- В логах нет паролей, токенов, uuid и Reality-ключей.
- `device_id` = SHA-256 от `ANDROID_ID` (сырой идентификатор на сервер не уходит).
