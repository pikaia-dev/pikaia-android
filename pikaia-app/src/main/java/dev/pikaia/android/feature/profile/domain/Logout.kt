package dev.pikaia.android.feature.profile.domain

import dev.pikaia.android.feature.profile.data.repository.ProfileRepository
import dev.pikaia.android.lib.usecase.FlowUseCase
import kotlinx.coroutines.flow.Flow

class Logout(
    private val profileRepository: ProfileRepository
) : FlowUseCase<Unit, Unit> {

    override fun invoke(params: Unit): Flow<Unit> = profileRepository.logout()
}
