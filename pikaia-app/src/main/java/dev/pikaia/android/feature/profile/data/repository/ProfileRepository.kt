package dev.pikaia.android.feature.profile.data.repository

import dev.pikaia.android.feature.profile.data.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface ProfileRepository {
    fun loadUserProfile(): Flow<Profile?>
}

class ProfileRepositoryImpl : ProfileRepository {

    override fun loadUserProfile() = flowOf(
        Profile("Sample name", "+48123123123", "sample@email.com")
    )
}
