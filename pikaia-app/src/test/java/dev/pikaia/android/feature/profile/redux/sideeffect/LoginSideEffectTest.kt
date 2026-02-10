package dev.pikaia.android.feature.profile.redux.sideeffect

import dev.pikaia.android.feature.profile.redux.ProfileAction
import dev.pikaia.android.feature.profile.redux.ProfileNavigationEffect
import dev.pikaia.android.lib.store.Action
import dev.pikaia.android.lib.store.Store
import dev.pikaia.android.state.AppState
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class LoginSideEffectTest {

    private lateinit var store: Store<AppState>
    private lateinit var sideEffect: LoginSideEffect

    @Before
    fun setUp() {
        store = mockk(relaxed = true)
        sideEffect = LoginSideEffect(store)
    }

    @Test
    fun `non-Login action does not post any navigation effect`() = runTest {
        val unrelatedAction = object : Action {}

        sideEffect.invoke(unrelatedAction)

        coVerify(exactly = 0) { store.postNavigationEffect(any()) }
    }

    @Test
    fun `LoadProfile action does not post any navigation effect`() = runTest {
        sideEffect.invoke(ProfileAction.LoadProfile)

        coVerify(exactly = 0) { store.postNavigationEffect(any()) }
    }

    @Test
    fun `Logout action does not post any navigation effect`() = runTest {
        sideEffect.invoke(ProfileAction.Logout)

        coVerify(exactly = 0) { store.postNavigationEffect(any()) }
    }

    @Test
    fun `Login action posts GoToLogin navigation effect`() = runTest {
        sideEffect.invoke(ProfileAction.Login)

        coVerify(exactly = 1) { store.postNavigationEffect(ProfileNavigationEffect.GoToLogin) }
    }

    @Test
    fun `Login action does not dispatch any action to store`() = runTest {
        sideEffect.invoke(ProfileAction.Login)

        coVerify(exactly = 0) { store.dispatch(any()) }
    }
}
