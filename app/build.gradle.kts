plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Signing values come from the environment (GitHub Secrets in CI). Nothing
// secret is ever committed. Without them, assembleRelease produces an
// unsigned APK.
val releaseKeystorePath: String? = System.getenv("FLINT_KEYSTORE_PATH")

// Azure app (client) ID used for Microsoft sign-in. It is an identifier, not a secret, but it is
// still supplied from outside the repository: -PFLINT_MS_CLIENT_ID=..., the environment
// variable of the same name, or flint.msClientId in ~/.gradle/gradle.properties.
// Empty means "not configured": the Accounts screen then says so instead of failing silently.
val microsoftClientId: String =
    (findProperty("FLINT_MS_CLIENT_ID") as String?)
        ?: System.getenv("FLINT_MS_CLIENT_ID")
        ?: (findProperty("flint.msClientId") as String?)
        ?: ""

android {
    namespace = "com.flintlauncher.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.flintlauncher.android"
        minSdk = 26
        targetSdk = 35
        versionCode = (findProperty("flintVersionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("flintVersionName") as String?) ?: "0.1.0-dev"
        buildConfigField("String", "MS_CLIENT_ID", "\"${microsoftClientId.trim()}\"")
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("FLINT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FLINT_KEY_ALIAS")
                keyPassword = System.getenv("FLINT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false // enabled once keep-rules for JNI/reflection exist
            signingConfig = signingConfigs.findByName("release")
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
        jniLibs {
            // The launcher dlopen()s libGL.so (GL4ES), LWJGL and the GLFW shim by absolute path from
            // applicationInfo.nativeLibraryDir, and PLAY checks libGL.so with File.isFile(). With the
            // default for minSdk >= 23 the .so files stay inside the APK and that directory is empty,
            // which made every launch report a missing renderer.
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":auth"))
    implementation(project(":account"))
    implementation(project(":minecraft"))
    implementation(project(":runtime"))
    implementation(project(":installer"))
    implementation(project(":download"))
    implementation(project(":profiles"))
    implementation(project(":mods"))
    implementation(project(":modpacks"))
    implementation(project(":news"))
    implementation(project(":partners"))
    implementation(project(":friends"))
    implementation(project(":network"))
    implementation(project(":database"))
    implementation(project(":ui"))
    implementation(project(":native"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
}
