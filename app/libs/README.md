# app/libs — нативный VPN-движок (AAR)

Сюда кладётся `libbox.aar` (sing-box mobile) — нативный движок VLESS Reality
с TUN-инбаундом. По умолчанию AAR в git НЕ коммитится (бинарь): добавляется
на машине сборки.

## Как получить libbox.aar

Нужны: Go (>=1.21), gomobile, Android SDK + NDK.

```sh
git clone https://github.com/SagerNet/sing-box
cd sing-box

# 1) поставить gomobile
make lib_install
# или: go install golang.org/x/mobile/cmd/gomobile@latest && gomobile init

# 2) собрать AAR (с gvisor/quic/reality тегами)
make lib_android
# артефакт: libbox.aar
```

Скопировать `libbox.aar` в этот каталог (`app/libs/libbox.aar`).

## Включение в проекте

1. `app/build.gradle.kts` — раскомментировать `implementation(files("libs/libbox.aar"))`.
2. `app/src/main/java/ru/sapn/vpn/vpn/libbox/AndroidPlatformInterface.kt` —
   раскомментировать тело файла.
3. `app/src/main/java/ru/sapn/vpn/vpn/XrayCoreVpnEngine.kt` —
   раскомментировать «РЕАЛЬНЫЙ ПУТЬ (libbox)» в `start()`/`stop()` и поставить
   `ENGINE_AAR_AVAILABLE = true`.

Конфиг sing-box строит `SingBoxConfigBuilder` (TUN inbound + VLESS Reality outbound),
формат зеркалит Windows-клиент (`internal/singbox`).
