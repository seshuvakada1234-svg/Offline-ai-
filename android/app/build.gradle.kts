plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
}

android {
    namespace = "com.myai.offline"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.myai.offline"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))
        }

        val localProps = project.rootProject.file("local.properties")
        val hasLocalNdk = localProps.exists() && localProps.readText().contains("ndk.dir")
        val hasEnvNdk = System.getenv("ANDROID_NDK_HOME") != null || System.getenv("ANDROID_NDK_ROOT") != null
        val includeNative = project.hasProperty("includeNative") || hasLocalNdk || hasEnvNdk || project.file("src/main/cpp/CMakeLists.txt").exists()

        if (includeNative) {
            externalNativeBuild {
                cmake {
                    cppFlags.addAll(listOf("-std=c++17", "-O3", "-fexceptions", "-frtti"))
                    arguments.addAll(listOf("-DANDROID_STL=c++_shared"))
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        disable += listOf("FullBackupContent")
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    val rootLocalProps = project.rootProject.file("local.properties")
    val hasRootNdk = rootLocalProps.exists() && rootLocalProps.readText().contains("ndk.dir")
    val hasSysNdk = System.getenv("ANDROID_NDK_HOME") != null || System.getenv("ANDROID_NDK_ROOT") != null
    val enableNativeBuild = project.hasProperty("includeNative") || hasRootNdk || hasSysNdk || project.file("src/main/cpp/CMakeLists.txt").exists()

    if (enableNativeBuild) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.commons.compress)
    implementation(files("libs/sherpa-onnx-1.13.7.aar"))

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
