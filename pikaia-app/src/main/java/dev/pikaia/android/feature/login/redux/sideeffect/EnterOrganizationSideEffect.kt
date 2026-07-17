package dev.pikaia.android.feature.login.redux.sideeffect

import dev.pikaia.android.feature.login.domain.CreateOrganization
import dev.pikaia.android.feature.login.domain.EnterOrganization
import dev.pikaia.android.feature.login.redux.LoginAction
import dev.pikaia.android.feature.login.redux.LoginNavigationEffect
import dev.pikaia.android.feature.profile.redux.ProfileAction
import dev.pikaia.android.lib.snackbar.ErrorNavigationEffect
import dev.pikaia.android.lib.store.Action
import dev.pikaia.android.lib.store.SideEffect
import dev.pikaia.android.lib.store.Store
import dev.pikaia.android.state.AppState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * Finishes the login flow: exchanges the intermediate session token for a full session
 * (entering an existing organization or creating a new one), then reloads the profile.
 */
class EnterOrganizationSideEffect(
    private val store: Store<AppState>,
    private val enterOrganization: EnterOrganization,
    private val createOrganization: CreateOrganization
) : SideEffect {

    override suspend fun invoke(action: Action) {
        val intermediateSessionToken = store.appState.value.login.intermediateSessionToken

        val sessionFlow: Flow<Unit> = when (action) {
            is LoginAction.SelectOrganization -> enterOrganization(
                EnterOrganization.Params(
                    intermediateSessionToken = intermediateSessionToken,
                    organizationId = action.organizationId
                )
            )

            is LoginAction.SubmitNewOrganization -> createOrganization(
                CreateOrganization.Params(
                    intermediateSessionToken = intermediateSessionToken,
                    name = action.name,
                    slug = action.slug
                )
            )

            else -> return
        }

        sessionFlow
            .catch {
                store.postNavigationEffect(ErrorNavigationEffect(it))
                store.dispatch(LoginAction.SubmissionFailed)
            }
            .collect {
                store.dispatch(LoginAction.LoggedIn)
                store.dispatch(ProfileAction.LoadProfile)
                store.postNavigationEffect(LoginNavigationEffect.LoginComplete)
            }
    }
}
