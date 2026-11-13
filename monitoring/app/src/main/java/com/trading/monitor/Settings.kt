package com.trading.monitor

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.trading.monitor.matrix.MatrixConfig
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var model: String
        get() = prefs.getString("model", "deepseek-chat") ?: "deepseek-chat"
        set(v) = prefs.edit().putString("model", v.trim()).apply()

    var dailyTokenCap: Int
        get() = prefs.getInt("dailyTokenCap", 300_000)
        set(v) = prefs.edit().putInt("dailyTokenCap", v.coerceAtLeast(0)).apply()

    var monitoringEnabled: Boolean
        get() = prefs.getBoolean("monitoringEnabled", true)
        set(v) = prefs.edit().putBoolean("monitoringEnabled", v).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean("keepScreenOn", false)
        set(v) = prefs.edit().putBoolean("keepScreenOn", v).apply()

    var minAgreeingAccounts: Int
        get() = prefs.getInt("minAgree", 2)
        set(v) = prefs.edit().putInt("minAgree", v.coerceAtLeast(1)).apply()

    var buyThreshold: Int
        get() = prefs.getInt("buyThreshold", 25)
        set(v) = prefs.edit().putInt("buyThreshold", v.coerceIn(1, 100)).apply()

    var strongThreshold: Int
        get() = prefs.getInt("strongThreshold", 60)
        set(v) = prefs.edit().putInt("strongThreshold", v.coerceIn(1, 100)).apply()

    var minRewardRisk: Float
        get() = prefs.getFloat("minRewardRisk", 2f)
        set(v) = prefs.edit().putFloat("minRewardRisk", v.coerceAtLeast(0f)).apply()

    /** Handles of the accounts whose Feed section is expanded. */
    var expandedFeedAccounts: Set<String>
        get() = prefs.getStringSet("expandedFeedAccounts", emptySet())?.toSet() ?: emptySet()
        set(v) = prefs.edit().putStringSet("expandedFeedAccounts", v.toSet()).apply()

    /** Handle of the Feed account being viewed; null means "All accounts" (the key is removed). */
    var selectedFeedAccount: String?
        get() = prefs.getString("selectedFeedAccount", null)
        set(v) {
            if (v == null) prefs.edit().remove("selectedFeedAccount").apply()
            else prefs.edit().putString("selectedFeedAccount", v).apply()
        }

    /** Feed sentiment filter: one of "all", "bullish", "bearish", "neutral"; anything else means "all". */
    var feedSentimentFilter: String
        get() = prefs.getString("feedSentimentFilter", "all")
            ?.takeIf { it in FEED_SENTIMENT_VALUES } ?: "all"
        set(v) {
            prefs.edit()
                .putString("feedSentimentFilter", v.takeIf { it in FEED_SENTIMENT_VALUES } ?: "all")
                .apply()
        }

    /** DeepSeek API key, stored encrypted with a key held in the Android Keystore. */
    var apiKey: String
        get() = prefs.getString("apiKeyEnc", null)?.let { KeyVault.decrypt(it) } ?: ""
        set(v) {
            val trimmed = v.trim()
            if (trimmed.isEmpty()) prefs.edit().remove("apiKeyEnc").apply()
            else prefs.edit().putString("apiKeyEnc", KeyVault.encrypt(trimmed)).apply()
        }

    fun matrixConfig() = MatrixConfig(
        minAgreeingAccounts = minAgreeingAccounts,
        buyThreshold = buyThreshold.toDouble(),
        strongThreshold = strongThreshold.toDouble(),
        minRewardRisk = minRewardRisk.toDouble(),
    )
}

/** Allowed values of [Settings.feedSentimentFilter]; any other value is treated as "all". */
private val FEED_SENTIMENT_VALUES = setOf("all", "bullish", "bearish", "neutral")

private object KeyVault {
    private const val ALIAS = "deepseek_api_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    fun decrypt(stored: String): String = try {
        val raw = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, IV_BYTES))
        String(cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES))
    } catch (e: Exception) {
        ""
    }
}
