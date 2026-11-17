package com.qqmusictv.data.datasource

/**
 * Radio data source. Original implementation streamed catalog radios; this stub
 * returns empty lists while the Radio screen is migrated to QQ Music's own station API.
 * The data class keeps legacy fields so RadioScreen (a legacy Apple Music UI) still compiles.
 */
data class RadioStation(
    val id: String,
    val name: String,
    val artworkUrl: String? = null,
    val faviconUrl: String? = null,
    val streamUrl: String? = null,
    val bitrate: Int = 128,
    val description: String? = null,
    val tags: String? = null,
    val country: String? = null,
)

interface RadioSource {
    suspend fun countryList(): List<Pair<String, String>> = emptyList()
    suspend fun detectCountry(): Pair<String, String>? = null
    suspend fun top(): List<RadioStation> = emptyList()
    suspend fun byCountry(code: String): List<RadioStation> = emptyList()
    suspend fun byCountryName(name: String): List<RadioStation> = emptyList()
    suspend fun search(q: String): List<RadioStation> = emptyList()
}

class RadioSourceImpl @javax.inject.Inject constructor() : RadioSource
