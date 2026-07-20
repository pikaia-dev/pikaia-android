package dev.pikaia.android.feature.profile.data.repository

import dev.pikaia.android.feature.profile.data.Profile
import dev.pikaia.android.sdk.auth.api.AuthAPI
import dev.pikaia.android.sdk.core.auth.TokenStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface ProfileRepository {
    fun loadUserProfile(): Flow<Profile?>
    fun logout(): Flow<Unit>
}

class ProfileRepositoryImpl(
    private val authAPI: AuthAPI,
    private val tokenStore: TokenStore
) : ProfileRepository {

    override fun loadUserProfile() = flow {
        if (tokenStore.getSession() == null) {
            emit(null)
            return@flow
        }

        val me = authAPI.getMe().getOrThrow()
        emit(
            Profile(
                name = me.user.name,
                phone = me.user.phoneNumber,
                email = me.user.email
            )
        )
    }

    override fun logout() = flow {
        // Best effort server-side; the local session is dropped regardless
        authAPI.logout()
        tokenStore.clear()
        emit(Unit)
    }
}
