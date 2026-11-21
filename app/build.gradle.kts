plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pedro.yarisobd"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pedro.yarisobd"
        // 26 mantem o app instalavel no celular de uso geral; na pratica o
        // Android Auto atual exige Android 11+ para o app aparecer no carro.
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-poc"
    }

    buildFeatures {
        buildConfig = true
    }

    // Categoria do app no Android Auto, escolhida em tempo de build:
    //   gradlew :app:assembleDebug -PcarCategory=androidx.car.app.category.NAVIGATION
    // O default é IOT (categoria de template genérica). NAVIGATION é a que a
    // documentação associa ao uso de NavigationTemplate + ACCESS_SURFACE.
    defaultConfig {
        manifestPlaceholders["carCategory"] =
            (project.findProperty("carCategory") as String?)
                ?: "androidx.car.app.category.IOT"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        debug {
            // Sem sufixo de applicationId: o Android Auto identifica o app pelo
            // pacote, e dois pacotes diferentes (debug/release) complicam o teste
            // na central sem nenhum ganho.
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            // APK de uso pessoal: assinado com a chave de debug para permitir
            // instalacao por sideload sem configurar keystore propria.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging {
        resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Android for Cars App Library: templates + superfície para desenho próprio.
    implementation("androidx.car.app:app:1.7.0")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
}
