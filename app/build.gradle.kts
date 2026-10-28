import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// CORE ships the hand tracker; large conversation/vision data is opt-in.
val bundleModel = providers.gradleProperty("nexusBundleModel").map(String::toBoolean).getOrElse(false)
val bundleVision = providers.gradleProperty("nexusBundleVision").map(String::toBoolean).getOrElse(false)
val bundledAssets = rootProject.file(".build-deps/bundled-assets")
val preparedAssets = layout.buildDirectory.dir("generated/nexusAssets")
val prepareNexusAssets by tasks.registering(Sync::class) {
    into(preparedAssets)
    from(layout.projectDirectory.dir("src/main/assets")) {
        if (!bundleVision) exclude("ocr/**", "vision/**")
    }
    if (bundleModel) from(bundledAssets)
}
val selectedAbis = providers.gradleProperty("nexusAbis").orNull
    ?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)
    ?: listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
check(selectedAbis.isNotEmpty() && selectedAbis.all { it in setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") }) {
    "nexusAbis deve conter apenas arquiteturas oficiais suportadas, separadas por vírgula."
}
// Explicit opt-in: release credentials come only from the developer/CI environment.
// A missing value must stop a requested signed build, never fall back to a debug key.
val releaseSigningRequested = providers.gradleProperty("nexusReleaseSigning").map(String::toBoolean).getOrElse(false)
fun releaseCredential(name: String): String = providers.environmentVariable(name).orNull
    ?.takeIf { it.isNotEmpty() } ?: error("Assinatura de release incompleta: configure $name no ambiente seguro.")


android {
    namespace = "br.com.nexus.assistant"
    compileSdk = 35
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "br.com.nexus.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 27
        versionName = "4.1.0-alpha.1-core"
        buildConfigField("boolean", "BUNDLED_MODEL", bundleModel.toString())
        buildConfigField("boolean", "BUNDLED_VISION", bundleVision.toString())
        buildConfigField("boolean", "CORE_INSTALLATION", (!bundleModel && !bundleVision).toString())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        // Universal installation; local LLM inference is packaged only for 64-bit ABIs.
        // 32-bit devices retain the UI, voice and optional HTTP provider.
        ndk { abiFilters += selectedAbis }
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
    signingConfigs {
        if (releaseSigningRequested) create("nexusRelease") {
            storeFile = rootProject.file(releaseCredential("NEXUS_RELEASE_STORE_FILE"))
            storePassword = releaseCredential("NEXUS_RELEASE_STORE_PASSWORD")
            keyAlias = releaseCredential("NEXUS_RELEASE_KEY_ALIAS")
            keyPassword = releaseCredential("NEXUS_RELEASE_KEY_PASSWORD")
            enableV1Signing = true
            enableV2Signing = true
        }
    }
    buildTypes {
        release {
            if (releaseSigningRequested) signingConfig = signingConfigs.getByName("nexusRelease")
            // Preserve SDK/JNI and shared instrumentation APIs in this alpha.
            // Size is reduced by optional model data and the selected icon set.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Installable production-like evaluation using the existing local test key.
        // This does not create a production signing identity.
        create("coreTest") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += listOf("release")
        }
    }
    testBuildType = providers.gradleProperty("nexusTestBuildType").getOrElse("debug")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // MediaPipe model loaders may memory-map these assets.
    androidResources { noCompress += listOf("task", "tflite", "gguf") }
    sourceSets.getByName("main").assets.setSrcDirs(listOf(preparedAssets))
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    // JVM tests use explicit streams/files; packaging gigabytes of Android assets here is unnecessary.
    testOptions { unitTests.isIncludeAndroidResources = false }
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

// The legacy full package remains an explicit developer option, never the CORE default.
val verifyBundledModel by tasks.registering {
    val expected = listOf(
        Triple("qwen2.5-0.5b-instruct-q4_k_m.gguf", 491_400_032L, "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"),
        Triple("qwen2.5-1.5b-instruct-q4_k_m.gguf", 1_117_320_736L, "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e"),
    )
    inputs.files(expected.map { File(bundledAssets, "models/${it.first}") })
    onlyIf { bundleModel }
    doLast {
        expected.forEach { (name, size, sha) ->
            val model = File(bundledAssets, "models/$name")
            check(model.isFile && model.length() == size) {
                "Execute python3 scripts/fetch_bundled_model.py --with-balanced para preparar os modelos incluídos no APK."
            }
            check(sha256(model) == sha) { "Modelo incluído inválido: $name. Prepare novamente os arquivos verificados." }
        }
    }
}
fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
}
// Transparent upstream-default no-usage-logging build, prepared reproducibly from a pinned AAR.
val mediaPipeRuntime = rootProject.file(".build-deps/mediapipe/tasks-core-0.10.35-no-usage-logging.aar")
val verifyMediaPipeRuntime by tasks.registering {
    inputs.file(mediaPipeRuntime)
    doLast {
        check(mediaPipeRuntime.isFile && mediaPipeRuntime.length() == 50_686_496L &&
            sha256(mediaPipeRuntime) == "25ac823c37f77d2870c32bfbe0dfae30b2b1a921b7fce395e743399c3b638bf3") {
            "Execute python3 scripts/prepare_mediapipe_runtime.py para preparar o MediaPipe sem envio de métricas de uso."
        }
    }
}
tasks.named("preBuild").configure {
    dependsOn(verifyMediaPipeRuntime, prepareNexusAssets)
    if (bundleModel) dependsOn(verifyBundledModel)
}

val verifyCoreHandModel by tasks.registering {
    val handModel = layout.projectDirectory.file("src/main/assets/hand_landmarker.task")
    inputs.file(handModel)
    doLast {
        val file = handModel.asFile
        check(file.isFile && file.length() == 7_819_105L &&
            sha256(file) == "fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1") {
            "O CORE precisa do rastreador offline íntegro. Execute python3 scripts/fetch_hand_model.py."
        }
    }
}
tasks.named("preBuild").configure { dependsOn(verifyCoreHandModel) }

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
    implementation("io.noties.markwon:core:4.6.2")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
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
    implementation("com.google.mediapipe:tasks-vision:0.10.35") {
        exclude(group = "com.google.mediapipe", module = "tasks-core")
    }
    implementation(files(mediaPipeRuntime).builtBy(verifyMediaPipeRuntime))
    // Explicit dependencies from the pinned upstream core POM; remote classes stay
    // present but the factory follows the upstream no-op usage-logging default.
    implementation("com.google.flogger:flogger:0.6")
    implementation("com.google.flogger:flogger-system-backend:0.6")
    // Patched Android variant; versions and OSV evidence are recorded in the security audit.
    implementation("com.google.guava:guava:33.7.2-android")
    // Security update for protobuf unknown-field recursion (CVE-2024-7254).
    implementation("com.google.protobuf:protobuf-javalite:4.28.3")
    implementation("com.google.android.datatransport:transport-api:3.0.0")
    implementation("com.google.android.datatransport:transport-backend-cct:3.1.0")
    implementation("com.google.android.datatransport:transport-runtime:3.1.0")
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
