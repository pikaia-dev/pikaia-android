package dev.pikaia.android.navigation.graphs

import kotlinx.serialization.Serializable

sealed interface Graphs {
    @Serializable
    data object MainGraph : Graphs
}