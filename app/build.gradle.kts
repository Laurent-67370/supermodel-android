plugins {
    id("com.android.application")
}

// supermodel-ios: Supermodel patched, its build (CMakeLists.txt), SDL2 and
// SDL2_net, fetched by fetch-supermodel.sh (see README.md)
val common = rootProject.file("supermodel-ios")
val version = System.getenv("SUPERMODEL_ANDROID_VERSION") ?: "0.1.dev"

android {
    namespace = "io.github.laurent67370.supermodel"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.laurent67370.supermodel"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = version

        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DSUPERMODEL_VERSION=android-$version-" + (System.getenv("SUPERMODEL_COMMIT") ?: "dev").take(7),
                )
                targets += listOf("supermodel", "SDL2", "SDL2_net")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("${common}/CMakeLists.txt")
        }
    }

    // One key for every build, so that a new APK installs over the last one.
    // The workflow writes it from the secrets SUPERMODEL_KEYSTORE_B64 and
    // SUPERMODEL_KEYSTORE_PASSWORD; without them the APK is signed with the
    // debug key of the machine that builds (new each time).
    val keystore = System.getenv("SUPERMODEL_KEYSTORE")?.let { file(it) }?.takeIf { it.isFile }
    val keystorePassword = System.getenv("SUPERMODEL_KEYSTORE_PASSWORD")
    signingConfigs {
        if (keystore != null && !keystorePassword.isNullOrEmpty()) {
            create("supermodel") {
                storeFile = keystore
                storeType = "pkcs12"
                storePassword = keystorePassword
                keyAlias = "supermodel"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("supermodel") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // SDL's Java side, from the same SDL that is built in
    sourceSets["main"].java.srcDirs("${common}/external/SDL/android-project/app/src/main/java")
    sourceSets["main"].assets.srcDirs(layout.buildDirectory.dir("generated/supermodelAssets"))
    packaging { jniLibs { useLegacyPackaging = false } }
}

// Supermodel's Config/ (Games.xml, Supermodel.ini) and Assets/ in the APK,
// copied by SetupActivity to the app's folder
val supermodelData by tasks.registering(Sync::class) {
    from("${common}/supermodel-src/Config") { into("supermodel/Config") }
    from("${common}/supermodel-src/Assets") { into("supermodel/Assets") }
    into(layout.buildDirectory.dir("generated/supermodelAssets"))
}
tasks.named("preBuild") { dependsOn(supermodelData) }
