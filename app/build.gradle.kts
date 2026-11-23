import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

val signingProps = Properties()
val signingPropsFile = rootProject.file("signing.properties")
if (signingPropsFile.exists()) signingProps.load(signingPropsFile.inputStream())

android {
    namespace = "df.root"
    testOptions { animationsDisabled = true }
    compileSdk { version = release(37) { minorApiLevel = 2 } }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    defaultConfig {
        applicationId = "df.root"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        minSdk = 33
        targetSdk = 37
        versionCode = 412
        versionName = "Next v1.0"
        buildConfigField("boolean", "UI_INSPECTION", providers.gradleProperty("uiInspection").orNull ?: "false")

        ndk {
            abiFilters += if (providers.gradleProperty("uiInspection").isPresent) listOf("x86_64") else listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(signingProps.getProperty("KEYSTORE_FILE"))
            storePassword = signingProps.getProperty("KEYSTORE_PASSWORD")
            keyAlias = signingProps.getProperty("KEY_ALIAS")
            keyPassword = signingProps.getProperty("KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        checkReleaseBuilds = false
    }

    if (!providers.gradleProperty("uiInspection").isPresent) {
        externalNativeBuild {
            cmake { path("src/main/jni/CMakeLists.txt") }
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set(output.versionName.map { vn -> "DFRoot_${vn}.apk" })
        }
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.5-beta03")
    implementation("top.yukonga.miuix.kmp:miuix-glass-android:0.9.5-beta03")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.5-beta03")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}


kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }
