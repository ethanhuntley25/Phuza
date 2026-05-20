package com.example.phuza

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.phuza.adapters.LocationSearchAdapter
import com.example.phuza.api.MapboxGeocodingService
import com.example.phuza.api.MbxFeature
import com.example.phuza.databinding.ActivityChangeLocationBinding
import com.example.phuza.databinding.FragmentChangeLocationBinding
import com.example.phuza.utils.LocationUtil
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.layers.getLayer
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.animation.flyTo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class ChangeLocationFragment : Fragment() {

    private var _binding: FragmentChangeLocationBinding? = null
    private val binding get() = _binding!!

    private var saveButton: TextView? = null

    //Location/geocoding resources
    private val locationUtil = LocationUtil()
    private val auth = FirebaseAuth.getInstance()
    private val rtdb = FirebaseDatabase.getInstance().reference
    private val mapboxApi = MapboxGeocodingService.api

    private val mapboxToken by lazy {
        requireContext()
            .packageManager
            .getApplicationInfo(requireContext().packageName,
                android.content.pm.PackageManager.GET_META_DATA)
            .metaData
            .getString("MAPBOX_ACCESS_TOKEN") ?: ""
    }
    private var userLat: Double? = null
    private var userLon: Double? = null
    private var searchJob: Job? = null
    private var selectedFeature: MbxFeature? = null
    private lateinit var searchAdapter: LocationSearchAdapter

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var locationSettingsLauncher: ActivityResultLauncher<IntentSenderRequest>

    companion object{
        private const val SRC_SEARCH = "search-source"
        private const val LAYER_SEARCH ="search-layer"
        private const val IMG_PIN = "search-pin-image"
    }

    override fun onAttach(context: Context){
        super.onAttach(context)
        val activityBinding = (requireActivity() as? ChangeLocationActivity)?.let{
            ActivityChangeLocationBinding.bind(it.findViewById(R.id.main))
        }
        saveButton = activityBinding?.saveButton
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View{
        _binding = FragmentChangeLocationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupLaunchers()
        setupMapbox()
        setupSearchAdapter()
        setupListeners()
        fetchInitialData()
    }

    private fun setupSearchAdapter(){
        searchAdapter = LocationSearchAdapter(emptyList(), ::onFeatureSelected)
        binding.recyclerViewSearchResults.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = searchAdapter
        }
    }

    private fun setupMapbox(){
        binding.mapView.mapboxMap.loadStyle(Style.MAPBOX_STREETS){ style ->
            val pinDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_location)
            val icon = pinDrawable?.toBitmap(width = 60, height = 60)?: return@loadStyle
            style.addImage(IMG_PIN, icon)
            if (style.getSource(SRC_SEARCH) == null){
                style.addSource(geoJsonSource(SRC_SEARCH){})
            }
            if (style.getLayer(LAYER_SEARCH) == null){
                style.addLayer(
                    symbolLayer(LAYER_SEARCH, SRC_SEARCH){
                        iconImage(IMG_PIN)
                        iconAnchor(IconAnchor.BOTTOM)
                        iconAllowOverlap(iconAllowOverlap = true)
                        iconIgnorePlacement(true)
                        iconSize(1.0)
                    }
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupLaunchers(){
        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ){ result ->
            val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted){
                locationUtil.checkLocationSettings(requireActivity(), locationSettingsLauncher){
                    locationUtil.getAccurateLocation(requireActivity(), ::onCurrentLocationFound){ msg ->
                        toast(msg)
                    }
                }
            }
            else{
                toast( "Location permission denied. Cannot find current location")
            }
        }
        locationSettingsLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ){ result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK){
                locationUtil.getAccurateLocation(requireActivity(), ::onCurrentLocationFound){ msg ->
                    toast(msg)
                }
            }
            else{
                toast("Location services not enabled.")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupListeners(){
        saveButton?.setOnClickListener { saveNewLocation() }
        saveButton?.visibility = View.GONE

        binding.fabMyLocation.setOnClickListener {
            searchAdapter.clearResults()
            binding.inputSearchLocation.setText("")

            locationUtil.requestPermissions(requireActivity(), requestPermissionLauncher){
                locationUtil.checkLocationSettings(requireActivity(), locationSettingsLauncher){
                    locationUtil.getAccurateLocation(requireActivity(), ::onCurrentLocationFound){ msg ->
                        toast(msg)
                    }
                }
            }
        }

        binding.inputSearchLocation.addTextChangedListener{ text ->
            val query = text?.toString()
            if (query.isNullOrEmpty()){
                clearSelectionAndSearch()
                return@addTextChangedListener
            }

            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                delay(300)
                if (query.length >= 3) performanceGeocodingSearch(query)
            }
        }
    }

    private fun fetchInitialData(){
        val uid = auth.currentUser?.uid ?:return
        rtdb.child("users").child(uid).child("location")
            .get().addOnSuccessListener { snapshot ->
                val savedLocation = snapshot.getValue(String::class.java)
                binding.tvCurrentSelection.text = savedLocation ?: "Tap the map or search to set a new location."
            }
    }

    private fun onCurrentLocationFound(lat: Double, lon: Double){
        userLat = lat
        userLon = lon

        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(lat, lon, 1) { addresses ->
                val area = if (addresses.isNotEmpty()) {
                    val city = addresses[0].locality
                    val suburb = addresses[0].subLocality
                    suburb ?: city ?: "Current Area"
                } else "Current Location"

                val currentFeature = MbxFeature(
                    id = "current",
                    placeName = "Current Location",
                    text = area,
                    center = listOf(lon, lat),
                    placeType = null,
                    geometry = null
                )
                requireActivity().runOnUiThread { onFeatureSelected(currentFeature) }
            }
        } else {
            try {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                val area = if (!addresses.isNullOrEmpty()) {
                    val city = addresses[0].locality
                    val suburb = addresses[0].subLocality
                    suburb ?: city ?: "Current Area"
                } else "Current Location"

                val currentFeature = MbxFeature(
                    id = "current",
                    placeName = "Current Location",
                    text = area,
                    center = listOf(lon, lat),
                    placeType = null,
                    geometry = null
                )
                onFeatureSelected(currentFeature)
            } catch (e: Exception) {
                toast("Could not reverse geocode location.")
            }
        }
    }

    private fun onFeatureSelected(feature: MbxFeature){
        selectedFeature = feature
        searchAdapter.clearResults()

        val lon = feature.lon()
        val lat = feature.lat()
        val areaName = feature.text

        binding.tvCurrentSelection.text = "Selected: $areaName"
        saveButton?.visibility = View.VISIBLE

        if (lat != null && lon != null){
            updateMapPin(Point.fromLngLat(lon, lat))
            binding.mapView.mapboxMap.flyTo(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(lon, lat))
                    .zoom(14.0)
                    .build()
            )
        }
    }

    private fun updateMapPin(point: Point){
        binding.mapView.mapboxMap.style?.getSourceAs<com.mapbox.maps.extension.style.sources.generated.GeoJsonSource>(SRC_SEARCH)
            ?.featureCollection(FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(point))))
    }

    private fun saveNewLocation(){
        val uid = auth.currentUser?.uid ?: run{
            toast("User not logged in.")
            return
        }
        val feature = selectedFeature ?: run{
            toast("Please select a location first.")
            return
        }

        val areaName = feature.text
        val lon = feature.lon()
        val lat = feature.lat()

        if (lon == null || lat == null){
            toast("Location coordinates are missing.")
            return
        }

        rtdb.child("users").child(uid).child("location").setValue(areaName)
            .addOnSuccessListener{
                rtdb.child("users").child(uid).child("lat").setValue(lat)
                rtdb.child("users").child(uid).child("lon").setValue(lon)

                toast("Location updated to $areaName")
                requireActivity().finish()
            }
            .addOnFailureListener { e ->
                toast("Failed to save location: ${e.message}")
            }
    }

    private suspend fun performanceGeocodingSearch(query: String){
        try{
            val proximity = if (userLat != null && userLon != null) "$userLon,$userLat" else null

            val response = mapboxApi.forward(
                query = query,
                token = mapboxToken,
                proximity = proximity,
                types = "locality,place,postcode",
                country = "ZA"
            )
            if (response.isSuccessful){
                val features = response.body()?.features ?: emptyList()
                searchAdapter.updateResults(features)
            }
            else{
                toast("Search Failed: ${response.code()}")
            }
        }
        catch (e: Exception){
            toast("Network error: ${e.message}")
        }
    }

    private fun clearSelectionAndSearch(){
        selectedFeature = null
        binding.tvCurrentSelection.text = "Tap the map or search to set a new location."
        saveButton?.visibility = View.GONE
        searchAdapter.clearResults()

        binding.mapView.mapboxMap.style?.getSourceAs<com.mapbox.maps.extension.style.sources.generated.GeoJsonSource>(SRC_SEARCH)
            ?.featureCollection(FeatureCollection.fromFeatures(emptyList()))
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        searchJob?.cancel()
    }
}