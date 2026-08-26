package com.qmxz.pilotbot.map

import android.content.SharedPreferences
import com.qmxz.pilotbot.config.AppConfig
import com.qmxz.pilotbot.config.MapProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MapEngineTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var appConfig: AppConfig

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        appConfig = AppConfig(fakePrefs)
    }

    @Test
    fun testDefaultMapProviderIsAmap() {
        assertEquals(MapProvider.AMAP, appConfig.mapProvider)
        assertEquals("", appConfig.googleMapsApiKey)
    }

    @Test
    fun testSetAndGetMapProviderGoogle() {
        appConfig.mapProvider = MapProvider.GOOGLE
        assertEquals(MapProvider.GOOGLE, appConfig.mapProvider)

        appConfig.mapProvider = MapProvider.AMAP
        assertEquals(MapProvider.AMAP, appConfig.mapProvider)
    }

    @Test
    fun testGoogleMapsApiKeyPersistence() {
        appConfig.googleMapsApiKey = "AIzaSyTest_Google_Maps_Api_Key_12345"
        assertEquals("AIzaSyTest_Google_Maps_Api_Key_12345", appConfig.googleMapsApiKey)
    }

    @Test
    fun testInvalidMapProviderFallback() {
        fakePrefs.edit().putString("map_provider", "UNKNOWN_PROVIDER").apply()
        assertEquals(MapProvider.AMAP, appConfig.mapProvider)
    }

    @Test
    fun testParseGoogleDirectionsSuccess() {
        val json = """
        {
          "status": "OK",
          "geocoded_waypoints": [],
          "routes": [
            {
              "summary": "Lebuhraya Persekutuan / Route 2",
              "legs": [
                {
                  "distance": {
                    "text": "14.2 km",
                    "value": 14200
                  },
                  "duration": {
                    "text": "18 mins",
                    "value": 1080
                  },
                  "start_address": "Petaling Jaya, Selangor, Malaysia",
                  "end_address": "Kuala Lumpur, Federal Territory of Kuala Lumpur, Malaysia"
                }
              ],
              "overview_polyline": {
                "points": "_p~iF~ps|U_ulLnnqC_mqNvxq@"
              }
            }
          ]
        }
        """.trimIndent()

        val result = GoogleMapEngine.parseDirectionsJson(json)
        assertTrue(result.isSuccess)

        val summary = result.getOrThrow()
        assertEquals(14200L, summary.totalDistanceMeters)
        assertEquals(1080L, summary.totalDurationSeconds)
        assertEquals("Lebuhraya Persekutuan / Route 2", summary.routeName)
        assertTrue(summary.polylinePoints.isNotEmpty())
    }

    @Test
    fun testParseGoogleDirectionsMultipleLegs() {
        val json = """
        {
          "status": "OK",
          "routes": [
            {
              "summary": "E1 / North-South Expressway",
              "legs": [
                {
                  "distance": { "value": 5000 },
                  "duration": { "value": 300 }
                },
                {
                  "distance": { "value": 7500 },
                  "duration": { "value": 450 }
                }
              ],
              "overview_polyline": {
                "points": ""
              }
            }
          ]
        }
        """.trimIndent()

        val result = GoogleMapEngine.parseDirectionsJson(json)
        assertTrue(result.isSuccess)

        val summary = result.getOrThrow()
        assertEquals(12500L, summary.totalDistanceMeters)
        assertEquals(750L, summary.totalDurationSeconds)
        assertEquals("E1 / North-South Expressway", summary.routeName)
        assertTrue(summary.polylinePoints.isEmpty())
    }

    @Test
    fun testParseGoogleDirectionsErrorStatuses() {
        val deniedJson = """
        {
          "status": "REQUEST_DENIED",
          "error_message": "The provided API key is invalid."
        }
        """.trimIndent()

        val deniedResult = GoogleMapEngine.parseDirectionsJson(deniedJson)
        assertTrue(deniedResult.isFailure)
        assertTrue(deniedResult.exceptionOrNull()?.message?.contains("API key is invalid") == true)

        val zeroResultsJson = """
        {
          "status": "ZERO_RESULTS",
          "routes": []
        }
        """.trimIndent()

        val zeroResult = GoogleMapEngine.parseDirectionsJson(zeroResultsJson)
        assertTrue(zeroResult.isFailure)
    }

    @Test
    fun testParseGooglePlacesSuccess() {
        val json = """
        {
          "status": "OK",
          "results": [
            {
              "name": "Petronas Twin Towers",
              "formatted_address": "Kuala Lumpur City Centre, 50088 Kuala Lumpur, Malaysia",
              "geometry": {
                "location": {
                  "lat": 3.15785,
                  "lng": 101.71185
                }
              }
            },
            {
              "name": "Suria KLCC",
              "formatted_address": "241, Suria KLCC, Kuala Lumpur City Centre, Malaysia",
              "geometry": {
                "location": {
                  "lat": 3.15812,
                  "lng": 101.71150
                }
              }
            }
          ]
        }
        """.trimIndent()

        val result = GoogleMapEngine.parsePlacesJson(json)
        assertTrue(result.isSuccess)

        val places = result.getOrThrow()
        assertEquals(2, places.size)
        assertEquals("Petronas Twin Towers", places[0].title)
        assertEquals("Kuala Lumpur City Centre, 50088 Kuala Lumpur, Malaysia", places[0].snippet)
        assertEquals(3.15785, places[0].lat, 0.0001)
        assertEquals(101.71185, places[0].lng, 0.0001)

        assertEquals("Suria KLCC", places[1].title)
    }

    @Test
    fun testParseGooglePlacesZeroResults() {
        val json = """
        {
          "status": "ZERO_RESULTS",
          "results": []
        }
        """.trimIndent()

        val result = GoogleMapEngine.parsePlacesJson(json)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun testParseGooglePlacesError() {
        val json = """
        {
          "status": "OVER_QUERY_LIMIT",
          "error_message": "You have exceeded your daily request quota for this API."
        }
        """.trimIndent()

        val result = GoogleMapEngine.parsePlacesJson(json)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("OVER_QUERY_LIMIT") == true)
    }

    @Test
    fun testDecodePolyline() {
        // Standard sample polyline: (38.5, -120.2), (40.7, -120.95), (43.252, -126.453)
        val encoded = "_p~iF~ps|U_ulLnnqC_mqNvxq@"
        val points = GoogleMapEngine.decodePolyline(encoded)

        assertEquals(3, points.size)
        assertEquals(38.5, points[0].lat, 0.0001)
        assertEquals(-120.2, points[0].lng, 0.0001)
        assertEquals(40.7, points[1].lat, 0.0001)
        assertEquals(-120.95, points[1].lng, 0.0001)
        assertEquals(43.252, points[2].lat, 0.0001)
        assertEquals(-121.21012, points[2].lng, 0.0001)
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data

        override fun getString(key: String?, defValue: String?): String? =
            (data[key] as? String) ?: defValue

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST")
            (data[key] as? MutableSet<String>) ?: defValues

        override fun getInt(key: String?, defValue: Int): Int =
            (data[key] as? Int) ?: defValue

        override fun getLong(key: String?, defValue: Long): Long =
            (data[key] as? Long) ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float =
            (data[key] as? Float) ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            (data[key] as? Boolean) ?: defValue

        override fun contains(key: String?): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) {}

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) {}

        private inner class FakeEditor : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private val removed = mutableSetOf<String>()
            private var clear = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                key?.let { temp[it] = value }
                return this
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                key?.let { temp[it] = values }
                return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                key?.let { temp[it] = value }
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                key?.let { temp[it] = value }
                return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                key?.let { temp[it] = value }
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                key?.let { temp[it] = value }
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                key?.let { removed.add(it) }
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clear = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clear) {
                    data.clear()
                }
                removed.forEach { data.remove(it) }
                temp.forEach { (k, v) ->
                    if (v == null) data.remove(k) else data[k] = v
                }
            }
        }
    }
}
