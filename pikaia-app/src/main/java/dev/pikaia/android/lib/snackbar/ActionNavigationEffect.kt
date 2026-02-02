package dev.pikaia.android.lib.snackbar

class ActionNavigationEffect(
    val message: String,
    val actionLabel: String,
    val action: () -> Unit
) : SnackbarNavigationEffect
