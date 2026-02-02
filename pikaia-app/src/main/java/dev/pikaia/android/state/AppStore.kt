package dev.pikaia.android.state

import dev.pikaia.android.lib.store.Action
import dev.pikaia.android.lib.store.NavigationEffect
import dev.pikaia.android.lib.store.Reducer
import dev.pikaia.android.lib.store.SideEffect
import dev.pikaia.android.lib.store.Store
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

class AppStore(
    initialReducers: List<Reducer<AppState>>,
    initialState: AppState = AppState()
) : Store<AppState> {

    private val _appState: MutableStateFlow<AppState> = MutableStateFlow(initialState)
    override val appState: StateFlow<AppState> = _appState

    private val _effect = MutableSharedFlow<NavigationEffect>()
    override val effect = _effect.asSharedFlow()

    private val reducers = mutableListOf<Reducer<AppState>>()
    private val sideEffects = mutableListOf<SideEffect>()

    init {
        initialReducers.forEach { addReducer(it) }
    }

    override suspend fun dispatch(action: Action) {
        var state = appState.value

        reducers.toList().forEach { reducer -> state = reducer(action, state) }
        _appState.value = state

        sideEffects.toList().forEach { sideEffect -> sideEffect(action) }
    }

    override fun addReducer(reducer: Reducer<AppState>) {
        reducers.add(reducer)
    }

    override fun addSideEffect(sideEffect: SideEffect) {
        sideEffects.add(sideEffect)
    }

    override suspend fun postNavigationEffect(effect: NavigationEffect) {
        _effect.emit(effect)
    }
}