package dev.pikaia.android.sdk.auth.data.passkeys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A registered passkey.
 *
 * @param id Passkey ID (use for deletion)
 * @param name User-friendly passkey name
 * @param createdAt Creation timestamp (ISO-8601 string, as sent by the backend)
 * @param lastUsedAt Last authentication timestamp, if ever used
 * @param backupEligible Whether the credential is eligible for backup (synced passkey)
 * @param backupState Whether the credential is currently backed up
 */
@Serializable
data class PasskeyInfo(
    val id: Long,
    val name: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("last_used_at")
    val lastUsedAt: String? = null,
    @SerialName("backup_eligible")
    val backupEligible: Boolean,
    @SerialName("backup_state")
    val backupState: Boolean
)

/**
 * The authenticated user's registered passkeys.
 */
@Serializable
data class PasskeyListResponse(
    val passkeys: List<PasskeyInfo>
)

/**
 * Response after deleting a passkey.
 */
@Serializable
data class PasskeyDeleteResponse(
    val success: Boolean = true,
    val message: String = ""
)
