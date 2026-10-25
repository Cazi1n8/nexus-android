import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val bundleModel = providers.gradleProperty("nexusBundleModel").map(String::toBoolean).getOrElse(true)
val bundledAssets = rootProject.file(".build-deps/bundled-assets")

android {
    namespace = "br.com.nexus.assistant"
    compileSdk = 35
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "br.com.nexus.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 23
        versionName = "2.0.0-alpha.4"
        buildConfigField("boolean", "BUNDLED_MODEL", bundleModel.toString())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        // Universal installation; local LLM inference is packaged only for 64-bit ABIs.
        // 32-bit devices retain the UI, voice and optional HTTP provider.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DGGML_NATIVE=OFF",
                    "-DGGML_CPU_ARM_ARCH=armv8-a",
                    "-DGGML_OPENMP=OFF",
                    "-DGGML_CPU_KLEIDIAI=OFF",
                    "-DGGML_BACKEND_DL=OFF",
                    "-DGGML_AVX=OFF", "-DGGML_AVX2=OFF", "-DGGML_BMI2=OFF",
                    "-DGGML_FMA=OFF", "-DGGML_F16C=OFF",
                    "-DLLAMA_BUILD_COMMON=OFF", "-DLLAMA_BUILD_TESTS=OFF",
                    "-DLLAMA_BUILD_EXAMPLES=OFF", "-DLLAMA_BUILD_SERVER=OFF",
                    "-DLLAMA_BUILD_TOOLS=OFF", "-DLLAMA_BUILD_APP=OFF",
                    "-DLLAMA_OPENSSL=OFF",
                )
            }
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // MediaPipe model loaders may memory-map these assets.
    androidResources { noCompress += listOf("task", "tflite", "gguf") }
    if (bundleModel) sourceSets.getByName("main").assets.srcDir(bundledAssets)
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

// Prepared from the upstream release by fetch_ocr_runtime.py. Using the verified
// AAR directly avoids intermittent JitPack POM failures on clean CI machines.
val ocrRuntime = rootProject.file(".build-deps/ocr/tesseract4android-4.9.0.aar")
val verifyOcrRuntime by tasks.registering {
    inputs.files(ocrRuntime)
    doLast {
        check(ocrRuntime.isFile && ocrRuntime.length() == 12_848_454L) {
            "Execute python3 scripts/fetch_ocr_runtime.py antes do Gradle para preparar o OCR."
        }
        val digest = MessageDigest.getInstance("SHA-256")
        ocrRuntime.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        check(actual == "bce5d6413a1a5ae3d7240033fbbc851ba3217d0a08d9769400e17a077f42cb2a") {
            "O AAR de OCR não corresponde ao checksum fixado. Prepare novamente com fetch_ocr_runtime.py."
        }
    }
}

// Full builds fail before packaging if the pinned bundled model is absent or altered.
val verifyBundledModel by tasks.registering {
    val model = File(bundledAssets, "models/qwen2.5-0.5b-instruct-q4_k_m.gguf")
    inputs.files(model)
    onlyIf { bundleModel }
    doLast {
        check(model.isFile && model.length() == 491_400_032L) {
            "Execute python3 scripts/fetch_bundled_model.py para preparar a IA incluída no APK."
        }
        val digest = MessageDigest.getInstance("SHA-256")
        model.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        check(actual == "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db") {
            "Modelo incluído inválido. Prepare novamente com fetch_bundled_model.py."
        }
    }
}
tasks.named("preBuild").configure { if (bundleModel) dependsOn(verifyBundledModel) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.mediapipe:tasks-vision:0.10.35")
    implementation(files(ocrRuntime).builtBy(verifyOcrRuntime))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
}
