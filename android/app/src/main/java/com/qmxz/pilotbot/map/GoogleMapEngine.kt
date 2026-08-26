package com.qmxz.pilotbot.map

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
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
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Google Maps & Global interactive vector map implementation of [MapEngine].
 *
 * Utilizes Google Directions REST API and Google Places API for route calculations and POI search,
 * and renders interactive global tiles (with Malaysian / SE Asian / Global English road networks)
 * with location markers, polylines, and tap-to-fullscreen support.
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
    private var webView: WebView? = null
    private var onMapClickCallback: (() -> Unit)? = null
    private var mapLoaded = false
    private var pendingJsCommands = mutableListOf<String>()

    override fun init(container: ViewGroup, savedInstanceState: Bundle?) {
        // Container setup if needed
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun bindWebView(view: WebView, onMapClick: () -> Unit) {
        this.webView = view
        this.onMapClickCallback = onMapClick

        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        view.addJavascriptInterface(object {
            @JavascriptInterface
            fun onMapTapped() {
                mainHandler.post { onMapClickCallback?.invoke() }
            }
        }, "AndroidHost")

        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                mapLoaded = true
                currentLocation?.let { updateLocation(it.lat, it.lng) }
                for (cmd in pendingJsCommands) {
                    view?.evaluateJavascript(cmd, null)
                }
                pendingJsCommands.clear()
            }
        }

        loadMapHtml()
    }

    override fun updateLocation(lat: Double, lng: Double) {
        currentLocation = GeoPoint(lat, lng)
        val js = "if (window.updateUserLocation) { updateUserLocation($lat, $lng); }"
        executeJs(js)
    }

    fun drawRoute(route: RouteSummary, start: GeoPoint, dest: GeoPoint, destTitle: String) {
        val pointsArr = JSONArray()
        for (pt in route.polylinePoints) {
            val arr = JSONArray()
            arr.put(pt.lat)
            arr.put(pt.lng)
            pointsArr.put(arr)
        }
        val safeTitle = JSONObject.quote(destTitle)
        val js = "if (window.drawRoute) { drawRoute(${pointsArr}, ${start.lat}, ${start.lng}, ${dest.lat}, ${dest.lng}, $safeTitle); }"
        executeJs(js)
    }

    fun clearRoute() {
        executeJs("if (window.clearRoute) { clearRoute(); }")
    }

    private fun executeJs(js: String) {
        mainHandler.post {
            val wv = webView
            if (wv != null && mapLoaded) {
                wv.evaluateJavascript(js, null)
            } else {
                pendingJsCommands.add(js)
            }
        }
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
    }

    override fun stopNavigation() {
        isNavigating = false
        clearRoute()
    }

    override fun onResume() {}

    override fun onPause() {}

    override fun onDestroy() {
        scope.cancel()
        webView = null
    }

    private fun loadMapHtml() {
        val initialLat = currentLocation?.lat ?: 3.1390
        val initialLng = currentLocation?.lng ?: 101.6869

        val html = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
    <style>
        body, html, #map {
            margin: 0; padding: 0; width: 100%; height: 100%; background: #0f172a;
        }
        .user-marker-pulse {
            width: 20px; height: 20px; border-radius: 50%;
            background: #2563EB; border: 3px solid #FFFFFF;
            box-shadow: 0 0 10px rgba(37,99,235,0.8);
        }
        .leaflet-control-attribution { display: none !important; }
    </style>
</head>
<body>
    <div id="map"></div>
    <script>
        var map = L.map('map', { zoomControl: false }).setView([$initialLat, $initialLng], 14);

        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19
        }).addTo(map);

        var userMarker = null;
        var routePolyline = null;
        var destMarker = null;

        function updateUserLocation(lat, lng) {
            if (!userMarker) {
                var pulseIcon = L.divIcon({
                    className: 'user-marker-pulse',
                    iconSize: [20, 20],
                    iconAnchor: [10, 10]
                });
                userMarker = L.marker([lat, lng], { icon: pulseIcon }).addTo(map);
                map.setView([lat, lng], 14);
            } else {
                userMarker.setLatLng([lat, lng]);
            }
        }

        function drawRoute(points, startLat, startLng, destLat, destLng, destTitle) {
            clearRoute();
            if (points && points.length > 0) {
                routePolyline = L.polyline(points, {
                    color: '#2563EB', weight: 6, opacity: 0.9, lineJoin: 'round'
                }).addTo(map);
                map.fitBounds(routePolyline.getBounds(), { padding: [50, 50] });
            } else {
                map.setView([destLat, destLng], 14);
            }

            destMarker = L.marker([destLat, destLng]).addTo(map);
            if (destTitle) {
                destMarker.bindPopup('<b>' + destTitle + '</b>').openPopup();
            }
        }

        function clearRoute() {
            if (routePolyline) {
                map.removeLayer(routePolyline);
                routePolyline = null;
            }
            if (destMarker) {
                map.removeLayer(destMarker);
                destMarker = null;
            }
        }

        map.on('click', function(e) {
            if (window.AndroidHost && window.AndroidHost.onMapTapped) {
                window.AndroidHost.onMapTapped();
            }
        });
    </script>
</body>
</html>
        """.trimIndent()

        webView?.loadDataWithBaseURL("https://maps.googleapis.com", html, "text/html", "UTF-8", null)
    }

    companion object {
        fun parseDirectionsJson(jsonString: String): Result<RouteSummary> {
            return runCatching {
                val root = JSONObject(jsonString)
                val status = root.optString("status", "")
                if (status != "OK") {
                    val detail = root.optString("error_message", "")
                    val errorMsg = if (detail.isNotBlank()) "Google Directions API $status: $detail" else "Google Directions API status: $status"
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
