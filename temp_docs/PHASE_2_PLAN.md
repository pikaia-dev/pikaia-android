# Phase 2 Implementation Plan - sdk-auth Module

## Overview

Phase 2 will implement the **sdk-auth** module, providing authentication and token management functionality. This module depends on sdk-core and implements the authentication interfaces defined in Phase 1.

## Module Information

**Module**: `sdk-auth`
**Dependencies**: `sdk-core`
**Package**: `dev.pikaia.android.sdk.auth`
**Estimated Complexity**: Medium
**Estimated Time**: 1-2 weeks

## Objectives

1. ✅ Implement secure token storage using EncryptedSharedPreferences
2. ✅ Implement in-memory token storage for testing
3. ✅ Implement token refresh coordination with Mutex-based synchronization
4. ✅ Implement auth token interceptor for automatic header injection
5. ✅ Implement all authentication API endpoints
6. ✅ Create all authentication DTOs with proper serialization
7. ✅ Provide comprehensive documentation and examples

## File Structure

```
sdk-auth/
├── build.gradle.kts                    (module configuration)
├── README.md                           (module documentation)
├── EXAMPLE.md                          (usage examples)
└── src/main/kotlin/dev/pikaia/android/sdk/auth/
    ├── token/
    │   ├── EncryptedTokenStore.kt      (secure storage implementation)
    │   └── InMemoryTokenStore.kt       (testing implementation)
    ├── refresh/
    │   └── DefaultRefreshCoordinator.kt (refresh coordination)
    ├── interceptors/
    │   └── AuthTokenInterceptor.kt     (auth header injection)
    ├── api/
    │   └── AuthAPI.kt                  (authentication endpoints)
    └── models/
        ├── AuthModels.kt               (magic link, discovery DTOs)
        ├── SessionModels.kt            (session, device provision DTOs)
        ├── ProfileModels.kt            (profile, phone OTP DTOs)
        └── CommonModels.kt             (shared models)
```

## Components to Implement

### 1. Token Storage (token/)

#### EncryptedTokenStore.kt
**Purpose**: Secure token storage using Android's EncryptedSharedPreferences

**Features**:
- Store access token, refresh token
- Store device token, device refresh token
- Encrypted at rest using AES256-GCM
- Thread-safe with Dispatchers.IO
- Clear all tokens

**Dependencies**:
- `androidx.security:security-crypto:1.1.0-alpha06`

**Key APIs**:
```kotlin
class EncryptedTokenStore(context: Context) : TokenStore {
    private val sharedPrefs: SharedPreferences

    override suspend fun getAccessToken(): String?
    override suspend fun getRefreshToken(): String?
    override suspend fun getDeviceToken(): String?
    override suspend fun getDeviceRefreshToken(): String?
    override suspend fun setTokens(access: String, refresh: String)
    override suspend fun setDeviceTokens(device: String, refresh: String)
    override suspend fun clear()
}
```

#### InMemoryTokenStore.kt
**Purpose**: Non-persistent token storage for testing

**Features**:
- Simple in-memory storage with nullable strings
- No encryption (for testing only)
- Thread-safe with synchronization
- Implements same TokenStore interface

**Key APIs**:
```kotlin
class InMemoryTokenStore : TokenStore {
    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var deviceToken: String? = null
    private var deviceRefreshToken: String? = null

    // Same interface as EncryptedTokenStore
}
```

### 2. Refresh Coordination (refresh/)

#### DefaultRefreshCoordinator.kt
**Purpose**: Prevent concurrent token refresh requests

**Features**:
- Uses Mutex for thread-safe coordination
- Coalesces multiple concurrent refresh attempts into one
- Returns the same result to all waiting callers
- Cleans up state after completion

**Key APIs**:
```kotlin
class DefaultRefreshCoordinator : RefreshCoordinator {
    private val mutex = Mutex()
    private var currentTask: Deferred<Pair<String, String?>>? = null

    override suspend fun refresh(
        refreshToken: String,
        authProvider: AuthProvider
    ): Pair<String, String?>
}
```

### 3. Auth Interceptor (interceptors/)

#### AuthTokenInterceptor.kt
**Purpose**: Automatically inject authentication headers

**Features**:
- Support for Bearer and DeviceToken auth schemes
- Four modes: BEARER_ONLY, DEVICE_ONLY, PREFER_BEARER, PREFER_DEVICE
- Automatically select credential based on mode
- Thread-safe token retrieval

**Key APIs**:
```kotlin
class AuthTokenInterceptor(
    private val tokenStore: TokenStore,
    private val mode: Mode = Mode.PREFER_BEARER
) : RequestInterceptor {
    enum class Mode {
        BEARER_ONLY,
        DEVICE_ONLY,
        PREFER_BEARER,
        PREFER_DEVICE
    }

    override suspend fun adapt(request: HttpRequestBuilder): HttpRequestBuilder
}
```

### 4. Authentication API (api/)

#### AuthAPI.kt
**Purpose**: All authentication-related HTTP endpoints

**Endpoints to Implement**:
1. **Magic Link** (2 endpoints)
   - `POST /v1/auth/magic-link/send` - Send magic link email
   - `POST /v1/auth/magic-link/authenticate` - Authenticate with token

2. **Discovery** (2 endpoints)
   - `POST /v1/auth/discovery/create-org` - Create new organization
   - `POST /v1/auth/discovery/exchange` - Exchange interim token for session

3. **Device Provisioning** (1 endpoint)
   - `POST /v1/auth/device/provision` - Provision device with shadow user

4. **Mobile Provisioning** (1 endpoint)
   - `POST /v1/auth/mobile/provision` - Provision mobile app session

5. **Session Management** (2 endpoints)
   - `POST /v1/auth/logout` - End current session
   - `GET /v1/auth/me` - Get current user info

6. **Profile Management** (1 endpoint)
   - `PATCH /v1/auth/me/profile` - Update user profile

7. **Phone Verification** (2 endpoints)
   - `POST /v1/auth/phone/send-otp` - Send OTP to phone
   - `POST /v1/auth/phone/verify-otp` - Verify OTP code

**Total**: 11 endpoints

**Key APIs**:
```kotlin
class AuthAPI(private val client: APIClient) {
    // Magic Link
    suspend fun sendMagicLink(request: MagicLinkSendRequest): MessageResponse
    suspend fun authenticateMagicLink(request: MagicLinkAuthenticateRequest): MagicLinkAuthenticateResponse

    // Discovery
    suspend fun createOrganization(request: DiscoveryCreateOrgRequest): SessionResponse
    suspend fun exchangeSession(request: DiscoveryExchangeRequest): SessionResponse

    // Device Provisioning
    suspend fun provisionDevice(request: DeviceProvisionRequest, mobileAPIKey: String): DeviceProvisionResponse

    // Mobile Provisioning
    suspend fun provisionMobile(request: MobileProvisionRequest, mobileAPIKey: String): SessionResponse

    // Session
    suspend fun logout(): MessageResponse
    suspend fun getMe(): MeResponse

    // Profile
    suspend fun updateProfile(request: UpdateProfileRequest): UserInfo

    // Phone
    suspend fun sendPhoneOtp(request: SendPhoneOtpRequest): PhoneOtpResponse
    suspend fun verifyPhoneOtp(request: VerifyPhoneOtpRequest): UserInfo
}
```

### 5. Data Models (models/)

#### AuthModels.kt - Magic Link & Discovery
**DTOs**:
- `MagicLinkSendRequest`
- `MagicLinkAuthenticateRequest`
- `MagicLinkAuthenticateResponse`
- `OrganizationInfo`
- `DiscoveryCreateOrgRequest`
- `DiscoveryExchangeRequest`

#### SessionModels.kt - Sessions & Device
**DTOs**:
- `SessionResponse`
- `DeviceProvisionRequest`
- `DeviceProvisionResponse`
- `MobileProvisionRequest`

#### ProfileModels.kt - User Profile & Phone
**DTOs**:
- `UserInfo`
- `UpdateProfileRequest`
- `MeResponse`
- `MemberInfo`
- `OrganizationDetail`
- `SendPhoneOtpRequest`
- `PhoneOtpResponse`
- `VerifyPhoneOtpRequest`

