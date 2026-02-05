---
name: scaffold-app
description: Create a new Android project based on Pikaia architecture. Takes app name and package name as arguments. Use when user wants to scaffold, create, or generate a new Android app based on this template.
---

# Scaffold Android App

This skill creates a new Android project based on the Pikaia architecture in the parent directory.

## Arguments

Parse the arguments string to extract:
- **app_name**: The display name for the new application (e.g., "TaskFlow Manager")
- **package_name**: The Java/Kotlin package name (e.g., "com.company.taskflow")

Expected format: `/scaffold-app "App Name" "com.package.name"` or `/scaffold-app AppName com.package.name`

## Execution Steps

### 1. Validation

Before starting, validate:
- Parent directory (`/Users/maciek/tango/`) is accessible and writable
- `app_name` is provided and not empty
- `package_name` matches valid Java package format: `^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$`
- Derive `project_dir_name` from app_name: convert to lowercase, replace spaces with hyphens (e.g., "TaskFlow Manager" → "taskflow-manager")
- Verify target directory `../{project_dir_name}/` does not already exist
- Derive `class_prefix` from app_name: remove spaces and special chars, PascalCase (e.g., "TaskFlow Manager" → "TaskFlowManager")

If validation fails, explain the error and stop.

### 2. Create Project Root Structure

In parent directory, create new project:

```bash
mkdir ../{project_dir_name}
cd ../{project_dir_name}
```

Copy root-level files from pikaia-android:
- Copy entire `gradle/` directory
- Copy `gradlew`, `gradlew.bat` (preserve execute permissions)
- Copy `gradle/libs.versions.toml` to `gradle/libs.versions.toml`
- Copy `.gitignore`

Create `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```
(Note: Removed `pikaia.sdk.version` property)

Create `settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "{app_name}"
include(":app")
```

Create root `build.gradle.kts`:
```kotlin
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
```

### 3. Copy App Module

Copy the entire pikaia-app directory structure:
```bash
cp -r {pikaia_android_path}/pikaia-app ../{project_dir_name}/app
```

Copy these specific files to `app/`:
- `pikaia-app/proguard-rules.pro` → `app/proguard-rules.pro`
- `pikaia-app/.gitignore` → `app/.gitignore`
- `pikaia-app/docs/` → `app/docs/` (entire directory)

### 4. Update App Build Configuration

Edit `app/build.gradle.kts`:

1. Update the `android.namespace`:
   ```kotlin
   namespace = "{package_name}"
   ```

2. Update `defaultConfig.applicationId`:
   ```kotlin
   applicationId = "{package_name}"
   ```

3. Update version info:
   ```kotlin
   versionCode = 1
   versionName = "1.0"
   ```

4. **Remove SDK dependencies** (lines 42-48 in original):
   ```kotlin
   // DELETE THESE LINES:
   // implementation(platform(project(":sdk-bom")))
   // implementation(project(":sdk-core"))
   // implementation(project(":sdk-auth"))
   // implementation(project(":sdk-sync"))
   ```

Keep all other dependencies (AndroidX, Compose, Koin, Timber, Navigation, etc.)

### 5. Package Rename and File Migration

This is the most complex step. You need to:

1. **Calculate paths**:
   - Old package: `dev.pikaia.android`
   - New package: `{package_name}` (e.g., `com.company.taskflow`)
   - Old path: `dev/pikaia/android`
   - New path: convert package_name dots to slashes (e.g., `com/company/taskflow`)

2. **Move source directories** for all source sets:
   ```bash
   # For main source set
   mkdir -p app/src/main/java/{new_path}
   mv app/src/main/java/dev/pikaia/android/* app/src/main/java/{new_path}/
   rm -rf app/src/main/java/dev

   # For test source set
   mkdir -p app/src/test/java/{new_path}
   mv app/src/test/java/dev/pikaia/android/* app/src/test/java/{new_path}/
   rm -rf app/src/test/java/dev

   # For androidTest source set
   mkdir -p app/src/androidTest/java/{new_path}
   mv app/src/androidTest/java/dev/pikaia/android/* app/src/androidTest/java/{new_path}/
   rm -rf app/src/androidTest/java/dev
   ```

3. **Update package declarations** in all `.kt` files:
   - Find all `.kt` files in `app/src/`
   - Replace `package dev.pikaia.android` with `package {package_name}`
   - Replace any sub-packages: `package dev.pikaia.android.feature` → `package {package_name}.feature`

4. **Update imports** in all `.kt` files:
   - Replace `import dev.pikaia.android` with `import {package_name}`
   - Use find and replace for all `.kt` files

