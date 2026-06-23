pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Локальный каталог для бинарного VPN-движка (libXray / sing-box AAR).
        // Положи .aar сюда, когда будешь подключать движок. См. README / VpnEngine.
        flatDir { dirs("libs") }
    }
}

rootProject.name = "SAPN VPN"
include(":app")
