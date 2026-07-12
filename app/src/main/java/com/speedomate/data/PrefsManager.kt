package com.speedomate.data

import android.content.Context
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("speedomate_prefs")

object ThemeColors {
    val PRESET_COLORS = listOf(
        "#00E5FF" to "Cyan",
        "#39FF14" to "Neon Green",
        "#FF8C00" to "Amber",
        "#FF2D78" to "Hot Pink",
        "#C6F135" to "Lime",
        "#FFD700" to "Gold",
    )
}

class PrefsManager(private val context: Context) {
    companion object {
        val KEY_METRIC       = booleanPreferencesKey("is_metric")
        val KEY_TRIP_DIST    = doublePreferencesKey("trip_distance")
        val KEY_MAX_SPEED    = floatPreferencesKey("max_speed")
        val KEY_SPEED_SUM    = floatPreferencesKey("speed_sum")
        val KEY_SPEED_COUNT  = floatPreferencesKey("speed_count")
        val KEY_SPEED_LIMIT  = intPreferencesKey("speed_limit_threshold")
        val KEY_ACCENT_COLOR = stringPreferencesKey("accent_color")

        val KEY_TRIP_START_TIME = longPreferencesKey("trip_start_time")
        val KEY_MIN_ALTITUDE    = doublePreferencesKey("min_altitude")
        val KEY_MAX_ALTITUDE    = doublePreferencesKey("max_altitude")
        val KEY_SPEED_HISTORY   = stringPreferencesKey("speed_history")
        val KEY_ALT_HISTORY     = stringPreferencesKey("altitude_history")
        val KEY_LAST_MOVEMENT   = longPreferencesKey("last_movement_time")

        val KEY_AUTO_SAVE_ENABLED    = booleanPreferencesKey("auto_save_enabled")
        val KEY_AUTO_SAVE_IDLE_MINS  = intPreferencesKey("auto_save_idle_mins")
        val KEY_AUTO_START_AA        = booleanPreferencesKey("auto_start_android_auto")
    }

    val isMetric: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_METRIC] ?: true }

    val speedLimitThreshold: Flow<Int> = context.dataStore.data
        .map { it[KEY_SPEED_LIMIT] ?: 0 }

    val accentColor: Flow<String> = context.dataStore.data
        .map { it[KEY_ACCENT_COLOR] ?: "#00E5FF" }

    val accentColorInt: Flow<Int> = accentColor.map { Color.parseColor(it) }

    val savedTripDistance: Flow<Double> = context.dataStore.data
        .map { it[KEY_TRIP_DIST] ?: 0.0 }

    val savedMaxSpeed: Flow<Float> = context.dataStore.data
        .map { it[KEY_MAX_SPEED] ?: 0f }

    val savedSpeedSum: Flow<Float> = context.dataStore.data
        .map { it[KEY_SPEED_SUM] ?: 0f }

    val savedSpeedCount: Flow<Float> = context.dataStore.data
        .map { it[KEY_SPEED_COUNT] ?: 0f }

    val savedTripStartTime: Flow<Long> = context.dataStore.data
        .map { it[KEY_TRIP_START_TIME] ?: 0L }

    val savedMinAltitude: Flow<Double> = context.dataStore.data
        .map { it[KEY_MIN_ALTITUDE] ?: Double.MAX_VALUE }

    val savedMaxAltitude: Flow<Double> = context.dataStore.data
        .map { it[KEY_MAX_ALTITUDE] ?: Double.MIN_VALUE }

    val savedSpeedHistory: Flow<String> = context.dataStore.data
        .map { it[KEY_SPEED_HISTORY] ?: "[]" }

    val savedAltitudeHistory: Flow<String> = context.dataStore.data
        .map { it[KEY_ALT_HISTORY] ?: "[]" }

    val savedLastMovementTime: Flow<Long> = context.dataStore.data
        .map { it[KEY_LAST_MOVEMENT] ?: 0L }

    val autoSaveEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_AUTO_SAVE_ENABLED] ?: false }

    val autoSaveIdleMinutes: Flow<Int> = context.dataStore.data
        .map { it[KEY_AUTO_SAVE_IDLE_MINS] ?: 15 }

    val autoStartAndroidAuto: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_AUTO_START_AA] ?: false }

    suspend fun setMetric(value: Boolean) {
        context.dataStore.edit { it[KEY_METRIC] = value }
    }

    suspend fun setSpeedLimitThreshold(value: Int) {
        context.dataStore.edit { it[KEY_SPEED_LIMIT] = value }
    }

    suspend fun setAccentColor(value: String) {
        context.dataStore.edit { it[KEY_ACCENT_COLOR] = value }
    }

    suspend fun saveTripData(
        distanceKm: Double,
        maxSpeed: Float,
        speedSum: Float,
        speedCount: Float
    ) {
        context.dataStore.edit {
            it[KEY_TRIP_DIST]   = distanceKm
            it[KEY_MAX_SPEED]   = maxSpeed
            it[KEY_SPEED_SUM]   = speedSum
            it[KEY_SPEED_COUNT] = speedCount
        }
    }

    suspend fun saveTripStartTime(time: Long) {
        context.dataStore.edit { it[KEY_TRIP_START_TIME] = time }
    }

    suspend fun saveAltBounds(minAlt: Double, maxAlt: Double) {
        context.dataStore.edit {
            it[KEY_MIN_ALTITUDE] = minAlt
            it[KEY_MAX_ALTITUDE] = maxAlt
        }
    }

    suspend fun saveHistory(speedJson: String, altJson: String) {
        context.dataStore.edit {
            it[KEY_SPEED_HISTORY] = speedJson
            it[KEY_ALT_HISTORY]   = altJson
        }
    }

    suspend fun saveLastMovementTime(time: Long) {
        context.dataStore.edit { it[KEY_LAST_MOVEMENT] = time }
    }

    suspend fun setAutoSaveEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_SAVE_ENABLED] = value }
    }

    suspend fun setAutoSaveIdleMinutes(value: Int) {
        context.dataStore.edit { it[KEY_AUTO_SAVE_IDLE_MINS] = value }
    }

    suspend fun setAutoStartAndroidAuto(value: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_START_AA] = value }
    }

    suspend fun resetTripData() {
        context.dataStore.edit {
            it[KEY_TRIP_DIST]      = 0.0
            it[KEY_MAX_SPEED]      = 0f
            it[KEY_SPEED_SUM]      = 0f
            it[KEY_SPEED_COUNT]    = 0f
            it[KEY_TRIP_START_TIME] = 0L
            it[KEY_MIN_ALTITUDE]   = Double.MAX_VALUE
            it[KEY_MAX_ALTITUDE]   = Double.MIN_VALUE
            it[KEY_SPEED_HISTORY]  = "[]"
            it[KEY_ALT_HISTORY]    = "[]"
            it[KEY_LAST_MOVEMENT]  = 0L
        }
    }
}
