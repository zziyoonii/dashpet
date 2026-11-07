plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.dashpet.signal"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 29
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.pleos.vehicle) // Pleos SDK 의존성은 :signal 에만 둔다
    testImplementation(libs.junit)
}
