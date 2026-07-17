package dev.pikaia.android.sdk.auth.token

/**
 * Symmetric encryption for individual stored values.
 *
 * Production code uses [KeystoreValueCipher]; tests inject a fake so token-store logic
 * can be verified on the JVM.
 */
interface ValueCipher {
    /** Encrypt [plaintext], returning an opaque ciphertext blob. */
    fun encrypt(plaintext: ByteArray): ByteArray

    /**
     * Decrypt a blob produced by [encrypt].
     *
     * @throws Exception if the blob is corrupt or was encrypted with a different key
     */
    fun decrypt(ciphertext: ByteArray): ByteArray
}
