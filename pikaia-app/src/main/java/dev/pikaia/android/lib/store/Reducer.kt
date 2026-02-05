package dev.pikaia.android.lib.store

interface Reducer<State> {
    operator fun invoke(action: Action, state: State): State
}
