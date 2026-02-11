package dev.pikaia.android.feature.profile.redux.sideeffect

import dev.pikaia.android.feature.profile.data.Profile
import dev.pikaia.android.feature.profile.domain.LoadProfile
import dev.pikaia.android.feature.profile.redux.ProfileAction
import dev.pikaia.android.lib.snackbar.ErrorNavigationEffect
import dev.pikaia.android.lib.store.Action
import dev.pikaia.android.lib.store.Store
import dev.pikaia.android.state.AppState
import io.mockk.Called
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class LoadProfileSideEffectTest {

    private lateinit var store: Store<AppState>
    private lateinit var loadProfile: LoadProfile
    private lateinit var sideEffect: LoadProfileSideEffect

    private val sampleProfile = Profile(
        name = "John Doe",
        phone = "+48123456789",
        email = "john@example.com"
    )

    @Before
    fun setUp() {
        store = mockk(relaxed = true)
        loadProfile = mockk()
        sideEffect = LoadProfileSideEffect(store, loadProfile)
    }

    @Test
    fun `non-LoadProfile action does not invoke loadProfile`() = runTest {
        val unrelatedAction = object : Action {}

        sideEffect.invoke(unrelatedAction)

        verify { loadProfile wasNot Called }
        coVerify(exactly = 0) { store.dispatch(any()) }
        coVerify(exactly = 0) { store.postNavigationEffect(any()) }
    }

    @Test
    fun `Logout action does not invoke loadProfile`() = runTest {
        sideEffect.invoke(ProfileAction.Logout)

        verify { loadProfile wasNot Called }
    }

    @Test
    fun `Login action does not invoke loadProfile`() = runTest {
        sideEffect.invoke(ProfileAction.Login)

        verify { loadProfile wasNot Called }
    }

    @Test
    fun `LoadProfile action dispatches ProfileLoaded with result from repository`() = runTest {
        every { loadProfile.invoke(Unit) } returns flowOf(sampleProfile)

        sideEffect.invoke(ProfileAction.LoadProfile)

        coVerify(exactly = 1) { store.dispatch(ProfileAction.ProfileLoaded(sampleProfile)) }
        coVerify(exactly = 0) { store.postNavigationEffect(any()) }
    }

    @Test
    fun `LoadProfile action dispatches ProfileLoaded with null profile`() = runTest {
        every { loadProfile.invoke(Unit) } returns flowOf(null)

        sideEffect.invoke(ProfileAction.LoadProfile)

        coVerify(exactly = 1) { store.dispatch(ProfileAction.ProfileLoaded(null)) }
    }

    @Test
    fun `LoadProfile action posts ErrorNavigationEffect when flow throws`() = runTest {
        val exception = RuntimeException("Network error")
        every { loadProfile.invoke(Unit) } returns flow { throw exception }

        sideEffect.invoke(ProfileAction.LoadProfile)

        coVerify(exactly = 0) { store.dispatch(any()) }
        coVerify(exactly = 1) { store.postNavigationEffect(ErrorNavigationEffect(exception)) }
    }

    @Test
    fun `LoadProfile action dispatches multiple ProfileLoaded for multiple emissions`() = runTest {
        val profile1 = sampleProfile
        val profile2 = sampleProfile.copy(name = "Jane Doe")
        every { loadProfile.invoke(Unit) } returns flowOf(profile1, profile2)

        sideEffect.invoke(ProfileAction.LoadProfile)

        coVerify(exactly = 1) { store.dispatch(ProfileAction.ProfileLoaded(profile1)) }
        coVerify(exactly = 1) { store.dispatch(ProfileAction.ProfileLoaded(profile2)) }
    }
}
