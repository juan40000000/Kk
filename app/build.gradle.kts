plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.experimento.wifiradar"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.experimento.wifiradar"
        minSdk = 24
        targetSdk = 34
        // En CI cada compilación sube el número de versión para que se instale como actualización
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.$build"
    }

    // Clave fija (sólo para este experimento): si cada compilación usara una clave distinta,
    // Android rechazaría instalar una versión nueva encima de la anterior.
    signingConfigs {
        create("fixed") {
            storeFile = file("wifiradar.keystore")
            storePassword = "wifiradar"
            keyAlias = "wifiradar"
            keyPassword = "wifiradar"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
    }

    lint {
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.1")
}
