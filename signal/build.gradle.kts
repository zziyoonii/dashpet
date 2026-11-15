plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.dashpet.signal"
    compileSdk {
        version = release(34)
    }

    defaultConfig {
        minSdk = 29
    }
    // AAOS 표준 Car API(android.car)는 컴파일 전용으로 참조한다. 런타임에는 부트 클래스패스가 제공한다(research R1).
    // 이 참조를 쓰는 파일은 car/AndroidCarGateway.kt 하나뿐이다.
    useLibrary("android.car")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.pleos.vehicle) // Pleos SDK 의존성은 :signal 에만 둔다
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
