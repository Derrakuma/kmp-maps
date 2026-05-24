package com.swmansion.kmpmaps.core

public object MapConfiguration {
    private var _googleMapsApiKey: String? = null
    private var _mapTilerApiKey: String? = null
    private var _mapTilerMapId: String = "base-v4"

    public val googleMapsApiKey: String
        get() =
            checkNotNull(_googleMapsApiKey) {
                "Google Maps API key not found. Provide it via " +
                    "`MapConfiguration.initialize(googleMapsApiKey = \"...\")`."
            }

    public val mapTilerStyleUrl: String
        get() {
            val apiKey =
                checkNotNull(_mapTilerApiKey) {
                    "MapTiler API key not found. Provide it via `MapConfiguration.initializeMapTiler(mapTilerApiKey = \"...\")`."
                }
            return "https://api.maptiler.com/maps/$_mapTilerMapId/style.json?key=$apiKey"
        }

    public fun initialize(googleMapsApiKey: String) {
        _googleMapsApiKey = googleMapsApiKey
    }

    public fun initializeMapTiler(mapTilerApiKey: String, mapId: String = "base-v4") {
        _mapTilerApiKey = mapTilerApiKey
        _mapTilerMapId = mapId
    }
}

