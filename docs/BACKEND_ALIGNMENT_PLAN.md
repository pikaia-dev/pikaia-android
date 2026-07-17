# Backend Alignment Plan — sdk-core / sdk-auth as the shared client layer

**Date:** 2026-07-17 · **Tracking epic:** [#13](https://github.com/pikaia-dev/pikaia-android/issues/13)

## Why

The `pikaia` and `prelint` backends share one auth implementation lineage: the same
Django Ninja + Stytch B2B stack, the same discovery → session flow, the same devices
(QR link, 1-year refreshable sessions) and passkeys surface, down to identical JSON
field names. Prelint's backend is a fork/superset — it adds email OTP (locally-signed
IST), `switch-organization`, `invite/accept`, and an organizations list, but changes
nothing the SDKs would cover.

Decision: **these SDKs become the shared auth/networking layer for all
pikaia-backend products.** First consumer is `prelint/prelint-android`
([ADR 010](https://github.com/prelint/prelint-android/issues/25) there). `pikaia-app`
is not a product — it is the reference implementation that shows consumers how to
assemble the SDKs.

## Where we are

The SDK implementation lives on the unmerged `feat/sdk-implementation` branch
(`main` has only project scaffolding). Known gaps, confirmed against the live
backend (`pikaia-dev/pikaia`):

| Area | State |
|---|---|
| Endpoint paths | `AuthAPI` calls `/v1/auth/...`; backend mounts at `/api/v1/...`. `POST /v1/auth/session/refresh` and `POST /v1/auth/device/provision` don't exist on the backend at all. |
| DTOs | `interim_session_token` vs backend's `intermediate_session_token`; devices/passkeys DTOs missing entirely. |
| Token model | `TokenStore`/`AuthProvider` assume access/refresh token pairs; reality is `session_jwt` + `session_token` + `device_uuid`, refreshed via authed `POST /devices/session/refresh`. |
| Refresh | `APIClient`'s 401 → coalesced-refresh → retry-once path is wired, but **nothing implements `AuthProvider`**, so it cannot run. |
| Storage | `DataStoreTokenStore` claims encryption but is a plain Preferences DataStore; the `EncryptedFile` helpers are dead code. |
| Tests | `sdk-core` and `sdk-auth` have zero tests; nothing in the repo (including `pikaia-app`) ever constructs an `APIClient` or fires a request. |
| Reference app | `pikaia-app` uses hardcoded sample data, a stub login, and lacks the `INTERNET` permission. |

## Target architecture

```
┌────────────────────────────┐   ┌────────────────────────────┐
│ pikaia-app (reference)     │   │ prelint-android (consumer) │
│ demo login, /me, devices   │   │ + prelint-only API on      │
│                            │   │   sdk-core (email OTP,     │
│                            │   │   switch-org, invites)     │
└─────────────┬──────────────┘   └─────────────┬──────────────┘
              │ JitPack / mavenLocal           │
┌─────────────▼────────────────────────────────▼──────────────┐
│ sdk-auth: AuthAPI · DevicesAPI · PasskeysAPI ·              │
│   encrypted TokenStore · AuthTokenInterceptor ·             │
│   DeviceSessionAuthProvider + RefreshCoordinator            │
├─────────────────────────────────────────────────────────────┤
│ sdk-core: APIClient (Ktor) · Endpoint · ApiResult ·         │
│   typed errors (incl. 429/Retry-After) · interceptor seams  │
└─────────────────────────────────────────────────────────────┘
```

**Consumption contract:** the SDK models only the surface shared by every
pikaia-backend product — magic-link + discovery, sessions, devices, passkeys,
`/me`/profile, mobile provisioning. Product-specific endpoints live in the consuming
app, implemented on `sdk-core`'s `APIClient`. Product-specific values (base URL, QR
deep-link scheme like `pikaia://` vs `prelint://`) are configuration, never constants.

## Workstreams

**Prerequisite:** land `feat/sdk-implementation` via PR — everything below builds on it.

### M1 — align & harden (blocks prelint adoption)

1. [#3](https://github.com/pikaia-dev/pikaia-android/issues/3) Sync `AuthAPI` paths and DTOs with the live backend, field for field.
2. [#4](https://github.com/pikaia-dev/pikaia-android/issues/4) Add the devices API (link initiate/complete, list, revoke, session refresh).
3. [#5](https://github.com/pikaia-dev/pikaia-android/issues/5) Reshape the token model around Stytch sessions; ship `DeviceSessionAuthProvider` so the 401-refresh path actually works; drop the `DeviceToken` header scheme the backend never accepted.
4. [#6](https://github.com/pikaia-dev/pikaia-android/issues/6) Make `DataStoreTokenStore` genuinely Keystore-encrypted (required by prelint's ADR 005).
5. [#7](https://github.com/pikaia-dev/pikaia-android/issues/7) Upstream prelint-android's `lib/network` improvements into `sdk-core`: `ApiResult`, typed errors with 429/`Retry-After`, installed `HttpTimeout`, injectable engine, and the MockEngine test suite. (prelint-android deletes its fork afterwards.)
6. [#9](https://github.com/pikaia-dev/pikaia-android/issues/9) MockEngine coverage for every endpoint plus refresh-coalescing concurrency tests.
7. [#10](https://github.com/pikaia-dev/pikaia-android/issues/10) First tagged JitPack release (0.x while the surface churns); verify `com.github.pikaia-dev.pikaia-android:<module>:<tag>` resolves.

### M2 — full surface & reference

8. [#8](https://github.com/pikaia-dev/pikaia-android/issues/8) Passkeys REST client (unblocks prelint's passkey epic).
9. [#11](https://github.com/pikaia-dev/pikaia-android/issues/11) Rewire `pikaia-app` into the canonical SDK assembly example: real login, `/me`-backed profile, device link + refresh.
10. [#12](https://github.com/pikaia-dev/pikaia-android/issues/12) Docs refresh — kill the stale `joinsnowball.io` examples, document the consumption contract, clean up `temp_docs/` and the empty root `sdk/` dir.

## Definition of done

- A consumer can add three catalog entries, assemble `APIClient` + `sdk-auth` from
  the README example, and have login → session persistence → bearer injection →
  401 refresh working without reading SDK source.
- prelint-android's migration issue ([prelint/prelint-android#25](https://github.com/prelint/prelint-android/issues/25)) is unblocked by M1.
