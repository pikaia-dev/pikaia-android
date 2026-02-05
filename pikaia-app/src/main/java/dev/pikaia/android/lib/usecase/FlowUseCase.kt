package dev.pikaia.android.lib.usecase

import kotlinx.coroutines.flow.Flow

interface FlowUseCase<Params, Result> {

    operator fun invoke(params: Params): Flow<Result>
}