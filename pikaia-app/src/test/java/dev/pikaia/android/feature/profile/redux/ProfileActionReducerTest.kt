package dev.pikaia.android.feature.profile.redux

import dev.pikaia.android.feature.profile.data.Profile
import dev.pikaia.android.lib.store.Action
import dev.pikaia.android.state.AppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ProfileActionReducerTest {

    private lateinit var reducer: ProfileActionReducer

    private val sampleProfile = Profile(
        name = "John Doe",
        phone = "+48123456789",
        email = "john@example.com"
    )

    @Before
    fun setUp() {
        reducer = ProfileActionReducer()
    }

    @Test
    fun `Logout action sets profile to null`() {
        val state = AppState(profile = sampleProfile)

        val result = reducer(ProfileAction.Logout, state)

        assertNull(result.profile)
    }

    @Test
    fun `ProfileLoaded action sets profile on state`() {
        val state = AppState(profile = null)

        val result = reducer(ProfileAction.ProfileLoaded(sampleProfile), state)

        assertEquals(sampleProfile, result.profile)
    }

    @Test
    fun `ProfileLoaded action overwrites existing profile`() {
        val updatedProfile = Profile("Jane Doe", "+48999999999", "jane@example.com")
        val state = AppState(profile = sampleProfile)

        val result = reducer(ProfileAction.ProfileLoaded(updatedProfile), state)

        assertEquals(updatedProfile, result.profile)
    }

    @Test
    fun `Unrelated action returns state unchanged`() {
        val state = AppState(profile = sampleProfile)
        val unrelatedAction = object : Action {}

        val result = reducer(unrelatedAction, state)

        assertEquals(state, result)
    }

    @Test
    fun `LoadProfile action returns state unchanged`() {
        val state = AppState(profile = sampleProfile)

        val result = reducer(ProfileAction.LoadProfile, state)

        assertEquals(state, result)
    }

    @Test
    fun `Login action returns state unchanged`() {
        val state = AppState(profile = sampleProfile)

        val result = reducer(ProfileAction.Login, state)

        assertEquals(state, result)
    }
}
