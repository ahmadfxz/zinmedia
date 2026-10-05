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
        // RootEncoder (pengirim RTMP untuk media-live, filter media-effects) hanya tersedia di JitPack.
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\.pedroSG94.*") }
        }
    }
}

rootProject.name = "zinmedia"
include(
    ":media:media-photoeditor",
    ":media:media-videoeditor",
    ":media:media-composer",
    ":media:media-effects",
    ":media:media-camera",
    ":media:media-live",
    // Aplikasi demo untuk mencoba editor; tidak dipublish.
    ":sample",
)
