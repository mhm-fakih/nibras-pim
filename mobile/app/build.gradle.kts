import java.io.FileInputStream
import java.util.Properties
import java.util.zip.ZipFile

/** Single source for the two strings that end up in both the APK and its published file name. */
val APP_VERSION_NAME = "1.2.36"
val APP_CODENAME = "Gratitude"

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

/**
 * The build number, ALSO written into the APK as `assets/build.txt`.
 *
 * Reading it back out of a built APK used to be impossible without `aapt`, so publishing meant
 * re-reading version.properties afterwards — and that file has moved on by then if any further
 * Gradle invocation has happened (running the tests after assembling is enough). That is how a
 * b314 APK came to be published under the name b315. An asset is the cheapest thing in an APK that
 * can be read back with nothing but a zip reader, so now the artefact carries its own answer and
 * the name can be checked against it.
 */
val buildStampDir = layout.buildDirectory.dir("generated/knotesStamp").get().asFile.apply {
    mkdirs()
    File(this, "build.txt").writeText("$buildNumber\n")
}

/** The published file name — one definition, used by the publish task and nothing else. */
val publishedApkName: (String) -> String = { variant ->
    "knotes-$APP_VERSION_NAME.b$buildNumber-$APP_CODENAME-$variant.apk"
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
        versionCode = 5
        versionName = APP_VERSION_NAME
        buildConfigField("int", "BUILD_NUMBER", "$buildNumber")
        buildConfigField("String", "CODENAME", "\"$APP_CODENAME\"")
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

    // Carries assets/build.txt (see buildStampDir) alongside the normal assets.
    sourceSets["main"].assets.srcDir(buildStampDir)

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
    // org.json ships with Android but is a THROWING STUB in JVM unit tests, so anything parsing
    // JSON off-device silently fails. This is the real implementation, for tests only — the app
    // still uses the platform's at runtime.
    testImplementation("org.json:json:20240303")
}


/** Deliverables live in a subfolder of their type — dist/apk, dist/war, dist/zip, dist/sql. */
val distApkDir: File = rootProject.file("../dist/apk")

/** How many builds of one artefact are kept there. The current one, and the one to fall back to. */
val keptBuilds = 2

/**
 * Build an APK and copy it to ./dist/apk under a name that cannot disagree with its contents.
 *
 * `buildNumber` is resolved once per Gradle invocation, and the same invocation both compiles it
 * into the APK and names the copy — so the two cannot drift the way they did when the name was
 * decided by a separate command reading version.properties afterwards. `verifyPublishedApk`
 * re-checks it from the artefact itself rather than taking this on trust.
 */
tasks.register<Copy>("publishDebugApk") {
    group = "publishing"
    description = "Assemble the debug APK and copy it to ./dist/apk, keeping the last $keptBuilds."
    dependsOn("assembleDebug")
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(distApkDir)
    rename { publishedApkName("debug") }
    doLast {
        logger.lifecycle("Published dist/apk/${publishedApkName("debug")}")
        pruneToLast(distApkDir, Regex("""^knotes-.*-debug\.apk$"""), keptBuilds)
    }
}

/**
 * Keep only the newest [keep] files matching [pattern] in [dir]; delete the rest.
 *
 * By MODIFICATION TIME, not by the build number in the name: the number is a decimal string, so
 * sorting on it puts b99 after b463, and one bad sort here deletes the build you are standing on.
 * Scoped to a pattern so nothing but this artefact's own published copies is ever a candidate —
 * dist is a Syncthing share and holds the user's documents too.
 */
fun pruneToLast(dir: File, pattern: Regex, keep: Int) {
    val published = dir.listFiles { f: File -> f.isFile && pattern.matches(f.name) }
        ?.sortedByDescending { it.lastModified() }
        ?: return
    published.drop(keep).forEach { old ->
        if (old.delete()) logger.lifecycle("Pruned ${dir.name}/${old.name}")
    }
}

/** Reads assets/build.txt back out of the published APK and fails if the name disagrees. */
tasks.register("verifyPublishedApk") {
    group = "verification"
    description = "Check the published APK's file name against the build number inside it."
    dependsOn("publishDebugApk")
    doLast {
        val apk = File(distApkDir, publishedApkName("debug"))
        require(apk.isFile) { "Published APK missing: $apk" }
        val stamped = ZipFile(apk).use { zip ->
            val entry = zip.getEntry("assets/build.txt")
                ?: error("APK carries no assets/build.txt — cannot verify the build number")
            zip.getInputStream(entry).bufferedReader().readText().trim()
        }
        require(stamped == "$buildNumber") {
            "Published ${apk.name} claims build $buildNumber but contains build $stamped"
        }
        logger.lifecycle("Verified ${apk.name} contains build $stamped")
    }
}
