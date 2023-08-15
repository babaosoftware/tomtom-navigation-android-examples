/*
 * ¬© 2023 TomTom NV. All rights reserved.
 *
 * This software is the proprietary copyright of TomTom NV and its subsidiaries and may be
 * used for internal evaluation purposes or commercial use strictly subject to separate
 * license agreement between you and TomTom NV. If you are the licensee, you are only permitted
 * to use this software in accordance with the terms of your license agreement. If you are
 * not the licensee, you are not authorized to use this software in any manner and should
 * immediately return or destroy it.
 */
package com.tomtom.sdk.examples.usecase


import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.tomtom.quantity.Speed
import com.tomtom.sdk.examples.R
import com.tomtom.sdk.location.GeoLocation
import com.tomtom.sdk.location.GeoPoint
import com.tomtom.sdk.location.LocationProvider
import com.tomtom.sdk.location.OnLocationUpdateListener
import com.tomtom.sdk.location.android.AndroidLocationProvider
import com.tomtom.sdk.location.mapmatched.MapMatchedLocationProvider
import com.tomtom.sdk.location.simulation.SimulationLocationProvider
import com.tomtom.sdk.location.simulation.strategy.InterpolationStrategy
import com.tomtom.sdk.map.display.MapOptions
import com.tomtom.sdk.map.display.TomTomMap
import com.tomtom.sdk.map.display.camera.CameraChangeListener
import com.tomtom.sdk.map.display.camera.CameraOptions
import com.tomtom.sdk.map.display.camera.CameraTrackingMode
import com.tomtom.sdk.map.display.common.screen.Padding
import com.tomtom.sdk.map.display.gesture.MapLongClickListener
import com.tomtom.sdk.map.display.image.ImageFactory
import com.tomtom.sdk.map.display.location.LocationMarkerOptions
import com.tomtom.sdk.map.display.marker.Label
import com.tomtom.sdk.map.display.marker.MarkerOptions
import com.tomtom.sdk.map.display.route.Instruction
import com.tomtom.sdk.map.display.route.RouteClickListener
import com.tomtom.sdk.map.display.route.RouteOptions
import com.tomtom.sdk.map.display.ui.MapFragment
import com.tomtom.sdk.map.display.ui.currentlocation.CurrentLocationButton.VisibilityPolicy
import com.tomtom.sdk.navigation.*
import com.tomtom.sdk.navigation.online.Configuration
import com.tomtom.sdk.navigation.online.OnlineTomTomNavigationFactory
import com.tomtom.sdk.navigation.replanning.BetterProposalAcceptanceMode
import com.tomtom.sdk.navigation.replanning.DeviationReplanningMode
import com.tomtom.sdk.navigation.routereplanner.RouteReplanner
import com.tomtom.sdk.navigation.routereplanner.online.OnlineRouteReplannerFactory
import com.tomtom.sdk.navigation.ui.NavigationFragment
import com.tomtom.sdk.navigation.ui.NavigationUiOptions
import com.tomtom.sdk.routing.RoutePlanner
import com.tomtom.sdk.routing.RoutePlanningCallback
import com.tomtom.sdk.routing.RoutePlanningResponse
import com.tomtom.sdk.routing.RoutingFailure
import com.tomtom.sdk.routing.online.OnlineRoutePlanner
import com.tomtom.sdk.routing.options.Itinerary
import com.tomtom.sdk.routing.options.RouteLegOptions
import com.tomtom.sdk.routing.options.RoutePlanningOptions
import com.tomtom.sdk.routing.options.calculation.ArrivalSidePreference
import com.tomtom.sdk.routing.options.calculation.ConsiderTraffic
import com.tomtom.sdk.routing.options.calculation.CostModel
import com.tomtom.sdk.routing.options.calculation.RouteType
import com.tomtom.sdk.routing.options.calculation.WaypointOptimization
import com.tomtom.sdk.routing.options.guidance.*
import com.tomtom.sdk.routing.route.Route
import com.tomtom.sdk.routing.route.Waypoint
import com.tomtom.sdk.vehicle.Vehicle

/**
 * This example shows how to build a simple navigation application using the TomTom Navigation SDK for Android.
 * The application displays a map and shows the user‚Äôs location. After the user selects a destination with a long click, the app plans a route and draws it on the map.
 * Navigation is started in a simulation mode, once the user taps on the route.
 * The application will display upcoming manoeuvres, remaining distance, estimated time of arrival (ETA), current speed, and speed limit information.
 *
 * For more details on this example, check out the tutorial: https://developer.tomtom.com/android/navigation/documentation/tutorials/navigation-use-case
 *
 **/