#### CommonModels.kt - Shared Types
**DTOs**:
- `MessageResponse`

**Total DTOs**: ~20 data classes

**Serialization Requirements**:
- All DTOs use `@Serializable` annotation
- Snake_case JSON fields with `@SerialName`
- ISO8601 date handling with custom serializer
- Nullable fields where appropriate

## Dependencies

### New Dependencies to Add

```kotlin
// In sdk-auth/build.gradle.kts
dependencies {
    // SDK Core
    api(project(":sdk-core"))

    // Encrypted Storage
    implementation(libs.androidx.security.crypto)

    // Coroutines (inherited from core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
```

### Version Catalog Updates

```toml
# Add to libs.versions.toml
[versions]
securityCrypto = "1.1.0-alpha06"

[libraries]
androidx-security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "securityCrypto" }
```

## Implementation Steps

### Step 1: Module Setup (15 min)
- [ ] Create sdk-auth module structure
- [ ] Configure build.gradle.kts
- [ ] Add dependencies to version catalog
- [ ] Update settings.gradle.kts

### Step 2: Token Storage (30 min)
- [ ] Implement InMemoryTokenStore
- [ ] Implement EncryptedTokenStore
- [ ] Add unit tests for token storage

### Step 3: Refresh Coordination (20 min)
- [ ] Implement DefaultRefreshCoordinator
- [ ] Add unit tests for coordination logic

### Step 4: Auth Interceptor (20 min)
- [ ] Implement AuthTokenInterceptor
- [ ] Test credential selection modes

### Step 5: Data Models (1 hour)
- [ ] Create all DTOs with serialization
- [ ] Add custom serializers (Instant)
- [ ] Verify JSON serialization/deserialization

### Step 6: Auth API (1 hour)
- [ ] Implement all 11 endpoint methods
- [ ] Test endpoint construction
- [ ] Verify request/response handling

### Step 7: Documentation (30 min)
- [ ] Write README.md
- [ ] Write EXAMPLE.md with usage patterns
- [ ] Add KDoc comments

### Step 8: Testing (1 hour)
- [ ] Unit tests for token storage
- [ ] Unit tests for refresh coordination
- [ ] Unit tests for auth interceptor
- [ ] Integration tests with mock server

## Testing Strategy

### Unit Tests
1. **InMemoryTokenStore**
   - Test get/set for all token types
   - Test clear functionality
   - Test null handling

2. **EncryptedTokenStore**
   - Test encryption/decryption
   - Test persistence across instances
   - Test clear functionality

3. **DefaultRefreshCoordinator**
   - Test single refresh
   - Test concurrent refresh coalescing
   - Test error handling

4. **AuthTokenInterceptor**
   - Test each mode (BEARER_ONLY, etc.)
   - Test credential selection logic
   - Test header injection

### Integration Tests
1. Mock AuthAPI endpoints
2. Test full auth flow (magic link → session)
3. Test token refresh flow
4. Test error scenarios

## Success Criteria

Phase 2 will be considered complete when:

- [ ] All source files compile without errors
- [ ] All unit tests pass (80%+ coverage)
- [ ] Build succeeds with no warnings
- [ ] EncryptedTokenStore persists tokens securely
- [ ] InMemoryTokenStore works for testing
- [ ] DefaultRefreshCoordinator prevents race conditions
- [ ] AuthTokenInterceptor correctly injects headers
- [ ] All 11 AuthAPI endpoints are implemented
- [ ] All ~20 DTOs serialize/deserialize correctly
- [ ] README.md and EXAMPLE.md are complete
- [ ] Module can be used in sample app

## Example Usage (Post-Implementation)

