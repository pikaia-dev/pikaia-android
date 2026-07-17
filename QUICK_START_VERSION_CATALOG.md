# Quick Start: Pikaia SDK with Version Catalogs

The modern, recommended way to use the Pikaia SDK in your projects.

---

## 🚀 One-Time Setup

### 1. Publish SDK to Local Maven
```bash
cd /Users/maciek/tango/pikaia-android
./gradlew publishToMavenLocal
```

---

## 📦 Using in Your Project

### 1. Enable mavenLocal in `settings.gradle.kts`
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()  // ← Add this
    }
}
```

### 2. Add to `gradle/libs.versions.toml`
```toml
[versions]
pikaia = "0.1.0-SNAPSHOT"

[libraries]
pikaia-bom = { group = "dev.pikaia.android", name = "sdk-bom", version.ref = "pikaia" }
pikaia-core = { group = "dev.pikaia.android", name = "sdk-core" }
pikaia-auth = { group = "dev.pikaia.android", name = "sdk-auth" }
pikaia-sync = { group = "dev.pikaia.android", name = "sdk-sync" }
```

### 3. Use in `app/build.gradle.kts`
```kotlin
dependencies {
    implementation(platform(libs.pikaia.bom))
    implementation(libs.pikaia.core)
    implementation(libs.pikaia.auth)
    implementation(libs.pikaia.sync)
}
```

### 4. Sync and Build
```bash
./gradlew build
```

---

## ✨ Benefits

✅ **Type-safe** - Autocomplete: `libs.pikaia.` shows all options
✅ **Centralized** - Version in one place
✅ **IDE-friendly** - Click to navigate, refactor support
✅ **Modern** - Gradle's recommended approach

---

## 🔄 Updating Version

When SDK publishes new version:

```toml
# gradle/libs.versions.toml
[versions]
pikaia = "0.2.0"  # ← Change only this line
```

Then:
```bash
./gradlew build --refresh-dependencies
```

All Pikaia SDK modules automatically use the new version!

---

## 📋 Complete Example

See working example at:
```
/Users/maciek/tango/scaffoldtest
```

Files:
- ✅ `gradle/libs.versions.toml` - Pikaia SDK catalog entries
- ✅ `settings.gradle.kts` - mavenLocal() enabled
- ✅ `app/build.gradle.kts` - Using `libs.pikaia.*`

---

## 📚 Detailed Guides

- **Version Catalog Guide:** `VERSION_CATALOG_GUIDE.md`
- **Publishing Guide:** `LOCAL_MAVEN_GUIDE.md`
- **Alternative Methods:** See scaffoldtest project's `DEPENDENCY_SETUP_COMPARISON.md`

---

## 🆚 Quick Comparison

### Version Catalog (Recommended) ✅
```kotlin
// Type-safe, autocomplete-friendly
implementation(platform(libs.pikaia.bom))
implementation(libs.pikaia.core)
```

### Direct Strings (Alternative)
```kotlin
// Simple but error-prone
implementation(platform("dev.pikaia.android:sdk-bom:0.1.0-SNAPSHOT"))
implementation("dev.pikaia.android:sdk-core")
```

---

## 💡 Pro Tips

### Use Bundles for Multiple Modules
```toml
[bundles]
pikaia = ["pikaia-core", "pikaia-auth", "pikaia-sync"]
```

```kotlin
implementation(platform(libs.pikaia.bom))
implementation(libs.bundles.pikaia)  # All at once
```

### Use Only What You Need
```kotlin
implementation(platform(libs.pikaia.bom))
implementation(libs.pikaia.core)  // Only core, skip others
```

### Mix with Other Catalogs
```kotlin
dependencies {
    // Pikaia SDK
    implementation(platform(libs.pikaia.bom))
    implementation(libs.pikaia.core)

    // Other dependencies from catalog
    implementation(libs.androidx.core.ktx)
    implementation(libs.koin.androidx.compose)
}
```

---

## 🐛 Troubleshooting

### "Unresolved reference: libs"
- File must be at `gradle/libs.versions.toml`
- Sync: File → Sync Project with Gradle Files

### "Cannot find pikaia-bom"
```bash
# Re-publish SDK
cd /Users/maciek/tango/pikaia-android
./gradlew publishToMavenLocal
```

### Changes Not Reflected
```bash
./gradlew clean --refresh-dependencies
```

---

## 📖 Next Steps

1. ✅ Set up version catalog (you're here!)
2. 📝 Read usage examples: scaffoldtest project's `PIKAIA_USAGE_EXAMPLE.md`
3. 🎯 Integrate in your app
4. 🔄 Republish SDK when you make changes

---

**That's it!** You're using the modern, recommended approach for managing Pikaia SDK dependencies. 🎉
