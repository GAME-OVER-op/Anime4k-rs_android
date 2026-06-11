plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.anime4k.upscaler"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.anime4k.upscaler"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        jniLibs.keepDebugSymbols += setOf("**/libffmpeg.so", "**/libffprobe.so")
    }
}

val rustProjectDir = rootProject.file("rust")
val jniLibsDir = project.file("src/main/jniLibs")
val rustTargets = listOf("arm64-v8a")

tasks.register("buildRust") {
    group = "rust"
    description = "Builds Rust JNI library for Android ABIs using cargo-ndk."
    doLast {
        rustTargets.forEach { abi ->
            exec {
                workingDir = rustProjectDir
                commandLine(
                    "cargo", "ndk",
                    "-t", abi,
                    "-o", jniLibsDir.absolutePath,
                    "--manifest-path", rustProjectDir.resolve("Cargo.toml").absolutePath,
                    "build", "--release"
                )
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn("buildRust")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
