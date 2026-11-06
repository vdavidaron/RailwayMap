package com.example.railwaymap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapOverlayTypeTest {

    @Test
    fun testOpenRailwayMapUrlGeneration() {
        val url = MapOverlayType.ORM_STANDARD.getTileUrl(x = 10, y = 20, zoom = 5)
        assertTrue(url.contains(".tiles.openrailwaymap.org/standard/5/10/20.png"))
    }

    @Test
    fun testOpenInfraMapPowerUrlGeneration() {
        val url = MapOverlayType.OIM_POWER.getTileUrl(x = 10, y = 20, zoom = 5)
        assertEquals("https://openinframap.org/map/power/5/10/20.pbf", url)
    }

    @Test
    fun testOpenInfraMapTelecomsUrlGeneration() {
        val url = MapOverlayType.OIM_TELECOMS.getTileUrl(x = 15, y = 25, zoom = 8)
        assertEquals("https://openinframap.org/map/telecoms/8/15/25.pbf", url)
    }

    @Test
    fun testOpenInfraMapPetroleumUrlGeneration() {
        val url = MapOverlayType.OIM_PETROLEUM.getTileUrl(x = 3, y = 4, zoom = 2)
        assertEquals("https://openinframap.org/map/petroleum/2/3/4.pbf", url)
    }

    @Test
    fun testOpenInfraMapWaterUrlGeneration() {
        val url = MapOverlayType.OIM_WATER.getTileUrl(x = 1, y = 2, zoom = 3)
        assertEquals("https://openinframap.org/map/water/3/1/2.pbf", url)
    }

    @Test
    fun testEmptyPbfDecodingReturnsNull() {
        val result = OpenInfraMapTileRenderer.decodeAndRender(ByteArray(0), "power", 512)
        assertNull(result)
    }
}
