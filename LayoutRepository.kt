package com.touchoverlay.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.touchoverlay.app.model.LayoutConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "touch_overlay_prefs")

/**
 * Persists the button/joystick layout using Jetpack DataStore (Preferences).
 * The whole [LayoutConfig] is serialized to a single JSON string under one key,
 * which keeps reads/writes atomic and simple.
 */
class LayoutRepository(context: Context) {

    private val appContext = context.applicationContext

    private companion object {
        val LAYOUT_KEY = stringPreferencesKey("layout_json")
    }

    /** Emits the current layout, and re-emits whenever it changes. */
    val layoutFlow: Flow<LayoutConfig> = appContext.dataStore.data.map { prefs ->
        val json = prefs[LAYOUT_KEY]
        if (json.isNullOrBlank()) {
            LayoutConfig.default()
        } else {
            try {
                LayoutConfig.fromJson(JSONObject(json))
            } catch (e: Exception) {
                // Corrupt or incompatible saved data: fall back to defaults rather than crash.
                LayoutConfig.default()
            }
        }
    }

    /** One-shot read of the current layout (or the default if nothing was ever saved). */
    suspend fun getLayout(): LayoutConfig = layoutFlow.first()

    suspend fun saveLayout(layout: LayoutConfig) {
        appContext.dataStore.edit { prefs ->
            prefs[LAYOUT_KEY] = layout.toJson().toString()
        }
    }

    /** Overwrites the saved layout with the built-in defaults and returns it. */
    suspend fun resetLayout(): LayoutConfig {
        val default = LayoutConfig.default()
        saveLayout(default)
        return default
    }
}
