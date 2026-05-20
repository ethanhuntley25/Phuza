package com.example.phuza

import android.Manifest
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import com.example.phuza.api.RouteViewModel
import com.example.phuza.api.UiState
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.layers.getLayer
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.locationcomponent.location

class MapActivity : AppCompatActivity() {

    private lateinit var mapView: com.mapbox.maps.MapView
    private lateinit var fabMyLocation: ExtendedFloatingActionButton

    private val vm: RouteViewModel by viewModels()

    private var lastPoint: Point? = null
    private var centeredOnce = false

    companion object {
        private const val SRC_PUBS = "pubs-source"
        private const val LAYER_PUBS = "pubs-layer"
        private const val IMG_PIN = "pub-pin-image"
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        val ok = res[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                res[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) enableLocation() else toast("Location permission is required")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        mapView = findViewById(R.id.mapView)
        fabMyLocation = findViewById(R.id.fabMyLocation)

        findViewById<MaterialToolbar>(R.id.topBar)
            .setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS) { style ->
            ensurePinStyle(style)
            askForLocation()
        }

        fabMyLocation.setOnClickListener { centerOnUser() }
        observePubs()
    }

    private fun ensurePinStyle(style: Style) {
        val pinDrawable = AppCompatResources.getDrawable(this, R.drawable.ic_pub_pin)
            ?: error("Drawable ic_pub_pin not found in res/drawable")
        val pinBitmap: Bitmap = pinDrawable.toBitmap(config = Bitmap.Config.ARGB_8888)

        style.addImage(IMG_PIN, pinBitmap)

        if (style.getSource(SRC_PUBS) == null) {
            style.addSource(geoJsonSource(SRC_PUBS) { })
        }
        if (style.getLayer(LAYER_PUBS) == null) {
            style.addLayer(
                symbolLayer(LAYER_PUBS, SRC_PUBS) {
                    iconImage(IMG_PIN)
                    iconAnchor(IconAnchor.BOTTOM)
                    iconAllowOverlap(true)
                    iconIgnorePlacement(true)
                    iconSize(1.0)
                }
            )
        }
    }

    private fun observePubs() {
        vm.pubsState.observe(this) { state ->
            when (state) {
                is UiState.Loading -> toast("Finding nearby pubs…")
                is UiState.Error -> toast(state.message)
                is UiState.Success -> {
                    val style = mapView.getMapboxMap().getStyle() ?: return@observe

                    val features = state.data.pubs.map { pub ->
                        Feature.fromGeometry(
                            Point.fromLngLat(pub.coordinates.longitude, pub.coordinates.latitude)
                        ).apply { addStringProperty("name", pub.name) }
                    }

                    style.getSourceAs<com.mapbox.maps.extension.style.sources.generated.GeoJsonSource>(SRC_PUBS)
                        ?.featureCollection(FeatureCollection.fromFeatures(features))

                    val points = state.data.pubs.map {
                        Point.fromLngLat(it.coordinates.longitude, it.coordinates.latitude)
                    }
                    if (points.isNotEmpty()) {
                        val camera = mapView.getMapboxMap().cameraForCoordinates(
                            points, EdgeInsets(100.0, 100.0, 100.0, 100.0), null, null
                        )
                        mapView.getMapboxMap().setCamera(camera)
                    }
                }
                else -> {}
            }
        }
    }

    private fun askForLocation() {
        permLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun enableLocation() {
        val loc = mapView.location
        loc.updateSettings {
            enabled = true
            pulsingEnabled = true
            locationPuck = LocationPuck2D(
                topImage = ImageHolder.from(R.drawable.ic_puck_top),
                bearingImage = ImageHolder.from(R.drawable.ic_puck_bearing),
                shadowImage = ImageHolder.from(R.drawable.ic_puck_shadow)
            )
        }
        loc.addOnIndicatorPositionChangedListener { point ->
            lastPoint = point


            if (!centeredOnce) {
                centeredOnce = true
                mapView.getMapboxMap().flyTo(
                    CameraOptions.Builder().center(point).zoom(14.5).build()
                )
                // Auto-discover pubs near current location
                vm.discoverPubs(lat = point.latitude(), lon = point.longitude())
            }
        }
    }

    private fun centerOnUser() {
        lastPoint?.let {
            mapView.getMapboxMap().flyTo(
                CameraOptions.Builder().center(it).zoom(15.0).build()
            )
        } ?: askForLocation()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // MapView lifecycle
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }
}
