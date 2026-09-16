plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.clickdownloader.core.extractor"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:download"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.youtubedl.android)
    implementation(libs.jackson.databind)
    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
