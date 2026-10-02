plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Koordinat: com.github.ahmadfxz.zinmedia:<nama-modul>:<versi>
// Dibuild oleh JitPack dari tag GitHub; group di-set eksplisit supaya
// dependency antar-modul di POM ikut koordinat yang sama.
val publishGroup = "com.github.ahmadfxz.zinmedia"

subprojects {
    group = publishGroup

    plugins.withId("com.android.library") {
        apply(plugin = "maven-publish")

        extensions.configure<com.android.build.api.dsl.LibraryExtension> {
            publishing {
                singleVariant("release") {
                    withSourcesJar()
                }
            }
        }

        afterEvaluate {
            extensions.configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("release") {
                        from(components["release"])
                        groupId = publishGroup
                        artifactId = project.name
                    }
                }
            }
        }
    }
}
