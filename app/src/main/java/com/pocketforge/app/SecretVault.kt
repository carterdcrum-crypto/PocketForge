package com.pocketforge.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object IntegrationKeys {
    const val OPENROUTER = "openrouter_api_key"
    const val GEMINI = "gemini_api_key"
    const val GROQ = "groq_api_key"

    const val GITHUB_REPO = "github_repo"
    const val GITHUB_TOKEN = "github_token"
    const val GITHUB_WORKFLOW = "github_workflow"

    const val CODEMAGIC_TOKEN = "codemagic_token"
    const val CODEMAGIC_APP_ID = "codemagic_app_id"
    const val CODEMAGIC_WORKFLOW = "codemagic_workflow"

    const val BITRISE_TOKEN = "bitrise_token"
    const val BITRISE_APP_SLUG = "bitrise_app_slug"
    const val BITRISE_WORKFLOW = "bitrise_workflow"
}

/**
 * Small encrypted-at-rest vault for beta BYOK credentials.
 * The encryption key is non-exportable and lives in Android Keystore.
 */
class SecretVault(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("pocketforge_integrations", Context.MODE_PRIVATE)

    fun put(name: String, value: String) {
        if (value.isBlank()) {
            prefs.edit().remove(name).apply()
            return
        }
        prefs.edit().putString(name, encrypt(value)).apply()
    }

    fun get(name: String): String = prefs.getString(name, null)?.let(::decrypt).orEmpty()

    fun has(name: String): Boolean = get(name).isNotBlank()

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = ByteBuffer.allocate(4 + cipher.iv.size + encrypted.size)
            .putInt(cipher.iv.size)
            .put(cipher.iv)
            .put(encrypted)
            .array()
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String = runCatching {
        val packed = ByteBuffer.wrap(Base64.decode(encoded, Base64.NO_WRAP))
        val ivSize = packed.int
        require(ivSize in 12..32)
        val iv = ByteArray(ivSize).also(packed::get)
        val encrypted = ByteArray(packed.remaining()).also(packed::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrDefault("")

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "PocketForgeIntegrationVault"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
