package dev.pikaia.android.feature.login.domain

import dev.pikaia.android.feature.login.data.repository.AuthRepository
import dev.pikaia.android.lib.usecase.FlowUseCase
import kotlinx.coroutines.flow.Flow

class SendMagicLink(
    private val authRepository: AuthRepository
) : FlowUseCase<String, Unit> {

    override fun invoke(params: String): Flow<Unit> = authRepository.sendMagicLink(params)
}