```kotlin
// Initialize
val tokenStore = EncryptedTokenStore(context)
val authAPI = AuthAPI(apiClient)

// Magic link flow
val sendResponse = authAPI.sendMagicLink(
    MagicLinkSendRequest(email = "user@example.com")
)

// User clicks link, gets token
val authResponse = authAPI.authenticateMagicLink(
    MagicLinkAuthenticateRequest(token = "...")
)

// Select organization
val session = authAPI.exchangeSession(
    DiscoveryExchangeRequest(
        interimSessionToken = authResponse.interimSessionToken,
        organizationId = authResponse.organizations.first().organizationId
    )
)

// Store tokens
tokenStore.setTokens(session.sessionToken, session.sessionJwt)

// Configure API client with auth
val authedClient = APIClient(
    config = config,
    httpClient = httpClient,
    requestInterceptors = listOf(
        AuthTokenInterceptor(tokenStore),
        LogRequestInterceptor()
    ),
    tokenStore = tokenStore,
    authProvider = object : AuthProvider {
        override suspend fun refreshTokens(refreshToken: String): Pair<String, String?> {
            // Implement using session refresh endpoint
            throw NotImplementedError()
        }
    },
    refreshCoordinator = DefaultRefreshCoordinator()
)

// Now all requests will include auth header automatically
```

## Risks & Mitigations

### Risk 1: EncryptedSharedPreferences Compatibility
**Mitigation**: Test on multiple Android versions (26-35), provide fallback to regular SharedPreferences if needed

### Risk 2: Token Refresh Race Conditions
**Mitigation**: Thoroughly test DefaultRefreshCoordinator with concurrent requests, use Mutex properly

### Risk 3: Serialization Edge Cases
**Mitigation**: Test with real API responses, handle null/missing fields gracefully

## Timeline

| Task | Duration | Status |
|------|----------|--------|
| Module setup | 15 min | ⏳ Pending |
| Token storage | 30 min | ⏳ Pending |
| Refresh coordination | 20 min | ⏳ Pending |
| Auth interceptor | 20 min | ⏳ Pending |
| Data models | 1 hour | ⏳ Pending |
| Auth API | 1 hour | ⏳ Pending |
| Documentation | 30 min | ⏳ Pending |
| Testing | 1 hour | ⏳ Pending |
| **Total** | **~5 hours** | |

## Files to Create

### Source Files (11 files)
1. `sdk-auth/build.gradle.kts`
2. `token/EncryptedTokenStore.kt`
3. `token/InMemoryTokenStore.kt`
4. `refresh/DefaultRefreshCoordinator.kt`
5. `interceptors/AuthTokenInterceptor.kt`
6. `api/AuthAPI.kt`
7. `models/AuthModels.kt`
8. `models/SessionModels.kt`
9. `models/ProfileModels.kt`
10. `models/CommonModels.kt`
11. `models/InstantSerializer.kt`

### Documentation Files (2 files)
1. `sdk-auth/README.md`
2. `sdk-auth/EXAMPLE.md`

### Configuration Files (1 file)
1. Update `gradle/libs.versions.toml`

**Total New Files**: 14

## Post-Implementation Checklist

- [ ] All source files created and compile successfully
- [ ] All dependencies added to version catalog
- [ ] Unit tests written and passing
- [ ] Documentation complete (README + EXAMPLES)
- [ ] Code follows Kotlin conventions
- [ ] No lint warnings
- [ ] Ready for integration with sdk-sync (Phase 3)
- [ ] Can be used in sample app for auth flows

## Questions Before Implementation

1. **EncryptedSharedPreferences**: Should we provide a fallback for devices that don't support encryption, or require API 26+ (which supports it)?
   - **Recommendation**: Require API 26+ (already our min SDK)

2. **Token Refresh Endpoint**: AuthProvider needs an endpoint to refresh tokens. Should we:
   - Add DeviceAPI.refreshSession to sdk-auth?
   - Wait for sdk-device module (Phase 4)?
   - **Recommendation**: Add a basic implementation in sdk-auth for now

3. **Error Response Parsing**: Should DTOs include error response models?
   - **Recommendation**: Yes, add error DTOs for structured error messages

4. **Testing Strategy**: Mock server or real API for integration tests?
   - **Recommendation**: Mock server (MockWebServer) for reproducible tests

---

## 🚦 Ready for Confirmation

Please review this plan and confirm:
- ✅ Approach is correct
- ✅ File structure makes sense
- ✅ All required components are included
- ✅ Dependencies are appropriate
- ✅ Ready to proceed with implementation

**Once confirmed, I'll begin implementing Phase 2 (sdk-auth) following this plan.**
