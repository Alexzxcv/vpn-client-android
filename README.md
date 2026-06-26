# SAPN VPN — Android client

Клиент платного VPN-сервиса SAPN под Android. Пользователь логинится, видит свою
подписку, выбирает локацию и подключается. Сырая VLESS-ссылка с серверов сервиса
наружу не выдаётся: конфиг приходит с control-plane по API и используется встроенным
VPN-движком. Дополнительно можно подключать **свои** VLESS-серверы (вставкой ссылки,
сканом QR или импортом подписки) — они работают мимо backend.

> Это публичный репозиторий. Секретов (ключей, токенов, паролей, keystore) в коде нет и
> быть не должно. Адрес control-plane — публичный. Подпись релиза и keystore приходят в
> CI через GitHub Secrets.

## Возможности

- **Реальный VPN-движок** — туннель VLESS + Reality поднимается нативным sing-box (через
  `libbox` AAR). Трафик реально заворачивается в туннель, не эмуляция.
- **Вход по email или логину** + пароль, отображение подписки (план, статус, лимит
  устройств, использованный трафик, срок действия). Для free-юзеров — бесплатный суточный
  лимит (1 GB/день).
- **Выбор локации** из списка нод сервиса с пингом (RTT) и подключение через системный
  `VpnService`.
- **Свои (кастомные) серверы** — вставка `vless://`-ссылки, скан QR-кода, импорт
  подписки (http(s)-ссылка, в т.ч. base64). Трафик по своим серверам идёт мимо
  control-plane: подпиской не учитывается и не ограничивается.
- **Маршрутизация по приложениям** (split tunneling / per-app): режимы «только выбранные»
  (allowlist) или «все, кроме выбранных» (denylist).
- **Direct-list и «российские сайты напрямую»** — домены/IP/CIDR в обход туннеля; `.ru`,
  `.su`, `.рф` напрямую одним переключателем.
- **Kill switch + Always-on** — поддержка системного always-on/блокировки без VPN: при
  системном старте сервис поднимает последний конфиг без UI; в настройках есть переход в
  системные настройки VPN для включения.
- **Плитка быстрых настроек** (Quick Settings tile) — вкл/выкл VPN из шторки. Если
  разрешение VPN уже выдано — подключается прямо из плитки; иначе открывает приложение.
- **Локализация EN/RU** с выбором языка прямо в приложении (System / Русский / English).
- **Авто-переподключение при смене локации** и авто-обновление конфига до истечения
  (refresh за ~12 ч до `expires_at`).
- **Автообновление APK** через публичный GitHub Releases: проверка последнего релиза,
  скачивание через `DownloadManager` и запуск установки.

## Стек

- **Kotlin**, Android, Gradle (Kotlin DSL), version catalog (`gradle/libs.versions.toml`).
- **Jetpack Compose** + Material3 — UI; `androidx.appcompat` — для per-app локали (минуя
  требование AppCompatActivity на minSdk 26; сам `MainActivity` — `AppCompatActivity`).
- **Retrofit + OkHttp** + `kotlinx.serialization` — сетевой слой.
- **DataStore (Preferences)** — JWT-токены, настройки, свои серверы, последний конфиг.
- **VPN-движок:** **sing-box** через `libbox` (`io.nekohasekai.libbox`), AAR в
  `app/libs/libbox.aar` (sing-box 1.11.x, теги `with_gvisor,with_quic,with_wireguard,
  with_ech,with_utls,with_clash_api`). Туннель — VLESS + Reality, стек **gvisor**.
- **net.i2p.crypto:eddsa** — Ed25519-идентичность устройства и подпись запросов.
- **ZXing (zxing-android-embedded)** — скан QR-кодов с `vless://`-ссылками.
- minSdk 26, target/compileSdk 35. ABI: только `arm64-v8a` и `armeabi-v7a` (нативный
  sing-box; x86/x86_64 не собираются, чтобы не раздувать APK).

## Архитектура

Чистая архитектура, разделение на слои; зависимости направлены внутрь (UI и data зависят
от интерфейсов из `domain`).

```
app/src/main/java/ru/sapn/vpn/
  domain/
    model/             # User, Subscription, Location, VlessConfig, CustomServer, VpnSettings
    repository/        # Auth/Vpn/Account-репозитории (порты)
    vpn/               # VpnEngine, VpnState
    update/            # AppUpdate, UpdateRepository
  data/
    remote/            # Retrofit VpnApi, Dto, AuthInterceptor (Bearer + refresh на 401),
                       #   GitHubDto, ApiError
    local/             # TokenStore, SettingsStore, CustomServerStore, LastConnectionStore,
                       #   DeviceIdentity (Ed25519 + Keystore), DeviceIdProvider
    repository/        # реализации репозиториев + мапперы DTO→domain
  vpn/                 # XrayVpnService (VpnService), XrayCoreVpnEngine (sing-box),
    libbox/            #   AndroidPlatformInterface (openTun/protect/мониторинг сети),
                       #   SingBoxConfigBuilder, VlessLinkParser, VpnController
  ui/
    auth/              # AuthViewModel + LoginScreen
    connection/        # ConnectionViewModel + ConnectionScreen
    account/           # AccountViewModel + AccountScreen
    settings/          # SettingsScreen/VM, PerAppScreen/VM (per-app routing)
    components/        # общие Compose-компоненты
    theme/             # тёмная тема SAPN
  tile/                # VpnTileService (Quick Settings tile)
  update/              # AppInstaller (DownloadManager → установка APK)
  di/                  # AppContainer (Service Locator)
  MainActivity.kt      # навигация login <-> основное приложение по состоянию сессии
  SapnApp.kt           # Application, держит AppContainer
```

