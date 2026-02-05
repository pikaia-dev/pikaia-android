package dev.pikaia.android.lib.store

import dev.pikaia.android.lib.store.Action
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

abstract class FeatureStoreProxy<AppState, FeatureState, SupportedAction: Action>(
    private val store: Store<AppState>
) {
    abstract fun getFeatureStateProjection(appState: AppState): FeatureState

    abstract fun isEffectRelevant(effect: NavigationEffect): Boolean

    fun getFeatureStateFlow() = store.appState.map { getFeatureStateProjection(it) }

    suspend fun dispatch(supportedAction: SupportedAction) = store.dispatch(supportedAction)

    fun getFeatureNavigationEffects() = store.effect.filter { isEffectRelevant(it) }

    open val clearStateAction: SupportedAction? = null
}