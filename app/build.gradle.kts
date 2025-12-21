plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.aihub"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.aihub"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }
    
    // Важная настройка для Java 17
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_10 // Используем синтаксис 1.8+
        targetCompatibility = JavaVersion.VERSION_1_10
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    
    composeOptions {
        // Эта версия жестко привязана к Kotlin 1.9.22
        kotlinCompilerExtensionVersion = "1.5.10"
    }
}

dependencies {
    // Основные библиотеки Compose (версии прописаны явно)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // UI
    implementation("androidx.compose.ui:ui:1.6.0")
    implementation("androidx.compose.ui:ui-graphics:1.6.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.0")
    implementation("androidx.compose.material3:material3:1.2.0")
    
    // Network & Image
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    
    // Icons
    implementation("androidx.compose.material:material-icons-extended:1.6.0")
}

