package dev.pikaia.android.feature.di

import dev.pikaia.android.feature.devices.di.devicesModule
import dev.pikaia.android.feature.home.di.homeModule
import dev.pikaia.android.feature.login.di.loginModule
import dev.pikaia.android.feature.profile.di.profileModule

val featureModules = listOf(
    homeModule,
    profileModule,
    loginModule,
    devicesModule
)
