# Using Pikaia SDK with Version Catalogs (libs.versions.toml)

This guide shows you how to add Pikaia SDK dependencies using Gradle Version Catalogs, which is the modern and recommended approach for managing dependencies.

## Why Use Version Catalogs?

✅ **Centralized version management** - All versions in one file
✅ **Type-safe accessors** - Autocomplete in IDE
✅ **Consistent across modules** - Share catalog across multi-module projects
✅ **Easy updates** - Change version in one place
✅ **Better readability** - `libs.pikaia.core` vs `"dev.pikaia.android:sdk-core"`

---

## Setup Instructions

### Step 1: Enable mavenLocal Repository

**In `settings.gradle.kts`:**
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()  // ← Add this for locally published SDK
    }
}
```

### Step 2: Add Pikaia SDK to Version Catalog

**In `gradle/libs.versions.toml`:**

```toml
[versions]
# ... your existing versions ...
pikaia = "0.1.0-SNAPSHOT"

[libraries]
# ... your existing libraries ...

# Pikaia SDK
pikaia-bom = { group = "dev.pikaia.android", name = "sdk-bom", version.ref = "pikaia" }
pikaia-core = { group = "dev.pikaia.android", name = "sdk-core" }
pikaia-auth = { group = "dev.pikaia.android", name = "sdk-auth" }
pikaia-sync = { group = "dev.pikaia.android", name = "sdk-sync" }
```

**Important Notes:**
- Only the **BOM** has a version specified (`version.ref = "pikaia"`)
- Individual modules (`pikaia-core`, `pikaia-auth`, `pikaia-sync`) **do not** have versions
- The BOM manages all module versions automatically

### Step 3: Use in Your Build Script

**In `app/build.gradle.kts`:**
```kotlin
dependencies {
    // Pikaia SDK - using BOM for version management
    implementation(platform(libs.pikaia.bom))
    implementation(libs.pikaia.core)
    implementation(libs.pikaia.auth)
    implementation(libs.pikaia.sync)

    // Your other dependencies...
}
```

### Step 4: Sync and Build
```bash
./gradlew build
```

---

## Complete Example

Here's a complete working example from the scaffoldtest project:

### `gradle/libs.versions.toml`
```toml
[versions]
agp = "9.0.0"
kotlin = "2.3.0"
pikaia = "0.1.0-SNAPSHOT"
# ... other versions ...

[libraries]
# Pikaia SDK
pikaia-bom = { group = "dev.pikaia.android", name = "sdk-bom", version.ref = "pikaia" }
pikaia-core = { group = "dev.pikaia.android", name = "sdk-core" }
pikaia-auth = { group = "dev.pikaia.android", name = "sdk-auth" }
pikaia-sync = { group = "dev.pikaia.android", name = "sdk-sync" }

# Other libraries...
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
# ...

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
# ...
```

### `app/build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.myapp"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 36
    }
}

dependencies {
    // Pikaia SDK
    implementation(platform(libs.pikaia.bom))
    implementation(libs.pikaia.core)
    implementation(libs.pikaia.auth)
    implementation(libs.pikaia.sync)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // ...
}
```

---

## Advanced Patterns

### Option 1: Using Only Specific Modules

If you only need some SDK modules:

```kotlin
dependencies {
    implementation(platform(libs.pikaia.bom))
    implementation(libs.pikaia.core)  // Only core
    implementation(libs.pikaia.auth)  // Only auth
    // No sync module
}
```

### Option 2: Using bundles

Create a bundle for all Pikaia SDK modules:

**In `gradle/libs.versions.toml`:**
```toml
[bundles]
pikaia = ["pikaia-core", "pikaia-auth", "pikaia-sync"]
```

**In `app/build.gradle.kts`:**
```kotlin
dependencies {
    implementation(platform(libs.pikaia.bom))
    implementation(libs.bundles.pikaia)  // Adds all three modules at once
}
```

### Option 3: Different Versions for Testing

**In `gradle/libs.versions.toml`:**
```toml
[versions]
pikaia = "0.1.0-SNAPSHOT"
pikaia-test = "1.0.0"
```

**In `app/build.gradle.kts`:**
```kotlin
dependencies {
    implementation(platform(libs.pikaia.bom))
    implementation(libs.pikaia.core)

    // Use a different version for testing
    testImplementation("dev.pikaia.android:sdk-core:1.0.0")
}
```

---

## Updating the SDK Version

When you publish a new version of the SDK, just update one line:

**In `gradle/libs.versions.toml`:**
```toml
[versions]
pikaia = "0.2.0-SNAPSHOT"  # ← Change only this line
```

Then sync:
```bash
./gradlew build --refresh-dependencies
```

All modules will automatically use the new version!

---

## IDE Integration

### Autocomplete Support

When typing in your `build.gradle.kts`, you'll get autocomplete:
- Type `libs.pikaia.` → IDE shows: `bom`, `core`, `auth`, `sync`

### Refactoring Support

If you rename a library in `libs.versions.toml`, the IDE will help refactor all usages.

### Navigate to Definition

Cmd/Ctrl + Click on `libs.pikaia.core` → jumps to definition in `libs.versions.toml`

---

## Comparison: Version Catalog vs Direct Dependencies

### Version Catalog (Recommended) ✅
```kotlin
// libs.versions.toml
[versions]
pikaia = "0.1.0-SNAPSHOT"

