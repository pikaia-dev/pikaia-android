package dev.pikaia.android.feature.login.redux

import dev.pikaia.android.feature.login.data.AuthenticatedLogin
import dev.pikaia.android.feature.login.data.LoginOrganization
import dev.pikaia.android.state.AppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginActionReducerTest {

    private val reducer = LoginActionReducer()

    private val organization = LoginOrganization(id = "org-1", name = "Acme", slug = "acme")

    @Test
    fun `SubmitEmail stores the email and marks submitting`() {
        val state = reducer(LoginAction.SubmitEmail("jane@example.com"), AppState())

        assertEquals("jane@example.com", state.login.email)
        assertTrue(state.login.isSubmitting)
        assertEquals(LoginPhase.EMAIL, state.login.phase)
    }

    @Test
    fun `MagicLinkSent advances to the token phase`() {
        val submitting = AppState(login = LoginState(isSubmitting = true))

        val state = reducer(LoginAction.MagicLinkSent, submitting)

        assertEquals(LoginPhase.TOKEN, state.login.phase)
        assertFalse(state.login.isSubmitting)
    }

    @Test
    fun `Authenticated advances to organization phase with discovered organizations`() {
        val state = reducer(
            LoginAction.Authenticated(
                AuthenticatedLogin(
                    intermediateSessionToken = "ist-1",
                    organizations = listOf(organization)
                )
            ),
            AppState(login = LoginState(phase = LoginPhase.TOKEN, isSubmitting = true))
        )

        assertEquals(LoginPhase.ORGANIZATION, state.login.phase)
        assertEquals("ist-1", state.login.intermediateSessionToken)
        assertEquals(listOf(organization), state.login.organizations)
        assertFalse(state.login.isSubmitting)
    }

    @Test
    fun `SubmissionFailed clears the submitting flag only`() {
        val submitting = AppState(
            login = LoginState(phase = LoginPhase.TOKEN, email = "jane@example.com", isSubmitting = true)
        )

        val state = reducer(LoginAction.SubmissionFailed, submitting)

        assertFalse(state.login.isSubmitting)
        assertEquals(LoginPhase.TOKEN, state.login.phase)
        assertEquals("jane@example.com", state.login.email)
    }

    @Test
    fun `LoggedIn resets the login state`() {
        val loggedIn = AppState(
            login = LoginState(
                phase = LoginPhase.ORGANIZATION,
                intermediateSessionToken = "ist-1",
                organizations = listOf(organization)
            )
        )

        val state = reducer(LoginAction.LoggedIn, loggedIn)

        assertEquals(LoginState(), state.login)
    }

    @Test
    fun `unrelated actions leave the state untouched`() {
        val initial = AppState(login = LoginState(phase = LoginPhase.TOKEN))

        val state = reducer(object : dev.pikaia.android.lib.store.Action {}, initial)

        assertEquals(initial, state)
    }
}
