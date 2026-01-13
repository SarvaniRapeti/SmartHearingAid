package com.hearing.hearingtest

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore("saved_tests")

object SavedTestsManager {
    private val gson = Gson()
    private val TESTS_KEY = stringPreferencesKey("tests_json")

    // data class to store
    data class SavedRun(
        val name: String,
        val ear: String,
        val timestamp: Long,
        val thresholds: List<FreqPoint>
    )

    // read all saved tests
    fun getAll(context: Context): Flow<List<SavedRun>> {
        return context.dataStore.data.map { prefs ->
            val json = prefs[TESTS_KEY] ?: "{}"
            val type = object : TypeToken<Map<String, SavedRun>>() {}.type
            val map: Map<String, SavedRun> = gson.fromJson(json, type)
            map.values.sortedByDescending { it.timestamp }
        }
    }

    // save a test
    suspend fun saveTest(context: Context, name: String, ear: String, list: List<FreqPoint>) {
        context.dataStore.edit { prefs ->
            val existingJson = prefs[TESTS_KEY] ?: "{}"
            val type = object : TypeToken<MutableMap<String, SavedRun>>() {}.type
            val map: MutableMap<String, SavedRun> = gson.fromJson(existingJson, type)

            map[name] = SavedRun(
                name = name,
                ear = ear,
                timestamp = System.currentTimeMillis(),
                thresholds = list
            )

            prefs[TESTS_KEY] = gson.toJson(map)
        }
    }

    // delete one test
    suspend fun deleteTest(context: Context, name: String) {
        context.dataStore.edit { prefs ->
            val existingJson = prefs[TESTS_KEY] ?: "{}"
            val type = object : TypeToken<MutableMap<String, SavedRun>>() {}.type
            val map: MutableMap<String, SavedRun> = gson.fromJson(existingJson, type)
            map.remove(name)
            prefs[TESTS_KEY] = gson.toJson(map)
        }
    }
}
