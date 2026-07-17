package dev.pikaia.android.feature.devices.data.repository

import dev.pikaia.android.feature.devices.data.Device
import dev.pikaia.android.feature.devices.data.DeviceLink
import dev.pikaia.android.sdk.auth.api.DevicesAPI
import dev.pikaia.android.sdk.auth.data.devices.DeviceSessionRefreshRequest
import dev.pikaia.android.sdk.core.auth.AuthSession
import dev.pikaia.android.sdk.core.auth.TokenStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface DevicesRepository {
    fun loadDevices(): Flow<List<Device>>
    fun initiateLink(): Flow<DeviceLink>
    fun revokeDevice(deviceId: Long): Flow<Unit>
    fun refreshSession(): Flow<Unit>
}

class DevicesRepositoryImpl(
    private val devicesAPI: DevicesAPI,
    private val tokenStore: TokenStore
) : DevicesRepository {

    override fun loadDevices() = flow {
        val response = devicesAPI.listDevices().getOrThrow()
        emit(
            response.devices.map {
                Device(
                    id = it.id,
                    name = it.name,
                    platform = it.platform,
                    createdAt = it.createdAt.toString()
                )
            }
        )
    }

    override fun initiateLink() = flow {
        val response = devicesAPI.initiateLink().getOrThrow()
        emit(
            DeviceLink(
                qrUrl = response.qrUrl,
                expiresInSeconds = response.expiresInSeconds
            )
        )
    }

    override fun revokeDevice(deviceId: Long) = flow {
        devicesAPI.revokeDevice(deviceId).getOrThrow()
        emit(Unit)
    }

    override fun refreshSession() = flow {
        val session = tokenStore.getSession()
        val deviceUuid = session?.deviceUuid
            ?: throw IllegalStateException("Current session is not device-linked")

        val response = devicesAPI
            .refreshSession(DeviceSessionRefreshRequest(deviceUuid = deviceUuid))
            .getOrThrow()

        tokenStore.setSession(
            AuthSession(
                sessionJwt = response.sessionJwt,
                sessionToken = response.sessionToken,
                sessionExpiresAt = response.sessionExpiresAt,
                deviceUuid = deviceUuid
            )
        )
        emit(Unit)
    }
}
