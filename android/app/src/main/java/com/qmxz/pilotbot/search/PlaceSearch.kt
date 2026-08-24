package com.qmxz.pilotbot.search

import android.content.Context
import com.amap.api.services.core.PoiItem
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch

data class PlaceResult(
    val title: String,
    val snippet: String,
    val lat: Double,
    val lng: Double,
)

/** Thin wrapper over the Amap POI keyword search (search SDK is bundled in the navi jar). */
class PlaceSearch(private val context: Context) {

    /**
     * Searches POIs by [keyword]; [cityCode] (from the location fix) narrows the search to one
     * city, falling back to a nationwide search when null/blank. Failures surface through
     * [Result.failure] with the Amap result code so the UI is never silently empty.
     */
    fun search(keyword: String, cityCode: String?, onResult: (Result<List<PlaceResult>>) -> Unit) {
        val query = try {
            PoiSearch.Query(keyword, cityCode ?: "").apply { pageSize = 10 }
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
                    onResult(Result.failure(IllegalStateException("搜索失败(高德码 $rCode)")))
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
}
