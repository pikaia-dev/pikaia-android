# Pikaia SDK Authentication - Examples

This document provides complete, working examples for common authentication scenarios.

## Table of Contents

1. [Setup](#setup)
2. [Magic Link Authentication](#magic-link-authentication)
3. [Device Provisioning](#device-provisioning)
4. [Mobile Provisioning](#mobile-provisioning)
5. [Session Management](#session-management)
6. [Profile Management](#profile-management)
7. [Phone Verification](#phone-verification)
8. [Complete ViewModel Example](#complete-viewmodel-example)

## Setup

### Initial SDK Configuration

```kotlin
class PikaiaSdkManager(private val context: Context) {

    private val tokenStore: TokenStore = DataStoreTokenStore(context)

    private val refreshCoordinator: RefreshCoordinator = DefaultRefreshCoordinator()

    private val authProvider: AuthProvider by lazy {
        PikaiaAuthProvider(tokenStore, authAPI)
    }

    val apiClient: APIClient = APIClient(
        baseURL = "https://api.joinsnowball.io/api",
        authProvider = authProvider,
        refreshCoordinator = refreshCoordinator,
        requestInterceptors = listOf(
            AuthTokenInterceptor(tokenStore, AuthTokenMode.PREFER_BEARER),
            LogRequestInterceptor()
        ),
        responseInterceptors = listOf(
            LogResponseInterceptor()
        )
    )

    val authAPI: AuthAPI = AuthAPI(apiClient)

    suspend fun isAuthenticated(): Boolean {
        return tokenStore.getAccessToken() != null || tokenStore.getDeviceToken() != null
    }

    suspend fun logout() {
        try {
            authAPI.logout()
        } catch (e: Exception) {
            // Ignore errors during logout
        } finally {
            tokenStore.clear()
        }
    }
}

// Custom AuthProvider implementation
class PikaiaAuthProvider(
    private val tokenStore: TokenStore,
    private val authAPI: AuthAPI
) : AuthProvider {

    override suspend fun getToken(): String? {
        return tokenStore.getAccessToken()
    }

    override suspend fun refreshTokens(refreshToken: String): Pair<String, String?> {
        val request = SessionRefreshRequest(refreshToken = refreshToken)
        val response = authAPI.refreshSession(request)

        // Update stored tokens
        tokenStore.setTokens(
            access = response.sessionToken,
            refresh = response.sessionJwt
        )

        return Pair(response.sessionToken, response.sessionJwt)
    }
}
```

## Magic Link Authentication

### Complete Magic Link Flow

```kotlin
class MagicLinkViewModel(
    private val sdkManager: PikaiaSdkManager
) : ViewModel() {

    private val _state = MutableStateFlow<AuthState>(AuthState.Initial)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    sealed class AuthState {
        object Initial : AuthState()
        object SendingLink : AuthState()
        object LinkSent : AuthState()
        object Authenticating : AuthState()
        data class SelectOrganization(
            val interimToken: String,
            val organizations: List<OrganizationInfo>
        ) : AuthState()
        object CreatingOrganization : AuthState()
        object Success : AuthState()
        data class Error(val message: String) : AuthState()
    }

    // Step 1: Send magic link
    fun sendMagicLink(email: String) {
        viewModelScope.launch {
            try {
                _state.value = AuthState.SendingLink

                val request = MagicLinkSendRequest(email = email)
                val response = sdkManager.authAPI.sendMagicLink(request)

                _state.value = AuthState.LinkSent
                Log.d("Auth", response.message)

            } catch (e: APIError) {
                _state.value = AuthState.Error(e.message ?: "Failed to send magic link")
            }
        }
    }

    // Step 2: Authenticate with token from deep link
    fun authenticateWithToken(token: String) {
        viewModelScope.launch {
            try {
                _state.value = AuthState.Authenticating

                val request = MagicLinkAuthenticateRequest(token = token)
                val response = sdkManager.authAPI.authenticateMagicLink(request)

                if (response.organizations.isEmpty()) {
                    // No organizations, need to create one
                    _state.value = AuthState.SelectOrganization(
                        interimToken = response.interimSessionToken,
                        organizations = emptyList()
                    )
                } else if (response.organizations.size == 1) {
                    // Single organization, auto-select
                    exchangeSession(response.interimSessionToken, response.organizations[0].organizationId)
                } else {
                    // Multiple organizations, let user choose
                    _state.value = AuthState.SelectOrganization(
                        interimToken = response.interimSessionToken,
                        organizations = response.organizations
                    )
                }

            } catch (e: APIError) {
                _state.value = AuthState.Error(e.message ?: "Authentication failed")
            }
        }
    }

    // Step 3a: Exchange for full session with selected org
    fun exchangeSession(interimToken: String, organizationId: String) {
        viewModelScope.launch {
            try {
                _state.value = AuthState.Authenticating

                val request = DiscoveryExchangeRequest(
                    interimSessionToken = interimToken,
                    organizationId = organizationId
                )
                val session = sdkManager.authAPI.exchangeSession(request)

                // Store tokens
                sdkManager.tokenStore.setTokens(
                    access = session.sessionToken,
                    refresh = session.sessionJwt
                )

                _state.value = AuthState.Success

            } catch (e: APIError) {
                _state.value = AuthState.Error(e.message ?: "Session exchange failed")
            }
        }
    }

    // Step 3b: Create new organization
    fun createOrganization(interimToken: String, organizationName: String) {
        viewModelScope.launch {
            try {
                _state.value = AuthState.CreatingOrganization

                val request = DiscoveryCreateOrgRequest(
                    interimSessionToken = interimToken,
                    organizationName = organizationName
                )
                val session = sdkManager.authAPI.createOrganization(request)

                // Store tokens
                sdkManager.tokenStore.setTokens(
                    access = session.sessionToken,
                    refresh = session.sessionJwt
                )

                _state.value = AuthState.Success

            } catch (e: APIError) {
                _state.value = AuthState.Error(e.message ?: "Organization creation failed")
            }
        }
    }
}
```

### Magic Link Composable UI

```kotlin
@Composable
fun MagicLinkScreen(
    viewModel: MagicLinkViewModel,
    onSuccess: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var email by remember { mutableStateOf("") }
    var orgName by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state is MagicLinkViewModel.AuthState.Success) {
            onSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        when (val currentState = state) {
            is MagicLinkViewModel.AuthState.Initial,
            is MagicLinkViewModel.AuthState.SendingLink -> {
                TextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.sendMagicLink(email) },
                    enabled = email.isNotBlank() && state !is MagicLinkViewModel.AuthState.SendingLink,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send Magic Link")
                }
            }

            is MagicLinkViewModel.AuthState.LinkSent -> {
                Text("Magic link sent! Check your email.")
            }

            is MagicLinkViewModel.AuthState.SelectOrganization -> {
                if (currentState.organizations.isEmpty()) {
                    Text("Create your organization:")

                    Spacer(modifier = Modifier.height(16.dp))

                    TextField(
                        value = orgName,
                        onValueChange = { orgName = it },
                        label = { Text("Organization Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            viewModel.createOrganization(currentState.interimToken, orgName)
                        },
                        enabled = orgName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Create Organization")
                    }
                } else {
                    Text("Select an organization:")

                    Spacer(modifier = Modifier.height(16.dp))

                    currentState.organizations.forEach { org ->
                        Button(
                            onClick = {
                                viewModel.exchangeSession(currentState.interimToken, org.organizationId)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column {
                                Text(org.organizationName)
                                Text("Role: ${org.role}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            is MagicLinkViewModel.AuthState.Error -> {
                Text("Error: ${currentState.message}", color = MaterialTheme.colorScheme.error)

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.sendMagicLink(email) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Try Again")
                }
            }

            else -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}
```

### Deep Link Handling

```kotlin
// In your Activity
class MainActivity : ComponentActivity() {

    private val viewModel: MagicLinkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle deep link
        handleIntent(intent)

        setContent {
            MagicLinkScreen(
                viewModel = viewModel,
                onSuccess = { /* Navigate to main screen */ }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val data = intent.data
        if (data?.scheme == "snowball" && data.host == "auth") {
            val token = data.getQueryParameter("token")
            if (token != null) {
                viewModel.authenticateWithToken(token)
            }
        }
    }
}

// In AndroidManifest.xml
// <intent-filter>
//     <action android:name="android.intent.action.VIEW" />
//     <category android:name="android.intent.category.DEFAULT" />
//     <category android:name="android.intent.category.BROWSABLE" />
//     <data android:scheme="snowball" android:host="auth" />
// </intent-filter>
```

## Device Provisioning

### Offline-First App Setup

```kotlin
class DeviceProvisionViewModel(
    private val sdkManager: PikaiaSdkManager,
    private val context: Context
) : ViewModel() {

    suspend fun provisionDevice(email: String, name: String): Result<DeviceProvisionResponse> {
        return try {
            val deviceUuid = getOrCreateDeviceUuid()

            val request = DeviceProvisionRequest(
                deviceUuid = deviceUuid,
                email = email,
                platform = "android",
                name = name,
                deviceName = Build.MODEL,
                osVersion = Build.VERSION.RELEASE,
                appVersion = BuildConfig.VERSION_NAME
            )

            val response = sdkManager.authAPI.provisionDevice(
                request = request,
                mobileAPIKey = BuildConfig.MOBILE_API_KEY
            )

            // Store device tokens
            sdkManager.tokenStore.setDeviceTokens(
                device = response.deviceToken,
                refresh = response.deviceToken
            )

            Result.success(response)

        } catch (e: APIError) {
            Result.failure(e)
        }
    }

    private fun getOrCreateDeviceUuid(): String {
        val prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE)
        var uuid = prefs.getString("uuid", null)

        if (uuid == null) {
            uuid = UUID.randomUUID().toString()
            prefs.edit().putString("uuid", uuid).apply()
        }

        return uuid
    }
}
```

## Mobile Provisioning

### Simple Mobile App Setup

```kotlin
class MobileProvisionViewModel(
    private val sdkManager: PikaiaSdkManager
) : ViewModel() {

    suspend fun provisionMobile(
        email: String,
        name: String? = null,
        phoneNumber: String? = null,
        organizationId: String? = null
    ): Result<SessionResponse> {
        return try {
            val request = MobileProvisionRequest(
                email = email,
                name = name,
                phoneNumber = phoneNumber,
                organizationId = organizationId
            )

            val session = sdkManager.authAPI.provisionMobile(
                request = request,
                mobileAPIKey = BuildConfig.MOBILE_API_KEY
            )

            // Store session tokens
            sdkManager.tokenStore.setTokens(
                access = session.sessionToken,
                refresh = session.sessionJwt
            )

            Result.success(session)

        } catch (e: APIError) {
            Result.failure(e)
        }
    }
}
```

## Session Management

### Get Current User

```kotlin
class UserViewModel(
    private val sdkManager: PikaiaSdkManager
) : ViewModel() {

    private val _user = MutableStateFlow<MeResponse?>(null)
    val user: StateFlow<MeResponse?> = _user.asStateFlow()

    fun loadCurrentUser() {
        viewModelScope.launch {
            try {
                val me = sdkManager.authAPI.getMe()
                _user.value = me

                Log.d("User", "User: ${me.user.name}")
                Log.d("User", "Email: ${me.user.email}")
                Log.d("User", "Organization: ${me.organization.organizationName}")
                Log.d("User", "Role: ${me.member.role}")

            } catch (e: APIError.Unauthorized) {
                // Not authenticated, redirect to login
                _user.value = null
            } catch (e: APIError) {
                Log.e("User", "Error loading user", e)
            }
        }
    }
}
```

### Logout

```kotlin
fun logout(sdkManager: PikaiaSdkManager, onComplete: () -> Unit) {
    viewModelScope.launch {
        sdkManager.logout()
        onComplete()
    }
}
```

## Profile Management

### Update Profile

```kotlin
class ProfileViewModel(
    private val sdkManager: PikaiaSdkManager
) : ViewModel() {

    suspend fun updateName(name: String): Result<UserInfo> {
        return try {
            val request = UpdateProfileRequest(name = name)
            val updatedUser = sdkManager.authAPI.updateProfile(request)
            Result.success(updatedUser)
        } catch (e: APIError) {
            Result.failure(e)
        }
    }
}

// In Composable
@Composable
fun ProfileScreen(viewModel: ProfileViewModel) {
    var name by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.padding(16.dp)) {
        TextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                isLoading = true
                viewModel.viewModelScope.launch {
                    val result = viewModel.updateName(name)
                    isLoading = false

                    result.onSuccess {
                        // Success
                    }.onFailure {
                        error = it.message
                    }
                }
            },
            enabled = !isLoading && name.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Update Profile")
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
```

## Phone Verification

### Complete Phone Verification Flow

```kotlin
class PhoneVerificationViewModel(
    private val sdkManager: PikaiaSdkManager
) : ViewModel() {

    private val _state = MutableStateFlow<VerificationState>(VerificationState.EnterPhone)
    val state: StateFlow<VerificationState> = _state.asStateFlow()

    sealed class VerificationState {
        object EnterPhone : VerificationState()
        object SendingOtp : VerificationState()
        data class EnterCode(val methodId: String) : VerificationState()
        object VerifyingCode : VerificationState()
        object Success : VerificationState()
        data class Error(val message: String) : VerificationState()
    }

    fun sendOtp(phoneNumber: String) {
        viewModelScope.launch {
            try {
                _state.value = VerificationState.SendingOtp

                val request = SendPhoneOtpRequest(phoneNumber = phoneNumber)
                val response = sdkManager.authAPI.sendPhoneOtp(request)

                _state.value = VerificationState.EnterCode(methodId = response.methodId)

            } catch (e: APIError) {
                _state.value = VerificationState.Error(e.message ?: "Failed to send OTP")
            }
        }
    }

    fun verifyOtp(methodId: String, code: String) {
        viewModelScope.launch {
            try {
                _state.value = VerificationState.VerifyingCode

                val request = VerifyPhoneOtpRequest(
                    methodId = methodId,
                    code = code
                )
                val verifiedUser = sdkManager.authAPI.verifyPhoneOtp(request)

                if (verifiedUser.phoneVerified) {
                    _state.value = VerificationState.Success
                } else {
                    _state.value = VerificationState.Error("Phone verification failed")
                }

            } catch (e: APIError) {
                _state.value = VerificationState.Error(e.message ?: "Invalid code")
            }
        }
    }
}

@Composable
fun PhoneVerificationScreen(viewModel: PhoneVerificationViewModel) {
    val state by viewModel.state.collectAsState()
    var phoneNumber by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        when (val currentState = state) {
            is PhoneVerificationViewModel.VerificationState.EnterPhone -> {
                Text("Enter your phone number (E.164 format):")

                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number") },
                    placeholder = { Text("+14155551234") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.sendOtp(phoneNumber) },
                    enabled = phoneNumber.startsWith("+"),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send Code")
                }
            }

            is PhoneVerificationViewModel.VerificationState.EnterCode -> {
                Text("Enter the 6-digit code sent to your phone:")

                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = code,
                    onValueChange = { if (it.length <= 6) code = it },
                    label = { Text("Verification Code") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.verifyOtp(currentState.methodId, code) },
                    enabled = code.length == 6,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Verify")
                }
            }

            is PhoneVerificationViewModel.VerificationState.Success -> {
                Text("Phone verified successfully!")
            }

            is PhoneVerificationViewModel.VerificationState.Error -> {
                Text("Error: ${currentState.message}", color = MaterialTheme.colorScheme.error)

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.sendOtp(phoneNumber) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Try Again")
                }
            }

            else -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}
```

## Complete ViewModel Example

### All-in-One Authentication Manager

```kotlin
class AuthenticationManager(
    private val context: Context
) {

    private val sdkManager = PikaiaSdkManager(context)

    val isAuthenticated: Flow<Boolean> = flow {
        while (true) {
            emit(sdkManager.isAuthenticated())
            delay(1000) // Check every second
        }
    }

    suspend fun sendMagicLink(email: String): Result<MessageResponse> {
        return runCatching {
            val request = MagicLinkSendRequest(email = email)
            sdkManager.authAPI.sendMagicLink(request)
        }
    }

    suspend fun authenticateMagicLink(token: String): Result<MagicLinkAuthenticateResponse> {
        return runCatching {
            val request = MagicLinkAuthenticateRequest(token = token)
            sdkManager.authAPI.authenticateMagicLink(request)
        }
    }

    suspend fun selectOrganization(interimToken: String, orgId: String): Result<SessionResponse> {
        return runCatching {
            val request = DiscoveryExchangeRequest(
                interimSessionToken = interimToken,
                organizationId = orgId
            )
            val session = sdkManager.authAPI.exchangeSession(request)

            sdkManager.tokenStore.setTokens(
                access = session.sessionToken,
                refresh = session.sessionJwt
            )

            session
        }
    }

    suspend fun createOrganization(interimToken: String, name: String): Result<SessionResponse> {
        return runCatching {
            val request = DiscoveryCreateOrgRequest(
                interimSessionToken = interimToken,
                organizationName = name
            )
            val session = sdkManager.authAPI.createOrganization(request)

            sdkManager.tokenStore.setTokens(
                access = session.sessionToken,
                refresh = session.sessionJwt
            )

            session
        }
    }

    suspend fun getCurrentUser(): Result<MeResponse> {
        return runCatching {
            sdkManager.authAPI.getMe()
        }
    }

    suspend fun logout() {
        sdkManager.logout()
    }
}
```

## Error Handling Best Practices

```kotlin
suspend fun <T> handleApiCall(call: suspend () -> T): Result<T> {
    return try {
        Result.success(call())
    } catch (e: APIError.Unauthorized) {
        Log.e("API", "Unauthorized - redirecting to login")
        // Navigate to login
        Result.failure(Exception("Please log in again"))
    } catch (e: APIError.RefreshFailed) {
        Log.e("API", "Token refresh failed - clearing session")
        // Clear tokens and navigate to login
        Result.failure(Exception("Session expired, please log in again"))
    } catch (e: APIError.Client) {
        Log.e("API", "Client error: ${e.message}")
        Result.failure(Exception("Invalid request: ${e.message}"))
    } catch (e: APIError.Server) {
        Log.e("API", "Server error: ${e.message}")
        Result.failure(Exception("Server error, please try again later"))
    } catch (e: APIError.Transport) {
        Log.e("API", "Network error: ${e.message}")
        Result.failure(Exception("Network error, check your connection"))
    } catch (e: Exception) {
        Log.e("API", "Unexpected error", e)
        Result.failure(Exception("An unexpected error occurred"))
    }
}

// Usage
val result = handleApiCall {
    sdkManager.authAPI.getMe()
}

result.onSuccess { me ->
    // Handle success
}.onFailure { error ->
    // Show error to user
    Toast.makeText(context, error.message, Toast.LENGTH_LONG).show()
}
```

## Testing

### Unit Test Example

```kotlin
class AuthAPITest {

    @Test
    fun `test magic link flow`() = runTest {
        val tokenStore = InMemoryTokenStore()
        val mockClient = createMockAPIClient()
        val authAPI = AuthAPI(mockClient)

        // Send magic link
        val sendRequest = MagicLinkSendRequest(email = "test@example.com")
        val sendResponse = authAPI.sendMagicLink(sendRequest)
        assertEquals("Magic link sent", sendResponse.message)

        // Authenticate
        val authRequest = MagicLinkAuthenticateRequest(token = "test-token")
        val authResponse = authAPI.authenticateMagicLink(authRequest)
        assertNotNull(authResponse.interimSessionToken)

        // Exchange session
        val exchangeRequest = DiscoveryExchangeRequest(
            interimSessionToken = authResponse.interimSessionToken,
            organizationId = authResponse.organizations[0].organizationId
        )
        val session = authAPI.exchangeSession(exchangeRequest)
        assertNotNull(session.sessionToken)

        // Store tokens
        tokenStore.setTokens(session.sessionToken, session.sessionJwt)
        assertEquals(session.sessionToken, tokenStore.getAccessToken())
    }
}
```
