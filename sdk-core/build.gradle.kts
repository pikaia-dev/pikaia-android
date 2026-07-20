plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kover)
    `maven-publish`
}

group = "dev.pikaia.android"
version = project.findProperty("pikaia.sdk.version") as String? ?: "0.1.0-SNAPSHOT"

android {
    namespace = "dev.pikaia.android.sdk_core"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    // Android Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // Ktor HTTP Client
    api(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)

    // Kotlinx Serialization
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.datetime)

    // Coroutines
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "dev.pikaia.android"
                artifactId = "sdk-core"
                version = project.version.toString()
            }
        }
        repositories {
            mavenLocal()
        }
    }
}