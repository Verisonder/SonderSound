package com.verisonder.sondersound

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The user's Gemini key, encrypted with an AES key that never leaves the Android Keystore.
 * What reaches SharedPreferences is the IV followed by the ciphertext.
 *
 * If the Keystore key is lost (a restore to a new phone, a cleared credential store) the
 * stored value cannot be decrypted; [read] returns null and the user pastes the key again.
 */
object KeyVault {
    private const val FILE = "vault"
    private const val GEMINI = "gemini"
    private const val ALIAS = "sondersound_vault"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    /** IV followed by ciphertext. Used for the key and for saved clips. */
    fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, secret())
        return cipher.iv + cipher.doFinal(plain)
    }

    /** Null if it cannot be decrypted, for instance after the Keystore key was lost. */
    fun open(sealed: ByteArray): ByteArray? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, sealed, 0, IV_BYTES))
        cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }.getOrNull()

    fun saveGeminiKey(context: Context, value: String) {
        val sealed = seal(value.trim().toByteArray(Charsets.UTF_8))
        prefs(context).edit().putString(GEMINI, Base64.encodeToString(sealed, Base64.NO_WRAP)).apply()
    }

    fun geminiKey(context: Context): String? {
        val stored = prefs(context).getString(GEMINI, null) ?: return null
        val bytes = runCatching { Base64.decode(stored, Base64.NO_WRAP) }.getOrNull() ?: return null
        return open(bytes)?.toString(Charsets.UTF_8)
    }

    fun hasGeminiKey(context: Context): Boolean = geminiKey(context) != null

    fun clearGeminiKey(context: Context) = prefs(context).edit().remove(GEMINI).apply()
}
