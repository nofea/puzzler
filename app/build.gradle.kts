import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

/**
 * Release signing credentials come from `keystore.properties` at the repo root
 * (written by scripts/gen-keystore.sh, gitignored) or from PUZZLER_KEYSTORE_* env vars.
 */
data class ReleaseKey(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

fun loadReleaseKey(): ReleaseKey? {
    val propsFile = rootProject.file("keystore.properties")
    val props = Properties()
    if (propsFile.exists()) propsFile.inputStream().use(props::load)

    fun value(
        prop: String,
        env: String,
    ): String? = props.getProperty(prop)?.takeIf { it.isNotBlank() } ?: System.getenv(env)?.takeIf { it.isNotBlank() }

    val store = value("storeFile", "PUZZLER_KEYSTORE_FILE") ?: return null
    return ReleaseKey(
        storeFile = file(store),
        storePassword = value("storePassword", "PUZZLER_KEYSTORE_PASSWORD") ?: return null,
        keyAlias = value("keyAlias", "PUZZLER_KEY_ALIAS") ?: return null,
        keyPassword = value("keyPassword", "PUZZLER_KEY_PASSWORD") ?: return null,
    )
}

val releaseKey = loadReleaseKey()

/**
 * App version X.Y.Z: `-PpuzzlerVersion=X.Y.Z` (CI passes the release tag's version), else the latest vX.Y.Z git tag,
 * else 0.0.0. versionCode is X*10000 + Y*100 + Z, so it rises with every release as Android requires for updates.
 */
fun latestTagVersion(): String? =
    runCatching {
        providers
            .exec {
                commandLine("git", "describe", "--tags", "--abbrev=0", "--match", "v[0-9]*.[0-9]*.[0-9]*")
                isIgnoreExitValue = true
            }.standardOutput.asText
            .get()
            .trim()
            .removePrefix("v")
            .ifBlank { null }
    }.getOrNull()

val appVersion = providers.gradleProperty("puzzlerVersion").orNull ?: latestTagVersion() ?: "0.0.0"
val appVersionCode =
    run {
        val parts =
            Regex("""(\d+)\.(\d+)\.(\d+)""")
                .matchEntire(appVersion)
                ?.destructured
                ?.toList()
                ?.map(String::toInt)
                ?: throw GradleException("Version '$appVersion' is not MAJOR.MINOR.PATCH")
        val (major, minor, patch) = parts
        require(minor < 100 && patch < 100) { "versionCode scheme needs MINOR and PATCH below 100: $appVersion" }
        // Untagged dev builds (0.0.0) still need a positive versionCode.
        (major * 10_000 + minor * 100 + patch).coerceAtLeast(1)
    }

android {
    namespace = "com.sheen.puzzler"
    compileSdk = 35
    // Pinned to what devenv provides; the Nix store SDK is read-only so AGP cannot download others.
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.sheen.puzzler"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKey != null) {
            create("release") {
                storeFile = releaseKey.storeFile
                storePassword = releaseKey.storePassword
                keyAlias = releaseKey.keyAlias
                keyPassword = releaseKey.keyPassword
                // Every minSdk 29+ device verifies v3, so AGP writes only the v3 block; v1 (JAR) adds nothing.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    // Zero tolerance: every warning fails the build. Exceptions live in lint.xml, each with a reason.
    lint {
        checkAllWarnings = true
        warningsAsErrors = true
        abortOnError = true
        checkTestSources = true
        lintConfig = file("lint.xml")
        xmlReport = true
        htmlReport = true
    }
}

ktlint {
    version.set(libs.versions.ktlint)
    android.set(true)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt.yml"))
    source.setFrom("src/main/java", "src/test/java", "src/androidTest/java")
}

// Fail loudly instead of silently producing an unsigned release APK.
tasks.matching { it.name == "assembleRelease" || it.name == "packageRelease" }.configureEach {
    doFirst {
        if (releaseKey == null) {
            throw GradleException(
                "No release signing key. Run `gen-keystore` (scripts/gen-keystore.sh) or set " +
                    "PUZZLER_KEYSTORE_FILE, PUZZLER_KEYSTORE_PASSWORD, PUZZLER_KEY_ALIAS, PUZZLER_KEY_PASSWORD.",
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
