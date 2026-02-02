package dev.pikaia.android.lib.store

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface Store<State> {
    val appState: StateFlow<State>
    val effect: SharedFlow<NavigationEffect>

    suspend fun dispatch(action: Action)
    fun addReducer(reducer: Reducer<State>)
    fun addSideEffect(sideEffect: SideEffect)
    suspend fun postNavigationEffect(effect: NavigationEffect)
}