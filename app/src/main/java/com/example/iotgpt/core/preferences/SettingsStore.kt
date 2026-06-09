package com.example.iotgpt.core.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.settingsDataStore by preferencesDataStore(name = "iotgpt_settings")

/**
 * Stores user-editable model service configuration and app preferences.
 */
class SettingsStore(
    private val context: Context
) {
    val modelProfiles: Flow<List<ModelProfile>> = context.settingsDataStore.data.map { preferences ->
        readProfiles(preferences)
    }

    val activeModelProfile: Flow<ModelProfile> = context.settingsDataStore.data.map { preferences ->
        val profiles = readProfiles(preferences)
        val activeId = preferences[ACTIVE_MODEL_PROFILE_ID]
            ?: inferActiveProfileId(preferences, profiles)
        profiles.firstOrNull { it.id == activeId } ?: profiles.first()
    }

    val settings: Flow<LlmSettings> = activeModelProfile.map { profile ->
        profile.toLlmSettings()
    }

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { preferences ->
        preferences[THEME_MODE]
            ?.let { ThemeMode.fromStorageValue(it) }
            ?: ThemeMode.System
    }

    val onboardingCompleted: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETED] ?: false
    }

    val lastApiError: Flow<String?> = context.settingsDataStore.data.map { preferences ->
        preferences[LAST_API_ERROR]?.takeIf { it.isNotBlank() }
    }

    suspend fun saveLlmSettings(settings: LlmSettings) {
        context.settingsDataStore.edit { preferences ->
            val profiles = readProfiles(preferences)
            val activeId = preferences[ACTIVE_MODEL_PROFILE_ID]
                ?: inferActiveProfileId(preferences, profiles)
            val updatedProfiles = profiles.map { profile ->
                if (profile.id == activeId) {
                    profile.copy(
                        baseUrl = settings.baseUrl.trim(),
                        apiKey = settings.apiKey.trim(),
                        model = settings.model.trim(),
                        supportsVision = settings.supportsVision
                    )
                } else {
                    profile
                }
            }
            writeProfiles(preferences, updatedProfiles)
            preferences[ACTIVE_MODEL_PROFILE_ID] = activeId
            preferences[BASE_URL] = settings.baseUrl.trim()
            preferences[API_KEY] = settings.apiKey.trim()
            preferences[MODEL] = settings.model.trim()
        }
    }

    suspend fun upsertModelProfile(profile: ModelProfile, activate: Boolean = true) {
        context.settingsDataStore.edit { preferences ->
            val normalized = profile.normalized()
            val currentProfiles = readProfiles(preferences)
            val updatedProfiles = if (currentProfiles.any { it.id == normalized.id }) {
                currentProfiles.map { existing ->
                    if (existing.id == normalized.id) normalized else existing
                }
            } else {
                currentProfiles + normalized
            }
            writeProfiles(preferences, updatedProfiles)

            val activeId = if (activate) {
                normalized.id
            } else {
                preferences[ACTIVE_MODEL_PROFILE_ID]
                    ?: inferActiveProfileId(preferences, updatedProfiles)
            }
            preferences[ACTIVE_MODEL_PROFILE_ID] = activeId
            updatedProfiles.firstOrNull { it.id == activeId }
                ?.writeLegacyPreferences(preferences)
        }
    }

    suspend fun setActiveModelProfile(profileId: String) {
        context.settingsDataStore.edit { preferences ->
            val profiles = readProfiles(preferences)
            val activeProfile = profiles.firstOrNull { it.id == profileId } ?: return@edit
            preferences[ACTIVE_MODEL_PROFILE_ID] = activeProfile.id
            activeProfile.writeLegacyPreferences(preferences)
        }
    }

    suspend fun updateActiveProfileModel(model: String) {
        val normalizedModel = model.trim()
        require(normalizedModel.isNotBlank()) { "请填写模型名称" }
        context.settingsDataStore.edit { preferences ->
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
            writeProfiles(preferences, updatedProfiles)
            preferences[ACTIVE_MODEL_PROFILE_ID] = activeId
            updatedProfiles.firstOrNull { it.id == activeId }
                ?.writeLegacyPreferences(preferences)
        }
    }

    suspend fun deleteModelProfile(profileId: String) {
        context.settingsDataStore.edit { preferences ->
            val profiles = readProfiles(preferences)
            val activeId = preferences[ACTIVE_MODEL_PROFILE_ID]
                ?: inferActiveProfileId(preferences, profiles)
            if (profileId == activeId || profiles.size <= 1) return@edit

            val updatedProfiles = profiles.filterNot { it.id == profileId }
            writeProfiles(preferences, updatedProfiles)
            updatedProfiles.firstOrNull { it.id == activeId }
                ?.writeLegacyPreferences(preferences)
        }
    }

    suspend fun saveThemeMode(themeMode: ThemeMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[THEME_MODE] = themeMode.storageValue
        }
    }

    suspend fun saveOnboardingCompleted(completed: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun saveLastApiError(message: String?) {
        context.settingsDataStore.edit { preferences ->
            if (message.isNullOrBlank()) {
                preferences.remove(LAST_API_ERROR)
            } else {
                preferences[LAST_API_ERROR] = message.take(MAX_ERROR_CHARS)
            }
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-chat"

        private val BASE_URL = stringPreferencesKey("api_base_url")
        private val API_KEY = stringPreferencesKey("api_key")
        private val MODEL = stringPreferencesKey("model")
        private val MODEL_PROFILES = stringPreferencesKey("model_profiles")
        private val ACTIVE_MODEL_PROFILE_ID = stringPreferencesKey("active_model_profile_id")
        private val LAST_API_ERROR = stringPreferencesKey("last_api_error")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private const val MAX_ERROR_CHARS = 240

        private fun readProfiles(preferences: Preferences): List<ModelProfile> {
            val raw = preferences[MODEL_PROFILES].orEmpty()
            val parsed = runCatching {
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        add(
                            ModelProfile(
                                id = item.optString("id"),
                                name = item.optString("name"),
                                provider = item.optString("provider"),
                                baseUrl = item.optString("baseUrl"),
                                apiKey = item.optString("apiKey"),
                                model = item.optString("model"),
                                supportsVision = item.optBoolean("supportsVision", false)
                            ).normalized()
                        )
                    }
                }.filter { it.id.isNotBlank() }
            }.getOrDefault(emptyList())

            return parsed.ifEmpty { defaultProfiles(preferences) }
        }

        private fun writeProfiles(preferences: MutablePreferences, profiles: List<ModelProfile>) {
            val array = JSONArray()
            profiles.map { it.normalized() }.forEach { profile ->
                array.put(
                    JSONObject()
                        .put("id", profile.id)
                        .put("name", profile.name)
                        .put("provider", profile.provider)
                        .put("baseUrl", profile.baseUrl)
                        .put("apiKey", profile.apiKey)
                        .put("model", profile.model)
                        .put("supportsVision", profile.supportsVision)
                )
            }
            preferences[MODEL_PROFILES] = array.toString()
        }

        private fun defaultProfiles(preferences: Preferences): List<ModelProfile> {
            val legacy = ModelProfile(
                id = inferLegacyProfileId(preferences),
                name = inferLegacyName(preferences),
                provider = inferLegacyProvider(preferences),
                baseUrl = preferences[BASE_URL] ?: DEFAULT_BASE_URL,
                apiKey = preferences[API_KEY].orEmpty(),
                model = preferences[MODEL] ?: DEFAULT_MODEL,
                supportsVision = false
            ).normalized()

            val presets = listOf(
                ModelProfile(
                    id = "deepseek",
                    name = "DeepSeek",
                    provider = "DeepSeek",
                    baseUrl = DEFAULT_BASE_URL,
                    apiKey = "",
                    model = DEFAULT_MODEL,
                    supportsVision = false
                ),
                ModelProfile(
                    id = "qwen",
                    name = "通义千问",
                    provider = "DashScope",
                    baseUrl = "https://dashscope.aliyuncs.com/compatible-mode",
                    apiKey = "",
                    model = "qwen-plus",
                    supportsVision = false
                ),
                ModelProfile(
                    id = "mimo",
                    name = "小米 MiMo",
                    provider = "MiMo",
                    baseUrl = "https://api.xiaomimimo.com/v1",
                    apiKey = "",
                    model = "mimo-v2-pro",
                    supportsVision = false
                ),
                ModelProfile(
                    id = "openai",
                    name = "OpenAI",
                    provider = "OpenAI",
                    baseUrl = "https://api.openai.com/v1",
                    apiKey = "",
                    model = "gpt-4.1-mini",
                    supportsVision = false
                )
            )

            return (listOf(legacy) + presets)
                .distinctBy { it.id }
                .map { profile ->
                    if (profile.id == legacy.id) legacy else profile
                }
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
    }
}

data class LlmSettings(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val supportsVision: Boolean = false
)

data class ModelProfile(
    val id: String,
    val name: String,
    val provider: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val supportsVision: Boolean = false
) {
    fun toLlmSettings(): LlmSettings {
        return LlmSettings(
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            supportsVision = supportsVision
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
            supportsVision = supportsVision
        )
    }

    fun maskedKeyStatus(): String {
        return if (apiKey.isBlank()) "API Key 未配置" else "API Key 已配置"
    }
}

private fun ModelProfile.writeLegacyPreferences(preferences: MutablePreferences) {
    preferences[SettingsStoreLegacyKeys.BASE_URL] = baseUrl
    preferences[SettingsStoreLegacyKeys.API_KEY] = apiKey
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
