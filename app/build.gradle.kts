import java.util.Properties
import org.gradle.api.tasks.Sync

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val releaseSigningProperties = providers.gradleProperty("clickDownloaderKeystoreProperties")
    .orElse(providers.environmentVariable("CLICK_DOWNLOADER_KEYSTORE_PROPERTIES"))
    .orNull
    ?.let(::file)
    ?.takeIf { it.isFile }
    ?.let { propertiesFile ->
        Properties().apply { propertiesFile.inputStream().use(::load) }
    }

val clickDownloaderNdkVersion = "29.0.14206865"
val ndkHostTag = when {
    System.getProperty("os.name").startsWith("Windows", ignoreCase = true) -> "windows-x86_64"
    System.getProperty("os.name").startsWith("Mac", ignoreCase = true) -> "darwin-x86_64"
    else -> "linux-x86_64"
}
val sdkDirectory = providers.environmentVariable("ANDROID_SDK_ROOT")
    .orElse(providers.environmentVariable("ANDROID_HOME"))
    .orElse(providers.provider {
        Properties().apply {
            rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
        }.getProperty("sdk.dir") ?: error("Set ANDROID_SDK_ROOT or sdk.dir in local.properties")
    })
val generatedCxxRuntime = layout.buildDirectory.dir("generated/cxx-runtime")
val syncCxxRuntime by tasks.registering(Sync::class) {
    val llvmLib = sdkDirectory.map {
        file("$it/ndk/$clickDownloaderNdkVersion/toolchains/llvm/prebuilt/$ndkHostTag/sysroot/usr/lib")
    }
    into(generatedCxxRuntime)
    from(llvmLib.map { it.resolve("aarch64-linux-android/libc++_shared.so") }) { into("arm64-v8a") }
    from(llvmLib.map { it.resolve("arm-linux-androideabi/libc++_shared.so") }) { into("armeabi-v7a") }
    from(llvmLib.map { it.resolve("i686-linux-android/libc++_shared.so") }) { into("x86") }
    from(llvmLib.map { it.resolve("x86_64-linux-android/libc++_shared.so") }) { into("x86_64") }
    doFirst {
        require(llvmLib.get().isDirectory) {
            "Android NDK $clickDownloaderNdkVersion is required; install it with sdkmanager."
        }
    }
}

android {
    namespace = "com.clickdownloader.app"
    compileSdk = 37
    ndkVersion = clickDownloaderNdkVersion

    defaultConfig {
        applicationId = "com.clickdownloader.app"
        minSdk = 26
        targetSdk = 37
        versionCode = providers.gradleProperty("clickVersionCode").orElse("12").get().toInt()
        versionName = providers.gradleProperty("clickVersionName").orElse("1.0.2").get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        releaseSigningProperties?.let { signing ->
            create("externalRelease") {
                storeFile = file(requireNotNull(signing.getProperty("storeFile")) { "Missing storeFile" })
                storePassword = requireNotNull(signing.getProperty("storePassword")) { "Missing storePassword" }
                keyAlias = requireNotNull(signing.getProperty("keyAlias")) { "Missing keyAlias" }
                keyPassword = requireNotNull(signing.getProperty("keyPassword")) { "Missing keyPassword" }
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("externalRelease")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    packaging.jniLibs.useLegacyPackaging = true
    packaging.jniLibs.keepDebugSymbols += setOf("**/libffmpeg.zip.so", "**/libpython.zip.so")
    sourceSets.getByName("main").jniLibs.srcDir(generatedCxxRuntime.get().asFile)
}

tasks.configureEach {
    if (name.startsWith("merge") && (name.endsWith("JniLibFolders") || name.endsWith("NativeLibs"))) {
        dependsOn(syncCxxRuntime)
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:download"))
    implementation(project(":core:extractor"))
    implementation(project(":core:media"))
    implementation(project(":core:storage"))
    implementation(project(":core:browser"))

    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime)
    implementation(libs.okhttp)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.youtubedl.ffmpeg)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
