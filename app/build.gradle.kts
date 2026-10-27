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
        // GitHub sets GITHUB_RUN_NUMBER, so every CI build gets a higher versionCode (Google Play requires this).
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0.0"
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
            signingConfig = if (hasSigningSecrets) signingConfigs.getByName("release") else null
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
    implementation(project(":feature-history"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

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
