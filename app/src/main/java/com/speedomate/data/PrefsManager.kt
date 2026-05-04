package com.speedomate.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("speedomate_prefs")

class PrefsManager(private val context: Context) {
    companion object {
        val KEY_METRIC      = booleanPreferencesKey("is_metric")
        val KEY_TRIP_DIST   = doublePreferencesKey("trip_distance")
        val KEY_MAX_SPEED   = floatPreferencesKey("max_speed")
        val KEY_SPEED_SUM   = floatPreferencesKey("speed_sum")
        val KEY_SPEED_COUNT = floatPreferencesKey("speed_count")
        val KEY_SPEED_LIMIT = intPreferencesKey("speed_limit_threshold")
    }

    val isMetric: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_METRIC] ?: true }

    val speedLimitThreshold: Flow<Int> = context.dataStore.data
        .map { it[KEY_SPEED_LIMIT] ?: 0 }

    val savedTripDistance: Flow<Double> = context.dataStore.data
        .map { it[KEY_TRIP_DIST] ?: 0.0 }

    val savedMaxSpeed: Flow<Float> = context.dataStore.data
        .map { it[KEY_MAX_SPEED] ?: 0f }

    val savedSpeedSum: Flow<Float> = context.dataStore.data
        .map { it[KEY_SPEED_SUM] ?: 0f }

    val savedSpeedCount: Flow<Float> = context.dataStore.data
        .map { it[KEY_SPEED_COUNT] ?: 0f }

    suspend fun setMetric(value: Boolean) {
        context.dataStore.edit { it[KEY_METRIC] = value }
    }

    suspend fun setSpeedLimitThreshold(value: Int) {
        context.dataStore.edit { it[KEY_SPEED_LIMIT] = value }
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

    suspend fun resetTripData() {
        context.dataStore.edit {
            it[KEY_TRIP_DIST]   = 0.0
            it[KEY_MAX_SPEED]   = 0f
            it[KEY_SPEED_SUM]   = 0f
            it[KEY_SPEED_COUNT] = 0f
        }
    }
}