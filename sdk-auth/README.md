# Pikaia SDK - Authentication Module

The `sdk-auth` module provides comprehensive authentication functionality for the Pikaia Android SDK, including magic link authentication, session management, token refresh, and secure token storage.

## Features

- **Magic Link Authentication**: Passwordless email-based authentication
- **Organization Discovery**: Multi-organization support with discovery flow
- **Device & Mobile Provisioning**: Offline-first shadow user provisioning
- **Automatic Token Refresh**: Transparent token refresh with race condition prevention
- **Secure Token Storage**: Encrypted DataStore-based token persistence
- **Session Management**: Full session lifecycle management
- **Profile Management**: User profile updates
- **Phone Verification**: OTP-based phone number verification

## Architecture

### Core Components

#### AuthAPI
The main API client providing all authentication endpoints:
- `sendMagicLink()` - Send magic link email
- `authenticateMagicLink()` - Authenticate with magic link token
- `createOrganization()` - Create new organization during discovery
- `exchangeSession()` - Exchange interim token for full session
- `provisionDevice()` - Provision device with shadow user
- `provisionMobile()` - Provision mobile app session
- `logout()` - End current session
- `getMe()` - Get current user context
- `refreshSession()` - Refresh access tokens
- `updateProfile()` - Update user profile
- `sendPhoneOtp()` - Send OTP to phone
- `verifyPhoneOtp()` - Verify phone OTP code

#### Token Storage

**TokenStore Interface**
```kotlin
interface TokenStore {
    suspend fun getAccessToken(): String?
    suspend fun getRefreshToken(): String?
    suspend fun getDeviceToken(): String?
    suspend fun getDeviceRefreshToken(): String?
    suspend fun setTokens(access: String, refresh: String)
    suspend fun setDeviceTokens(device: String, refresh: String)
    suspend fun clear()
}
```

**Implementations:**
- `InMemoryTokenStore` - For testing/development (tokens lost on app restart)
- `DataStoreTokenStore` - Production-ready encrypted storage (recommended)

#### Token Refresh Coordination

**DefaultRefreshCoordinator**
Handles concurrent token refresh requests with Deferred-based coordination:
- Prevents multiple simultaneous refresh requests
- Coalesces concurrent requests into a single network call
- Thread-safe with Mutex synchronization
- Automatic cleanup of completed refresh tasks

#### Auth Token Interceptor

**AuthTokenInterceptor**
Automatically injects authentication tokens into requests:

**Modes:**
- `BEARER_ONLY` - Only inject Bearer token (session auth)
- `DEVICE_ONLY` - Only inject Device-Token header (device auth)
- `PREFER_BEARER` - Use Bearer token if available, fallback to Device-Token
- `PREFER_DEVICE` - Use Device-Token if available, fallback to Bearer

## Installation

Add the dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":sdk-auth"))
}
```

## Usage

### Basic Setup

```kotlin
// Create API client with auth support
val tokenStore = DataStoreTokenStore(context)
val refreshCoordinator = DefaultRefreshCoordinator()

val apiClient = APIClient(
    baseURL = "https://api.joinsnowball.io/api",
    authProvider = MyAuthProvider(tokenStore),
    refreshCoordinator = refreshCoordinator,
    requestInterceptors = listOf(
        AuthTokenInterceptor(tokenStore, AuthTokenMode.PREFER_BEARER),
        LogRequestInterceptor()
    ),
    responseInterceptors = listOf(
        LogResponseInterceptor()
    )
)

val authAPI = AuthAPI(apiClient)
```

### Magic Link Authentication Flow

```kotlin
// Step 1: Send magic link
val sendRequest = MagicLinkSendRequest(email = "user@example.com")
val sendResponse = authAPI.sendMagicLink(sendRequest)
// sendResponse.message: "Magic link sent"

// Step 2: User clicks link in email, extract token
// (Token comes from deep link: snowball://auth?token=abc123)

// Step 3: Authenticate with token
val authRequest = MagicLinkAuthenticateRequest(token = "abc123")
val authResponse = authAPI.authenticateMagicLink(authRequest)
// authResponse contains:
// - interimSessionToken: String
// - organizations: List<OrganizationInfo>

// Step 4a: User selects existing organization
val exchangeRequest = DiscoveryExchangeRequest(
    interimSessionToken = authResponse.interimSessionToken,
    organizationId = selectedOrg.organizationId
)
val session = authAPI.exchangeSession(exchangeRequest)

// OR Step 4b: User creates new organization
val createRequest = DiscoveryCreateOrgRequest(
    interimSessionToken = authResponse.interimSessionToken,
    organizationName = "My New Org"
)
val session = authAPI.createOrganization(createRequest)

// Step 5: Store tokens
tokenStore.setTokens(
    access = session.sessionToken,
    refresh = session.sessionJwt
)
```

### Device Provisioning (Offline-First)

```kotlin
// Provision device with shadow user
val request = DeviceProvisionRequest(
    deviceUuid = UUID.randomUUID().toString(),
    email = "user@example.com",
    platform = "android",
    name = "John Doe",
    deviceName = Build.MODEL,
    osVersion = Build.VERSION.RELEASE,
    appVersion = BuildConfig.VERSION_NAME
)

val response = authAPI.provisionDevice(
    request = request,
    mobileAPIKey = "your_mobile_api_key"
)

// Store device tokens
tokenStore.setDeviceTokens(
    device = response.deviceToken,
    refresh = response.deviceToken // Use same token for refresh
)
```

### Session Management

```kotlin
// Get current user
val meResponse = authAPI.getMe()
println("User: ${meResponse.user.name}")
println("Organization: ${meResponse.organization.organizationName}")
println("Role: ${meResponse.member.role}")

