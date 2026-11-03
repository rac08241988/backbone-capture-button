plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rattlehead.backbonecapture"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rattlehead.backbonecapture"
        minSdk = 28  // really needs Android 10+ (internal audio); kept equal to targetSdk
        // Target 28 on purpose: this is a personal root tool installed by the Magisk module.
        // It keeps MediaProjection/foreground-service rules simple and lets the module
        // pre-approve screen capture with appops, so no prompt appears for each recording.
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 28
        versionCode = 3
        versionName = "1.2"
    }

    // Signing keys are NOT in this repo. Pass them with -P flags (see companion-app/README.md).
    signingConfigs {
        create("release") {
            val store = project.findProperty("BBC_STORE_FILE") as String?
            if (store != null) {
                storeFile = file(store)
                storePassword = project.findProperty("BBC_STORE_PASSWORD") as String?
                keyAlias = project.findProperty("BBC_KEY_ALIAS") as String?
                keyPassword = project.findProperty("BBC_KEY_PASSWORD") as String?
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (project.findProperty("BBC_STORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
