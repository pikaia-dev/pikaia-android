package dev.pikaia.android.feature.home.redux

import app.cash.turbine.test
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
import org.junit.Before
import org.junit.Test

class HomeStoreProxyTest {

    private lateinit var store: Store<AppState>
    private lateinit var proxy: HomeStoreProxy

    @Before
    fun setUp() {
        store = mockk(relaxed = true)
        every { store.appState } returns MutableStateFlow(AppState())
        every { store.effect } returns MutableSharedFlow()
        proxy = HomeStoreProxy(store)
    }

    @Test
    fun `getFeatureStateProjection maps homeMessage from AppState`() {
        val appState = AppState(homeMessage = "Hello test")

        val featureState = proxy.getFeatureStateProjection(appState)

        assertEquals(HomeState(message = "Hello test"), featureState)
    }

    @Test
    fun `getFeatureStateProjection uses default message when not overridden`() {
        val appState = AppState()

        val featureState = proxy.getFeatureStateProjection(appState)

        assertEquals(HomeState(message = "Sample home message"), featureState)
    }

    @Test
    fun `isEffectRelevant always returns false`() {
        val effect = mockk<NavigationEffect>()

        assertFalse(proxy.isEffectRelevant(effect))
    }

    @Test
    fun `getFeatureStateFlow emits mapped HomeState when store state changes`() = runTest {
        val stateFlow = MutableStateFlow(AppState(homeMessage = "initial"))
        every { store.appState } returns stateFlow
        proxy = HomeStoreProxy(store)

        proxy.getFeatureStateFlow().test {
            assertEquals(HomeState(message = "initial"), awaitItem())

            stateFlow.value = AppState(homeMessage = "updated")
            assertEquals(HomeState(message = "updated"), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getFeatureNavigationEffects emits nothing since isEffectRelevant is always false`() = runTest {
        val effectFlow = MutableSharedFlow<NavigationEffect>()
        every { store.effect } returns effectFlow
        proxy = HomeStoreProxy(store)

        proxy.getFeatureNavigationEffects().test {
            effectFlow.emit(mockk())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
