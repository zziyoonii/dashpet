plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.dashpet.engine"
    compileSdk {
        version = release(34)
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
    api(project(":signal"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
