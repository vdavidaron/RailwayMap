package com.example.railwaymap

enum class MapOverlayType(
    val displayName: String,
    val provider: Provider,
    val styleKey: String
) {
    ORM_STANDARD("OpenRailwayMap - Standard", Provider.OPEN_RAILWAY_MAP, "standard"),
    ORM_MAXSPEED("OpenRailwayMap - Max Speed", Provider.OPEN_RAILWAY_MAP, "maxspeed"),
    ORM_GAUGE("OpenRailwayMap - Gauge", Provider.OPEN_RAILWAY_MAP, "gauge"),
    ORM_ELECTRIFICATION("OpenRailwayMap - Electrification", Provider.OPEN_RAILWAY_MAP, "electrification"),
    ORM_SIGNALS("OpenRailwayMap - Signals", Provider.OPEN_RAILWAY_MAP, "signals"),
    OIM_POWER("OpenInfraMap - Power", Provider.OPEN_INFRA_MAP, "power"),
    OIM_TELECOMS("OpenInfraMap - Telecoms", Provider.OPEN_INFRA_MAP, "telecoms"),
    OIM_PETROLEUM("OpenInfraMap - Petroleum", Provider.OPEN_INFRA_MAP, "petroleum"),
    OIM_WATER("OpenInfraMap - Water", Provider.OPEN_INFRA_MAP, "water");

    enum class Provider {
        OPEN_RAILWAY_MAP,
        OPEN_INFRA_MAP
    }

    fun getTileUrl(x: Int, y: Int, zoom: Int): String {
        return when (provider) {
            Provider.OPEN_RAILWAY_MAP -> {
                val subdomain = listOf("a", "b", "c").random()
                "https://$subdomain.tiles.openrailwaymap.org/$styleKey/$zoom/$x/$y.png"
            }
            Provider.OPEN_INFRA_MAP -> {
                "https://openinframap.org/map/$styleKey/$zoom/$x/$y.pbf"
            }
        }
    }
}
