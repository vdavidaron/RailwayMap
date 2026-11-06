package com.example.railwaymap

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ImageButton
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
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
    private lateinit var binding: ActivityMapsBinding
    private var tileOverlay: TileOverlay? = null
    private lateinit var styleSpinner: Spinner
    private var currentOverlay: MapOverlayType = MapOverlayType.ORM_STANDARD
    private lateinit var reloadButton: ImageButton
    private lateinit var infoButton: ImageButton
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

        // Info button
        infoButton = findViewById(R.id.infoButton)
        infoButton.setOnClickListener {
            showLegendDialog()
        }

        // Reload button
        reloadButton = findViewById(R.id.reloadButton)
        reloadButton.setOnClickListener {
            Log.d("ReloadButton", "Reloading map overlay...")
            updateMapOverlay()
        }

        // Style spinner
        styleSpinner = findViewById(R.id.styleSpinner)
        styleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>, view: View?, position: Int, id: Long
            ) {
                val selectedOverlay = MapOverlayType.values().getOrNull(position) ?: MapOverlayType.ORM_STANDARD
                if (selectedOverlay != currentOverlay) {
                    currentOverlay = selectedOverlay
                    updateMapOverlay()
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
        updateMapOverlay()

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

    private fun updateMapOverlay() {
        tileOverlay?.remove()

        val tileProvider = object : TileProvider {
            override fun getTile(x: Int, y: Int, zoom: Int): Tile {
                try {
                    val url = URL(currentOverlay.getTileUrl(x, y, zoom))

                    val connection = url.openConnection() as HttpURLConnection
                    connection.setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Android RailwayMapApp)"
                    )
                    connection.setRequestProperty("Accept-Encoding", "gzip")
                    connection.connectTimeout = 4000
                    connection.readTimeout = 4000

                    val rawBytes = connection.inputStream.use { it.readBytes() }

                    val bitmap: Bitmap? = if (currentOverlay.provider == MapOverlayType.Provider.OPEN_INFRA_MAP) {
                        OpenInfraMapTileRenderer.decodeAndRender(rawBytes, currentOverlay.styleKey, 512, zoom)
                    } else {
                        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                    }

                    if (bitmap == null) {
                        return TileProvider.NO_TILE
                    }

                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    val data = stream.toByteArray()

                    return Tile(512, 512, data)

                } catch (e: Exception) {
                    Log.e("TileProvider", "Tile load failed: ${e.message}")
                    return TileProvider.NO_TILE
                }
            }
        }

        tileOverlay = mMap.addTileOverlay(
            TileOverlayOptions()
                .tileProvider(tileProvider)
                .zIndex(1f)
        )
    }

    private fun showLegendDialog() {
        val title = "${currentOverlay.displayName} - Legend"

        val message = when (currentOverlay) {
            MapOverlayType.OIM_POWER -> """
                <b>⚡ OpenInfraMap Power Grid:</b><br><br>
                • <font color="#7B1FA2"><b>■ Purple (500–750+ kV):</b></font> Ultra High Voltage Transmission<br>
                • <font color="#D32F2F"><b>■ Red (300–400 kV):</b></font> Extra High Voltage Grid<br>
                • <font color="#F57C00"><b>■ Orange (200–290 kV):</b></font> High Voltage Grid<br>
                • <font color="#FBC02D"><b>■ Yellow (100–170 kV):</b></font> Sub-Transmission / Distribution<br>
                • <font color="#388E3C"><b>■ Green (&lt; 100 kV):</b></font> Medium &amp; Local Voltage Lines<br>
                • <b>Solid Lines:</b> Overhead Power Lines<br>
                • <b>Dashed Lines:</b> Underground / Submarine Cables<br>
                • 🟡 <b>Circles &amp; Outlines:</b> Substations, Generators, Power Plants<br><br>
                <i>Tip: Zoom in (level 12+) to view substation names and line voltage labels.</i>
            """.trimIndent()

            MapOverlayType.OIM_TELECOMS -> """
                <b>📡 OpenInfraMap Telecoms:</b><br><br>
                • <font color="#00ACC1"><b>■ Cyan Lines:</b></font> Fiber Optic Cables &amp; Submarine Communication Lines<br>
                • <font color="#00897B"><b>■ Teal Markers:</b></font> Telecom Towers, Masts, Antennas &amp; Data Centers<br>
                • <b>Dashed Lines:</b> Underground Fiber Cables<br><br>
                <i>Tip: Zoom in (level 12+) to view site names.</i>
            """.trimIndent()

            MapOverlayType.OIM_PETROLEUM -> """
                <b>🛢️ OpenInfraMap Petroleum &amp; Oil/Gas:</b><br><br>
                • <font color="#E65100"><b>■ Dark Amber Lines:</b></font> Crude Oil, Natural Gas &amp; Chemical Pipelines<br>
                • <font color="#6D4C41"><b>■ Brown Markers:</b></font> Oil/Gas Wells, Refineries &amp; Pumping Stations<br>
                • <b>Dashed Lines:</b> Underground Pipelines<br><br>
                <i>Tip: Zoom in (level 12+) to view pipeline &amp; facility names.</i>
            """.trimIndent()

            MapOverlayType.OIM_WATER -> """
                <b>💧 OpenInfraMap Water Infrastructure:</b><br><br>
                • <font color="#1976D2"><b>■ Royal Blue Lines:</b></font> Water Mains &amp; Supply Pipelines<br>
                • <font color="#039BE5"><b>■ Light Blue Markers:</b></font> Water Reservoirs, Pumping Stations &amp; Treatment Plants<br>
                • <b>Dashed Lines:</b> Underground Water Mains<br><br>
                <i>Tip: Zoom in (level 12+) to view facility names.</i>
            """.trimIndent()

            MapOverlayType.ORM_STANDARD -> """
                <b>🚂 OpenRailwayMap Standard:</b><br><br>
                • <b>Black / Dark Gray:</b> Mainline Railway Tracks<br>
                • <b>Gray Thin Lines:</b> Branch Lines &amp; Industrial Tracks<br>
                • <b>Light Red / Cyan:</b> Trams &amp; Light Rail<br>
                • <b>Blue:</b> Subway / Metro Lines<br>
                • <b>Dashed Lines:</b> Tunnels, Disused, or Construction Tracks
            """.trimIndent()

            MapOverlayType.ORM_MAXSPEED -> """
                <b>🚀 OpenRailwayMap Max Speed:</b><br><br>
                • <font color="#D32F2F"><b>■ Red:</b></font> &gt; 300 km/h (High Speed Rail)<br>
                • <font color="#E65100"><b>■ Dark Orange:</b></font> 250 – 290 km/h<br>
                • <font color="#F57C00"><b>■ Orange:</b></font> 200 – 240 km/h<br>
                • <font color="#FBC02D"><b>■ Yellow:</b></font> 160 – 190 km/h<br>
                • <font color="#388E3C"><b>■ Green:</b></font> 120 – 150 km/h<br>
                • <font color="#1976D2"><b>■ Blue:</b></font> 80 – 110 km/h<br>
                • <font color="#7B1FA2"><b>■ Purple:</b></font> &lt; 80 km/h
            """.trimIndent()

            MapOverlayType.ORM_GAUGE -> """
                <b>📏 OpenRailwayMap Track Gauge:</b><br><br>
                • <font color="#D32F2F"><b>■ Red:</b></font> Standard Gauge (1435 mm)<br>
                • <font color="#1976D2"><b>■ Blue:</b></font> Broad Gauge (&gt; 1435 mm, e.g. 1520mm / 1668mm)<br>
                • <font color="#388E3C"><b>■ Green:</b></font> Narrow Gauge (&lt; 1435 mm, e.g. 1000mm / 750mm)
            """.trimIndent()

            MapOverlayType.ORM_ELECTRIFICATION -> """
                <b>⚡ OpenRailwayMap Electrification:</b><br><br>
                • <font color="#D32F2F"><b>■ Red:</b></font> 25 kV AC (50/60 Hz)<br>
                • <font color="#F57C00"><b>■ Orange:</b></font> 15 kV AC (16.7 Hz)<br>
                • <font color="#1976D2"><b>■ Blue:</b></font> 3 kV DC<br>
                • <font color="#00ACC1"><b>■ Cyan:</b></font> 1.5 kV DC<br>
                • <font color="#7B1FA2"><b>■ Purple:</b></font> 750 V DC (3rd Rail)<br>
                • <b>Gray:</b> Non-electrified
            """.trimIndent()

            MapOverlayType.ORM_SIGNALS -> """
                <b>🚦 OpenRailwayMap Signals:</b><br><br>
                • Displays railway signal aspect markers, interlocking areas, and train protection systems (ETCS, PZB, TVM, CBTC).
            """.trimIndent()
        }

        val spannedMessage = Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(spannedMessage)
            .setPositiveButton("Close", null)
            .show()
    }
}