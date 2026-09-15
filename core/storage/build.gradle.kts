plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.clickdownloader.core.storage"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit4)
}
