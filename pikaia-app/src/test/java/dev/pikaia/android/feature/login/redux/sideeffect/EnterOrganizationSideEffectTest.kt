package dev.pikaia.android.feature.login.redux.sideeffect

import dev.pikaia.android.feature.login.domain.CreateOrganization
import dev.pikaia.android.feature.login.domain.EnterOrganization
import dev.pikaia.android.feature.login.redux.LoginAction
import dev.pikaia.android.feature.login.redux.LoginNavigationEffect
import dev.pikaia.android.feature.login.redux.LoginState
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class EnterOrganizationSideEffectTest {

    private lateinit var store: Store<AppState>
    private lateinit var enterOrganization: EnterOrganization
    private lateinit var createOrganization: CreateOrganization
    private lateinit var sideEffect: EnterOrganizationSideEffect

    private val appState = AppState(
        login = LoginState(intermediateSessionToken = "ist-1")
    )

    @Before
    fun setUp() {
        store = mockk(relaxed = true)
        every { store.appState } returns MutableStateFlow(appState)
        enterOrganization = mockk()
        createOrganization = mockk()
        sideEffect = EnterOrganizationSideEffect(store, enterOrganization, createOrganization)
    }

    @Test
    fun `unrelated action does nothing`() = runTest {
        sideEffect.invoke(object : Action {})

        verify { enterOrganization wasNot Called }
        verify { createOrganization wasNot Called }
    }

    @Test
    fun `SelectOrganization exchanges with the stored intermediate token`() = runTest {
        every {
            enterOrganization.invoke(
                EnterOrganization.Params(intermediateSessionToken = "ist-1", organizationId = "org-1")
            )
        } returns flowOf(Unit)

        sideEffect.invoke(LoginAction.SelectOrganization("org-1"))

        coVerify(exactly = 1) { store.dispatch(LoginAction.LoggedIn) }
        coVerify(exactly = 1) { store.dispatch(ProfileAction.LoadProfile) }
        coVerify(exactly = 1) { store.postNavigationEffect(LoginNavigationEffect.LoginComplete) }
    }

    @Test
    fun `SubmitNewOrganization creates with the stored intermediate token`() = runTest {
        every {
            createOrganization.invoke(
                CreateOrganization.Params(
                    intermediateSessionToken = "ist-1",
                    name = "Acme",
                    slug = "acme"
                )
            )
        } returns flowOf(Unit)

        sideEffect.invoke(LoginAction.SubmitNewOrganization(name = "Acme", slug = "acme"))

        coVerify(exactly = 1) { store.dispatch(LoginAction.LoggedIn) }
        coVerify(exactly = 1) { store.postNavigationEffect(LoginNavigationEffect.LoginComplete) }
    }

    @Test
    fun `failure posts an error and does not complete the login`() = runTest {
        val exception = RuntimeException("slug taken")
        every { enterOrganization.invoke(any()) } returns flow { throw exception }

        sideEffect.invoke(LoginAction.SelectOrganization("org-1"))

        coVerify(exactly = 1) { store.postNavigationEffect(ErrorNavigationEffect(exception)) }
        coVerify(exactly = 1) { store.dispatch(LoginAction.SubmissionFailed) }
        coVerify(exactly = 0) { store.dispatch(LoginAction.LoggedIn) }
        coVerify(exactly = 0) { store.postNavigationEffect(LoginNavigationEffect.LoginComplete) }
    }
}