5. **Update AndroidManifest.xml**:
   - The package attribute in the root `<manifest>` tag is no longer needed in modern Android (namespace in build.gradle handles it)
   - Relative class names (like `.PikaiaApp`) will automatically resolve to the new package
   - But verify the application name and activity names are correct

### 6. Rename Pikaia-Branded Components

Replace "Pikaia" references with new app name:

1. **Rename and update Application class**:
   - Rename file: `app/src/main/java/{new_path}/PikaiaApp.kt` → `{class_prefix}App.kt`
   - Update class name inside: `class PikaiaApp` → `class {class_prefix}App`
   - Update AndroidManifest.xml: `android:name=".PikaiaApp"` → `android:name=".{class_prefix}App"`

2. **Rename and update AppComponent**:
   - Rename file: `app/src/main/java/{new_path}/activity/PikaiaAppComponent.kt` → `{class_prefix}AppComponent.kt`
   - Update function name: `fun PikaiaAppComponent` → `fun {class_prefix}AppComponent`
   - Update usage in MainActivity.kt: `PikaiaAppComponent(navController)` → `{class_prefix}AppComponent(navController)`

3. **Update Theme**:
   - In `app/src/main/java/{new_path}/theme/Theme.kt`:
     - Update function: `fun PikaiaAndroidTheme` → `fun {class_prefix}Theme`
   - In `app/src/main/java/{new_path}/activity/MainActivity.kt`:
     - Update usage: `PikaiaAndroidTheme {` → `{class_prefix}Theme {`

4. **Update theme in resources**:
   - Edit `app/src/main/res/values/themes.xml`:
     - Change `<style name="Theme.PikaiaAndroid"` → `<style name="Theme.{class_prefix}"`
   - Edit `app/src/main/AndroidManifest.xml`:
     - Update all `android:theme="@style/Theme.PikaiaAndroid"` → `android:theme="@style/Theme.{class_prefix}"`

5. **Update string resources**:
   - Edit `app/src/main/res/values/strings.xml`:
     - Change `<string name="app_name">Pikaia Android</string>` → `<string name="app_name">{app_name}</string>`

6. **Update documentation**:
   - If `app/docs/` exists, update any references to "Pikaia" with `{app_name}` or `{class_prefix}` as appropriate
   - Update architecture documents with the new app name

### 7. Copy Claude Configuration

Copy Claude Code configuration (excluding this skill):

```bash
mkdir -p ../{project_dir_name}/.claude
cp -r .claude/rules ../{project_dir_name}/.claude/
cp .claude/settings.local.json ../{project_dir_name}/.claude/
```

**Important**: Do NOT copy `.claude/skills/scaffold-app/` to avoid recursion.

### 8. Initialize Git Repository

Create a fresh git repository:

```bash
cd ../{project_dir_name}
git init
git add .
git commit -m "Initial commit: Scaffolded from Pikaia template

App name: {app_name}
Package: {package_name}
Base template: Pikaia Android SDK Sample App
Architecture: Redux + Koin + Jetpack Compose"
```

### 9. Build Verification

Run a build to verify everything works:

```bash
./gradlew build
```

If the build fails, analyze the error and attempt to fix common issues:
- Missing package declarations
- Incorrect import statements
- Theme reference errors

### 10. Success Output

Display a summary:

```
✅ Successfully created Android project: {app_name}

📁 Location: /Users/maciek/tango/{project_dir_name}/
📦 Package: {package_name}
🎨 App Name: {app_name}

🏗️ Architecture:
  - Redux pattern (Store/Action/State/SideEffect)
  - Koin dependency injection
  - Jetpack Compose + Material 3
  - Navigation with bottom bar
  - Feature-based modular structure

📋 Included Features:
  - Home screen
  - Profile screen
  - Redux state management examples
  - Navigation setup
  - Theming system

⚠️ Notes:
  - SDK dependencies have been removed (sdk-core, sdk-auth, sdk-sync)
  - Any code referencing SDK modules will need to be updated
  - Pikaia launcher icons are preserved as placeholders

🚀 Next steps:
  1. cd ../{project_dir_name}
  2. Open in Android Studio
  3. Update launcher icons
  4. Remove or implement SDK-dependent features
  5. Run: ./gradlew installDebug

✨ Project is ready for development!
```

## Error Handling

If any step fails:
1. Log the specific error
2. Attempt to rollback by deleting the partially created project directory
3. Provide clear guidance on what went wrong and how to fix it

## Notes

- The skill preserves the entire architecture and patterns from Pikaia
- All SDK-specific dependencies are removed, but code references remain (will cause compilation errors until addressed)
- The new project uses the latest stable versions defined in `libs.versions.toml`
- Git history starts fresh from the scaffolding point
- Claude configuration is copied to maintain consistent AI assistance
