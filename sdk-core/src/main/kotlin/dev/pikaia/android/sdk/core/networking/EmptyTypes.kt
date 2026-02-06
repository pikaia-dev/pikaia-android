package dev.pikaia.android.sdk.core.networking

import kotlinx.serialization.Serializable

/**
 * Represents an empty request body for endpoints that don't require a body.
 */
object EmptyBody

/**
 * Represents an empty response for endpoints that return no content.
 */
@Serializable
object EmptyResponse
