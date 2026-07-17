package dev.pikaia.android.feature.login.data.repository

import dev.pikaia.android.feature.login.data.AuthenticatedLogin
import dev.pikaia.android.feature.login.data.LoginOrganization
import dev.pikaia.android.sdk.auth.api.AuthAPI
import dev.pikaia.android.sdk.auth.data.auth.DiscoveryCreateOrgRequest
import dev.pikaia.android.sdk.auth.data.auth.DiscoveryExchangeRequest
import dev.pikaia.android.sdk.auth.data.auth.MagicLinkAuthenticateRequest
import dev.pikaia.android.sdk.auth.data.auth.MagicLinkSendRequest
import dev.pikaia.android.sdk.auth.data.session.SessionResponse
import dev.pikaia.android.sdk.core.auth.AuthSession
import dev.pikaia.android.sdk.core.auth.TokenStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface AuthRepository {
    fun sendMagicLink(email: String): Flow<Unit>
    fun authenticateMagicLink(token: String): Flow<AuthenticatedLogin>
    fun enterOrganization(intermediateSessionToken: String, organizationId: String): Flow<Unit>
    fun createOrganization(intermediateSessionToken: String, name: String, slug: String): Flow<Unit>
}

class AuthRepositoryImpl(
    private val authAPI: AuthAPI,
    private val tokenStore: TokenStore
) : AuthRepository {

    override fun sendMagicLink(email: String) = flow {
        authAPI.sendMagicLink(MagicLinkSendRequest(email = email)).getOrThrow()
        emit(Unit)
    }

    override fun authenticateMagicLink(token: String) = flow {
        val response = authAPI
            .authenticateMagicLink(MagicLinkAuthenticateRequest(token = token))
            .getOrThrow()

        emit(
            AuthenticatedLogin(
                intermediateSessionToken = response.intermediateSessionToken,
                organizations = response.discoveredOrganizations.map {
                    LoginOrganization(
                        id = it.organizationId,
                        name = it.organizationName,
                        slug = it.organizationSlug
                    )
                }
            )
        )
    }

    override fun enterOrganization(
        intermediateSessionToken: String,
        organizationId: String
    ) = flow {
        val session = authAPI.exchangeSession(
            DiscoveryExchangeRequest(
                intermediateSessionToken = intermediateSessionToken,
                organizationId = organizationId
            )
        ).getOrThrow()

        persist(session)
        emit(Unit)
    }

    override fun createOrganization(
        intermediateSessionToken: String,
        name: String,
        slug: String
    ) = flow {
        val session = authAPI.createOrganization(
            DiscoveryCreateOrgRequest(
                intermediateSessionToken = intermediateSessionToken,
                organizationName = name,
                organizationSlug = slug
            )
        ).getOrThrow()

        persist(session)
        emit(Unit)
    }

    private suspend fun persist(session: SessionResponse) {
        tokenStore.setSession(
            AuthSession(
                sessionJwt = session.sessionJwt,
                sessionToken = session.sessionToken
            )
        )
    }
}
