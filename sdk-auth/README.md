# sdk-auth

The shared auth surface of the pikaia backend stack: magic-link + organization
discovery, mobile provisioning, `/me`/profile, phone verification, device linking with
long-lived refreshable sessions, passkeys, Keystore-encrypted token storage, and a
working 401 → refresh → retry pipeline.

All API methods are `suspend` and return `ApiResult<T>` (see `sdk-core`). Paths are
relative — the `APIClient`'s `baseUrl` carries the backend's API prefix.

## Surface

### AuthAPI

| Method | Endpoint |
|---|---|
| `sendMagicLink` | `POST auth/magic-link/send` |
| `authenticateMagicLink` | `POST auth/magic-link/authenticate` → intermediate session token + discovered organizations |
| `createOrganization` | `POST auth/discovery/create-org` → full session |
| `exchangeSession` | `POST auth/discovery/exchange` → full session |
| `provisionMobile` | `POST auth/mobile/provision` (`X-Mobile-API-Key` header) → full session |
| `logout` | `POST auth/logout` |
| `getMe` | `GET auth/me` → user / member / organization |
| `updateProfile` | `PATCH auth/me/profile` |
| `sendPhoneOtp` / `verifyPhoneOtp` | `POST auth/phone/send-otp` / `POST auth/phone/verify-otp` |

### DevicesAPI

| Method | Endpoint |
|---|---|
| `initiateLink` | `POST devices/link/initiate` (auth) → `qr_url` + expiry |
| `completeLink` | `POST devices/link/complete` (public, rate-limited) → long-lived session |
| `listDevices` | `GET devices/` |
| `revokeDevice` | `DELETE devices/{id}` |
| `refreshSession` | `POST devices/session/refresh` (auth) → fresh session tokens |

The QR `qr_url` uses your product's own deep-link scheme; the SDK passes it through
verbatim and never parses it.

### PasskeysAPI

| Method | Endpoint |
|---|---|
| `registerOptions` | `POST auth/passkeys/register/options` (auth) |
| `registerVerify` | `POST auth/passkeys/register/verify` (auth) |
| `authenticateOptions` | `POST auth/passkeys/authenticate/options` (public) |
| `authenticateVerify` | `POST auth/passkeys/authenticate/verify` (public) → full session |
| `listPasskeys` / `deletePasskey` | `GET auth/passkeys/` / `DELETE auth/passkeys/{id}` |

REST client only: the WebAuthn ceremony (Credential Manager) stays in the consuming
app; `options` and `credential` payloads pass through as verbatim JSON.

## Sessions and refresh

The backend issues Stytch-based sessions: a short-lived `session_jwt` (the bearer
credential) plus an opaque `session_token`. Device-linked sessions additionally carry
the `device_uuid` needed to refresh.

- `AuthTokenInterceptor` injects `Authorization: Bearer <session_jwt>` from the
  `TokenStore` on every request.
- On a 401, `APIClient` asks `DeviceSessionAuthProvider` (via `DefaultRefreshCoordinator`,
  which coalesces concurrent refreshes into one call) to obtain fresh tokens through
  `POST devices/session/refresh`, persists them, and retries once. The refresh request
  authenticates with the current — possibly just-expired — JWT; the backend validates
  the underlying long-lived session server-side.
- If the refresh fails, the session is gone (revoked or fully expired): surface login.

The canonical assembly lives in the repo root [README](../README.md#assembling-the-auth-stack)
and is exercised end-to-end by `AuthRefreshIntegrationTest`.

## Token storage

`DataStoreTokenStore` persists the session in a Preferences DataStore with every value
encrypted by an Android Keystore AES-256-GCM key (`KeystoreValueCipher`) that never
leaves the device. Values that cannot be decrypted — a backup restored onto another
device, an invalidated key — read as logged-out rather than crashing.

Exclude the DataStore file from Auto Backup (`dataExtractionRules` + `fullBackupContent`):

```xml
<exclude domain="file" path="datastore/pikaia_tokens.preferences_pb" />
```

`InMemoryTokenStore` is the non-persistent test double.

## Errors

Non-2xx responses carry the backend's uniform `{"detail": "..."}` body, surfaced as
`APIError.Http(statusCode, detail, …)`; 429 becomes `APIError.RateLimited(retryAfter, …)`.
See [EXAMPLE.md](EXAMPLE.md) for complete flows.
