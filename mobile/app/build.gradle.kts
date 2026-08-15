import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Optional release-signing config loaded from keystore.properties (kept out of VCS).
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(FileInputStream(keystorePropertiesFile))
}
val hasReleaseSigning = keystorePropertiesFile.exists()

// Auto-incrementing build number, persisted in app/version.properties (gitignored). It bumps on
// every Gradle configuration (i.e. every build) and is surfaced as BuildConfig.BUILD_NUMBER.
val buildNumberFile = file("version.properties")
val buildNumber: Int = run {
    val props = Properties().apply {
        if (buildNumberFile.exists()) FileInputStream(buildNumberFile).use { load(it) }
    }
    val next = (props.getProperty("buildNumber")?.toIntOrNull() ?: 0) + 1
    props.setProperty("buildNumber", next.toString())
    buildNumberFile.outputStream().use { props.store(it, "Auto-incremented build number") }
    next
}

android {
    namespace = "com.knotes"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.knotes"
        minSdk = 23
        targetSdk = 35
        // Bumped with each released versionName: Android refuses to install over a build with a
        // higher code, so a weekly versionName needs this to move too.
        versionCode = 4
        versionName = "1.3.33"
        buildConfigField("int", "BUILD_NUMBER", "$buildNumber")
        buildConfigField("String", "CODENAME", "\"Gratitude\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        // java.time on minSdk 23.
        isCoreLibraryDesugaringEnabled = true
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

    lint {
        // Don't block release packaging on lint (run `./gradlew lint` separately if desired).
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Periodic background sync, so the app doesn't need a foreground service (and therefore a
    // permanent status-bar notification) just to poll the backend. See SyncWorker.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")

    // Async image loading for the Gallery page.
    implementation("io.coil-kt:coil-compose:2.7.0")

    // EXIF orientation, for the gallery's annotation editor. Coil rotates images for display, so
    // marks are drawn against the upright picture — burning them onto a bitmap decoded without the
    // rotation would put every mark in the wrong place on a phone-camera photo.
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // Plain JVM unit tests (./gradlew :app:testDebugUnitTest) for the Android-free helpers —
    // filename handling, parsing. Anything touching the Android framework belongs on a device.
    testImplementation("junit:junit:4.13.2")
}
