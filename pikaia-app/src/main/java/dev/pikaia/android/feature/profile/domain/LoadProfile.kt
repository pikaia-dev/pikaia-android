package dev.pikaia.android.feature.profile.domain

import dev.pikaia.android.feature.profile.data.Profile
import dev.pikaia.android.feature.profile.data.repository.ProfileRepository
import dev.pikaia.android.lib.usecase.FlowUseCase

class LoadProfile(
    private val profileRepository: ProfileRepository
) : FlowUseCase<Unit, Profile?> {

    override fun invoke(params: Unit) = profileRepository.loadUserProfile()
}