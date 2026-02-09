# Quick Guide: Publishing and Using Pikaia SDK Locally

## ✅ Setup Complete

Your Pikaia SDK is now configured to publish to local Maven and has been successfully tested in the scaffoldtest project.

---

## 📦 Publishing to Local Maven

### Publish Everything
```bash
./gradlew publishToMavenLocal
```

This publishes all modules:
- ✅ `dev.pikaia.android:sdk-bom:0.1.0-SNAPSHOT`
- ✅ `dev.pikaia.android:sdk-core:0.1.0-SNAPSHOT`
- ✅ `dev.pikaia.android:sdk-auth:0.1.0-SNAPSHOT`
- ✅ `dev.pikaia.android:sdk-sync:0.1.0-SNAPSHOT`

### Change Version
```bash
./gradlew publishToMavenLocal -Ppikaia.sdk.version=1.0.0
```

### Published Location
```
~/.m2/repository/dev/pikaia/android/
```

---

## 🎯 Using in Other Projects

### Step 1: Enable mavenLocal()

**In `settings.gradle.kts`:**
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()  // Add this
    }
}
```

### Step 2: Add Dependencies (Recommended: Use BOM)

**In `app/build.gradle.kts`:**
```kotlin
dependencies {
    // Option A: Use BOM (Recommended)
    implementation(platform("dev.pikaia.android:sdk-bom:0.1.0-SNAPSHOT"))
    implementation("dev.pikaia.android:sdk-core")
    implementation("dev.pikaia.android:sdk-auth")
    implementation("dev.pikaia.android:sdk-sync")

    // Option B: Direct dependencies (not recommended)
    // implementation("dev.pikaia.android:sdk-core:0.1.0-SNAPSHOT")
    // implementation("dev.pikaia.android:sdk-auth:0.1.0-SNAPSHOT")
    // implementation("dev.pikaia.android:sdk-sync:0.1.0-SNAPSHOT")
}
```

### Step 3: Sync and Build
```bash
./gradlew build
```

---

## 🔄 Development Workflow

1. **Make changes** in pikaia-android project
2. **Build** to verify: `./gradlew build`
3. **Publish** to local Maven: `./gradlew publishToMavenLocal`
4. **Switch** to your app project
5. **Refresh** dependencies: `./gradlew clean build`

---

## ✨ Working Example

A fully configured example is available in:
```
/Users/maciek/tango/scaffoldtest
```

This project demonstrates:
- ✅ mavenLocal() repository configured
- ✅ Pikaia SDK dependencies using BOM
- ✅ Successfully builds and resolves all dependencies
- ✅ Usage examples in `PIKAIA_USAGE_EXAMPLE.md`

---

## 🐛 Troubleshooting

### "SDK not found" or "Failed to resolve"
```bash
# 1. Re-publish from pikaia-android
cd /Users/maciek/tango/pikaia-android
./gradlew publishToMavenLocal

# 2. Verify artifacts exist
ls ~/.m2/repository/dev/pikaia/android/

# 3. In your app project, refresh dependencies
./gradlew clean --refresh-dependencies
```

### Changes not reflected
```bash
# 1. Re-publish
cd /Users/maciek/tango/pikaia-android
./gradlew publishToMavenLocal

# 2. Clean your app
cd /path/to/your/app
./gradlew clean

# 3. Invalidate Android Studio caches
# File → Invalidate Caches → Invalidate and Restart
```

### Version conflicts
Use the BOM to automatically manage versions:
```kotlin
implementation(platform("dev.pikaia.android:sdk-bom:0.1.0-SNAPSHOT"))
```

---

## 📚 More Resources

- **Detailed Publishing Guide**: `PUBLISHING.md`
- **Usage Examples**: `/Users/maciek/tango/scaffoldtest/PIKAIA_USAGE_EXAMPLE.md`

---

## 🎉 Benefits of This Setup

✅ **No remote deployment needed** - Test locally before publishing
✅ **Fast iteration** - Quick publish and test cycle
✅ **Version management** - BOM ensures compatibility across all SDK modules
✅ **Multiple projects** - Share the same SDK across all your local projects
✅ **Offline development** - Works without internet connection

---

## 📋 Quick Command Reference

```bash
# Publish everything
./gradlew publishToMavenLocal

# Publish specific module
./gradlew :sdk-core:publishToMavenLocal

# Build everything
./gradlew build

# Clean and rebuild
./gradlew clean build

# Check dependencies in another project
./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep pikaia
```