// Logout
val logoutResponse = authAPI.logout()
tokenStore.clear()
```

### Profile Management

```kotlin
// Update profile
val updateRequest = UpdateProfileRequest(name = "Jane Smith")
val updatedUser = authAPI.updateProfile(updateRequest)
```

### Phone Verification

```kotlin
// Send OTP
val sendOtpRequest = SendPhoneOtpRequest(phoneNumber = "+14155551234")
val otpResponse = authAPI.sendPhoneOtp(sendOtpRequest)

// Verify OTP (user enters code from SMS)
val verifyRequest = VerifyPhoneOtpRequest(
    methodId = otpResponse.methodId,
    code = "123456"
)
val verifiedUser = authAPI.verifyPhoneOtp(verifyRequest)
// verifiedUser.phoneVerified == true
```

## Token Refresh

Token refresh happens automatically when using `APIClient` with an `AuthProvider` and `RefreshCoordinator`. The client will:

1. Detect 401 Unauthorized responses
2. Acquire refresh lock (prevents concurrent refreshes)
3. Call `refreshSession()` endpoint
4. Update stored tokens
5. Retry original request with new token

### Implementing AuthProvider

```kotlin
class MyAuthProvider(
    private val tokenStore: TokenStore,
    private val authAPI: AuthAPI
) : AuthProvider {

    override suspend fun getToken(): String? {
        return tokenStore.getAccessToken()
    }

    override suspend fun refreshTokens(refreshToken: String): Pair<String, String?> {
        val request = SessionRefreshRequest(refreshToken = refreshToken)
        val response = authAPI.refreshSession(request)

        // Store new tokens
        tokenStore.setTokens(
            access = response.sessionToken,
            refresh = response.sessionJwt
        )

        return Pair(response.sessionToken, response.sessionJwt)
    }
}
```

## Security

### Token Storage

The `DataStoreTokenStore` provides:
- **Encryption at rest** using Android Keystore
- **Hardware-backed security** on supported devices
- **Atomic operations** preventing race conditions
- **Thread-safe access** with coroutines

### Token Refresh

The `DefaultRefreshCoordinator` prevents:
- **Concurrent refresh requests** that could invalidate tokens
- **Race conditions** with Mutex synchronization
- **Token reuse** by clearing expired tokens immediately

### Best Practices

1. **Use DataStoreTokenStore in production**
   - InMemoryTokenStore is for testing only

2. **Protect Mobile API Key**
   - Store in BuildConfig or secure location
   - Never commit to version control

3. **Handle authentication errors**
   - Catch `APIError.Unauthorized` and redirect to login
   - Catch `APIError.RefreshFailed` and clear session

4. **Use HTTPS only**
   - All API calls must use HTTPS in production

## Error Handling

```kotlin
try {
    val session = authAPI.exchangeSession(request)
} catch (e: APIError.Unauthorized) {
    // Token invalid or expired, redirect to login
    navigateToLogin()
} catch (e: APIError.RefreshFailed) {
    // Refresh failed, clear session and login again
    tokenStore.clear()
    navigateToLogin()
} catch (e: APIError.Client) {
    // 4xx error - bad request
    showError("Invalid request: ${e.message}")
} catch (e: APIError.Server) {
    // 5xx error - server issue
    showError("Server error, please try again")
} catch (e: APIError.Transport) {
    // Network error
    showError("Network error: ${e.message}")
}
```

## Testing

For unit tests, use `InMemoryTokenStore`:

```kotlin
@Test
fun testAuthFlow() = runTest {
    val tokenStore = InMemoryTokenStore()
    val mockClient = MockAPIClient()
    val authAPI = AuthAPI(mockClient)

    // Test authentication
    tokenStore.setTokens("access", "refresh")
    assertEquals("access", tokenStore.getAccessToken())
}
```

## Data Models

### Session Models
- `SessionResponse` - Full session with tokens and user context
- `SessionRefreshRequest` - Request to refresh session
- `SessionRefreshResponse` - New tokens from refresh
- `DeviceProvisionRequest/Response` - Device provisioning
- `MobileProvisionRequest` - Mobile provisioning

### Auth Models
- `MagicLinkSendRequest` - Send magic link
- `MagicLinkAuthenticateRequest/Response` - Magic link auth
- `OrganizationInfo` - Organization details
- `DiscoveryCreateOrgRequest` - Create organization
- `DiscoveryExchangeRequest` - Exchange interim token

### Profile Models
- `UserInfo` - User details
- `MemberInfo` - Member in organization
- `OrganizationDetail` - Full organization details
- `MeResponse` - Current user context
- `UpdateProfileRequest` - Profile update
- `SendPhoneOtpRequest` - Send phone OTP
- `PhoneOtpResponse` - OTP method ID
- `VerifyPhoneOtpRequest` - Verify phone OTP

### Common Models
- `MessageResponse` - Simple message response

All models use `kotlinx.serialization` with proper `@SerialName` annotations for JSON serialization.

## Dependencies

The module depends on:
- `sdk-core` - Core networking and auth interfaces
- `androidx.datastore:datastore-preferences` - Token storage
- `androidx.security:security-crypto` - Encryption
- `kotlinx-serialization-json` - JSON serialization
- `kotlinx-datetime` - Date/time handling
- `kotlinx-coroutines-core` - Async operations

## See Also

- [EXAMPLE.md](EXAMPLE.md) - Complete working examples
- [sdk-core README](../sdk-core/README.md) - Core networking documentation
- [API Documentation](https://api.joinsnowball.io/api/v1/docs) - Backend API reference
