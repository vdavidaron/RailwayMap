package com.example.railwaymap

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ImageButton
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.railwaymap.databinding.ActivityMapsBinding
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private var tileOverlay: TileOverlay? = null
    private lateinit var styleSpinner: Spinner
    private var currentStyle: String = "standard"
    private lateinit var reloadButton: ImageButton
    private lateinit var mapTypeSpinner: Spinner

    private val LOCATION_PERMISSION_REQUEST = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(android.view.WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }

        // Reload button
        reloadButton = findViewById(R.id.reloadButton)
        reloadButton.setOnClickListener {
            Log.d("ReloadButton", "Reloading map overlay...")
            updateRailwayOverlay()
        }

        // Style spinner
        styleSpinner = findViewById(R.id.styleSpinner)
        styleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>, view: View?, position: Int, id: Long
            ) {
                val selectedStyle = parent.getItemAtPosition(position).toString()
                if (selectedStyle != currentStyle) {
                    currentStyle = selectedStyle
                    updateRailwayOverlay()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Map fragment
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Set default map type
        mMap.mapType = GoogleMap.MAP_TYPE_HYBRID
        mMap.setPadding(15, 240, 0, 0)
        enableLocationFeatures()
        updateRailwayOverlay()

        // Map type spinner logic (SAFE to access mMap now)
        mapTypeSpinner = findViewById(R.id.mapTypeSpinner)
        mapTypeSpinner.setSelection(2) // Hybrid
        mapTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>, view: View?, position: Int, id: Long
            ) {
                when (position) {
                    0 -> mMap.mapType = GoogleMap.MAP_TYPE_NORMAL
                    1 -> mMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
                    2 -> mMap.mapType = GoogleMap.MAP_TYPE_HYBRID
                    3 -> mMap.mapType = GoogleMap.MAP_TYPE_TERRAIN
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun enableLocationFeatures() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
            mMap.uiSettings.isMyLocationButtonEnabled = true

            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val userLatLng = LatLng(it.latitude, it.longitude)
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 14f))
                }
            }

        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            enableLocationFeatures()
        }
    }

    private fun updateRailwayOverlay() {
        tileOverlay?.remove()

        val tileProvider = object : TileProvider {
            override fun getTile(x: Int, y: Int, zoom: Int): Tile? {
                try {
                    val subdomain = listOf("a", "b", "c").random()
                    val url = URL("https://$subdomain.tiles.openrailwaymap.org/$currentStyle/$zoom/$x/$y.png")

                    val connection = url.openConnection() as HttpURLConnection
                    connection.setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Android RailwayMapApp)"
                    )
                    connection.connectTimeout = 3000
                    connection.readTimeout = 3000

                    val inputStream: InputStream = connection.inputStream
                    val bitmap = BitmapFactory.decodeStream(inputStream)

                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    val data = stream.toByteArray()

                    return Tile(512, 512, data)

                } catch (e: Exception) {
                    Log.e("TileProvider", "Tile load failed: ${e.message}")
                    return null
                }
            }
        }

        tileOverlay = mMap.addTileOverlay(
            TileOverlayOptions()
                .tileProvider(tileProvider)
                .zIndex(1f)
        )
    }
}
