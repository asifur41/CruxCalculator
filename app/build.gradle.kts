import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ---------------------------------------------------------------------------------------------
// Release signing. The four values below come from environment variables (GitHub Secrets).
// If any is missing or empty, the release build is simply left UNSIGNED instead of failing.
// ---------------------------------------------------------------------------------------------
val keystoreBase64: String? = System.getenv("KEYSTORE_BASE64")?.takeIf { it.isNotBlank() }
val keystorePassword: String? = System.getenv("KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
val keyAliasName: String? = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() }
val keyPasswordValue: String? = System.getenv("KEY_PASSWORD")?.takeIf { it.isNotBlank() }
val hasSigningSecrets = keystoreBase64 != null && keystorePassword != null && keyAliasName != null && keyPasswordValue != null

// Test-only signing: `./gradlew assembleRelease -PtestSign=true` signs the release APK with the public key stored in
// app/test-signing.keystore, so the optimized build can be installed on a phone (an unsigned APK cannot be installed) and
// later test builds update over earlier ones. It is NEVER used for Google Play: real secrets always win, and the AAB step
// does not pass the flag.
val useTestSigning: Boolean = !hasSigningSecrets && providers.gradleProperty("testSign").orNull == "true"

// ---------------------------------------------------------------------------------------------
// versionCode = number of commits. Both GitHub workflows build the same code, so they now produce the same,
// always-rising number (GITHUB_RUN_NUMBER is counted separately per workflow, which caused downgrades).
// This needs the FULL git history, so both workflows use `fetch-depth: 0` when checking out.
// ---------------------------------------------------------------------------------------------
fun gitOutput(vararg args: String): String? = runCatching {
    providers.exec { commandLine(listOf("git") + args.toList()) }.standardOutput.asText.get().trim()
}.getOrNull()

if (System.getenv("GITHUB_ACTIONS") == "true" && gitOutput("rev-parse", "--is-shallow-repository") != "false") {
    // Fail loudly instead of silently shipping versionCode 1.
    throw GradleException("The git history is incomplete. Add 'fetch-depth: 0' to the actions/checkout step so versionCode can be computed.")
}
val commitCount: Int = gitOutput("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1

val decodedKeystore: File? = if (hasSigningSecrets) {
    val file = layout.buildDirectory.file("signing/release.keystore").get().asFile
    file.parentFile.mkdirs()
    file.writeBytes(Base64.getMimeDecoder().decode(keystoreBase64))
    file
} else {
    null
}

android {
    namespace = "com.tapecalc.pro"
    compileSdk = 36

    defaultConfig {
        // Permanent after the first Google Play upload. Decide now; it can never change later.
        applicationId = "com.cruxcalculator.app"
        minSdk = 26
        targetSdk = 36
        // Number of git commits (see above): rises with every commit, identical across both workflows (Google Play requires it to rise).
        versionCode = commitCount
        versionName = "1.4.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (decodedKeystore != null) {
                storeFile = decodedKeystore
                storePassword = keystorePassword
                keyAlias = keyAliasName
                keyPassword = keyPasswordValue
            }
        }
        create("testKey") {
            storeFile = file("test-signing.keystore")
            storePassword = "android"
            keyAlias = "cruxtest"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = when {
                hasSigningSecrets -> signingConfigs.getByName("release")
                useTestSigning -> signingConfigs.getByName("testKey")
                else -> null
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
    }

    lint {
        // Lint must never block producing an APK/AAB; run `./gradlew lint` manually when you want the report.
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core-precision"))
    implementation(project(":core-math"))
    implementation(project(":core-data"))
    implementation(project(":core-ui"))
    implementation(project(":feature-basic"))
    implementation(project(":feature-scientific"))
    implementation(project(":feature-converter"))
    implementation(project(":feature-tools"))
    implementation(project(":feature-history"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Installs the baseline profile (app/src/main/baseline-prof.txt) on the phone at first launch: faster start-up and smoother screens.
    implementation(libs.androidx.profileinstaller)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.datastore.preferences)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.compose.ui.test.manifest)
}
