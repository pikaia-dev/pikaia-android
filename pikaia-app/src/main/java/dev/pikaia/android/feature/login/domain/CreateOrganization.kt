package dev.pikaia.android.feature.login.domain

import dev.pikaia.android.feature.login.data.repository.AuthRepository
import dev.pikaia.android.lib.usecase.FlowUseCase
import kotlinx.coroutines.flow.Flow

class CreateOrganization(
    private val authRepository: AuthRepository
) : FlowUseCase<CreateOrganization.Params, Unit> {

    data class Params(
        val intermediateSessionToken: String,
        val name: String,
        val slug: String
    )

    override fun invoke(params: Params): Flow<Unit> = authRepository.createOrganization(
        intermediateSessionToken = params.intermediateSessionToken,
        name = params.name,
        slug = params.slug
    )
}
