package dev.pikaia.android.feature.devices.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.pikaia.android.R
import dev.pikaia.android.feature.components.toolbar.Toolbar
import dev.pikaia.android.feature.devices.data.Device
import dev.pikaia.android.feature.devices.redux.DevicesAction
import dev.pikaia.android.feature.devices.redux.DevicesState
import dev.pikaia.android.theme.normal18
import dev.pikaia.android.theme.semiBold16
import dev.pikaia.android.theme.spacings
import org.koin.androidx.compose.koinViewModel

@Composable
fun DevicesScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: DevicesViewModel = koinViewModel()
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()

    DevicesScreenContent(
        state = state,
        actionHandler = viewModel::handleAction,
        modifier = modifier
    )
}

@Composable
private fun DevicesScreenContent(
    state: DevicesState,
    actionHandler: (DevicesAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = { Toolbar(title = stringResource(R.string.devices_screen_title)) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = MaterialTheme.spacings.d16)
        ) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                if (state.devices.isEmpty()) {
                    item {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = MaterialTheme.spacings.d16),
                            text = stringResource(R.string.devices_empty_hint),
                            style = MaterialTheme.typography.normal18
                        )
                    }
                }
                items(state.devices, key = { it.id }) { device ->
                    DeviceRow(
                        device = device,
                        isBusy = state.isBusy,
                        actionHandler = actionHandler
                    )
                }
                state.link?.let { link ->
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = MaterialTheme.spacings.d16)
                        ) {
                            Column(modifier = Modifier.padding(MaterialTheme.spacings.d16)) {
                                Text(
                                    text = stringResource(
                                        R.string.devices_link_hint,
                                        link.expiresInSeconds
                                    ),
                                    style = MaterialTheme.typography.normal18
                                )
                                Text(
                                    modifier = Modifier.padding(top = MaterialTheme.spacings.d8),
                                    text = link.qrUrl,
                                    style = MaterialTheme.typography.semiBold16
                                )
                            }
                        }
                    }
                }
            }

            Button(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = MaterialTheme.spacings.d8),
                enabled = !state.isBusy,
                onClick = { actionHandler(DevicesAction.InitiateLink) }
            ) {
                Text(stringResource(R.string.devices_link_btn))
            }
            Button(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = MaterialTheme.spacings.d32),
                enabled = !state.isBusy,
                onClick = { actionHandler(DevicesAction.RefreshSession) }
            ) {
                Text(stringResource(R.string.devices_refresh_session_btn))
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: Device,
    isBusy: Boolean,
    actionHandler: (DevicesAction) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.spacings.d8),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = device.name, style = MaterialTheme.typography.semiBold16)
            Text(
                text = "${device.platform} · ${device.createdAt}",
                style = MaterialTheme.typography.normal18
            )
        }
        Spacer(modifier = Modifier.padding(horizontal = MaterialTheme.spacings.d8))
        TextButton(
            enabled = !isBusy,
            onClick = { actionHandler(DevicesAction.RevokeDevice(device.id)) }
        ) {
            Text(stringResource(R.string.devices_revoke_btn))
        }
    }
}
