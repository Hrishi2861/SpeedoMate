// data/PrefsManager.kt
package com.speedomate.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("speedomate_prefs")

class PrefsManager(private val context: Context) {
    companion object {
        val KEY_METRIC = booleanPreferencesKey("is_metric")
    }

    val isMetric: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_METRIC] ?: true } // default: km/h

    suspend fun setMetric(value: Boolean) {
        context.dataStore.edit { it[KEY_METRIC] = value }
    }
}