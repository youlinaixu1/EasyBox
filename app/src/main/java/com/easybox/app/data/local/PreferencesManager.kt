package com.easybox.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "easybox_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_NICKNAME = stringPreferencesKey("nickname")
        private val KEY_SPINNER_PRESETS = stringPreferencesKey("spinner_presets")
    }

    val nickname: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_NICKNAME] ?: ""
    }

    val spinnerPresets: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SPINNER_PRESETS] ?: "[]"
    }

    suspend fun setNickname(name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NICKNAME] = name
        }
    }

    suspend fun setSpinnerPresets(json: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SPINNER_PRESETS] = json
        }
    }
}
