package com.qmxz.pilotbot.map

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.qmxz.pilotbot.config.AppConfig
import com.qmxz.pilotbot.config.MapProvider
import com.qmxz.pilotbot.search.PlaceResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Google Maps implementation of [MapEngine].
 *
 * Utilizes Google Directions REST API and Google Places API to provide global route calculation,
 * distance, duration, polyline decoding, and POI search for locations outside China (e.g. Malaysia,
 * North America, Europe, SE Asia) without AMap domestic road network restrictions.
 */
class GoogleMapEngine(
    private val context: Context,
    private val apiKeyProvider: () -> String = { AppConfig(context).googleMapsApiKey },
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
) : MapEngine {

    override val provider: MapProvider = MapProvider.GOOGLE

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentLocation: GeoPoint? = null
    private var isNavigating: Boolean = false
    private var containerView: FrameLayout? = null
    private var infoTextView: TextView? = null

    override fun init(container: ViewGroup, savedInstanceState: Bundle?) {
        val root = FrameLayout(container.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(0xFF1E293B.toInt())
        }

        val tv = TextView(container.context).apply {
            text = "🌍 Google Maps 全球导航模式\n(Directions & Places REST API)"
            setTextColor(0xFFE2E8F0.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            )
        }

        root.addView(tv)
        container.addView(root)
        containerView = root
        infoTextView = tv
    }

    override fun updateLocation(lat: Double, lng: Double) {
        currentLocation = GeoPoint(lat, lng)
        infoTextView?.text = String.format(
            "🌍 Google Maps 全球模式\n当前位置: %.5f, %.5f",
            lat,
            lng,
        )
    }

    override fun searchPlaces(
        keyword: String,
        city: String?,
        callback: (Result<List<PlaceResult>>) -> Unit,
    ) {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) {
            callback(Result.failure(IllegalStateException("Google Maps API Key 未配置，请在设置中填入有效的 API Key")))
            return
        }

        val queryText = if (!city.isNullOrBlank()) "$keyword $city" else keyword

        scope.launch(Dispatchers.IO) {
            val encodedQuery = runCatching { URLEncoder.encode(queryText, "UTF-8") }.getOrDefault(queryText)
            val locationParam = currentLocation?.let { "&location=${it.lat},${it.lng}&radius=50000" } ?: ""
            val url = "https://maps.googleapis.com/maps/api/place/textsearch/json?query=$encodedQuery$locationParam&key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val result = try {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Result.failure(IllegalStateException("Google Places HTTP ${response.code}: ${response.message}"))
                    } else {
                        val body = response.body?.string().orEmpty()
                        parsePlacesJson(body)
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }

            withContext(Dispatchers.Main) {
                callback(result)
            }
        }
    }

    override fun calculateRoute(
        start: GeoPoint,
        dest: GeoPoint,
        callback: (Result<RouteSummary>) -> Unit,
    ) {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) {
            callback(Result.failure(IllegalStateException("Google Maps API Key 未配置，请在设置中填入有效的 API Key")))
            return
        }

        scope.launch(Dispatchers.IO) {
            val url = "https://maps.googleapis.com/maps/api/directions/json?origin=${start.lat},${start.lng}&destination=${dest.lat},${dest.lng}&mode=driving&key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val result = try {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Result.failure(IllegalStateException("Google Directions HTTP ${response.code}: ${response.message}"))
                    } else {
                        val body = response.body?.string().orEmpty()
                        parseDirectionsJson(body)
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }

            withContext(Dispatchers.Main) {
                callback(result)
            }
        }
    }

    override fun startNavigation() {
        isNavigating = true
        infoTextView?.text = "🌍 Google Maps 全球导航中..."
    }

    override fun stopNavigation() {
        isNavigating = false
        infoTextView?.text = "🌍 Google Maps 导航已结束"
    }

    override fun onResume() {}

    override fun onPause() {}

    override fun onDestroy() {
        scope.cancel()
        containerView = null
        infoTextView = null
    }

    companion object {
        /**
         * Parses Google Directions API JSON response into [RouteSummary].
         */
        fun parseDirectionsJson(jsonString: String): Result<RouteSummary> {
            return runCatching {
                val root = JSONObject(jsonString)
                val status = root.optString("status", "")
                if (status != "OK") {
                    val errorMsg = root.optString("error_message", "Google Directions API status: $status")
                    throw IllegalStateException(errorMsg)
                }

                val routes = root.getJSONArray("routes")
                if (routes.length() == 0) {
                    throw IllegalStateException("Google Directions 返回空路线列表")
                }

                val firstRoute = routes.getJSONObject(0)
                val summary = firstRoute.optString("summary", "Google 推荐路线")
                val legs = firstRoute.optJSONArray("legs")

                var totalDistance = 0L
                var totalDuration = 0L

                if (legs != null) {
                    for (i in 0 until legs.length()) {
                        val leg = legs.getJSONObject(i)
                        val distObj = leg.optJSONObject("distance")
                        val durObj = leg.optJSONObject("duration")
                        if (distObj != null) totalDistance += distObj.optLong("value", 0L)
                        if (durObj != null) totalDuration += durObj.optLong("value", 0L)
                    }
                }

                val overviewPolyline = firstRoute.optJSONObject("overview_polyline")
                val encodedPoints = overviewPolyline?.optString("points", "").orEmpty()
                val polylinePoints = if (encodedPoints.isNotBlank()) {
                    decodePolyline(encodedPoints)
                } else {
                    emptyList()
                }

                RouteSummary(
                    totalDistanceMeters = totalDistance,
                    totalDurationSeconds = totalDuration,
                    routeName = if (summary.isNotBlank()) summary else "Google 推荐路线",
                    polylinePoints = polylinePoints,
                )
            }
        }

        /**
         * Parses Google Places API JSON response into a list of [PlaceResult].
         */
        fun parsePlacesJson(jsonString: String): Result<List<PlaceResult>> {
            return runCatching {
                val root = JSONObject(jsonString)
                val status = root.optString("status", "")
                if (status == "ZERO_RESULTS") {
                    return@runCatching emptyList()
                }
                if (status != "OK") {
                    val detail = root.optString("error_message", "")
                    val errorMsg = if (detail.isNotBlank()) "Google Places API $status: $detail" else "Google Places API status: $status"
                    throw IllegalStateException(errorMsg)
                }

                val results = root.optJSONArray("results") ?: return@runCatching emptyList()
                val places = mutableListOf<PlaceResult>()
                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    val name = item.optString("name", "")
                    val address = item.optString("formatted_address", item.optString("vicinity", ""))
                    val geometry = item.optJSONObject("geometry")
                    val location = geometry?.optJSONObject("location")
                    val lat = location?.optDouble("lat", 0.0) ?: 0.0
                    val lng = location?.optDouble("lng", 0.0) ?: 0.0
                    places.add(PlaceResult(title = name, snippet = address, lat = lat, lng = lng))
                }
                places
            }
        }

        /**
         * Decodes Google Encoded Polyline algorithm into coordinate points.
         */
        fun decodePolyline(encoded: String): List<GeoPoint> {
            val poly = ArrayList<GeoPoint>()
            var index = 0
            val len = encoded.length
            var lat = 0
            var lng = 0

            while (index < len) {
                var b: Int
                var shift = 0
                var result = 0
                do {
                    b = encoded[index++].code - 63
                    result = result or ((b and 0x1f) shl shift)
                    shift += 5
                } while (b >= 0x20)
                val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
                lat += dlat

                shift = 0
                result = 0
                do {
                    b = encoded[index++].code - 63
                    result = result or ((b and 0x1f) shl shift)
                    shift += 5
                } while (b >= 0x20)
                val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
                lng += dlng

                poly.add(GeoPoint(lat.toDouble() / 1E5, lng.toDouble() / 1E5))
            }

            return poly
        }
    }
}
