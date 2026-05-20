package com.example.phuza.api

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.phuza.api.RetrofitInstance.api
import kotlinx.coroutines.launch


class RouteViewModel : ViewModel() {
    //TODO Part 3
//    private val _routeState = MutableLiveData<UiState<RouteResponse>>(UiState.Idle)
//
//    private val _searchState = MutableLiveData<UiState<List<MapboxFeatureDto>>>(UiState.Idle)
//    private val _saveState = MutableLiveData<UiState<LocationDto>>(UiState.Idle)

    private val _pubsState = MutableLiveData<UiState<DiscoverPubsResponse>>()
    val pubsState: LiveData<UiState<DiscoverPubsResponse>> = _pubsState

    fun discoverPubs(lat: Double, lon: Double) {
        viewModelScope.launch {
            _pubsState.value = UiState.Loading
            try {
                val body = DiscoverPubsRequest(
                    origin = StartingPoint(
                        name = "Current location",
                        address = "Your position",
                        coordinates = Coordinates(
                            longitude = lon,
                            latitude = lat
                        )
                    ),
                    numberOfPubs = 20, //Number of pubs
                    radiusMeters = 10000 //Radius in meters
                )

                val response = api.discoverPubs(body)
                val envelope = response.body()

                if (response.isSuccessful && envelope?.data != null) {
                    _pubsState.value = UiState.Success(envelope.data)
                } else {
                    _pubsState.value = UiState.Error(envelope?.message ?: "Error: ${response.code()}")
                }
            } catch (e: Exception) {
                _pubsState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

}
