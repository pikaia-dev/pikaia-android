# Backend Alignment Plan — sdk-core / sdk-auth as the shared client layer

**Date:** 2026-07-17 · **Tracking epic:** [#13](https://github.com/pikaia-dev/pikaia-android/issues/13)

## Why

Products built on the pikaia backend stack share one auth implementation lineage: the
same Django Ninja + Stytch B2B architecture, the same discovery → session flow, the
same devices (QR link, 1-year refreshable sessions) and passkeys surface, down to
identical JSON field names. Platform backends are forks/supersets of this stack —
they may add product-specific endpoints, but the shared surface stays identical.

Decision: **these SDKs become the shared auth/networking layer for all
pikaia-backend platform implementations.** The first platform implementation is
adopting them now (its migration is tracked in its own repo). `pikaia-app` is not a
product — it is the reference implementation that shows consumers how to assemble
the SDKs.

## Where we are

The SDK implementation lives on the unmerged `feat/sdk-implementation` branch
(`main` has only project scaffolding). Known gaps, confirmed against the live
backend (`pikaia-dev/pikaia`):

| Area | State |
|---|---|
| Endpoint paths | `AuthAPI` hardcodes a `/v1/...` prefix; the backend mounts at `/api/v1/...`. Settled convention: the SDK uses relative paths and the consumer's `baseUrl` carries the full prefix. `POST auth/session/refresh` and `POST auth/device/provision` don't exist on the backend and are removed. |
| DTOs | `interim_session_token` vs the backend's `intermediate_session_token`; devices/passkeys DTOs missing entirely. |
| Token model | `TokenStore`/`AuthProvider` assume access/refresh token pairs; reality is `session_jwt` + `session_token` + `device_uuid`, refreshed via authed `POST devices/session/refresh`. |
| Refresh | `APIClient`'s 401 → coalesced-refresh → retry-once path is wired, but **nothing implements `AuthProvider`**, so it cannot run. |
| Storage | `DataStoreTokenStore` claims encryption but is a plain Preferences DataStore; the `EncryptedFile` helpers are dead code. |
| Tests | `sdk-core` and `sdk-auth` have zero tests; nothing in this repo (including `pikaia-app`) ever constructs an `APIClient` or fires a request. |
| Reference app | `pikaia-app` uses hardcoded sample data, a stub login, and lacks the `INTERNET` permission. |

## Target architecture

```
┌────────────────────────────┐   ┌────────────────────────────┐
│ pikaia-app (reference)     │   │ platform app (consumer)    │
│ demo login, /me, devices   │   │ + platform-specific API    │
│                            │   │   on sdk-core (endpoints   │
│                            │   │   its backend fork adds)   │
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
`/me`/profile, mobile provisioning. Platform-specific endpoints live in the
consuming app, implemented on `sdk-core`'s `APIClient`. Platform-specific values
(base URL including the `/api/v1` prefix, the QR deep-link scheme such as
`pikaia://device/link`) are configuration, never constants.

## Workstreams

**Prerequisite:** land `feat/sdk-implementation` via PR — everything below builds on it.

### M1 — align & harden (blocks the first platform adoption)

1. [#3](https://github.com/pikaia-dev/pikaia-android/issues/3) Sync `AuthAPI` paths and DTOs with the live backend, field for field.
2. [#4](https://github.com/pikaia-dev/pikaia-android/issues/4) Add the devices API (link initiate/complete, list, revoke, session refresh).
3. [#5](https://github.com/pikaia-dev/pikaia-android/issues/5) Reshape the token model around Stytch sessions; ship `DeviceSessionAuthProvider` so the 401-refresh path actually works; drop the `DeviceToken` header scheme (the backend only accepts `Bearer`).
4. [#6](https://github.com/pikaia-dev/pikaia-android/issues/6) Make `DataStoreTokenStore` genuinely Keystore-encrypted (platform implementations require it for their token-storage policies).
5. [#7](https://github.com/pikaia-dev/pikaia-android/issues/7) Upstream the networking improvements proven in the first platform implementation's fork of `sdk-core`: `ApiResult`, typed errors with 429/`Retry-After`, installed `HttpTimeout`, injectable engine, and a MockEngine test suite. (The fork is deleted once upstreamed.)
6. [#9](https://github.com/pikaia-dev/pikaia-android/issues/9) MockEngine coverage for every endpoint plus refresh-coalescing concurrency tests.
7. [#10](https://github.com/pikaia-dev/pikaia-android/issues/10) First tagged JitPack release (0.x while the surface churns); verify `com.github.pikaia-dev.pikaia-android:<module>:<tag>` resolves.

### M2 — full surface & reference

8. [#8](https://github.com/pikaia-dev/pikaia-android/issues/8) Passkeys REST client (required for platform passkey login).
9. [#11](https://github.com/pikaia-dev/pikaia-android/issues/11) Rewire `pikaia-app` into the canonical SDK assembly example: real login, `/me`-backed profile, device link + refresh.
10. [#12](https://github.com/pikaia-dev/pikaia-android/issues/12) Docs refresh — replace stale example hosts, document the consumption contract, clean up `temp_docs/` and the empty root `sdk/` dir.

## Definition of done

- A consumer can add three catalog entries, assemble `APIClient` + `sdk-auth` from
  the README example, and have login → session persistence → bearer injection →
  401 refresh working without reading SDK source.
- The first platform implementation's migration is unblocked by M1.
