package com.example.phuza.api

import com.example.phuza.data.UserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("/")
    suspend fun health(): Response<Unit>

//    TODO for Part 3
//    @POST("api/locations")
//    suspend fun searchPlaces(
//        @Body body: SearchRequest
//    ): Response<ApiEnvelope<List<MapboxFeatureDto>>>
//
//    @POST("api/locations")
//    suspend fun saveOrLookupLocation(
//        @Body body: LocationPayload
//    ): Response<ApiEnvelope<LocationDto>>
//
//    // ---- List stored locations (MongoDB) ----
//    @GET("api/locations")
//    suspend fun listLocations(
//        @Query("limit") limit: Int = 50,
//        @Query("skip") skip: Int = 0
//    ): Response<ApiEnvelope<List<LocationDto>>>
//
//    @POST("api/discover-route")
//    suspend fun discoverRoute(
//        @Body body: DiscoverRouteRequest
//    ): Response<ApiEnvelope<RouteResponse>>

    @POST("api/discover-pubs")
    suspend fun discoverPubs(
        @Body body: DiscoverPubsRequest
    ): Response<ApiEnvelope<DiscoverPubsResponse>>

    @GET("api/friends")
    suspend fun listUsers(
        @Query("q") q: String? = null,
        @Query("limit") limit: Int = 100,
        @Query("skip") skip: Int = 0
    ): Response<ApiEnvelope<List<UserDto>>>

    @POST("api/notify/follow-request")
    suspend fun notifyFollowRequest(@Body body: Map<String, String>): Response<ApiEnvelope<Unit>>

    @POST("api/notify/follow-accept")
    suspend fun notifyFollowAccept(@Body body: Map<String, String>): Response<ApiEnvelope<Unit>>

}
