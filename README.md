# Pikaia Android

Pikaia Android is a modern Android application template featuring a well-architected sample app and SDK modules. It demonstrates best practices for building scalable Android applications with cutting-edge technologies.

## Project Structure

```
pikaia-android/
├── pikaia-app/              # Sample application demonstrating SDK usage
├── sdk-core/                # Core SDK functionality
├── sdk-auth/                # Authentication SDK module
├── sdk-sync/                # Synchronization SDK module
├── sdk-bom/                 # Bill of Materials for SDK versions
└── .claude/                 # Claude Code AI assistant configuration
```

## Technology Stack

- **Kotlin** - Modern, concise programming language
- **Jetpack Compose** - Declarative UI toolkit
- **Material 3** - Latest Material Design components
- **Koin** - Lightweight dependency injection
- **Redux Pattern** - Predictable state management (Store/Action/State/SideEffect)
- **Navigation Component** - Type-safe navigation
- **Coroutines & Flow** - Asynchronous programming
- **Gradle Version Catalogs** - Centralized dependency management

## Architecture

The sample app demonstrates a feature-based modular architecture:

- **Feature Modules**: Organized by feature (home, profile)
- **Redux State Management**: Unidirectional data flow with Store, Actions, State, and SideEffects
- **Dependency Injection**: Koin modules for testability and maintainability
- **Base Classes**: Reusable ViewModel and UseCase patterns
- **Navigation**: Centralized navigation graphs with bottom bar

See `pikaia-app/docs/` for detailed architecture documentation.

## Getting Started

### Prerequisites

- Android Studio Ladybug Feature Drop | 2024.2.2 or later
- JDK 11 or later
- Android SDK 26 (minimum) to 36 (target)

### Building the Project

```bash
# Clone the repository
git clone <repository-url>
cd pikaia-android

# Build all modules
./gradlew build

# Install sample app on device
./gradlew pikaia-app:installDebug
```

## Scaffolding New Projects

This repository includes a powerful Claude Code skill that allows you to quickly scaffold new Android projects based on the Pikaia architecture.

### Using the `/scaffold-app` Skill

The `/scaffold-app` skill creates a complete Android project with all the architectural patterns and dependencies from Pikaia, minus the SDK modules. This is perfect for starting new applications that follow the same best practices.

#### Requirements

- Claude Code CLI installed and running in this project
- Write permissions to the parent directory

#### Usage

```bash
/scaffold-app "Your App Name" "com.yourcompany.appname"
```

**Arguments:**
- **App Name** (first argument): The display name for your application (e.g., "TaskFlow Manager")
- **Package Name** (second argument): The Java/Kotlin package name (e.g., "com.company.taskflow")

**Example:**

```bash
/scaffold-app "TaskFlow" "com.example.taskflow"
```

This creates a new project at `../taskflow/` with:
- Package: `com.example.taskflow`
- App class: `TaskFlowApp`
- Theme: `TaskFlowTheme`
- Fresh git repository
- Complete architecture ready to use

#### What Gets Scaffolded

✅ **Included:**
- Complete app module with feature-based architecture
- Redux state management setup (Store, Actions, State, SideEffects)
- Koin dependency injection configuration
- Jetpack Compose + Material 3 UI
- Navigation with bottom bar (Home, Profile screens)
- Gradle configuration with version catalogs
- ProGuard rules
- Theme system
- Architecture documentation
- Claude Code configuration (rules and settings)

❌ **Excluded:**
- All SDK modules (sdk-core, sdk-auth, sdk-sync, sdk-bom)
- SDK dependencies from build.gradle
- Pikaia-specific branding

#### After Scaffolding

Your new project will be created in the parent directory with:

1. **Fresh Git Repository**: Initialized with an initial commit
2. **Updated Branding**: All "Pikaia" references replaced with your app name
3. **Clean Dependencies**: Only app-level dependencies (no SDK modules)
4. **Version Reset**: versionCode = 1, versionName = "1.0"
5. **Ready to Build**: `./gradlew build` should work immediately

**Next Steps:**

```bash
# Navigate to your new project
cd ../your-app-name

# Open in Android Studio
studio .

# Or build and run
./gradlew installDebug
```

**Post-Scaffolding Tasks:**
- Update launcher icons (currently uses Pikaia placeholders)
- Review and remove/implement SDK-dependent features
- Customize theme colors and typography
- Update architecture docs with your app specifics
- Configure CI/CD pipelines

#### Troubleshooting

**Skill not recognized?**
- Ensure you're running Claude Code in the pikaia-android directory
- The skill file is located at `.claude/skills/scaffold-app/SKILL.md`
- Try restarting your Claude Code session

