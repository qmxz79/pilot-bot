package com.qmxz.pilotbot.search

import android.content.Context
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItem
import com.amap.api.services.geocoder.GeocodeQuery
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeResult
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch

data class PlaceResult(
    val title: String,
    val snippet: String,
    val lat: Double,
    val lng: Double,
)

/**
 * Robust search engine over Amap SDK.
 * Features multi-stage cascading search:
 * 1. Local city POI search (with cityLimit disabled for fuzzy boundary tolerance)
 * 2. Nationwide POI fallback (for other cities or cross-region searches)
 * 3. GeocodeSearch (resolves exact street addresses, building numbers, intersections, towns/villages)
 */
class PlaceSearch(private val context: Context) {

    /**
     * Cascading search: Local POI -> Nationwide POI -> GeocodeSearch.
     */
    fun search(keyword: String, cityCode: String?, onResult: (Result<List<PlaceResult>>) -> Unit) {
        val cleanKeyword = keyword.trim()
        if (cleanKeyword.isEmpty()) {
            onResult(Result.success(emptyList()))
            return
        }

        // Stage 1: POI search in specified or current city
        searchPoi(cleanKeyword, cityCode) { cityResult ->
            cityResult.onSuccess { list ->
                if (list.isNotEmpty()) {
                    onResult(Result.success(list))
                } else if (!cityCode.isNullOrBlank()) {
                    // Stage 2: Fall back to Nationwide POI search if city search was empty
                    searchPoi(cleanKeyword, "") { nationResult ->
                        nationResult.onSuccess { nationList ->
                            if (nationList.isNotEmpty()) {
                                onResult(Result.success(nationList))
                            } else {
                                // Stage 3: POI returned empty, resolve via GeocodeSearch (for street/house addresses)
                                searchGeocode(cleanKeyword, cityCode, onResult)
                            }
                        }.onFailure {
                            searchGeocode(cleanKeyword, cityCode, onResult)
                        }
                    }
                } else {
                    // Stage 3: Direct GeocodeSearch
                    searchGeocode(cleanKeyword, null, onResult)
                }
            }.onFailure { err ->
                // If POI failed, still attempt GeocodeSearch before giving up
                searchGeocode(cleanKeyword, cityCode) { geocodeResult ->
                    if (geocodeResult.isSuccess && geocodeResult.getOrNull()?.isNotEmpty() == true) {
                        onResult(geocodeResult)
                    } else {
                        onResult(Result.failure(err))
                    }
                }
            }
        }
    }

    /** POI keyword query */
    private fun searchPoi(
        keyword: String,
        cityCode: String?,
        onResult: (Result<List<PlaceResult>>) -> Unit,
    ) {
        val query = try {
            PoiSearch.Query(keyword, "", cityCode ?: "").apply {
                pageSize = 15
                // Allow fuzzy search across border
                setCityLimit(false)
            }
        } catch (e: Exception) {
            onResult(Result.failure(e))
            return
        }

        val poiSearch = try {
            PoiSearch(context.applicationContext, query)
        } catch (e: Exception) {
            onResult(Result.failure(e))
            return
        }

        poiSearch.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
            override fun onPoiSearched(result: PoiResult?, rCode: Int) {
                if (rCode != 1000) {
                    val msg = formatAmapError(rCode)
                    onResult(Result.failure(IllegalStateException(msg)))
                    return
                }
                val list = result?.pois.orEmpty().mapNotNull { poi ->
                    val point = poi.latLonPoint ?: return@mapNotNull null
                    PlaceResult(
                        title = poi.title,
                        snippet = poi.snippet?.ifBlank { poi.cityName + poi.adName + poi.title } ?: poi.title,
                        lat = point.latitude,
                        lng = point.longitude,
                    )
                }
                onResult(Result.success(list))
            }

            override fun onPoiItemSearched(poiItem: PoiItem?, rCode: Int) {}
        })
        poiSearch.searchPOIAsyn()
    }

    /** Geocode search: specifically resolves exact street numbers, villages, buildings and intersections */
    private fun searchGeocode(
        keyword: String,
        cityCode: String?,
        onResult: (Result<List<PlaceResult>>) -> Unit,
    ) {
        try {
            val geocoder = GeocodeSearch(context.applicationContext)
            val query = GeocodeQuery(keyword, cityCode ?: "")
            geocoder.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                override fun onGeocodeSearched(result: GeocodeResult?, rCode: Int) {
                    if (rCode == 1000 && !result?.geocodeAddressList.isNullOrEmpty()) {
                        val list = result!!.geocodeAddressList.mapNotNull { addr ->
                            val pt = addr.latLonPoint ?: return@mapNotNull null
                            val title = addr.building?.takeIf { it.isNotBlank() }
                                ?: addr.neighborhood?.takeIf { it.isNotBlank() }
                                ?: addr.formatAddress ?: keyword
                            PlaceResult(
                                title = title,
                                snippet = addr.formatAddress ?: keyword,
                                lat = pt.latitude,
                                lng = pt.longitude,
                            )
                        }
                        onResult(Result.success(list))
                    } else if (!cityCode.isNullOrBlank()) {
                        // Try nationwide geocode
                        searchGeocode(keyword, null, onResult)
                    } else {
                        onResult(Result.success(emptyList()))
                    }
                }

                override fun onRegeocodeSearched(result: RegeocodeResult?, rCode: Int) {}
            })
            geocoder.getFromLocationNameAsyn(query)
        } catch (e: Exception) {
            onResult(Result.failure(e))
        }
    }

    /** POIs around a center point ([radiusMeters] in metres), with optional [keyword]. */
    fun searchAround(
        lat: Double,
        lng: Double,
        radiusMeters: Int = 3000,
        keyword: String = "",
        onResult: (Result<List<PlaceResult>>) -> Unit,
    ) {
        val query = try {
            PoiSearch.Query(keyword, "", "").apply { pageSize = 20 }
        } catch (e: Exception) {
            onResult(Result.failure(e))
            return
        }
        val poiSearch = try {
            PoiSearch(context.applicationContext, query).apply {
                bound = PoiSearch.SearchBound(LatLonPoint(lat, lng), radiusMeters)
            }
        } catch (e: Exception) {
            onResult(Result.failure(e))
            return
        }

        poiSearch.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
            override fun onPoiSearched(result: PoiResult?, rCode: Int) {
                if (rCode != 1000) {
                    onResult(Result.failure(IllegalStateException(formatAmapError(rCode))))
                    return
                }
                val list = result?.pois.orEmpty().mapNotNull { poi ->
                    val point = poi.latLonPoint ?: return@mapNotNull null
                    PlaceResult(
                        title = poi.title,
                        snippet = poi.snippet,
                        lat = point.latitude,
                        lng = point.longitude,
                    )
                }
                onResult(Result.success(list))
            }

            override fun onPoiItemSearched(poiItem: PoiItem?, rCode: Int) {}
        })
        poiSearch.searchPOIAsyn()
    }

    private fun formatAmapError(code: Int): String {
        return when (code) {
            1001 -> "高德开发者签名(SHA1)或包名未通过验证 (代码 $code)"
            1002 -> "高德 Key 无效或未开通对应搜索服务 (代码 $code)"
            1003, 1009 -> "高德 API 配额已超日访问限制 (代码 $code)"
            1008 -> "高德搜索服务网络异常 (代码 $code)"
            1804 -> "该区域不在高德数据支持范围内 (代码 $code)"
            else -> "搜索失败 (高德错误码 $code)"
        }
    }
}
