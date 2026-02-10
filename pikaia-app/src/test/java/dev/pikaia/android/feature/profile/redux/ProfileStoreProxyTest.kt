package dev.pikaia.android.feature.profile.redux

import app.cash.turbine.test
import dev.pikaia.android.feature.profile.data.Profile
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProfileStoreProxyTest {

    private lateinit var store: Store<AppState>
    private lateinit var proxy: ProfileStoreProxy

    private val sampleProfile = Profile(
        name = "John Doe",
        phone = "+48123456789",
        email = "john@example.com"
    )

    @Before
    fun setUp() {
        store = mockk(relaxed = true)
        every { store.appState } returns MutableStateFlow(AppState())
        every { store.effect } returns MutableSharedFlow()
        proxy = ProfileStoreProxy(store)
    }

    @Test
    fun `getFeatureStateProjection maps profile from AppState`() {
        val appState = AppState(profile = sampleProfile)

        val featureState = proxy.getFeatureStateProjection(appState)

        assertEquals(ProfileState(profile = sampleProfile), featureState)
    }

    @Test
    fun `getFeatureStateProjection returns null profile when AppState has no profile`() {
        val appState = AppState(profile = null)

        val featureState = proxy.getFeatureStateProjection(appState)

        assertNull(featureState.profile)
    }

    @Test
    fun `isEffectRelevant returns true for ProfileNavigationEffect`() {
        val effect: NavigationEffect = ProfileNavigationEffect.GoToLogin

        assertTrue(proxy.isEffectRelevant(effect))
    }

    @Test
    fun `isEffectRelevant returns false for non-ProfileNavigationEffect`() {
        val effect = mockk<NavigationEffect>()

        assertFalse(proxy.isEffectRelevant(effect))
    }

    @Test
    fun `getFeatureStateFlow emits updated ProfileState when store state changes`() = runTest {
        val stateFlow = MutableStateFlow(AppState(profile = null))
        every { store.appState } returns stateFlow
        proxy = ProfileStoreProxy(store)

        proxy.getFeatureStateFlow().test {
            assertEquals(ProfileState(profile = null), awaitItem())

            stateFlow.value = AppState(profile = sampleProfile)
            assertEquals(ProfileState(profile = sampleProfile), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getFeatureNavigationEffects emits only ProfileNavigationEffect`() = runTest {
        val effectFlow = MutableSharedFlow<NavigationEffect>()
        every { store.effect } returns effectFlow
        proxy = ProfileStoreProxy(store)

        proxy.getFeatureNavigationEffects().test {
            effectFlow.emit(mockk<NavigationEffect>())
            expectNoEvents()

            effectFlow.emit(ProfileNavigationEffect.GoToLogin)
            assertEquals(ProfileNavigationEffect.GoToLogin, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
