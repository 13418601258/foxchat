package com.wjy.foxchat.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class DeviceIdentityStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    val deviceId: String
        get() {
            val current = preferences.getString(KEY_DEVICE_ID, null)
            if (!current.isNullOrBlank()) return current

            return UUID.randomUUID().toString().also {
                preferences.edit().putString(KEY_DEVICE_ID, it).apply()
            }
        }

    val participantRole: String?
        get() = decrypt(KEY_PARTICIPANT_ROLE)

    val roomId: String?
        get() = decrypt(KEY_ROOM_ID)

    val isPaired: Boolean
        get() = !participantRole.isNullOrBlank() && !roomId.isNullOrBlank()

    val analysisConsent: Boolean
        get() = preferences.getBoolean(KEY_ANALYSIS_CONSENT, false)

    fun savePairing(role: String, roomId: String, analysisConsent: Boolean) {
        encrypt(KEY_PARTICIPANT_ROLE, role)
        encrypt(KEY_ROOM_ID, roomId)
        preferences.edit()
            .putBoolean(KEY_ANALYSIS_CONSENT, analysisConsent)
            .apply()
    }

    fun updateAnalysisConsent(consent: Boolean) {
        preferences.edit().putBoolean(KEY_ANALYSIS_CONSENT, consent).apply()
    }

    fun saveAvatarPath(path: String) {
        preferences.edit().putString(KEY_AVATAR, path).apply()
    }

    fun avatarPath(): String? = preferences.getString(KEY_AVATAR, null)

    fun saveAiConfiguration(baseUrl: String, apiKey: String) {
        preferences.edit().putString(KEY_AI_BASE_URL, baseUrl).apply()
        encrypt(KEY_AI_API_KEY, apiKey)
    }

    fun aiBaseUrl(): String =
        preferences.getString(KEY_AI_BASE_URL, DEFAULT_AI_BASE_URL) ?: DEFAULT_AI_BASE_URL

    fun aiApiKey(): String = decrypt(KEY_AI_API_KEY).orEmpty()

    fun saveAuthSession(
        accessToken: String,
        refreshToken: String,
        savedAt: Long = System.currentTimeMillis()
    ) {
        encrypt(KEY_AUTH_TOKEN, accessToken)
        encrypt(KEY_REFRESH_TOKEN, refreshToken)
        preferences.edit().putLong(KEY_AUTH_SAVED_AT, savedAt).apply()
    }

    fun saveAuthToken(token: String) {
        encrypt(KEY_AUTH_TOKEN, token)
        preferences.edit().putLong(KEY_AUTH_SAVED_AT, System.currentTimeMillis()).apply()
    }

    fun authToken(): String? = decrypt(KEY_AUTH_TOKEN)

    fun refreshToken(): String? = decrypt(KEY_REFRESH_TOKEN)

    /** access_token 有效期约 1 小时，这里留 10 分钟余量，提前视为过期。 */
    fun authTokenExpired(): Boolean {
        val savedAt = preferences.getLong(KEY_AUTH_SAVED_AT, 0L)
        if (savedAt == 0L) return true
        return System.currentTimeMillis() - savedAt > AUTH_TOKEN_TTL_MS
    }

    fun saveRemoteSyncTime(time: Long) {
        preferences.edit().putLong(KEY_REMOTE_SYNC_TIME, time).apply()
    }

    fun remoteSyncTime(): Long = preferences.getLong(KEY_REMOTE_SYNC_TIME, 0L)

    private fun encrypt(key: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val payload = cipher.iv + encrypted
        preferences.edit()
            .putString(key, Base64.encodeToString(payload, Base64.NO_WRAP))
            .apply()
    }

    private fun decrypt(key: String): String? {
        val encoded = preferences.getString(key, null) ?: return null
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = payload.copyOfRange(0, GCM_IV_LENGTH)
            val encrypted = payload.copyOfRange(GCM_IV_LENGTH, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            )
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFERENCES_NAME = "foxchat_identity"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_PARTICIPANT_ROLE = "participant_role"
        private const val KEY_ROOM_ID = "room_id"
        private const val KEY_ANALYSIS_CONSENT = "analysis_consent"
        private const val KEY_AVATAR = "avatar"
        private const val KEY_AI_BASE_URL = "ai_base_url"
        private const val KEY_AI_API_KEY = "ai_api_key"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_AUTH_SAVED_AT = "auth_token_saved_at"
        private const val KEY_REMOTE_SYNC_TIME = "remote_sync_time"
        private const val AUTH_TOKEN_TTL_MS = 50L * 60L * 1000L
        private const val DEFAULT_AI_BASE_URL = "https://api.deepseek.com"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "foxchat_identity_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
