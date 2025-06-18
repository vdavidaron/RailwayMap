package com.example.railwaymap

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
import java.net.HttpURLConnection
import java.net.URL

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var mapReady = false

    private lateinit var binding: ActivityMapsBinding
    private var tileOverlay: TileOverlay? = null

    private lateinit var styleSpinner: Spinner
    private lateinit var mapTypeSpinner: Spinner
    private lateinit var reloadButton: ImageButton

    private var currentStyle: String = "standard"

    private val LOCATION_PERMISSION_REQUEST = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(android.view.WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        styleSpinner = findViewById(R.id.styleSpinner)
        mapTypeSpinner = findViewById(R.id.mapTypeSpinner)
        reloadButton = findViewById(R.id.reloadButton)

        reloadButton.setOnClickListener {
            if (mapReady) {
                Log.d("ReloadButton", "Reloading map overlay...")
                updateRailwayOverlay()
            }
        }

        styleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>, view: View?, position: Int, id: Long
            ) {
                if (!mapReady) return
                val selected = parent.getItemAtPosition(position).toString()
                if (selected != currentStyle) {
                    currentStyle = selected
                    updateRailwayOverlay()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        mapTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>, view: View?, position: Int, id: Long
            ) {
                if (!mapReady) return
                mMap.mapType = when (position) {
                    0 -> GoogleMap.MAP_TYPE_NORMAL
                    1 -> GoogleMap.MAP_TYPE_SATELLITE
                    2 -> GoogleMap.MAP_TYPE_HYBRID
                    3 -> GoogleMap.MAP_TYPE_TERRAIN
                    else -> GoogleMap.MAP_TYPE_HYBRID
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        (supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment)
            .getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mapReady = true

        mMap.mapType = GoogleMap.MAP_TYPE_HYBRID
        mMap.setPadding(15, 240, 0, 0)
        enableLocationFeatures()
        updateRailwayOverlay()

        mapTypeSpinner.setSelection(2)
    }

    private fun enableLocationFeatures() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
            mMap.uiSettings.isMyLocationButtonEnabled = true

            LocationServices.getFusedLocationProviderClient(this)
                .lastLocation.addOnSuccessListener { loc: Location? ->
                    loc?.let {
                        val here = LatLng(it.latitude, it.longitude)
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(here, 14f))
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
        ) enableLocationFeatures()
    }

    private fun updateRailwayOverlay() {
        tileOverlay?.remove()

        val tileProvider = object : TileProvider {
            override fun getTile(x: Int, y: Int, zoom: Int): Tile {
                return try {
                    val sub = listOf("a", "b", "c").random()
                    val url = URL(
                        "https://$sub.tiles.openrailwaymap.org/$currentStyle/$zoom/$x/$y.png"
                    )

                    val conn = url.openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (RailwayMapApp)")
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000

                    val bmp = BitmapFactory.decodeStream(conn.inputStream)
                    val stream = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    Tile(512, 512, stream.toByteArray())

                } catch (e: Exception) {
                    Log.e("TileProvider", "Tile load failed: ${e.message}")
                    createTransparentTile()
                }
            }
        }

        tileOverlay = mMap.addTileOverlay(
            TileOverlayOptions()
                .tileProvider(tileProvider)
                .zIndex(1f)
                .fadeIn(true)
        )
    }

    private fun createTransparentTile(): Tile {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 0, stream)
        return Tile(512, 512, stream.toByteArray())
    }
}

