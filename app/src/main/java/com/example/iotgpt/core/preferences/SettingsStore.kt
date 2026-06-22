package com.example.iotgpt.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.settingsDataStore by preferencesDataStore(name = "iotgpt_settings")

/**
 * Stores user-editable model service configuration and app preferences.
 */
class SettingsStore(
    context: Context,
    internal val dataStore: DataStore<Preferences> = context.settingsDataStore,
    internal val encryption: ApiKeyEncryption = ApiKeyEncryption(context)
) {
    val modelProfiles: Flow<List<ModelProfile>> = dataStore.data.map { preferences ->
        readProfiles(preferences)
    }

    val activeModelProfile: Flow<ModelProfile> = dataStore.data.map { preferences ->
        val profiles = readProfiles(preferences)
        val activeId = preferences[ACTIVE_MODEL_PROFILE_ID]
            ?: inferActiveProfileId(preferences, profiles)
        profiles.firstOrNull { it.id == activeId } ?: profiles.first()
    }

    val settings: Flow<LlmSettings> = activeModelProfile.map { profile ->
        profile.toLlmSettings()
    }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { preferences ->
        preferences[THEME_MODE]
            ?.let { ThemeMode.fromStorageValue(it) }
            ?: ThemeMode.System
    }

    val onboardingCompleted: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETED] ?: false
    }

    val lastApiError: Flow<String?> = dataStore.data.map { preferences ->
        preferences[LAST_API_ERROR]?.takeIf { it.isNotBlank() }
    }

    suspend fun saveLlmSettings(settings: LlmSettings) {
        dataStore.edit { preferences ->
            val profiles = readProfiles(preferences)
            val activeId = preferences[ACTIVE_MODEL_PROFILE_ID]
                ?: inferActiveProfileId(preferences, profiles)
            val updatedProfiles = profiles.map { profile ->
                if (profile.id == activeId) {
                    profile.copy(
                        baseUrl = settings.baseUrl.trim(),
                        apiKey = settings.apiKey.trim(),
                        model = settings.model.trim(),
                        supportsVision = settings.supportsVision,
                        reasoningEnabled = profile.supportsReasoning && settings.reasoningEnabled,
                        supportsAudioTranscription = settings.supportsAudioTranscription,
                        transcriptionModel = settings.transcriptionModel
                    )
                } else {
                    profile
                }
            }
            writeProfiles(preferences, updatedProfiles, encryption)
            preferences[ACTIVE_MODEL_PROFILE_ID] = activeId
            preferences[BASE_URL] = settings.baseUrl.trim()
            preferences[API_KEY] = encryption.encryptApiKey(settings.apiKey.trim())
                ?: settings.apiKey.trim()
            preferences[MODEL] = settings.model.trim()
        }
    }

    suspend fun upsertModelProfile(profile: ModelProfile, activate: Boolean = true) {
        dataStore.edit { preferences ->
            val normalized = profile.normalized()
            val currentProfiles = readProfiles(preferences)
            val updatedProfiles = if (currentProfiles.any { it.id == normalized.id }) {
                currentProfiles.map { existing ->
                    if (existing.id == normalized.id) normalized else existing
                }
            } else {
                currentProfiles + normalized
            }
            writeProfiles(preferences, updatedProfiles, encryption)

            val activeId = if (activate) {
                normalized.id
            } else {
                preferences[ACTIVE_MODEL_PROFILE_ID]
                    ?: inferActiveProfileId(preferences, updatedProfiles)
            }
            preferences[ACTIVE_MODEL_PROFILE_ID] = activeId
            updatedProfiles.firstOrNull { it.id == activeId }
                ?.writeLegacyPreferences(preferences, encryption)
        }
    }

    suspend fun setActiveModelProfile(profileId: String) {
        dataStore.edit { preferences ->
            val profiles = readProfiles(preferences)
            val activeProfile = profiles.firstOrNull { it.id == profileId } ?: return@edit
            preferences[ACTIVE_MODEL_PROFILE_ID] = activeProfile.id
            activeProfile.writeLegacyPreferences(preferences, encryption)
        }
    }

    suspend fun updateActiveProfileModel(model: String) {
        val normalizedModel = model.trim()
        require(normalizedModel.isNotBlank()) { "请填写模型名称" }
        dataStore.edit { preferences ->
            val profiles = readProfiles(preferences)
            val activeId = preferences[ACTIVE_MODEL_PROFILE_ID]
                ?: inferActiveProfileId(preferences, profiles)
            val updatedProfiles = profiles.map { profile ->
                if (profile.id == activeId) {
                    profile.copy(model = normalizedModel).normalized()
                } else {
                    profile
                }
            }
            writeProfiles(preferences, updatedProfiles, encryption)
            preferences[ACTIVE_MODEL_PROFILE_ID] = activeId
            updatedProfiles.firstOrNull { it.id == activeId }
                ?.writeLegacyPreferences(preferences, encryption)
        }
    }

    suspend fun updateActiveReasoningEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            val profiles = readProfiles(preferences)
            val activeId = preferences[ACTIVE_MODEL_PROFILE_ID]
                ?: inferActiveProfileId(preferences, profiles)
            val updatedProfiles = profiles.map { profile ->
                if (profile.id == activeId) {
                    profile.copy(reasoningEnabled = profile.supportsReasoning && enabled).normalized()
                } else {
                    profile
                }
            }
            writeProfiles(preferences, updatedProfiles, encryption)
            preferences[ACTIVE_MODEL_PROFILE_ID] = activeId
            updatedProfiles.firstOrNull { it.id == activeId }
                ?.writeLegacyPreferences(preferences, encryption)
        }
    }

    suspend fun deleteModelProfile(profileId: String) {
        dataStore.edit { preferences ->
            val profiles = readProfiles(preferences)
            val activeId = preferences[ACTIVE_MODEL_PROFILE_ID]
                ?: inferActiveProfileId(preferences, profiles)
            if (profileId == activeId || profiles.size <= 1) return@edit

            val updatedProfiles = profiles.filterNot { it.id == profileId }
            writeProfiles(preferences, updatedProfiles, encryption)
            updatedProfiles.firstOrNull { it.id == activeId }
                ?.writeLegacyPreferences(preferences, encryption)
        }
    }

    suspend fun saveThemeMode(themeMode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE] = themeMode.storageValue
        }
    }

    suspend fun saveOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun saveLastApiError(message: String?) {
        dataStore.edit { preferences ->
            if (message.isNullOrBlank()) {
                preferences.remove(LAST_API_ERROR)
            } else {
                preferences[LAST_API_ERROR] = message.take(MAX_ERROR_CHARS)
            }
        }
    }

    /**
     * Re-encrypts any plaintext API keys found in stored profiles.
     * Reads the raw MODEL_PROFILES JSON directly to avoid decrypting
     * already-encrypted keys through [readProfiles], which would cause
     * double-encryption on repeated calls.
     */
    suspend fun ensureKeyMigration() {
        dataStore.edit { preferences ->
            // ── Migrate MODEL_PROFILES raw JSON ──────────────────────────
            val raw = preferences[MODEL_PROFILES].orEmpty()
            if (raw.isNotBlank()) {
                runCatching {
                    val array = JSONArray(raw)
                    var changed = false
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val rawKey = obj.optString("apiKey", "")
                        if (rawKey.isNotBlank() && !encryption.isLikelyEncrypted(rawKey)) {
                            val encrypted = encryption.encryptApiKey(rawKey)
                            if (encrypted != null) {
                                obj.put("apiKey", encrypted)
                                changed = true
                            } else {
                                android.util.Log.w(
                                    "SettingsStore",
                                    "Failed to encrypt apiKey in profile ${obj.optString("id")}; keeping plaintext"
                                )
                            }
                        }
                    }
                    if (changed) {
                        preferences[MODEL_PROFILES] = array.toString()
                    }
                }
            }

            // ── Migrate legacy standalone API_KEY ────────────────────────
            val legacyKey = preferences[API_KEY].orEmpty()
            if (legacyKey.isNotBlank() && !encryption.isLikelyEncrypted(legacyKey)) {
                val encrypted = encryption.encryptApiKey(legacyKey)
                if (encrypted != null) {
                    preferences[API_KEY] = encrypted
                } else {
                    android.util.Log.w(
                        "SettingsStore",
                        "Failed to encrypt legacy API_KEY during migration; keeping plaintext"
                    )
                }
            }
        }
    }

    private fun readProfiles(preferences: Preferences): List<ModelProfile> {
        val raw = preferences[MODEL_PROFILES].orEmpty()
        val parsed = runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val rawApiKey = item.optString("apiKey", "")
                    val decryptedKey = when {
                        rawApiKey.isBlank() -> ""
                        encryption.isLikelyEncrypted(rawApiKey) ->
                            encryption.decryptApiKey(rawApiKey) ?: ""
                        else -> rawApiKey
                    }
                    add(
                        ModelProfile(
                            id = item.optString("id"),
                            name = item.optString("name"),
                            provider = item.optString("provider"),
                            baseUrl = item.optString("baseUrl"),
                            apiKey = decryptedKey,
                            model = item.optString("model"),
                            supportsVision = item.optBoolean("supportsVision", false),
                            supportsReasoning = item.optBoolean("supportsReasoning", false),
                            reasoningEnabled = item.optBoolean("reasoningEnabled", false),
                            supportsAudioTranscription = item.optBoolean(
                                "supportsAudioTranscription",
                                false
                            ),
                            transcriptionModel = item.optString(
                                "transcriptionModel",
                                DEFAULT_TRANSCRIPTION_MODEL
                            )
                        ).normalized()
                    )
                }
            }.filter { it.id.isNotBlank() }
        }.getOrDefault(emptyList())

        return parsed
            .filterNot { it.isUnusedBuiltInPreset() }
            .ifEmpty { defaultProfiles(preferences, encryption) }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-chat"
        const val DEFAULT_TRANSCRIPTION_MODEL = "whisper-1"
        const val MIMO_TRANSCRIPTION_MODEL = "mimo-v2.5-asr"

        private val BASE_URL = stringPreferencesKey("api_base_url")
        private val API_KEY = stringPreferencesKey("api_key")
        private val MODEL = stringPreferencesKey("model")
        private val MODEL_PROFILES = stringPreferencesKey("model_profiles")
        private val ACTIVE_MODEL_PROFILE_ID = stringPreferencesKey("active_model_profile_id")
        private val LAST_API_ERROR = stringPreferencesKey("last_api_error")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private const val MAX_ERROR_CHARS = 240

        fun createForTest(context: Context, file: File): SettingsStore {
            return SettingsStore(
                context = context,
                dataStore = PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                    produceFile = { file }
                )
            )
        }

        private fun writeProfiles(
            preferences: MutablePreferences,
            profiles: List<ModelProfile>,
            encryption: ApiKeyEncryption
        ) {
            val array = JSONArray()
            profiles.map { it.normalized() }.forEach { profile ->
                val storedKey = if (encryption.isLikelyEncrypted(profile.apiKey)) {
                    profile.apiKey
                } else {
                    encryption.encryptApiKey(profile.apiKey) ?: profile.apiKey
                }
                array.put(
                    JSONObject()
                        .put("id", profile.id)
                        .put("name", profile.name)
                        .put("provider", profile.provider)
                        .put("baseUrl", profile.baseUrl)
                        .put("apiKey", storedKey)
                        .put("model", profile.model)
                        .put("supportsVision", profile.supportsVision)
                        .put("supportsReasoning", profile.supportsReasoning)
                        .put("reasoningEnabled", profile.reasoningEnabled)
                        .put("supportsAudioTranscription", profile.supportsAudioTranscription)
                        .put("transcriptionModel", profile.transcriptionModel)
                )
            }
            preferences[MODEL_PROFILES] = array.toString()
        }

        private fun defaultProfiles(
            preferences: Preferences,
            encryption: ApiKeyEncryption
        ): List<ModelProfile> {
            val legacyApiKeyRaw = preferences[API_KEY].orEmpty()
            val legacyApiKey = if (encryption.isLikelyEncrypted(legacyApiKeyRaw)) {
                encryption.decryptApiKey(legacyApiKeyRaw) ?: ""
            } else {
                legacyApiKeyRaw
            }
            val legacy = ModelProfile(
                id = inferLegacyProfileId(preferences),
                name = inferLegacyName(preferences),
                provider = inferLegacyProvider(preferences),
                baseUrl = preferences[BASE_URL] ?: DEFAULT_BASE_URL,
                apiKey = legacyApiKey,
                model = preferences[MODEL] ?: DEFAULT_MODEL,
                supportsVision = false,
                supportsReasoning = inferLegacySupportsReasoning(preferences),
                reasoningEnabled = false,
                supportsAudioTranscription = inferLegacySupportsAudioTranscription(preferences),
                transcriptionModel = inferLegacyTranscriptionModel(preferences)
            ).normalized()

            return listOf(legacy)
        }

        private fun inferActiveProfileId(
            preferences: Preferences,
            profiles: List<ModelProfile>
        ): String {
            val legacyId = inferLegacyProfileId(preferences)
            return profiles.firstOrNull { it.id == legacyId }?.id
                ?: profiles.firstOrNull()?.id
                ?: "deepseek"
        }

        private fun inferLegacyProfileId(preferences: Preferences): String {
            val baseUrl = preferences[BASE_URL] ?: DEFAULT_BASE_URL
            val model = preferences[MODEL] ?: DEFAULT_MODEL
            return when {
                model.startsWith("qwen", ignoreCase = true) -> "qwen"
                model.contains("mimo", ignoreCase = true) -> "mimo"
                baseUrl.contains("api.openai.com", ignoreCase = true) -> "openai"
                baseUrl.contains("deepseek", ignoreCase = true) -> "deepseek"
                else -> "custom-${(baseUrl + model).hashCode().toUInt()}"
            }
        }

        private fun inferLegacyName(preferences: Preferences): String {
            return when (inferLegacyProfileId(preferences)) {
                "deepseek" -> "DeepSeek"
                "qwen" -> "通义千问"
                "mimo" -> "小米 MiMo"
                "openai" -> "OpenAI"
                else -> "自定义模型"
            }
        }

        private fun inferLegacyProvider(preferences: Preferences): String {
            return when (inferLegacyProfileId(preferences)) {
                "deepseek" -> "DeepSeek"
                "qwen" -> "DashScope"
                "mimo" -> "MiMo"
                "openai" -> "OpenAI"
                else -> "Custom"
            }
        }

        private fun inferLegacySupportsReasoning(preferences: Preferences): Boolean {
            val marker = "${inferLegacyProvider(preferences)} ${preferences[MODEL] ?: DEFAULT_MODEL}".lowercase()
            return listOf("deepseek", "reasoner", "qwen", "mimo", "thinking", "reasoning")
                .any { marker.contains(it) }
        }

        private fun inferLegacySupportsAudioTranscription(preferences: Preferences): Boolean {
            val marker = "${inferLegacyProvider(preferences)} ${preferences[BASE_URL].orEmpty()}".lowercase()
            return listOf("openai", "whisper", "mimo", "xiaomimimo").any { marker.contains(it) }
        }

        private fun inferLegacyTranscriptionModel(preferences: Preferences): String {
            val marker = "${inferLegacyProvider(preferences)} ${preferences[BASE_URL].orEmpty()}".lowercase()
            return if (marker.contains("mimo") || marker.contains("xiaomimimo")) {
                MIMO_TRANSCRIPTION_MODEL
            } else {
                DEFAULT_TRANSCRIPTION_MODEL
            }
        }

        private fun ModelProfile.isUnusedBuiltInPreset(): Boolean {
            return id in BUILT_IN_PRESET_IDS && apiKey.isBlank()
        }

        private val BUILT_IN_PRESET_IDS = setOf("deepseek", "qwen", "mimo", "openai")
    }
}

