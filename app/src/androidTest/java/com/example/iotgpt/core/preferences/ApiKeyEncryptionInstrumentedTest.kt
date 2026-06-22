package com.example.iotgpt.core.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [ApiKeyEncryption] roundtrip and [SettingsStore] key
 * migration. These require a real Android device / emulator because Android
 * Keystore is not available on the JVM.
 */
@RunWith(AndroidJUnit4::class)
class ApiKeyEncryptionInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var encryption: ApiKeyEncryption
    private lateinit var storeFile: File
    private lateinit var settingsStore: SettingsStore

    @Before
    fun setUp() {
        encryption = ApiKeyEncryption(context)
        storeFile = File.createTempFile("test_settings_", ".preferences_pb", context.cacheDir)
        settingsStore = SettingsStore.createForTest(context, storeFile)
    }

    @After
    fun tearDown() {
        storeFile.delete()
    }

    @Test
    fun encryptApiKey_producesEncMarker() {
        val result = encryption.encryptApiKey("sk-test")
        assertNotNull(result)
        assertTrue("Encrypted value must start with enc:", result!!.startsWith("enc:"))
    }

    @Test
    fun isLikelyEncrypted_trueForEncryptedValue() {
        val encrypted = encryption.encryptApiKey("sk-test")!!
        assertTrue(encryption.isLikelyEncrypted(encrypted))
    }

    @Test
    fun isLikelyEncrypted_falseForPlaintext() {
        assertFalse(encryption.isLikelyEncrypted("sk-test"))
        assertFalse(encryption.isLikelyEncrypted(""))
        assertFalse(encryption.isLikelyEncrypted("enc:bad-format"))
    }

    @Test
    fun roundtrip_decryptRestoresOriginal() {
        val original = "sk-test-1234567890abcdef"
        val encrypted = encryption.encryptApiKey(original)!!
        val decrypted = encryption.decryptApiKey(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun roundtrip_emptyString_unchanged() {
        assertEquals("", encryption.encryptApiKey(""))
        assertEquals("", encryption.decryptApiKey(""))
    }

    @Test
    fun roundtrip_blankString_unchanged() {
        assertEquals("  ", encryption.encryptApiKey("  "))
    }

    @Test
    fun modelProfile_apiKeyStoredEncrypted() {
        runBlocking {
            val plaintextKey = "sk-my-secret-key"
            val profile = ModelProfile(
                id = "test-profile",
                name = "Test",
                provider = "Test",
                baseUrl = "https://api.test.com",
                apiKey = plaintextKey,
                model = "test-model"
            )
            settingsStore.upsertModelProfile(profile, activate = true)

            // Read back: the apiKey should be decrypted transparently
            val readBack = settingsStore.activeModelProfile.first()
            assertEquals(plaintextKey, readBack.apiKey)
        }
    }

    @Test
    fun ensureKeyMigration_encryptsLegacyApiKey() {
        runBlocking {
            val migrationFile = File.createTempFile(
                "test_migration_legacy_", ".preferences_pb", context.cacheDir
            )
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val rawStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { migrationFile }
            )

            val legacyApiKey = stringPreferencesKey("api_key")
            val modelProfiles = stringPreferencesKey("model_profiles")
            val legacyBaseUrl = stringPreferencesKey("api_base_url")
            val legacyModel = stringPreferencesKey("model")
            val plaintextKey = "sk-legacy-plaintext-key"

            rawStore.edit { prefs ->
                prefs[legacyApiKey] = plaintextKey
                prefs[legacyBaseUrl] = "https://api.deepseek.com"
                prefs[legacyModel] = "deepseek-chat"
                prefs.remove(modelProfiles)
            }

            val migrationStore = SettingsStore(
                context = context,
                dataStore = rawStore,
                encryption = encryption
            )

            // Before migration: active profile reads the plaintext key
            val before = migrationStore.activeModelProfile.first()
            assertEquals(plaintextKey, before.apiKey)

            // Run migration
            migrationStore.ensureKeyMigration()

            // After migration: the legacy API_KEY is encrypted, but reading
            // through SettingsStore still returns the decrypted plaintext.
            val after = migrationStore.activeModelProfile.first()
            assertEquals(plaintextKey, after.apiKey)

            // Verify the raw stored value is actually encrypted
            val prefsAfter = migrationStore.dataStore.data.first()
            val storedLegacy = prefsAfter[legacyApiKey].orEmpty()
            assertTrue(
                "Legacy API_KEY must be encrypted after migration, got: $storedLegacy",
                migrationStore.encryption.isLikelyEncrypted(storedLegacy)
            )

            migrationFile.delete()
        }
    }

    @Test
    fun ensureKeyMigration_plaintextProfileInRawJson() {
        runBlocking {
            val migrationFile = File.createTempFile(
                "test_migration_json_", ".preferences_pb", context.cacheDir
            )
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val rawStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { migrationFile }
            )

            val modelProfilesKey = stringPreferencesKey("model_profiles")
            val activeIdKey = stringPreferencesKey("active_model_profile_id")
            val plaintextKey = "sk-raw-plaintext"
            val rawJson = org.json.JSONArray().apply {
                put(
                    org.json.JSONObject()
                        .put("id", "raw-test")
                        .put("name", "Raw Test")
                        .put("provider", "Test")
                        .put("baseUrl", "https://api.test.com")
                        .put("apiKey", plaintextKey)
                        .put("model", "test-model")
                        .put("supportsVision", false)
                        .put("supportsReasoning", false)
                        .put("reasoningEnabled", false)
                        .put("supportsAudioTranscription", false)
                        .put("transcriptionModel", "whisper-1")
                )
            }.toString()

            rawStore.edit { prefs ->
                prefs[modelProfilesKey] = rawJson
                prefs[activeIdKey] = "raw-test"
            }

            val migrationStore = SettingsStore(
                context = context,
                dataStore = rawStore,
                encryption = encryption
            )
            migrationStore.ensureKeyMigration()

            // The apiKey in MODEL_PROFILES JSON must now be encrypted
            val prefsAfter = migrationStore.dataStore.data.first()
            val afterJson = prefsAfter[modelProfilesKey].orEmpty()
            val afterArray = org.json.JSONArray(afterJson)
            val storedKey = afterArray.getJSONObject(0).getString("apiKey")
            assertTrue(
                "apiKey in MODEL_PROFILES must be encrypted, got: $storedKey",
                migrationStore.encryption.isLikelyEncrypted(storedKey)
            )

            // Reading through SettingsStore returns the decrypted plaintext
            val profile = migrationStore.activeModelProfile.first()
            assertEquals(plaintextKey, profile.apiKey)

            migrationFile.delete()
        }
    }

    @Test
    fun ensureKeyMigration_alreadyEncryptedProfile_notModified() {
        runBlocking {
            // Save a profile through the normal API (encrypts the key on write)
            val plaintextKey = "sk-already-encrypted"
            val profile = ModelProfile(
                id = "enc-test",
                name = "Enc Test",
                provider = "Test",
                baseUrl = "https://api.test.com",
                apiKey = plaintextKey,
                model = "test-model"
            )
            settingsStore.upsertModelProfile(profile, activate = true)

            // Read the raw JSON through the SAME DataStore — no second instance
            val modelProfilesKey = stringPreferencesKey("model_profiles")
            val rawBefore = settingsStore.dataStore.data.first()[modelProfilesKey].orEmpty()

            // Run migration — should be a no-op for already-encrypted keys
            settingsStore.ensureKeyMigration()

            val rawAfter = settingsStore.dataStore.data.first()[modelProfilesKey].orEmpty()
            assertEquals(
                "MODEL_PROFILES must not change when keys are already encrypted",
                rawBefore, rawAfter
            )

            // Reading back still returns the original plaintext
            val readBack = settingsStore.activeModelProfile.first()
            assertEquals(plaintextKey, readBack.apiKey)
        }
    }
}
