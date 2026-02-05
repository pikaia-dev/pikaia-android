package dev.pikaia.android.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class Spacings(
    val d2: Dp,
    val d4: Dp,
    val d8: Dp,
    val d10: Dp,
    val d12: Dp,
    val d16: Dp,
    val d20: Dp,
    val d24: Dp,
    val d32: Dp,
    val d64: Dp,
    val d100: Dp
)

val defaultSpacings = Spacings(
    d2 = 2.dp,
    d4 = 4.dp,
    d8 = 8.dp,
    d10 = 10.dp,
    d12 = 12.dp,
    d16 = 16.dp,
    d20 = 20.dp,
    d24 = 24.dp,
    d32 = 32.dp,
    d64 = 64.dp,
    d100 = 100.dp
)

@Suppress("CompositionLocalAllowlist")
val LocalSpacings = staticCompositionLocalOf { defaultSpacings }
val MaterialTheme.spacings: Spacings
    @Composable
    get() = LocalSpacings.current
