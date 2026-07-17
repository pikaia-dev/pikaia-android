package dev.pikaia.android.feature.devices.domain

import dev.pikaia.android.feature.devices.data.Device
import dev.pikaia.android.feature.devices.data.DeviceLink
import dev.pikaia.android.feature.devices.data.repository.DevicesRepository
import dev.pikaia.android.lib.usecase.FlowUseCase
import kotlinx.coroutines.flow.Flow

class LoadDevices(
    private val devicesRepository: DevicesRepository
) : FlowUseCase<Unit, List<Device>> {
    override fun invoke(params: Unit): Flow<List<Device>> = devicesRepository.loadDevices()
}

class InitiateDeviceLink(
    private val devicesRepository: DevicesRepository
) : FlowUseCase<Unit, DeviceLink> {
    override fun invoke(params: Unit): Flow<DeviceLink> = devicesRepository.initiateLink()
}

class RevokeDevice(
    private val devicesRepository: DevicesRepository
) : FlowUseCase<Long, Unit> {
    override fun invoke(params: Long): Flow<Unit> = devicesRepository.revokeDevice(params)
}

class RefreshDeviceSession(
    private val devicesRepository: DevicesRepository
) : FlowUseCase<Unit, Unit> {
    override fun invoke(params: Unit): Flow<Unit> = devicesRepository.refreshSession()
}
