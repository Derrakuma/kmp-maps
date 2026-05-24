package com.swmansion.kmpmaps.core

import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition as MapLibreCameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon as GeoJsonPolygon

@Composable
public actual fun Map(
    modifier: Modifier,
    cameraPosition: CameraPosition?,
    properties: MapProperties,
    uiSettings: MapUISettings,
    clusterSettings: ClusterSettings,
    markers: List<Marker>,
    circles: List<Circle>,
    polygons: List<Polygon>,
    polylines: List<Polyline>,
    onCameraMove: ((CameraPosition) -> Unit)?,
    onMarkerClick: ((Marker) -> Unit)?,
    onMarkerDragEnd: ((Marker) -> Unit)?,
    onCircleClick: ((Circle) -> Unit)?,
    onPolygonClick: ((Polygon) -> Unit)?,
    onPolylineClick: ((Polyline) -> Unit)?,
    onMapClick: ((Coordinates) -> Unit)?,
    onMapLongClick: ((Coordinates) -> Unit)?,
    onPOIClick: ((Coordinates) -> Unit)?,
    onMapLoaded: (() -> Unit)?,
    geoJsonLayers: List<GeoJsonLayer>,
    customMarkerContent: kotlin.collections.Map<String, @Composable (Marker) -> Unit>,
    webCustomMarkerContent: kotlin.collections.Map<String, (Marker) -> String>,
) {
    val context = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            onCreate(null)
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)

    LaunchedEffect(mapView, cameraPosition, properties, uiSettings, markers, circles, polygons, polylines) {
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri(MapConfiguration.mapTilerStyleUrl)) {
                cameraPosition?.let { map.cameraPosition = it.toMapLibreCameraPosition() }
                map.uiSettings.isCompassEnabled = uiSettings.compassEnabled
                map.uiSettings.isZoomGesturesEnabled = uiSettings.zoomEnabled
                map.uiSettings.isScrollGesturesEnabled = uiSettings.scrollEnabled
                map.uiSettings.isRotateGesturesEnabled = uiSettings.rotateEnabled
                map.uiSettings.isTiltGesturesEnabled = uiSettings.togglePitchEnabled
                map.addMapTilerContent(markers, circles, polygons, polylines, onMarkerClick)
                map.addOnMapClickListener { latLng ->
                    onMapClick?.invoke(Coordinates(latLng.latitude, latLng.longitude))
                    onMapClick != null
                }
                map.addOnMapLongClickListener { latLng ->
                    onMapLongClick?.invoke(Coordinates(latLng.latitude, latLng.longitude))
                    onMapLongClick != null
                }
                map.addOnCameraIdleListener {
                    val position = map.cameraPosition
                    onCameraMove?.invoke(
                        CameraPosition(
                            coordinates = Coordinates(position.target?.latitude ?: 0.0, position.target?.longitude ?: 0.0),
                            zoom = position.zoom.toFloat(),
                            androidCameraPosition = AndroidCameraPosition(bearing = position.bearing.toFloat(), tilt = position.tilt.toFloat()),
                        ),
                    )
                }
                onMapLoaded?.invoke()
            }
        }
    }

    DisposableEffect(mapView) {
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
}

private fun MapLibreMap.addMapTilerContent(
    markers: List<Marker>,
    circles: List<Circle>,
    polygons: List<Polygon>,
    polylines: List<Polyline>,
    onMarkerClick: ((Marker) -> Unit)?,
) {
    clear()
    markers.forEach { marker ->
        addMarker(MarkerOptions().position(marker.coordinates.toLatLng()).title(marker.title))
    }
    setOnMarkerClickListener { nativeMarker ->
        val marker = markers.firstOrNull { it.title == nativeMarker.title && it.coordinates.toLatLng() == nativeMarker.position }
        if (marker != null) onMarkerClick?.invoke(marker)
        onMarkerClick != null
    }

    val style = style ?: return
    circles.forEachIndexed { index, circle ->
        val sourceId = "kmpmaps-circle-source-$index"
        val layerId = "kmpmaps-circle-layer-$index"
        style.addSource(GeoJsonSource(sourceId, Feature.fromGeometry(Point.fromLngLat(circle.center.longitude, circle.center.latitude))))
        style.addLayer(
            CircleLayer(layerId, sourceId).withProperties(
                circleRadius(circle.radius.coerceAtMost(30f)),
                circleColor(circle.color?.toArgb() ?: AndroidColor.argb(80, 38, 132, 255)),
                circleOpacity(0.5f),
            ),
        )
    }
    polygons.forEachIndexed { index, polygon ->
        if (polygon.coordinates.isEmpty()) return@forEachIndexed
        val sourceId = "kmpmaps-polygon-source-$index"
        val layerId = "kmpmaps-polygon-layer-$index"
        val ring = (polygon.coordinates + polygon.coordinates.first()).map { Point.fromLngLat(it.longitude, it.latitude) }
        style.addSource(GeoJsonSource(sourceId, Feature.fromGeometry(GeoJsonPolygon.fromLngLats(listOf(ring)))))
        style.addLayer(
            FillLayer(layerId, sourceId).withProperties(
                fillColor(polygon.color?.toArgb() ?: AndroidColor.argb(80, 38, 132, 255)),
                fillOpacity(0.35f),
            ),
        )
    }
    polylines.forEachIndexed { index, polyline ->
        val sourceId = "kmpmaps-polyline-source-$index"
        val layerId = "kmpmaps-polyline-layer-$index"
        style.addSource(GeoJsonSource(sourceId, FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(polyline.coordinates.map { Point.fromLngLat(it.longitude, it.latitude) })))))
        style.addLayer(
            LineLayer(layerId, sourceId).withProperties(
                lineColor(polyline.lineColor?.toArgb() ?: AndroidColor.BLACK),
                lineWidth(polyline.width),
            ),
        )
    }
}

private fun Coordinates.toLatLng(): LatLng = LatLng(latitude, longitude)

private fun MapBounds.toLatLngBounds(): LatLngBounds = LatLngBounds.Builder()
    .include(southwest.toLatLng())
    .include(northeast.toLatLng())
    .build()

private fun CameraPosition.toMapLibreCameraPosition(): MapLibreCameraPosition = MapLibreCameraPosition.Builder()
    .target(bounds?.toLatLngBounds()?.center ?: coordinates?.toLatLng() ?: LatLng(0.0, 0.0))
    .zoom((zoom ?: 0f).toDouble())
    .bearing((androidCameraPosition?.bearing ?: 0f).toDouble())
    .tilt((androidCameraPosition?.tilt ?: 0f).toDouble())
    .build()
