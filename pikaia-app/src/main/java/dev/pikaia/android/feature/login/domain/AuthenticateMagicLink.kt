package dev.pikaia.android.feature.login.domain

import dev.pikaia.android.feature.login.data.AuthenticatedLogin
import dev.pikaia.android.feature.login.data.repository.AuthRepository
import dev.pikaia.android.lib.usecase.FlowUseCase
import kotlinx.coroutines.flow.Flow

class AuthenticateMagicLink(
    private val authRepository: AuthRepository
) : FlowUseCase<String, AuthenticatedLogin> {

    override fun invoke(params: String): Flow<AuthenticatedLogin> =
        authRepository.authenticateMagicLink(params)
}
