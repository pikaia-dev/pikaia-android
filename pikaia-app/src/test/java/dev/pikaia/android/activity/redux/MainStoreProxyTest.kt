package dev.pikaia.android.activity.redux

import app.cash.turbine.test
import dev.pikaia.android.lib.snackbar.ErrorNavigationEffect
import dev.pikaia.android.lib.snackbar.SnackbarNavigationEffect
import dev.pikaia.android.lib.store.NavigationEffect
import dev.pikaia.android.lib.store.Store
import dev.pikaia.android.state.AppState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MainStoreProxyTest {

    private lateinit var store: Store<AppState>
    private lateinit var proxy: MainStoreProxy

    @Before
    fun setUp() {
        store = mockk(relaxed = true)
        every { store.appState } returns MutableStateFlow(AppState())
        every { store.effect } returns MutableSharedFlow()
        proxy = MainStoreProxy(store)
    }

    @Test
    fun `getFeatureStateProjection maps isLoading from AppState`() {
        val appState = AppState(isLoading = true)

        val featureState = proxy.getFeatureStateProjection(appState)

        assertEquals(MainState(isLoading = true), featureState)
    }

    @Test
    fun `getFeatureStateProjection returns isLoading false by default`() {
        val appState = AppState()

        val featureState = proxy.getFeatureStateProjection(appState)

        assertEquals(MainState(isLoading = false), featureState)
    }

    @Test
    fun `isEffectRelevant returns true for SnackbarNavigationEffect`() {
        val effect = ErrorNavigationEffect(RuntimeException("error"))

        assertTrue(proxy.isEffectRelevant(effect))
    }

    @Test
    fun `isEffectRelevant returns false for non-SnackbarNavigationEffect`() {
        val effect = mockk<NavigationEffect>()

        assertFalse(proxy.isEffectRelevant(effect))
    }

    @Test
    fun `getFeatureStateFlow emits updated MainState when store state changes`() = runTest {
        val stateFlow = MutableStateFlow(AppState(isLoading = false))
        every { store.appState } returns stateFlow
        proxy = MainStoreProxy(store)

        proxy.getFeatureStateFlow().test {
            assertEquals(MainState(isLoading = false), awaitItem())

            stateFlow.value = AppState(isLoading = true)
            assertEquals(MainState(isLoading = true), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getFeatureNavigationEffects emits only SnackbarNavigationEffect`() = runTest {
        val effectFlow = MutableSharedFlow<NavigationEffect>()
        every { store.effect } returns effectFlow
        proxy = MainStoreProxy(store)

        proxy.getFeatureNavigationEffects().test {
            effectFlow.emit(mockk<NavigationEffect>())
            expectNoEvents()

            val snackbarEffect = ErrorNavigationEffect(RuntimeException("test"))
            effectFlow.emit(snackbarEffect)
            assertEquals(snackbarEffect, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
