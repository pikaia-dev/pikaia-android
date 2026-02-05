package dev.pikaia.android.lib.store

interface SideEffect {
    suspend operator fun invoke(action: Action)
}