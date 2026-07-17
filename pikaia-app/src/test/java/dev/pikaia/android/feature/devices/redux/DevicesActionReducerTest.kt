package dev.pikaia.android.feature.devices.redux

import dev.pikaia.android.feature.devices.data.Device
import dev.pikaia.android.feature.devices.data.DeviceLink
import dev.pikaia.android.state.AppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicesActionReducerTest {

    private val reducer = DevicesActionReducer()

    private val device = Device(id = 7, name = "Pixel 9 Pro", platform = "android", createdAt = "2026-07-01")

    @Test
    fun `LoadDevices marks busy`() {
        val state = reducer(DevicesAction.LoadDevices, AppState())

        assertTrue(state.devices.isBusy)
    }

    @Test
    fun `DevicesLoaded stores devices and clears busy`() {
        val busy = AppState(devices = DevicesState(isBusy = true))

        val state = reducer(DevicesAction.DevicesLoaded(listOf(device)), busy)

        assertEquals(listOf(device), state.devices.devices)
        assertFalse(state.devices.isBusy)
    }

    @Test
    fun `LinkInitiated stores the link`() {
        val link = DeviceLink(qrUrl = "app://device/link?token=x", expiresInSeconds = 300)

        val state = reducer(DevicesAction.LinkInitiated(link), AppState())

        assertEquals(link, state.devices.link)
        assertFalse(state.devices.isBusy)
    }

    @Test
    fun `DeviceRevoked removes the device from the list`() {
        val other = device.copy(id = 8, name = "Tablet")
        val loaded = AppState(devices = DevicesState(devices = listOf(device, other)))

        val state = reducer(DevicesAction.DeviceRevoked(7), loaded)

        assertEquals(listOf(other), state.devices.devices)
    }

    @Test
    fun `OperationFailed clears busy and keeps data`() {
        val busy = AppState(devices = DevicesState(devices = listOf(device), isBusy = true))

        val state = reducer(DevicesAction.OperationFailed, busy)

        assertFalse(state.devices.isBusy)
        assertEquals(listOf(device), state.devices.devices)
    }

    @Test
    fun `Reset restores the initial state`() {
        val populated = AppState(
            devices = DevicesState(
                devices = listOf(device),
                link = DeviceLink("url", 300),
                isBusy = true
            )
        )

        val state = reducer(DevicesAction.Reset, populated)

        assertEquals(DevicesState(), state.devices)
    }
}
