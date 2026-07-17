plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlinSerialization)
    `maven-publish`
}

group = "dev.pikaia.android"
version = project.findProperty("pikaia.sdk.version") as String? ?: "0.1.0-SNAPSHOT"

android {
    namespace = "dev.pikaia.android.sdk.auth"
    compileSdk = 36

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
    // SDK Core
    api(project(":sdk-core"))

    // Android Core
    implementation(libs.androidx.core.ktx)

    // DataStore for secure token storage (values encrypted via Android Keystore)
    implementation(libs.androidx.datastore.preferences)

    // Coroutines (inherited from core but explicit for clarity)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Kotlinx Serialization (inherited from core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

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
                artifactId = "sdk-auth"
                version = project.version.toString()
            }
        }
        repositories {
            mavenLocal()
        }
    }
}