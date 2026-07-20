package dev.pikaia.android.di

import dev.pikaia.android.BuildConfig
import dev.pikaia.android.sdk.auth.api.AuthAPI
import dev.pikaia.android.sdk.auth.api.DevicesAPI
import dev.pikaia.android.sdk.auth.interceptor.AuthTokenInterceptor
import dev.pikaia.android.sdk.auth.refresh.DefaultRefreshCoordinator
import dev.pikaia.android.sdk.auth.refresh.DeviceSessionAuthProvider
import dev.pikaia.android.sdk.auth.token.DataStoreTokenStore
import dev.pikaia.android.sdk.core.auth.AuthProvider
import dev.pikaia.android.sdk.core.auth.TokenStore
import dev.pikaia.android.sdk.core.networking.APIClient
import dev.pikaia.android.sdk.core.networking.APIClientConfig
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Canonical assembly of the SDK auth stack — the wiring every consumer copies.
 *
 * Two clients share one encrypted token store:
 * - the refresh client has no auth provider, so a failing refresh can never recurse;
 * - the main client gets bearer injection plus the automatic
 *   401 → coalesced refresh → retry-once pipeline.
 */
private const val REFRESH_CLIENT = "refreshClient"

val sdkModule = module {
    single { APIClientConfig(baseUrl = BuildConfig.PIKAIA_API_BASE_URL) }

    single<TokenStore> { DataStoreTokenStore(androidContext()) }

    single(named(REFRESH_CLIENT)) {
        APIClient(
            config = get(),
            requestInterceptors = listOf(AuthTokenInterceptor(get()))
        )
    }

    single<AuthProvider> {
        DeviceSessionAuthProvider(DevicesAPI(get(named(REFRESH_CLIENT))))
    }

    single {
        APIClient(
            config = get(),
            requestInterceptors = listOf(AuthTokenInterceptor(get())),
            tokenStore = get(),
            authProvider = get(),
            refreshCoordinator = DefaultRefreshCoordinator()
        )
    }

    single { AuthAPI(get()) }
    single { DevicesAPI(get()) }
}