class BasicNavigationActivity : AppCompatActivity() {
    private lateinit var mapFragment: MapFragment
    private lateinit var tomTomMap: TomTomMap
    private lateinit var locationProvider: LocationProvider
    private lateinit var onLocationUpdateListener: OnLocationUpdateListener
    private lateinit var routePlanner: RoutePlanner
    private lateinit var routeReplanner: RouteReplanner
    private var route: Route? = null
    private lateinit var routePlanningOptions: RoutePlanningOptions
    private lateinit var tomTomNavigation: TomTomNavigation
    private lateinit var navigationFragment: NavigationFragment
    private var waypointsReachedMap: MutableMap<GeoPoint, Boolean>? = null

    private val simulationSpeed = 80.0 // Simulation speed in mph
    private val isSimulated = true // Set to false for real driving

    /**
     * Navigation SDK is only avaialble upon request.
     * Use the API key provided by TomTom to start using the SDK.
     */
    private val apiKey = "ICqQvGMGbu49DQmGhhwbzI78dvAUG9eV"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_basic_navigation)
        initMap()
        initLocationProvider()
        initRouting()
        initNavigation()
    }

    // private val routeUpdatedListener by lazy {
    //     RouteUpdatedListener { route, updateReason ->
    //         if (updateReason != RouteUpdateReason.Refresh &&
    //             updateReason != RouteUpdateReason.Increment &&
    //             updateReason != RouteUpdateReason.LanguageChange
    //         ) {
    //             tomTomMap.removeRoutes()
    //             drawRoute(route)
    //         }
    //     }
    // }

    private val waypointReachedListener by lazy {
        object : WaypointArrivalListener {
            override fun onWaypointReached(waypoint: Waypoint) {
                waypointsReachedMap?.let {
                    it[waypoint.place.coordinate] = true
                }
            }

            override fun onWaypointVisited(waypoint: Waypoint) {
            }
        }
    }

    /**
     * Displaying a map
     *
     * MapOptions is required to initialize the map.
     * Use MapFragment to display a map.
     * Optional configuration: You can further configure the map by setting various properties of the MapOptions object. You can learn more in the Map Configuration guide.
     * The last step is adding the MapFragment to the previously created container.
     */
    private fun initMap() {
        val mapOptions = MapOptions(mapKey = apiKey)
        mapFragment = MapFragment.newInstance(mapOptions)
        supportFragmentManager.beginTransaction()
            .replace(R.id.map_container, mapFragment)
            .commit()
        mapFragment.getMapAsync { map ->
            tomTomMap = map
            enableUserLocation()
            setUpMapListeners()
        }
    }

    /**
     * The SDK provides a LocationProvider interface that is used between different modules to get location updates.
     * This examples uses the AndroidLocationProvider.
     * Under the hood, the engine uses Android‚Äôs system location services.
     */
    private fun initLocationProvider() {
        locationProvider = AndroidLocationProvider(context = this)
    }

    /**
     * You can plan route by initializing by using the online route planner and default route replanner.
     */
    private fun initRouting() {
        routePlanner =
            OnlineRoutePlanner.create(context = this, apiKey = apiKey)
        routeReplanner = OnlineRouteReplannerFactory.create(routePlanner)
    }

    /**
     * To use navigation in the application, start by by initialising the navigation configuration.
     */
    private fun initNavigation() {
        val navigationConfiguration = Configuration(
            context = this,
            apiKey = apiKey,
            locationProvider = locationProvider,
            routeReplanner = routeReplanner,
            betterProposalAcceptanceMode = BetterProposalAcceptanceMode.UnreachableOnly,
            deviationReplanningMode = DeviationReplanningMode.None
        )
        tomTomNavigation = OnlineTomTomNavigationFactory.create(navigationConfiguration)
    }

    /**
     * In order to show the user‚Äôs location, the application must use the device‚Äôs location services, which requires the appropriate permissions.
     */
    private fun enableUserLocation() {
        if (areLocationPermissionsGranted()) {
            showUserLocation()
        } else {
            requestLocationPermission()
        }
    }

    /**
     * The LocationProvider itself only reports location changes. It does not interact internally with the map or navigation.
     * Therefore, to show the user‚Äôs location on the map you have to set the LocationProvider to the TomTomMap.
     * You also have to manually enable the location indicator.
     * It can be configured using the LocationMarkerOptions class.
     *
     * Read more about user location on the map in the Showing User Location guide.
     */
    private fun showUserLocation() {
        locationProvider.enable()
        // zoom to current location at city level
        onLocationUpdateListener = OnLocationUpdateListener { location ->
            tomTomMap.moveCamera(CameraOptions(location.position, zoom = 8.0))
            locationProvider.removeOnLocationUpdateListener(onLocationUpdateListener)
        }
        locationProvider.addOnLocationUpdateListener(onLocationUpdateListener)
        tomTomMap.setLocationProvider(locationProvider)
        val locationMarker = LocationMarkerOptions(type = LocationMarkerOptions.Type.Pointer)
        tomTomMap.enableLocationMarker(locationMarker)
    }

    /**
     * In this example on planning a route, the origin is the user‚Äôs location and the destination is determined by the user selecting a location on the map.
     * Navigation is started once the user taps on the route.
     *
     * To mark the destination on the map, add the MapLongClickListener event handler to the map view.
     * To start navigation, add the addRouteClickListener event handler to the map view.
     */
    private fun setUpMapListeners() {
        tomTomMap.addMapLongClickListener(mapLongClickListener)
        tomTomMap.addRouteClickListener(routeClickListener)
    }

    /**
     * Used to calculate a route based on a selected location.
     * - The method removes all polygons, circles, routes, and markers that were previously added to the map.
     * - It then creates a route between the user‚Äôs location and the selected location.
     * - The method needs to return a boolean value when the callback is consumed.
     */
    private val mapLongClickListener = MapLongClickListener { geoPoint ->
        clearMap()
        calculateRouteTo(geoPoint)
        true
    }

    /**
     * Used to start navigation based on a tapped route.
     * - Hide the location button
     * - Then start the navigation using the selected route.
     */
    private val routeClickListener = RouteClickListener {
        route?.let { route ->
            mapFragment.currentLocationButton.visibilityPolicy = VisibilityPolicy.Invisible
            startNavigation(route)
        }
    }

    /**
     * Used to calculate a route using the following parameters:
     * - InstructionType - This indicates that the routing result has to contain guidance instructions.
     * - InstructionPhoneticsType - This specifies whether to include phonetic transcriptions in the response.
     * - AnnouncementPoints - When this parameter is specified, the instruction in the response includes up to three additional fine-grained announcement points, each with its own location, maneuver type, and distance to the instruction point.
     * - ExtendedSections - This specifies whether to include extended guidance sections in the response, such as sections of type road shield, lane, and speed limit.
     * - ProgressPoints - This specifies whether to include progress points in the response.
     */
    private fun calculateRouteTo(destination: GeoPoint) {
        //calculateRouteToOriginal(destination)
        calculateRouteWaypointsAndMultipleLegs()
        //calculateRouteNoWaypointsSingleLeg()
        //calculateRouteWaypointsAndNoLegs()
    }

    // private fun calculateRouteToOriginal(destination: GeoPoint) {
    //     val userLocation =
    //         tomTomMap.currentLocation?.position ?: return
    //     val itinerary = Itinerary(origin = userLocation, destination = destination)
    //     routePlanningOptions = RoutePlanningOptions(
    //         itinerary = itinerary,
    //         guidanceOptions = GuidanceOptions(
    //             instructionType = InstructionType.Text,
    //             phoneticsType = InstructionPhoneticsType.Ipa,
    //             announcementPoints = AnnouncementPoints.All,
    //             extendedSections = ExtendedSections.All,
    //             progressPoints = ProgressPoints.All
    //         ),
    //         vehicle = Vehicle.Car()
    //     )
    //     routePlanner.planRoute(routePlanningOptions, routePlanningCallback)
    // }

    val track = mutableListOf(
        listOf(
            GeoPoint(41.424386, -73.502532),
            GeoPoint(41.424028, -73.50241),
            GeoPoint(41.423562, -73.502234),
            GeoPoint(41.423413, -73.502132),
            GeoPoint(41.423016, -73.501762),
            GeoPoint(41.422956, -73.501988),
            GeoPoint(41.422603, -73.50292),
            GeoPoint(41.422522, -73.503105),
            GeoPoint(41.42281, -73.503322),
            GeoPoint(41.42304, -73.503502),
            GeoPoint(41.423114, -73.503615),
            GeoPoint(41.423131, -73.503754),
            GeoPoint(41.423129, -73.503786),
            GeoPoint(41.423116, -73.503943),
            GeoPoint(41.423077, -73.504052),
        ),
        listOf(
            GeoPoint(41.423077, -73.504052),
            GeoPoint(41.423049, -73.504131),
            GeoPoint(41.422973, -73.504343),
            GeoPoint(41.422831, -73.504714),
            GeoPoint(41.422702, -73.504933),
            GeoPoint(41.422587, -73.505032),
            GeoPoint(41.422103, -73.505265),
            GeoPoint(41.4219, -73.50532),
            GeoPoint(41.421625, -73.505372),
            GeoPoint(41.421015, -73.505322),
            GeoPoint(41.421318, -73.504389),
            GeoPoint(41.421546, -73.503722),
        ),
        listOf(
            GeoPoint(41.421546, -73.503722),
            GeoPoint(41.421729, -73.503187),
            GeoPoint(41.421803, -73.503037),
            GeoPoint(41.42189, -73.502957),
            GeoPoint(41.42201, -73.502934),
            GeoPoint(41.422173, -73.502955),
            GeoPoint(41.422352, -73.503017),
            GeoPoint(41.422522, -73.503105),
            GeoPoint(41.42281, -73.503322),
            GeoPoint(41.42304, -73.503502),
            GeoPoint(41.423114, -73.503615),
            GeoPoint(41.423131, -73.503754),
            GeoPoint(41.423129, -73.503786),
            GeoPoint(41.423116, -73.503943),
            GeoPoint(41.423049, -73.504131),
            GeoPoint(41.422973, -73.504343),
            GeoPoint(41.422831, -73.504714),
            GeoPoint(41.422702, -73.504933),
            GeoPoint(41.422587, -73.505032),
            GeoPoint(41.422103, -73.505265),
            GeoPoint(41.4219, -73.50532),
            GeoPoint(41.421625, -73.505372),
            GeoPoint(41.421015, -73.505322),
            GeoPoint(41.420768, -73.505274),
            GeoPoint(41.420475, -73.505188),
            GeoPoint(41.419706, -73.504835),
            GeoPoint(41.41916, -73.504506),
            GeoPoint(41.41854, -73.504179),
            GeoPoint(41.418091, -73.503874),
            GeoPoint(41.417919, -73.503753),
            GeoPoint(41.417803, -73.504143),
            GeoPoint(41.417766, -73.504375),
            GeoPoint(41.417769, -73.504586),
            GeoPoint(41.417785, -73.504796),
            GeoPoint(41.41786, -73.505565),
            GeoPoint(41.417867, -73.505634),
            GeoPoint(41.417579, -73.505691),
            GeoPoint(41.417177, -73.505757),
            GeoPoint(41.417101, -73.505772),
            GeoPoint(41.4168, -73.50583),
            GeoPoint(41.416602, -73.505877),
            GeoPoint(41.416479, -73.505923),
            GeoPoint(41.416388, -73.505994),
            GeoPoint(41.416268, -73.506124),
            GeoPoint(41.415975, -73.505845),
            GeoPoint(41.415602, -73.50546),
            GeoPoint(41.41558, -73.505431),
        ),
        listOf(
            GeoPoint(41.41558, -73.505431),
            GeoPoint(41.415491, -73.505317),
            GeoPoint(41.415352, -73.505135),
            GeoPoint(41.415333, -73.50511),
            GeoPoint(41.415068, -73.504751),
        ),
        listOf(
            GeoPoint(41.415068, -73.504751),
            GeoPoint(41.414969, -73.50461),
            GeoPoint(41.414559, -73.504029),
        ),
        listOf(
            GeoPoint(41.414559, -73.504029),
            GeoPoint(41.414495, -73.503938),
            GeoPoint(41.414269, -73.503624),
            GeoPoint(41.41401, -73.503254),
            GeoPoint(41.413889, -73.503142),
            GeoPoint(41.413778, -73.503079),
            GeoPoint(41.413651, -73.503087),
            GeoPoint(41.413483, -73.503114),
            GeoPoint(41.413348, -73.503193),
            GeoPoint(41.413171, -73.503349),
            GeoPoint(41.412945, -73.503616),
            GeoPoint(41.412845, -73.503773),
            GeoPoint(41.412773, -73.503972),
            GeoPoint(41.412757, -73.504107),
            GeoPoint(41.412754, -73.504311),
            GeoPoint(41.412786, -73.504448),
            GeoPoint(41.41283, -73.504597),
            GeoPoint(41.412913, -73.504767),
            GeoPoint(41.413177, -73.505158),
            GeoPoint(41.413404, -73.50549),
            GeoPoint(41.413617, -73.505812),
            GeoPoint(41.413651, -73.505863),
            GeoPoint(41.413765, -73.506035),
            GeoPoint(41.413876, -73.50627),
            GeoPoint(41.413898, -73.506316),
            GeoPoint(41.413995, -73.50661),
            GeoPoint(41.414003, -73.506648),
            GeoPoint(41.414076, -73.506987),
            GeoPoint(41.414157, -73.507327),
            GeoPoint(41.414219, -73.507591),
            GeoPoint(41.414384, -73.508109),
            GeoPoint(41.414457, -73.508242),
            GeoPoint(41.414607, -73.508379),
            GeoPoint(41.414879, -73.508577),
            GeoPoint(41.415422, -73.507551),
            GeoPoint(41.416268, -73.506124),
            GeoPoint(41.416388, -73.505994),
            GeoPoint(41.416479, -73.505923),
            GeoPoint(41.416602, -73.505877),
            GeoPoint(41.4168, -73.50583),
            GeoPoint(41.417101, -73.505772),
            GeoPoint(41.417177, -73.505757),
            GeoPoint(41.417579, -73.505691),
            GeoPoint(41.417867, -73.505634),
            GeoPoint(41.417956, -73.50648),
            GeoPoint(41.417953, -73.506729),
            GeoPoint(41.417947, -73.506775),
            GeoPoint(41.417922, -73.506966),
            GeoPoint(41.417873, -73.507265),
            GeoPoint(41.417752, -73.507591),
            GeoPoint(41.417593, -73.50788),
            GeoPoint(41.417584, -73.507896),
            GeoPoint(41.417485, -73.508048),
            GeoPoint(41.417304, -73.508247),
        ),
    )

    private fun calculateRouteWaypointsAndMultipleLegs() {

        val lat = locationProvider.lastKnownLocation!!.position.latitude
        val lon = locationProvider.lastKnownLocation!!.position.longitude

        val origin = GeoPoint(lat, lon)
        val destination = track.last().last()
        val waypoints = (track.map { it.last() }).toMutableList()
        waypointsReachedMap = waypoints.associateWith { false }.toMutableMap()
        waypoints.forEachIndexed { index, geoPoint ->
            tomTomMap.addMarker(
                MarkerOptions(
                    geoPoint,
                    pinImage = ImageFactory.fromResource(android.R.drawable.star_big_on),
                    label = Label("${index+1}", textSize = 20.0),
                )
            )
        }
        val routeLegOptions = track.map { trackSegment ->
            RouteLegOptions(supportingPoints = trackSegment)
        }
        val itinerary = Itinerary(
            origin = origin,
            destination =destination,
            waypoints = waypoints.map{ it },
            heading = null,
        )
        routePlanningOptions = RoutePlanningOptions(
            itinerary = itinerary,
            guidanceOptions = GuidanceOptions(
            instructionType = InstructionType.Text,
            phoneticsType = InstructionPhoneticsType.Ipa,
            announcementPoints = AnnouncementPoints.All,
            extendedSections = ExtendedSections.All,
            progressPoints = ProgressPoints.None
        ),
            costModel = CostModel(
                RouteType.Short,
                ConsiderTraffic.No,
            ),
            waypointOptimization = WaypointOptimization.None,
            vehicle = Vehicle.Bus(),
            routeLegOptions = routeLegOptions,
            arrivalSidePreference = ArrivalSidePreference.AnySide,
        )
        routePlanner.planRoute(routePlanningOptions, routePlanningCallback)
        tomTomNavigation.addRouteDeviationListener(routeDeviationListener)
    }
    
    // private fun calculateRouteWaypointsAndMultipleLegs() {
    //     val origin = track.first().first()
    //     val destination = track.last().last()
    //     val waypoints = (track.map { it.last() }).toMutableList()
    //     waypoints.removeLast() // destination is passed separately

    //     waypointsReachedMap = waypoints.associateWith { false }.toMutableMap()

    //     waypoints.forEach {
    //         tomTomMap.addMarker(
    //             MarkerOptions(
    //                 it,
    //                 pinImage = ImageFactory.fromResource(android.R.drawable.star_big_on)
    //             )
    //         )
    //     }
    //     val routeLegOptions = track.map { trackSegment ->
    //         RouteLegOptions(supportingPoints = trackSegment)
    //     }

    //     val itinerary = Itinerary(
    //         origin = origin,
    //         destination = destination,
    //         waypoints = waypoints.map { it })
    //     routePlanningOptions = RoutePlanningOptions(
    //         itinerary = itinerary,
    //         guidanceOptions = GuidanceOptions(
    //             instructionType = InstructionType.Text,
    //             phoneticsType = InstructionPhoneticsType.Ipa,
    //             announcementPoints = AnnouncementPoints.All,
    //             extendedSections = ExtendedSections.All,
    //             progressPoints = ProgressPoints.None
    //         ),
    //         vehicle = Vehicle.Car(),
    //         routeLegOptions = routeLegOptions,
    //     )

    //     tomTomNavigation.addRouteDeviationListener(routeDeviationListener)
    //     routePlanner.planRoute(routePlanningOptions, routePlanningCallback)
    // }

    fun Double.round(decimals: Int = 2): Double = "%.${decimals}f".format(this).toDouble()


    private val routeDeviationListener by lazy {
        RouteDeviationListener { location, route ->

//            if (isSimulated) {
//                tomTomNavigation.update(NavigationOptions(RoutePlan(route, routePlanningOptions)))
//                return@RouteDeviationListener
//            }

            val tempTrack = track
            val destination = track.last().last()
    
            //Delete reached waypoints and its legs
            tempTrack.removeIf { waypointsReachedMap?.get(it.last()) == true }
    
            val updatedWaypoints = (tempTrack.map { it.last() }).toMutableList()
            updatedWaypoints.removeLast() // destination is passed separately
    
            // add the markers
            // updatedWaypoints.forEach {
            //     tomTomMap.addMarker(
            //         MarkerOptions(
            //             it,
            //             pinImage = ImageFactory.fromResource(android.R.drawable.star_big_on)
            //         )
            //     )
            // }
    
            // define supporting points
            val updatedRouteLegOptions = tempTrack.map { trackSegment ->
                RouteLegOptions(supportingPoints = trackSegment)
            }
    
            val heading = tomTomMap.currentLocation?.course
    
            val origin = GeoPoint(location.position.latitude.round(6), location.position.longitude.round(6))
    
    
            // define itinerary
            val updatedItinerary = Itinerary(
                origin = origin,
                destination = destination,
                waypoints = updatedWaypoints.map { it },
                heading = heading)
    
            //define routePlanningOptions
            routePlanningOptions = RoutePlanningOptions(
                itinerary = updatedItinerary,
                guidanceOptions = GuidanceOptions(
                    instructionType = InstructionType.Text,
                    phoneticsType = InstructionPhoneticsType.Ipa,
                    announcementPoints = AnnouncementPoints.All,
                    extendedSections = ExtendedSections.All,
                    progressPoints = ProgressPoints.None,
                ),
                waypointOptimization = WaypointOptimization.None,
                vehicle = Vehicle.Bus(),
                routeLegOptions = updatedRouteLegOptions,
            )
    
            //plan the route
            routePlanner.planRoute(routePlanningOptions, routePlanningCallbackOnDeviation)
        }
    }
    private val routePlanningCallback = object : RoutePlanningCallback {
        override fun onSuccess(result: RoutePlanningResponse) {
            route = result.routes.first()
            drawRoute(route!!)
        }

        override fun onFailure(failure: RoutingFailure) {
            Toast.makeText(this@BasicNavigationActivity, failure.message, Toast.LENGTH_SHORT).show()
        }

        override fun onRoutePlanned(route: Route) = Unit
    }

    private val routePlanningCallbackOnDeviation = object : RoutePlanningCallback {
        override fun onSuccess(result: RoutePlanningResponse) {
            route = result.routes.first()
            drawRoute(route!!)
            val routePlan = RoutePlan(route!!, routePlanningOptions)
            navigationFragment.update(routePlan)
        }

        override fun onFailure(failure: RoutingFailure) {
            Toast.makeText(this@BasicNavigationActivity, failure.message, Toast.LENGTH_SHORT).show()
        }

        override fun onRoutePlanned(route: Route) = Unit
    }

    /**
     * Used to draw route on the map
     * You can show the overview of the added routes using the TomTomMap.zoomToRoutes(Int) method. Note that its padding parameter is expressed in pixels.
     */
    private fun drawRoute(route: Route) {
        tomTomMap.removeRoutes()
        val instructions = route.mapInstructions()
        val routeOptions = RouteOptions(
            geometry = route.geometry,
            destinationMarkerVisible = true,
            departureMarkerVisible = true,
            instructions = instructions,
            routeOffset = route.routePoints.map { it.routeOffset }
        )
        tomTomMap.addRoute(routeOptions)
        tomTomMap.zoomToRoutes(ZOOM_TO_ROUTE_PADDING)
    }

    /**
     * For the navigation use case, the instructions can be drawn on the route in form of arrows that indicate maneuvers.
     * To do this, map the Instruction object provided by routing to the Instruction object used by the map.
     * Note that during navigation, you need to update the progress property of the drawn route to display the next instructions.
     */
    private fun Route.mapInstructions(): List<Instruction> {
        val routeInstructions = legs.flatMap { routeLeg -> routeLeg.instructions }
        return routeInstructions.map {
            Instruction(
                routeOffset = it.routeOffset,
                combineWithNext = it.combineWithNext
            )
        }
    }

    /**
     * Used to start navigation by
     * - initializing the NavigationFragment to display the UI navigation information,
     * - passing the Route object along which the navigation will be done, and RoutePlanningOptions used during the route planning,
     * - handling the updates to the navigation states using the NavigationListener.
     * Note that you have to set the previously-created TomTom Navigation object to the NavigationFragment before using it.
     */

    private fun startNavigation(route: Route) {
        initNavigationFragment()
        navigationFragment.setTomTomNavigation(tomTomNavigation)
        val routePlan = RoutePlan(route, routePlanningOptions)
        navigationFragment.startNavigation(routePlan)
        navigationFragment.addNavigationListener(navigationListener)
        tomTomNavigation.addWaypointArrivalListener(waypointReachedListener)
        tomTomNavigation.addProgressUpdatedListener(progressUpdatedListener)
        //tomTomNavigation.addRouteUpdatedListener(routeUpdatedListener)
    }

    /**
     * Handle the updates to the navigation states using the NavigationListener
     * - Use CameraChangeListener to observe camera tracking mode and detect if the camera is locked on the chevron. If the user starts to move the camera, it will change and you can adjust the UI to suit.
     * - Use the SimulationLocationProvider for testing purposes.
     * - Once navigation is started, the camera is set to follow the user position, and the location indicator is changed to a chevron. To match raw location updates to the routes, use MapMatchedLocationProvider and set it to the TomTomMap.
     * - Set the bottom padding on the map. The padding sets a safe area of the MapView in which user interaction is not received. It is used to uncover the chevron in the navigation panel.
     */
    private val navigationListener = object : NavigationFragment.NavigationListener {
        override fun onStarted() {
            tomTomMap.addCameraChangeListener(cameraChangeListener)
            tomTomMap.cameraTrackingMode = CameraTrackingMode.FollowRoute
            tomTomMap.enableLocationMarker(LocationMarkerOptions(LocationMarkerOptions.Type.Chevron))
            setMapMatchedLocationProvider()
            if (isSimulated)
                setSimulationLocationProviderToNavigation(route!!)
            setMapNavigationPadding()
        }

        override fun onFailed(failure: NavigationFailure) {
            Toast.makeText(this@BasicNavigationActivity, failure.message, Toast.LENGTH_SHORT).show()
            stopNavigation()
        }

        override fun onStopped() {
            stopNavigation()
        }
    }

    /**
     * Used to initialize the NavigationFragment to display the UI navigation information,
     */
    private fun initNavigationFragment() {
        val navigationUiOptions = NavigationUiOptions(
            keepInBackground = true
        )
        navigationFragment = NavigationFragment.newInstance(navigationUiOptions)
        supportFragmentManager.beginTransaction()
            .add(R.id.navigation_fragment_container, navigationFragment)
            .commitNow()
    }

    private val progressUpdatedListener = ProgressUpdatedListener {
        tomTomMap.routes.first().progress = it.distanceAlongRoute
    }

    /**
     * Use the SimulationLocationProvider for testing purposes.
     */
    // private fun setSimulationLocationProviderToNavigation(route: Route) {
    //     // val routeGeoLocations = route.geometry.map { GeoLocation(it) }
    //     val simulation = listOf(
    //         GeoPoint(52.324592, 4.869293),
    //         GeoPoint(52.324579, 4.870189),
    //         GeoPoint(52.323271, 4.872340),
    //         GeoPoint(52.324641, 4.872281),
    //         GeoPoint(52.324677, 4.874609),
    //         GeoPoint(52.326985, 4.874459)
    //     ).map { GeoLocation(it) }
    //     val simulationStrategy = InterpolationStrategy(simulation)
    //     locationProvider = SimulationLocationProvider.create(strategy = simulationStrategy)
    //     tomTomNavigation.locationProvider = locationProvider
    //     locationProvider.enable()
    // }

    private fun setSimulationLocationProviderToNavigation(route: Route) {

        val interpolationStrategy = InterpolationStrategy(
            locations = route.geometry.map { GeoLocation(it) },
            currentSpeed = Speed.Companion.milesPerHour(simulationSpeed)
        )
        locationProvider = SimulationLocationProvider.create(interpolationStrategy)
        tomTomNavigation.locationProvider = locationProvider
        locationProvider.enable()
    }

    /**
     * Stop the navigation process using NavigationFragment.
     * This hides the UI elements and calls the TomTomNavigation.stop() method.
     * Don‚Äôt forget to reset any map settings that were changed, such as camera tracking, location marker, and map padding.
     */
    private fun stopNavigation() {
        navigationFragment.stopNavigation()
        mapFragment.currentLocationButton.visibilityPolicy =
            VisibilityPolicy.InvisibleWhenRecentered
        tomTomMap.cameraTrackingMode = CameraTrackingMode.None
        tomTomMap.enableLocationMarker(LocationMarkerOptions(LocationMarkerOptions.Type.Pointer))
        resetMapPadding()
        navigationFragment.removeNavigationListener(navigationListener)
        tomTomNavigation.removeProgressUpdatedListener(progressUpdatedListener)
        routeDeviationListener.let { tomTomNavigation.removeRouteDeviationListener(it) }
        clearMap()
        initLocationProvider()
        enableUserLocation()
    }

    /**
     * Set the bottom padding on the map. The padding sets a safe area of the MapView in which user interaction is not received. It is used to uncover the chevron in the navigation panel.
     */
    private fun setMapNavigationPadding() {
        val paddingBottom = resources.getDimensionPixelOffset(R.dimen.map_padding_bottom)
        val padding = Padding(0, 0, 0, paddingBottom)
        setPadding(padding)
    }

    private fun setPadding(padding: Padding) {
        val scale: Float = resources.displayMetrics.density
        val paddingInPixels = Padding(
            top = (padding.top * scale).toInt(),
            left = (padding.left * scale).toInt(),
            right = (padding.right * scale).toInt(),
            bottom = (padding.bottom * scale).toInt()
        )
        tomTomMap.setPadding(paddingInPixels)
    }

    private fun resetMapPadding() {
        tomTomMap.setPadding(Padding(0, 0, 0, 0))
    }

    /**
     * Once navigation is started, the camera is set to follow the user position, and the location indicator is changed to a chevron.
     * To match raw location updates to the routes, use MapMatchedLocationProvider and set it to the TomTomMap.
     */
    private fun setMapMatchedLocationProvider() {
        val mapMatchedLocationProvider = MapMatchedLocationProvider(tomTomNavigation)
        tomTomMap.setLocationProvider(mapMatchedLocationProvider)
        mapMatchedLocationProvider.enable()
    }

    /**
     *
     * The method removes all polygons, circles, routes, and markers that were previously added to the map.
     */
    private fun clearMap() {
        tomTomMap.clear()
    }

    private fun requestLocationPermission() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private val cameraChangeListener by lazy {
        CameraChangeListener {
            val cameraTrackingMode = tomTomMap.cameraTrackingMode
            if (cameraTrackingMode == CameraTrackingMode.FollowRoute) {
                navigationFragment.navigationView.showSpeedView()
            } else {
                navigationFragment.navigationView.hideSpeedView()
            }
        }
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true &&
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            showUserLocation()
        } else {
            Toast.makeText(
                this,
                getString(R.string.location_permission_denied),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun areLocationPermissionsGranted() = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        locationProvider.close()
        tomTomNavigation.close()
        super.onDestroy()
    }

    companion object {
        private const val ZOOM_TO_ROUTE_PADDING = 100
    }
}
