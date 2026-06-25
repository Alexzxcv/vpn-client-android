plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Подпись релиза из секретов CI. Если переменных окружения нет (локальная
// сборка / первый запуск без секретов) — signingConfig не настраивается и
// release собирается без подписи (CI в этом случае публикует debug-APK).
val keystoreBase64: String? = System.getenv("KEYSTORE_BASE64")
val hasReleaseSigning = !keystoreBase64.isNullOrBlank()

android {
    namespace = "ru.sapn.vpn"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.sapn.vpn"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "0.1.8"

        // База API control-plane. Переопределяется по сборкам ниже.
        // Никаких секретов здесь — только публичный адрес.
        buildConfigField("String", "API_BASE_URL", "\"https://bot.niffty.ru/api/\"")
        // Веб-дашборд для кнопки «Создать аккаунт» (регистрация в браузере).
        // Меняем здесь, когда сменится домен/страница регистрации.
        buildConfigField("String", "DASHBOARD_URL", "\"https://bot.niffty.ru/\"")

        // Реальные устройства — только ARM. x86/x86_64 (эмуляторы) не тащим,
        // чтобы вдвое срезать размер APK с нативным sing-box (libbox.so).
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                // Keystore приходит из CI как base64 и декодируется в файл до сборки.
                storeFile = rootProject.file("release.keystore")
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // При желании можно указать локальный backend для отладки:
            // buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080/api/\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Идентичность устройства: Ed25519-подпись на всех API (>=26).
    implementation(libs.eddsa)

    testImplementation(libs.junit)

    // ── VPN-движок: sing-box (libbox AAR) ──────────────────────────────────
    // Выбран sing-box: нативный tun-inbound (без tun2socks), формат конфига
    // совпадает с Windows-клиентом. См. KDoc XrayCoreVpnEngine про сборку AAR.
    //
    // Шаги включения:
    //   1) собрать libbox.aar (SagerNet/sing-box: `make lib_android`);
    //   2) положить его в app/libs/libbox.aar;
    //   3) раскомментировать строку ниже;
    //   4) раскомментировать тело AndroidPlatformInterface.kt и реальный путь
    //      в XrayCoreVpnEngine.start()/stop();
    //   5) ENGINE_AAR_AVAILABLE = true.
    //
    // ВКЛЮЧЕНО: libbox.aar собран (sing-box 1.11.15, gomobile bind, теги
    // with_gvisor,with_quic,with_wireguard,with_ech,with_utls,with_clash_api).
    implementation(files("libs/libbox.aar"))
}
