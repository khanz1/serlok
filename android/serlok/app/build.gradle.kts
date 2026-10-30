plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

// Production Heroku custom domain. Override for local Bun with:
//   -PSERLOK_HTTP_URL=http://127.0.0.1:3000 -PSERLOK_WS_URL=ws://127.0.0.1:3000
// Emulator local access also needs: adb reverse tcp:3000 tcp:3000
val serlokHttpUrl = providers.gradleProperty("SERLOK_HTTP_URL")
    .orElse(providers.environmentVariable("SERLOK_HTTP_URL"))
    .orElse("https://serlok.sonangga.dev")
val serlokWsUrl = providers.gradleProperty("SERLOK_WS_URL")
    .orElse(providers.environmentVariable("SERLOK_WS_URL"))
    .orElse("wss://serlok.sonangga.dev")

android {
    namespace = "com.khanz1.serlok"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.khanz1.serlok"
        minSdk = 24
        targetSdk = 36
        versionCode = 7
        versionName = "0.4.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "HTTP_BASE_URL", serlokHttpUrl.get().asBuildConfigString())
        buildConfigField("String", "WS_BASE_URL", serlokWsUrl.get().asBuildConfigString())
    }

    buildTypes {
        debug {
            // Same endpoints as release by default (production). Override with SERLOK_* for local Bun.
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    // Match compileOptions (Java 11); Studio JDK 21 would otherwise default Kotlin to 21.
    jvmToolchain(11)
}

afterEvaluate {
    tasks.named<org.gradle.api.tasks.testing.Test>("testDebugUnitTest").configure {
        val evidenceDir = (findProperty("serlok.evidence.dir") as String?)
            ?: System.getenv("SERLOK_EVIDENCE_DIR")
            ?: rootProject.projectDir.resolve("../../evidence").canonicalFile.absolutePath
        systemProperty("serlok.evidence.dir", evidenceDir)
        outputs.upToDateWhen { false }
        if (gradle.startParameter.taskNames.any { name -> name.contains("detectionBenchmark") }) {
            filter {
                includeTestsMatching(
                    "com.khanz1.serlok.safety.benchmark.GForceDetectionBenchmarkTest"
                )
            }
        }
    }
    tasks.named("assembleDebug").configure {
        doLast {
            val version = android.defaultConfig.versionName ?: "0.0.0"
            val debugDir = file("build/outputs/apk/debug")
            val source = debugDir.resolve("app-debug.apk")
            val named = debugDir.resolve("serlok-$version.apk")
            if (source.exists()) {
                source.copyTo(named, overwrite = true)
            }
        }
    }
}

tasks.register("detectionBenchmark") {
    group = "verification"
    description = "Run GForceDetector synthetic benchmark and write evidence CSV"
    dependsOn("testDebugUnitTest")
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Location
    implementation(libs.play.services.location)

    // Maps — OSMDroid (OpenStreetMap, free, no API key)
    implementation(libs.osmdroid)
    implementation(libs.androidx.preference)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // WebSocket
    implementation(libs.okhttp)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