data class LlmSettings(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val supportsVision: Boolean = false,
    val reasoningEnabled: Boolean = false,
    val supportsAudioTranscription: Boolean = false,
    val transcriptionModel: String = SettingsStore.DEFAULT_TRANSCRIPTION_MODEL
)

data class ModelProfile(
    val id: String,
    val name: String,
    val provider: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val supportsVision: Boolean = false,
    val supportsReasoning: Boolean = false,
    val reasoningEnabled: Boolean = false,
    val supportsAudioTranscription: Boolean = false,
    val transcriptionModel: String = SettingsStore.DEFAULT_TRANSCRIPTION_MODEL
) {
    fun toLlmSettings(): LlmSettings {
        return LlmSettings(
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            supportsVision = supportsVision,
            reasoningEnabled = supportsReasoning && reasoningEnabled,
            supportsAudioTranscription = supportsAudioTranscription,
            transcriptionModel = transcriptionModel.trim()
                .ifBlank { SettingsStore.DEFAULT_TRANSCRIPTION_MODEL }
        )
    }

    fun normalized(): ModelProfile {
        return copy(
            id = id.ifBlank { "profile-${System.currentTimeMillis()}" },
            name = name.trim().ifBlank { model.trim().ifBlank { "未命名模型" } },
            provider = provider.trim().ifBlank { "Custom" },
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim(),
            model = model.trim(),
            supportsVision = supportsVision,
            supportsReasoning = supportsReasoning,
            reasoningEnabled = supportsReasoning && reasoningEnabled,
            supportsAudioTranscription = supportsAudioTranscription,
            transcriptionModel = transcriptionModel.trim()
                .ifBlank { SettingsStore.DEFAULT_TRANSCRIPTION_MODEL }
        )
    }

    fun maskedKeyStatus(): String {
        return if (apiKey.isBlank()) "API Key 未配置" else "API Key 已配置"
    }
}

private fun ModelProfile.writeLegacyPreferences(
    preferences: MutablePreferences,
    encryption: ApiKeyEncryption
) {
    preferences[SettingsStoreLegacyKeys.BASE_URL] = baseUrl
    val storedKey = if (encryption.isLikelyEncrypted(apiKey)) {
        apiKey
    } else {
        encryption.encryptApiKey(apiKey) ?: apiKey
    }
    preferences[SettingsStoreLegacyKeys.API_KEY] = storedKey
    preferences[SettingsStoreLegacyKeys.MODEL] = model
}

private object SettingsStoreLegacyKeys {
    val BASE_URL = stringPreferencesKey("api_base_url")
    val API_KEY = stringPreferencesKey("api_key")
    val MODEL = stringPreferencesKey("model")
}

enum class ThemeMode(
    val storageValue: String,
    val label: String
) {
    System("system", "跟随系统"),
    Light("light", "浅色"),
    Dark("dark", "深色");

    companion object {
        fun fromStorageValue(value: String): ThemeMode {
            return entries.firstOrNull { it.storageValue == value } ?: System
        }
    }
}
