package dev.pikaia.android.lib.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.pikaia.android.lib.dispatcher.DefaultDispatcherProvider
import dev.pikaia.android.lib.dispatcher.DispatcherProvider
import dev.pikaia.android.lib.store.Action
import dev.pikaia.android.lib.store.FeatureStoreProxy
import dev.pikaia.android.state.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

abstract class BaseViewModel<FeatureState, SupportedAction : Action>(
    private val storeProxy: FeatureStoreProxy<AppState, FeatureState, SupportedAction>,
    initialState: FeatureState,
    dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    val viewState: StateFlow<FeatureState> = storeProxy.getFeatureStateFlow()
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            initialState
        )

    val effect = storeProxy.getFeatureNavigationEffects()

    private val cleanupScope = CoroutineScope(dispatcherProvider.io)

    fun handleAction(action: SupportedAction) {
        viewModelScope.launch { storeProxy.dispatch(action) }
    }

    override fun onCleared() {
        storeProxy.clearStateAction?.let { action ->
            cleanupScope.launch {
                storeProxy.dispatch(action)
            }
        }
        super.onCleared()
    }
}