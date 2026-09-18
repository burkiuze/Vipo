package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.InferenceParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vipo_settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_ACTIVE_MODEL_PATH = stringPreferencesKey("active_model_path")
        val KEY_ACTIVE_MODEL_NAME = stringPreferencesKey("active_model_name")
        val KEY_ACTIVE_MODEL_ARCH = stringPreferencesKey("active_model_arch")
        val KEY_AUTO_LOAD_LAST_MODEL = booleanPreferencesKey("auto_load_last_model")
        val KEY_SHOW_PERFORMANCE_STATS = booleanPreferencesKey("show_performance_stats")
        val KEY_PURE_BLACK_THEME = booleanPreferencesKey("pure_black_theme")

        // Inference Params
        val KEY_CONTEXT_SIZE = intPreferencesKey("context_size")
        val KEY_TEMPERATURE = floatPreferencesKey("temperature")
        val KEY_TOP_P = floatPreferencesKey("top_p")
        val KEY_TOP_K = intPreferencesKey("top_k")
        val KEY_MIN_P = floatPreferencesKey("min_p")
        val KEY_REPEAT_PENALTY = floatPreferencesKey("repeat_penalty")
        val KEY_THREADS = intPreferencesKey("threads")
        val KEY_BATCH_SIZE = intPreferencesKey("batch_size")
        val KEY_SEED = longPreferencesKey("seed")
    }

    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_ONBOARDING_COMPLETED] ?: false
    }

    val activeModelPath: Flow<String?> = context.dataStore.data.map {
        it[KEY_ACTIVE_MODEL_PATH]
    }

    val activeModelName: Flow<String?> = context.dataStore.data.map {
        it[KEY_ACTIVE_MODEL_NAME]
    }

    val activeModelArch: Flow<String?> = context.dataStore.data.map {
        it[KEY_ACTIVE_MODEL_ARCH]
    }

    val showPerformanceStats: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_SHOW_PERFORMANCE_STATS] ?: true
    }

    val pureBlackTheme: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_PURE_BLACK_THEME] ?: true
    }

    val autoLoadLastModel: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_AUTO_LOAD_LAST_MODEL] ?: true
    }

    val inferenceParams: Flow<InferenceParams> = context.dataStore.data.map { prefs ->
        val defaultThreads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 8)
        InferenceParams(
            contextSize = prefs[KEY_CONTEXT_SIZE] ?: 2048,
            temperature = prefs[KEY_TEMPERATURE] ?: 0.7f,
            topP = prefs[KEY_TOP_P] ?: 0.9f,
            topK = prefs[KEY_TOP_K] ?: 40,
            minP = prefs[KEY_MIN_P] ?: 0.05f,
            repeatPenalty = prefs[KEY_REPEAT_PENALTY] ?: 1.1f,
            threads = prefs[KEY_THREADS] ?: defaultThreads,
            batchSize = prefs[KEY_BATCH_SIZE] ?: 64,
            seed = prefs[KEY_SEED] ?: -1L
        )
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setActiveModel(path: String?, name: String?, architecture: String? = null) {
        context.dataStore.edit { prefs ->
            if (path != null) prefs[KEY_ACTIVE_MODEL_PATH] = path else prefs.remove(KEY_ACTIVE_MODEL_PATH)
            if (name != null) prefs[KEY_ACTIVE_MODEL_NAME] = name else prefs.remove(KEY_ACTIVE_MODEL_NAME)
            if (architecture != null) prefs[KEY_ACTIVE_MODEL_ARCH] = architecture else prefs.remove(KEY_ACTIVE_MODEL_ARCH)
        }
    }

    suspend fun setShowPerformanceStats(show: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_PERFORMANCE_STATS] = show }
    }

    suspend fun setPureBlackTheme(pureBlack: Boolean) {
        context.dataStore.edit { it[KEY_PURE_BLACK_THEME] = pureBlack }
    }

    suspend fun setAutoLoadLastModel(autoLoad: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_LOAD_LAST_MODEL] = autoLoad }
    }

    suspend fun updateInferenceParams(params: InferenceParams) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CONTEXT_SIZE] = params.contextSize
            prefs[KEY_TEMPERATURE] = params.temperature
            prefs[KEY_TOP_P] = params.topP
            prefs[KEY_TOP_K] = params.topK
            prefs[KEY_MIN_P] = params.minP
            prefs[KEY_REPEAT_PENALTY] = params.repeatPenalty
            prefs[KEY_THREADS] = params.threads
            prefs[KEY_BATCH_SIZE] = params.batchSize
            prefs[KEY_SEED] = params.seed
        }
    }
}
