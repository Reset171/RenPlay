import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "ru.reset.renplay"
    compileSdk = 36

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    defaultConfig {
        applicationId = "ru.reset.renplay"
        minSdk = 28
        targetSdk = 34
        versionCode = 2
        versionName = "0.2"
        vectorDrawables { useSupportLibrary = true }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("key.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties()
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))

                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storePassword = keystoreProperties["storePassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            val keystorePropertiesFile = rootProject.file("key.properties")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            val keystorePropertiesFile = rootProject.file("key.properties")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    
    kotlin {
        jvmToolchain(21)
    }

    buildFeatures { compose = true }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(module("androidx.core:core")).using(module("sesl.androidx.core:core:1.17.0+1.0.7-sesl8+rev1"))
        substitute(module("androidx.core:core-ktx")).using(module("sesl.androidx.core:core-ktx:1.17.0+1.0.0-sesl8+rev0"))
        substitute(module("androidx.appcompat:appcompat")).using(module("sesl.androidx.appcompat:appcompat:1.7.1+1.0.21-sesl8+rev8"))
        substitute(module("androidx.recyclerview:recyclerview")).using(module("sesl.androidx.recyclerview:recyclerview:1.4.0+1.0.12-sesl8+rev3"))
        substitute(module("androidx.customview:customview")).using(module("sesl.androidx.customview:customview:1.2.0-rc01+1.0.0-sesl8+rev0"))
        substitute(module("androidx.drawerlayout:drawerlayout")).using(module("sesl.androidx.drawerlayout:drawerlayout:1.2.0+1.0.0-sesl8+rev0"))
        substitute(module("androidx.fragment:fragment")).using(module("sesl.androidx.fragment:fragment:1.8.9+1.0.5-sesl8+rev1"))
        substitute(module("androidx.preference:preference")).using(module("sesl.androidx.preference:preference:1.2.1+1.0.0-sesl8+rev1"))
        substitute(module("androidx.viewpager:viewpager")).using(module("sesl.androidx.viewpager:viewpager:1.1.0-beta01+1.0.0-sesl8+rev0"))
        substitute(module("androidx.viewpager2:viewpager2")).using(module("sesl.androidx.viewpager2:viewpager2:1.1.0+1.0.0-sesl8+rev0"))
        substitute(module("androidx.coordinatorlayout:coordinatorlayout")).using(module("sesl.androidx.coordinatorlayout:coordinatorlayout:1.3.0+1.0.0-sesl8+rev0"))
        substitute(module("androidx.slidingpanelayout:slidingpanelayout")).using(module("sesl.androidx.slidingpanelayout:slidingpanelayout:1.2.0+1.0.4-sesl8+rev0"))
        substitute(module("androidx.swiperefreshlayout:swiperefreshlayout")).using(module("sesl.androidx.swiperefreshlayout:swiperefreshlayout:1.2.0-alpha01+1.0.0-sesl8+rev0"))
        substitute(module("com.google.android.material:material")).using(module("sesl.com.google.android.material:material:1.12.0+1.0.32-sesl8+rev2"))
    }
    exclude(group = "sesl.androidx.picker", module = "picker-color")
    exclude(group = "sesl.androidx.picker", module = "picker-app")
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.oneui.design)
    implementation(libs.oneui.icons)
    implementation(libs.sesl.appcompat)
    implementation(libs.sesl.material)
    implementation(libs.sesl.recyclerview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
}