[libraries]
pikaia-bom = { group = "dev.pikaia.android", name = "sdk-bom", version.ref = "pikaia" }
pikaia-core = { group = "dev.pikaia.android", name = "sdk-core" }

// build.gradle.kts
dependencies {
    implementation(platform(libs.pikaia.bom))
    implementation(libs.pikaia.core)
}
```

**Pros:**
- ✅ Centralized version management
- ✅ Type-safe, autocomplete support
- ✅ Easy to maintain and update
- ✅ Consistent across multi-module projects

### Direct Dependencies (Alternative)
```kotlin
dependencies {
    implementation(platform("dev.pikaia.android:sdk-bom:0.1.0-SNAPSHOT"))
    implementation("dev.pikaia.android:sdk-core")
}
```

**Pros:**
- ✅ Simpler for single-module projects
- ✅ Less setup

**Cons:**
- ❌ No autocomplete
- ❌ Harder to update versions
- ❌ Version scattered across multiple files

---

## Multi-Module Projects

Version catalogs really shine in multi-module projects:

```
my-app/
├── app/
│   └── build.gradle.kts
├── feature-a/
│   └── build.gradle.kts
├── feature-b/
│   └── build.gradle.kts
└── gradle/
    └── libs.versions.toml  ← One catalog for all modules
```

All modules can use:
```kotlin
dependencies {
    implementation(platform(libs.pikaia.bom))
    implementation(libs.pikaia.core)
}
```

Change the version once in `libs.versions.toml`, all modules get updated!

---

## Troubleshooting

### "Unresolved reference: libs"

**Cause:** Version catalog not properly configured

**Solution:**
1. Check file location: `gradle/libs.versions.toml` (not `build/libs.versions.toml`)
2. Sync project: File → Sync Project with Gradle Files
3. Invalidate caches: File → Invalidate Caches → Restart

### "Cannot find pikaia-bom"

**Cause:** SDK not published to local Maven

**Solution:**
```bash
cd /Users/maciek/tango/pikaia-android
./gradlew publishToMavenLocal
```

### Changes not reflected

**Solution:**
```bash
# In your app project
./gradlew clean --refresh-dependencies
./gradlew build
```

---

## Working Example

A fully configured example using version catalogs is available at:
```
/Users/maciek/tango/scaffoldtest
```

Files to check:
- ✅ `gradle/libs.versions.toml` - Pikaia SDK definitions
- ✅ `settings.gradle.kts` - mavenLocal() configured
- ✅ `app/build.gradle.kts` - Using `libs.pikaia.*` references

---

## Quick Reference

```bash
# 1. Publish SDK
cd /Users/maciek/tango/pikaia-android
./gradlew publishToMavenLocal

# 2. In your project, add to libs.versions.toml:
[versions]
pikaia = "0.1.0-SNAPSHOT"

[libraries]
pikaia-bom = { group = "dev.pikaia.android", name = "sdk-bom", version.ref = "pikaia" }
pikaia-core = { group = "dev.pikaia.android", name = "sdk-core" }
pikaia-auth = { group = "dev.pikaia.android", name = "sdk-auth" }
pikaia-sync = { group = "dev.pikaia.android", name = "sdk-sync" }

# 3. In build.gradle.kts:
dependencies {
    implementation(platform(libs.pikaia.bom))
    implementation(libs.pikaia.core)
    implementation(libs.pikaia.auth)
    implementation(libs.pikaia.sync)
}

# 4. Build
./gradlew build
```

---

## Benefits Summary

🎯 **Type Safety** - Catch typos at compile time
📦 **Centralized** - One place to manage all versions
🔄 **Consistent** - Same versions across all modules
💡 **IDE Support** - Autocomplete, refactoring, navigation
🚀 **Modern** - Recommended by Gradle and Android teams
⚡ **Fast** - Better build cache efficiency
