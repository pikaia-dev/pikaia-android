# sdk-auth flows

Complete flows against the aligned backend API. Assembly of `apiClient` / `tokenStore`
is shown in the repo root [README](../README.md#assembling-the-auth-stack); the
`pikaia-app` module is the living reference implementation.

## Magic-link login → organization → session

```kotlin
val authAPI = AuthAPI(apiClient)

// 1. Send the magic link
authAPI.sendMagicLink(MagicLinkSendRequest(email = "jane@example.com"))

// 2. The user opens the link; your app receives the token (deep link or paste)
val auth = authAPI.authenticateMagicLink(MagicLinkAuthenticateRequest(token))
    .getOrThrow()

// 3. Enter an existing organization — or create one if none were discovered
val session: SessionResponse = when {
    auth.discoveredOrganizations.isNotEmpty() -> authAPI.exchangeSession(
        DiscoveryExchangeRequest(
            intermediateSessionToken = auth.intermediateSessionToken,
            organizationId = auth.discoveredOrganizations.first().organizationId
        )
    ).getOrThrow()

    else -> authAPI.createOrganization(
        DiscoveryCreateOrgRequest(
            intermediateSessionToken = auth.intermediateSessionToken,
            organizationName = "Acme Corp",
            organizationSlug = "acme-corp"
        )
    ).getOrThrow()
}

// 4. Persist — from here on every request carries the bearer credential
tokenStore.setSession(
    AuthSession(sessionJwt = session.sessionJwt, sessionToken = session.sessionToken)
)
```

Note: magic-link sessions have no `deviceUuid`, so they cannot be refreshed when the
JWT expires — they're fine for short web-style sessions. Mobile apps that need
long-lived sessions link the device (below).

## Device linking (long-lived refreshable session)

```kotlin
val devicesAPI = DevicesAPI(apiClient)

// On an already-authenticated client (e.g. your product's web app):
val link = devicesAPI.initiateLink().getOrThrow()
renderQr(link.qrUrl)   // product deep-link scheme; expires in link.expiresInSeconds

// On the mobile device, after scanning and extracting the token from YOUR scheme:
val linked = devicesAPI.completeLink(
    CompleteLinkRequest(
        token = tokenFromQr,
        deviceUuid = stableDeviceId,     // generate once, keep forever
        name = "Pixel 9 Pro",
        platform = "android",
        osVersion = Build.VERSION.RELEASE,
        appVersion = BuildConfig.VERSION_NAME
    )
).getOrThrow()

tokenStore.setSession(
    AuthSession(
        sessionJwt = linked.sessionJwt,
        sessionToken = linked.sessionToken,
        sessionExpiresAt = linked.sessionExpiresAt,
        deviceUuid = stableDeviceId      // enables automatic 401-refresh
    )
)
```

With `deviceUuid` stored, JWT expiry is invisible: any 401 triggers
`POST devices/session/refresh` and a single retry, coalesced across concurrent calls.

Device management:

```kotlin
val devices = devicesAPI.listDevices().getOrThrow()   // devices, count
devicesAPI.revokeDevice(devices.devices.first().id)   // 204 → ApiResult<Unit>
```

## Session state

```kotlin
when (val result = authAPI.getMe()) {
    is ApiResult.Success -> render(result.value.user, result.value.organization)
    is ApiResult.Failure -> when (result.error) {
        is APIError.RefreshFailed -> goToLogin()   // session revoked or fully expired
        else -> showError(result.error)
    }
}

authAPI.logout()          // POST auth/logout
tokenStore.clear()        // then drop the local session
```

## Passkeys

The SDK is the REST half; the WebAuthn ceremony runs through Android's Credential
Manager. `options` and `credential` are verbatim `JsonObject`s.

```kotlin
val passkeysAPI = PasskeysAPI(apiClient)

// Registration (requires an authenticated client)
val reg = passkeysAPI.registerOptions().getOrThrow()
val credential: JsonObject = credentialManagerCreate(reg.options)   // your ceremony code
passkeysAPI.registerVerify(
    PasskeyRegistrationVerifyRequest(
        challengeId = reg.challengeId,
        credential = credential,
        name = "Pixel 9 Pro"
    )
)

// Authentication (public) → full session
val opts = passkeysAPI.authenticateOptions(
    PasskeyAuthenticationOptionsRequest(email = "jane@example.com")
).getOrThrow()
val assertion: JsonObject = credentialManagerGet(opts.options)      // your ceremony code
val session = passkeysAPI.authenticateVerify(
    PasskeyAuthenticationVerifyRequest(challengeId = opts.challengeId, credential = assertion)
).getOrThrow()

tokenStore.setSession(
    AuthSession(sessionJwt = session.sessionJwt, sessionToken = session.sessionToken)
)

// Management
passkeysAPI.listPasskeys()
passkeysAPI.deletePasskey(id)
```

## Phone verification & profile

```kotlin
authAPI.sendPhoneOtp(SendPhoneOtpRequest(phoneNumber = "+14155551234"))
authAPI.verifyPhoneOtp(VerifyPhoneOtpRequest(phoneNumber = "+14155551234", otpCode = code))
authAPI.updateProfile(UpdateProfileRequest(name = "Jane D"))
```
