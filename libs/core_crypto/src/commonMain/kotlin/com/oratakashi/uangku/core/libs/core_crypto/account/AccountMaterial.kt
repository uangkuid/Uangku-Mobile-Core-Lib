package com.oratakashi.uangku.core.libs.core_crypto.account

import com.oratakashi.uangku.core.libs.core_crypto.key.SecretBytes
import com.oratakashi.uangku.core.libs.core_crypto.key.UangkuSecretKey

/**
 * Plain data-in/data-out types for the account facade. Every field the server needs is a
 * base64 string here — the app maps these onto its own request DTOs. This is the "no HTTP"
 * boundary of the library.
 *
 * @since 17 July 2026
 */

/** The `/auth/salt` response: per-user salt + iteration count. */
class SaltChallenge(val saltBase64: String, val iterations: Int)

/** The `wrapped_private_key` returned at login/register. */
class EncryptedVault(val wrappedPrivateKeyBase64: String)

/** Output of registration — maps onto `POST /auth/register`. [secretKey] is shown to the user once. */
class RegistrationMaterial(
    val secretKey: UangkuSecretKey,
    val saltBase64: String,
    val iterations: Int,
    val authKey: String,
    val publicKeyBase64: String,
    val wrappedPrivateKeyBase64: String,
    val session: UnlockedSession,
)

/** Step 1 of login: the authKey to send, carrying the unlockKey for step 2. */
class LoginPreparation(val authKey: String, internal val unlockKey: SecretBytes)

/** Output of an authenticated credential change — keypair (and thus data) preserved. */
class CredentialChangeMaterial(
    val saltBase64: String,
    val iterations: Int,
    val authKey: String,
    val wrappedPrivateKeyBase64: String,
    val session: UnlockedSession,
)

/** Output of a password reset — a fresh keypair, so data under the old keypair is unrecoverable. */
class CredentialResetMaterial(
    val saltBase64: String,
    val iterations: Int,
    val authKey: String,
    val publicKeyBase64: String,
    val wrappedPrivateKeyBase64: String,
    val session: UnlockedSession,
)
