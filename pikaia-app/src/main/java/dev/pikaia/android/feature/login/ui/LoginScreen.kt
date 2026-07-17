package dev.pikaia.android.feature.login.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.pikaia.android.R
import dev.pikaia.android.feature.components.toolbar.Toolbar
import dev.pikaia.android.feature.login.redux.LoginAction
import dev.pikaia.android.feature.login.redux.LoginNavigationEffect
import dev.pikaia.android.feature.login.redux.LoginPhase
import dev.pikaia.android.feature.login.redux.LoginState
import dev.pikaia.android.lib.base.ObserveEffects
import dev.pikaia.android.theme.normal18
import dev.pikaia.android.theme.spacings
import org.koin.androidx.compose.koinViewModel

@Composable
fun LoginScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = koinViewModel()
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()

    ObserveEffects(viewModel.effect) { effect ->
        when (effect) {
            LoginNavigationEffect.LoginComplete -> navController.popBackStack()
        }
    }

    LoginScreenContent(
        state = state,
        actionHandler = viewModel::handleAction,
        modifier = modifier
    )
}

@Composable
private fun LoginScreenContent(
    state: LoginState,
    actionHandler: (LoginAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = { Toolbar(title = stringResource(R.string.login_screen_title)) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = MaterialTheme.spacings.d16),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state.phase) {
                LoginPhase.EMAIL -> EmailPhase(state, actionHandler)
                LoginPhase.TOKEN -> TokenPhase(state, actionHandler)
                LoginPhase.ORGANIZATION -> OrganizationPhase(state, actionHandler)
            }
        }
    }
}

@Composable
private fun EmailPhase(
    state: LoginState,
    actionHandler: (LoginAction) -> Unit
) {
    var email by rememberSaveable { mutableStateOf(state.email) }

    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.spacings.d16),
        value = email,
        onValueChange = { email = it },
        label = { Text(stringResource(R.string.login_email_label)) },
        singleLine = true
    )
    Button(
        enabled = !state.isSubmitting && email.isNotBlank(),
        onClick = { actionHandler(LoginAction.SubmitEmail(email)) }
    ) {
        Text(stringResource(R.string.login_send_link_btn))
    }
}

@Composable
private fun TokenPhase(
    state: LoginState,
    actionHandler: (LoginAction) -> Unit
) {
    var token by rememberSaveable { mutableStateOf("") }

    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MaterialTheme.spacings.d16),
        text = stringResource(R.string.login_token_hint),
        style = MaterialTheme.typography.normal18
    )
    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.spacings.d16),
        value = token,
        onValueChange = { token = it },
        label = { Text(stringResource(R.string.login_token_label)) },
        singleLine = true
    )
    Button(
        enabled = !state.isSubmitting && token.isNotBlank(),
        onClick = { actionHandler(LoginAction.SubmitToken(token)) }
    ) {
        Text(stringResource(R.string.login_verify_btn))
    }
}

@Composable
private fun OrganizationPhase(
    state: LoginState,
    actionHandler: (LoginAction) -> Unit
) {
    if (state.organizations.isNotEmpty()) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MaterialTheme.spacings.d16),
            text = stringResource(R.string.login_pick_org_hint),
            style = MaterialTheme.typography.normal18
        )
        state.organizations.forEach { organization ->
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = MaterialTheme.spacings.d8),
                enabled = !state.isSubmitting,
                onClick = { actionHandler(LoginAction.SelectOrganization(organization.id)) }
            ) {
                Text(organization.name)
            }
        }
    } else {
        var name by rememberSaveable { mutableStateOf("") }
        var slug by rememberSaveable { mutableStateOf("") }

        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MaterialTheme.spacings.d16),
            text = stringResource(R.string.login_create_org_hint),
            style = MaterialTheme.typography.normal18
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.login_org_name_label)) },
            singleLine = true
        )
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MaterialTheme.spacings.d16),
            value = slug,
            onValueChange = { slug = it },
            label = { Text(stringResource(R.string.login_org_slug_label)) },
            singleLine = true
        )
        Button(
            enabled = !state.isSubmitting && name.isNotBlank() && slug.isNotBlank(),
            onClick = { actionHandler(LoginAction.SubmitNewOrganization(name, slug)) }
        ) {
            Text(stringResource(R.string.login_create_org_btn))
        }
    }
}
