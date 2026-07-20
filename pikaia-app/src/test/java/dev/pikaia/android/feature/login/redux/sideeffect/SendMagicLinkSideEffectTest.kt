package dev.pikaia.android.feature.login.redux.sideeffect

import dev.pikaia.android.feature.login.domain.SendMagicLink
import dev.pikaia.android.feature.login.redux.LoginAction
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

class SendMagicLinkSideEffectTest {

    private lateinit var store: Store<AppState>
    private lateinit var sendMagicLink: SendMagicLink
    private lateinit var sideEffect: SendMagicLinkSideEffect

    @Before
    fun setUp() {
        store = mockk(relaxed = true)
        sendMagicLink = mockk()
        sideEffect = SendMagicLinkSideEffect(store, sendMagicLink)
    }

    @Test
    fun `unrelated action does not send anything`() = runTest {
        sideEffect.invoke(object : Action {})

        verify { sendMagicLink wasNot Called }
    }

    @Test
    fun `SubmitEmail sends the link and dispatches MagicLinkSent`() = runTest {
        every { sendMagicLink.invoke("jane@example.com") } returns flowOf(Unit)

        sideEffect.invoke(LoginAction.SubmitEmail("jane@example.com"))

        coVerify(exactly = 1) { store.dispatch(LoginAction.MagicLinkSent) }
        coVerify(exactly = 0) { store.postNavigationEffect(any()) }
    }

    @Test
    fun `failure posts an error and unsticks the submitting flag`() = runTest {
        val exception = RuntimeException("boom")
        every { sendMagicLink.invoke(any()) } returns flow { throw exception }

        sideEffect.invoke(LoginAction.SubmitEmail("jane@example.com"))

        coVerify(exactly = 1) { store.postNavigationEffect(ErrorNavigationEffect(exception)) }
        coVerify(exactly = 1) { store.dispatch(LoginAction.SubmissionFailed) }
        coVerify(exactly = 0) { store.dispatch(LoginAction.MagicLinkSent) }
    }
}
