package com.example.phuza.api

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class MbxGeocodingResponse(
    val features: List<MbxFeature> = emptyList()
)

data class MbxFeature(
    val id: String,
    @SerializedName("place_name") val placeName: String,
    val text: String,
    val center: List<Double>?,
    @SerializedName("place_type") val placeType: List<String>?,
    val geometry: MbxGeometry?
) {
    fun lon(): Double? = center?.getOrNull(0) ?: geometry?.coordinates?.getOrNull(0)
    fun lat(): Double? = center?.getOrNull(1) ?: geometry?.coordinates?.getOrNull(1)
}

data class MbxGeometry(
    val type: String,
    val coordinates: List<Double>
)

interface MapboxGeocodingApi {
    @GET("geocoding/v5/mapbox.places/{query}.json")
    suspend fun forward(
        @Path("query") query: String,
        @Query("access_token") token: String,
        @Query("limit") limit: Int = 6,
        @Query("proximity") proximity: String? = null,   // "lon,lat" (bias results near user)
        @Query("types") types: String? = null,           // e.g., "poi,place"
        @Query("categories") categories: String? = null, // e.g., "bar,pub,brewery,nightclub"
        @Query("country") country: String? = null,       // e.g., "ZA"
        @Query("language") language: String? = null      // e.g., "en"
    ): Response<MbxGeocodingResponse>
}
object MapboxGeocodingService {
    private const val BASE_URL = "https://api.mapbox.com/"

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }
    private val ok = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(ok)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: MapboxGeocodingApi = retrofit.create(MapboxGeocodingApi::class.java)
}
