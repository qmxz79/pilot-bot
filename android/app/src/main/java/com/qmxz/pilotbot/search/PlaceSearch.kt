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
     * city, falling back to a nationwide search when null/blank.
     */
    fun search(keyword: String, cityCode: String?, onResult: (Result<List<PlaceResult>>) -> Unit) {
        val query = PoiSearch.Query(keyword, cityCode ?: "").apply { pageSize = 10 }
        val poiSearch = PoiSearch(context.applicationContext, query)
        poiSearch.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
            override fun onPoiSearched(result: PoiResult?, rCode: Int) {
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