Замена Service Locator (`di/AppContainer`) на Hilt/Koin не затронет доменный слой —
интерфейсы репозиториев уже выделены.

### Как устроен VPN-движок

- `XrayVpnService` (наследник `VpnService`) ведёт жизненный цикл туннеля, foreground-
  уведомление и состояние через `VpnController`. Always-on/системный старт без UI
  поднимает последний сохранённый конфиг (`LastConnectionStore`).
- `XrayCoreVpnEngine` инициализирует `libbox` и запускает `BoxService` с JSON-конфигом.
- `AndroidPlatformInterface` (реализация `io.nekohasekai.libbox.PlatformInterface`):
  - `openTun()` строит TUN через `VpnService.Builder` (адреса, маршруты, DNS, per-app
    allow/deny-списки);
  - `autoDetectInterfaceControl()` вызывает `protect()` сокетов движка (трафик handshake
    до ноды идёт мимо туннеля — иначе петля);
  - мониторит активную сеть через `ConnectivityManager` и сообщает её sing-box (без этого
    «подключено, но интернета нет»).
- `SingBoxConfigBuilder` собирает конфиг sing-box: TUN inbound (gvisor, auto_route, sniff
  DNS) + VLESS Reality outbound (utls-fingerprint, public_key, short_id) + direct/block,
  правила маршрутизации (hijack-dns, нода напрямую, RU-direct, direct-list, блок «голого»
  IPv6). Конфиг содержит uuid и ключи Reality — **нигде не логируется**.

## API control-plane

База — `BuildConfig.API_BASE_URL` (по умолчанию `https://bot.niffty.ru/api/`,
переопределяется в `app/build.gradle.kts`). Авторизация — Bearer JWT, проставляется
`AuthInterceptor`; при 401 он один раз обновляет токен через `auth/refresh` (single-flight),
при неудаче — чистит токены и выкидывает на логин.

| Метод  | Путь | Назначение |
|--------|------|-----------|
| POST   | `auth/login` | вход по login/email + password → токены |
| POST   | `auth/refresh` | обновление токенов |
| POST   | `auth/change-password` | смена пароля |
| GET    | `me` | профиль |
| PATCH  | `me` | частичное обновление профиля (email/username) |
| GET    | `subscription` | подписка (план, лимиты, трафик, free-daily) |
| GET    | `vpn/locations` | список локаций (с RTT) |
| GET    | `devices` | список привязанных устройств |
| POST   | `devices` | привязка устройства (Ed25519 public_key); идемпотентно |
| DELETE | `devices/{device_id}` | удаление устройства |
| POST   | `vpn/config` | VLESS Reality конфиг (требует подпись устройства, см. ниже) |

`POST /vpn/config` помимо JWT требует подпись устройства в заголовках:
`X-Device-Id`, `X-Device-Timestamp` (unix-сек) и
`X-Device-Signature` = `base64(Ed25519_sign("<device_id>.<timestamp>"))`.

## Идентичность устройства

`device_id` НЕ выводится из `ANDROID_ID`/MAC. При первом запуске генерируется
**Ed25519 keypair** (`DeviceIdentity`):

- приватный seed (32 байта) никогда не хранится в открытом виде — шифруется AES/GCM-ключом
  из Android Keystore (Ed25519 в Keystore доступен только с API 33, поэтому гибрид:
  софтовый Ed25519 + аппаратный AES);
- публичный ключ (base64) отправляется на `POST /devices`, сервер возвращает `device_id`
  (идемпотентно по public_key);
- запросы `POST /vpn/config` подписываются приватным ключом (заголовки `X-Device-*`).

`platform` устройства отправляется как `"android"`.

## Сборка

```bash
./gradlew assembleDebug      # debug APK
./gradlew :app:assembleRelease  # release (нужны секреты подписи, см. ниже)
```

APK появляется в `app/build/outputs/apk/`.

> Нужен Android SDK (через Android Studio или `sdkmanager`) и JDK 17. Для нативного
> движка должен присутствовать `app/libs/libbox.aar` (см. `app/libs/README.md` — сборка из
> SagerNet/sing-box через `make lib_android`). **Gradle wrapper JAR**
> (`gradle/wrapper/gradle-wrapper.jar`) есть в репозитории; если его нет — `gradle wrapper`
> или открой проект в Android Studio.

### Релиз (GitHub Actions)

`.github/workflows/release.yml` срабатывает на пуш тега `v*`:

1. подпись релиза включается, только если заданы секреты keystore — keystore приходит как
   `KEYSTORE_BASE64` и декодируется в `release.keystore`; пароли/alias — из
   `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`;
2. при наличии секретов собирается подписанный `assembleRelease`, иначе — `assembleDebug`;
3. APK переименовывается в `SAPN-VPN-<tag>.apk` и публикуется в **GitHub Releases**.

Подпись настраивается в `app/build.gradle.kts` из тех же env-переменных; при их отсутствии
(локальная сборка) release собирается без подписи. Никаких ключей/паролей в репозитории.

Автообновление в приложении тянет последний релиз из публичного
`api.github.com/repos/Alexzxcv/vpn-client-android/releases/latest`, скачивает приложенный
`.apk` через `DownloadManager` и запускает установку (нужно `REQUEST_INSTALL_PACKAGES`).

## Безопасность

- Пароли/токены не логируются; HTTP-логирование в debug ограничено уровнем BASIC (body c
  токенами не пишется).
- JWT-токены в DataStore; приватный seed устройства зашифрован ключом Android Keystore.
- Конфиг sing-box (uuid, public_key Reality, short_id) и подписи устройства **никогда не
  логируются**.
- Секреты подписи релиза — только в CI через GitHub Secrets, не в git.
