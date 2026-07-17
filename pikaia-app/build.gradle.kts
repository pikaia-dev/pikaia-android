plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kover)
}

android {
    namespace = "dev.pikaia.android"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "dev.pikaia.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Backend base URL including the API prefix; override per environment with
        // -Ppikaia.api.baseUrl=https://your-backend/api/v1
        val baseUrl = (project.findProperty("pikaia.api.baseUrl") as String?)
            ?: "https://api.example.com/api/v1"
        buildConfigField("String", "PIKAIA_API_BASE_URL", "\"$baseUrl\"")
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Merge SDK module coverage into this module's kover report (CI reads one XML)
    kover(project(":sdk-core"))
    kover(project(":sdk-auth"))
    kover(project(":sdk-sync"))

    // Pikaia SDK BOM
    implementation(platform(project(":sdk-bom")))

    // Pikaia SDK modules
    implementation(project(":sdk-core"))
    implementation(project(":sdk-auth"))
    implementation(project(":sdk-sync"))

    // AndroidX dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.androidx.compose.nav)
    implementation(libs.timber)
    implementation(libs.androidx.navigation.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.material.icons.extended)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.ktor.client.mock)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // Debug
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

}

kover {
    reports {
        filters {
            excludes {
                packages(
                    "dev.pikaia.android.di",
                    "dev.pikaia.android.navigation",
                    "dev.pikaia.android.theme",
                    "dev.pikaia.android.**.ui",
                    "dev.pikaia.android.**.di"
                )
                files(
                    "*Screen*",
                    "*Dialog*"
                )
                classes(
                    "*ComposableSingletons*",
                    "*ViewModel*"
                )
                annotatedBy("*Composable")
            }
        }
    }
}