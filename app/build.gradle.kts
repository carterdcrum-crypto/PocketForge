plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val ciBuildNumber = listOf(
    providers.environmentVariable("GITHUB_RUN_NUMBER").orNull,
    providers.environmentVariable("BUILD_NUMBER").orNull,
    providers.environmentVariable("BITRISE_BUILD_NUMBER").orNull
).firstOrNull { !it.isNullOrBlank() }?.toIntOrNull() ?: 1

val betaKeystorePath = providers.environmentVariable("POCKETFORGE_BETA_KEYSTORE").orNull
val betaKeystorePassword = providers.environmentVariable("POCKETFORGE_BETA_KEY_PASSWORD").orNull
val betaKeyAlias = providers.environmentVariable("POCKETFORGE_BETA_KEY_ALIAS").orNull ?: "pocketforge-beta"
val betaSigningConfigured = !betaKeystorePath.isNullOrBlank() &&
    !betaKeystorePassword.isNullOrBlank() &&
    project.file(betaKeystorePath!!).isFile

android {
    namespace = "com.pocketforge.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pocketforge.app"
        minSdk = 26
        targetSdk = 35
        versionCode = ciBuildNumber
        versionName = "0.4.$ciBuildNumber"
    }

    signingConfigs {
        if (betaSigningConfigured) {
            create("beta") {
                storeFile = project.file(betaKeystorePath!!)
                storePassword = betaKeystorePassword
                keyAlias = betaKeyAlias
                keyPassword = betaKeystorePassword
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "ENABLE_SIDELOAD_UPDATER", "true")
            buildConfigField("boolean", "BETA_SIGNING_STABLE", betaSigningConfigured.toString())
            if (betaSigningConfigured) {
                signingConfig = signingConfigs.getByName("beta")
            }
        }
        release {
            buildConfigField("boolean", "ENABLE_SIDELOAD_UPDATER", "false")
            buildConfigField("boolean", "BETA_SIGNING_STABLE", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
