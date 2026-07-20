package dev.pikaia.android.feature.login.domain

import dev.pikaia.android.feature.login.data.repository.AuthRepository
import dev.pikaia.android.lib.usecase.FlowUseCase
import kotlinx.coroutines.flow.Flow

class EnterOrganization(
    private val authRepository: AuthRepository
) : FlowUseCase<EnterOrganization.Params, Unit> {

    data class Params(
        val intermediateSessionToken: String,
        val organizationId: String
    )

    override fun invoke(params: Params): Flow<Unit> = authRepository.enterOrganization(
        intermediateSessionToken = params.intermediateSessionToken,
        organizationId = params.organizationId
    )
}
