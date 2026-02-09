# Publishing and Using Pikaia SDK Locally

This guide shows you how to publish the Pikaia SDK to your local Maven repository and use it in other Android projects.

## Publishing to Local Maven

### Quick Publish

To publish all SDK modules (BOM + all libraries) to your local Maven repository:

```bash
./gradlew publishToMavenLocal
```

This will publish:
- `dev.pikaia.android:sdk-bom:0.1.0-SNAPSHOT`
- `dev.pikaia.android:sdk-core:0.1.0-SNAPSHOT`
- `dev.pikaia.android:sdk-auth:0.1.0-SNAPSHOT`
- `dev.pikaia.android:sdk-sync:0.1.0-SNAPSHOT`

### Publish Individual Modules

You can also publish individual modules:

```bash
# Publish only BOM
./gradlew :sdk-bom:publishToMavenLocal

# Publish only Core
./gradlew :sdk-core:publishToMavenLocal

# Publish only Auth
./gradlew :sdk-auth:publishToMavenLocal

# Publish only Sync
./gradlew :sdk-sync:publishToMavenLocal
```

### Set Custom Version

By default, the SDK uses version `0.1.0-SNAPSHOT`. To publish with a custom version:

```bash
./gradlew publishToMavenLocal -Ppikaia.sdk.version=1.0.0
```

Or add it to `gradle.properties`:

```properties
pikaia.sdk.version=1.0.0
```

### Location

Published artifacts are located in:
```
~/.m2/repository/dev/pikaia/android/
├── sdk-bom/
├── sdk-core/
├── sdk-auth/
└── sdk-sync/
```

## Using in Other Projects

### Option 1: Using BOM (Recommended)

The BOM (Bill of Materials) manages all SDK versions for you, ensuring compatibility.

**In your project's `settings.gradle.kts`:**
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()  // Add this line
    }
}
```

**In your app's `build.gradle.kts`:**
```kotlin
dependencies {
    // Import the BOM
    implementation(platform("dev.pikaia.android:sdk-bom:0.1.0-SNAPSHOT"))

    // Add SDK dependencies without specifying versions
    implementation("dev.pikaia.android:sdk-core")
    implementation("dev.pikaia.android:sdk-auth")
    implementation("dev.pikaia.android:sdk-sync")

    // Other dependencies...
}
```

### Option 2: Using Catalog (Alternative)

**In your project's `libs.versions.toml`:**
```toml
[versions]
pikaia-sdk = "0.1.0-SNAPSHOT"

[libraries]
pikaia-bom = { group = "dev.pikaia.android", name = "sdk-bom", version.ref = "pikaia-sdk" }
pikaia-core = { group = "dev.pikaia.android", name = "sdk-core" }
pikaia-auth = { group = "dev.pikaia.android", name = "sdk-auth" }
pikaia-sync = { group = "dev.pikaia.android", name = "sdk-sync" }
```

**In your app's `build.gradle.kts`:**
```kotlin
dependencies {
    implementation(platform(libs.pikaia.bom))
    implementation(libs.pikaia.core)
    implementation(libs.pikaia.auth)
    implementation(libs.pikaia.sync)
}
```

### Option 3: Direct Dependencies (Not Recommended)

If you don't want to use the BOM, you can specify versions explicitly:

```kotlin
dependencies {
    implementation("dev.pikaia.android:sdk-core:0.1.0-SNAPSHOT")
    implementation("dev.pikaia.android:sdk-auth:0.1.0-SNAPSHOT")
    implementation("dev.pikaia.android:sdk-sync:0.1.0-SNAPSHOT")
}
```

**Note:** This approach requires you to manually keep all SDK versions in sync.

## Example: Complete Setup

Here's a complete example of setting up a new project to use the Pikaia SDK:

### 1. Enable mavenLocal in `settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}

rootProject.name = "My App"
include(":app")
```

### 2. Add dependencies in `app/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.myapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.myapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    // Pikaia SDK using BOM
    implementation(platform("dev.pikaia.android:sdk-bom:0.1.0-SNAPSHOT"))
    implementation("dev.pikaia.android:sdk-core")
    implementation("dev.pikaia.android:sdk-auth")
    implementation("dev.pikaia.android:sdk-sync")

    // Other dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
}
```

### 3. Sync and Build
```bash
./gradlew build
```

## Verifying Installation

To verify the SDK is properly installed in your new project:

```kotlin
import dev.pikaia.android.sdk.core.PikaiaClient
import dev.pikaia.android.sdk.auth.AuthAPI
import dev.pikaia.android.sdk.sync.engine.SyncEngine

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If this compiles, the SDK is properly installed
        val client = PikaiaClient(baseURL = "https://api.example.com")
        println("Pikaia SDK loaded successfully!")
    }
}
```

## Workflow Summary

1. **Develop**: Make changes to the Pikaia SDK
2. **Build**: `./gradlew build`
3. **Publish**: `./gradlew publishToMavenLocal`
4. **Use**: Add dependencies to your other projects
5. **Sync**: Run Gradle sync in your other projects

## Troubleshooting

### SDK not found
- Ensure you've run `./gradlew publishToMavenLocal` in the pikaia-android project
- Check that `mavenLocal()` is in your repositories list
- Verify artifacts exist in `~/.m2/repository/dev/pikaia/android/`

### Version conflicts
- Use the BOM to manage versions automatically
- Clear Gradle cache: `./gradlew clean --refresh-dependencies`

### Changes not reflected
- Republish: `./gradlew publishToMavenLocal`
- Invalidate caches in Android Studio: File → Invalidate Caches → Invalidate and Restart
- Clean your app project: `./gradlew clean`