**Build fails after scaffolding?**
- Check that all package declarations were updated
- Verify theme references in AndroidManifest.xml
- Run `./gradlew clean build` to ensure a fresh build

**Package name validation failed?**
- Package names must be lowercase
- Must contain at least two segments (e.g., `com.app`)
- Can only contain letters, numbers, and underscores
- Must start with a letter

## Development

### Adding New Features

Follow the feature module pattern demonstrated in `pikaia-app/src/main/java/dev/pikaia/android/feature/`:

1. Create feature package (e.g., `feature/settings`)
2. Add Redux components (State, Action, Store)
3. Create UI composables
4. Define Koin DI module
5. Register in navigation graph

### Code Style

This project follows the official Kotlin coding conventions. The project uses:
- `kotlin.code.style=official` in `gradle.properties`
- Android Studio's built-in formatting
- Meaningful names over comments

## SDK Modules

### sdk-core
Networking foundation: `APIClient` (Ktor), declarative `Endpoint`s, non-throwing
`ApiResult`, typed errors (including 429 with `Retry-After`), interceptor seams, and the
session-based auth abstractions (`TokenStore`, `AuthProvider`, `RefreshCoordinator`).

### sdk-auth
The shared auth surface of the backend: magic-link + discovery, mobile provisioning,
`/me`/profile, phone verification, device linking with long-lived refreshable sessions,
Keystore-encrypted token storage, and a working 401 → refresh → retry pipeline.

### sdk-sync
Data synchronization capabilities.

### sdk-bom
Bill of Materials for managing SDK module versions consistently.

## Installing the SDKs

Tagged releases are distributed via [JitPack](https://jitpack.io); local iteration uses
`mavenLocal` (see [LOCAL_MAVEN_GUIDE.md](LOCAL_MAVEN_GUIDE.md)).

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

// build.gradle.kts
dependencies {
    implementation("com.github.pikaia-dev.pikaia-android:sdk-core:<tag>")
    implementation("com.github.pikaia-dev.pikaia-android:sdk-auth:<tag>")
}
```

Versions stay 0.x while the API surface churns during backend alignment.

## Assembling the Auth Stack

The SDK models only the surface shared by every product on this backend stack.
Product-specific values — the base URL **including the API prefix** (e.g.
`https://api.example.com/api/v1`) and the QR deep-link scheme — are configuration, never
SDK constants. Product-specific endpoints belong in the consuming app, built on
`sdk-core`'s `APIClient`.

```kotlin
val config = APIClientConfig(baseUrl = "https://api.example.com/api/v1")
val tokenStore = DataStoreTokenStore(context)   // Keystore-encrypted at rest

// Refresh path: shares the token store for bearer injection, but has no auth
// provider of its own so a failing refresh can never recurse.
val refreshClient = APIClient(
    config = config,
    requestInterceptors = listOf(AuthTokenInterceptor(tokenStore))
)
val authProvider = DeviceSessionAuthProvider(DevicesAPI(refreshClient))

// Main client: bearer injection + automatic 401 → coalesced refresh → retry-once.
val apiClient = APIClient(
    config = config,
    requestInterceptors = listOf(AuthTokenInterceptor(tokenStore)),
    tokenStore = tokenStore,
    authProvider = authProvider,
    refreshCoordinator = DefaultRefreshCoordinator()
)

val authAPI = AuthAPI(apiClient)
val devicesAPI = DevicesAPI(apiClient)

// After login (e.g. completing a device link), persist the session:
devicesAPI.completeLink(request).onSuccess { link ->
    tokenStore.setSession(
        AuthSession(
            sessionJwt = link.sessionJwt,
            sessionToken = link.sessionToken,
            sessionExpiresAt = link.sessionExpiresAt,
            deviceUuid = request.deviceUuid
        )
    )
}

// From here on, calls carry the bearer credential and survive JWT expiry:
when (val result = authAPI.getMe()) {
    is ApiResult.Success -> render(result.value)
    is ApiResult.Failure -> handle(result.error)
}
```

**Backups:** exclude the token DataStore file from Auto Backup so tokens never land in
device backups — in `dataExtractionRules` (API 31+) and `fullBackupContent`:

```xml
<exclude domain="file" path="datastore/pikaia_tokens.preferences_pb" />
```

The encryption key lives in the Android Keystore and never leaves the device, so restored
ciphertext is unreadable either way; the exclusion simply keeps ciphertext out of backups.

## Contributing

Please read the architecture documentation in `pikaia-app/docs/` before contributing to understand the design patterns and principles used in this project.

## License

See the [LICENSE](LICENSE) file for details.

---

**Built with ❤️ using modern Android development practices**
