package de.mm20.launcher2.plugin.metoffice

import android.content.Intent
import android.util.Log
import de.mm20.launcher2.plugin.config.WeatherPluginConfig
import de.mm20.launcher2.plugin.metoffice.api.MetForecast
import de.mm20.launcher2.plugin.metoffice.api.MetForecastGeometry
import de.mm20.launcher2.plugin.metoffice.api.MetForecastTimeSeries
import de.mm20.launcher2.sdk.PluginState
import de.mm20.launcher2.sdk.weather.C
import de.mm20.launcher2.sdk.weather.Forecast
import de.mm20.launcher2.sdk.weather.WeatherIcon
import de.mm20.launcher2.sdk.weather.WeatherLocation
import de.mm20.launcher2.sdk.weather.WeatherProvider
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.OffsetDateTime

class MetOfficeWeatherProvider : WeatherProvider(
    WeatherPluginConfig()
) {

    private lateinit var apiClient: MetOfficeApiClient

    override fun onCreate(): Boolean {
        apiClient = MetOfficeApiClient(context!!.applicationContext)
        return super.onCreate()
    }

    override suspend fun getWeatherData(location: WeatherLocation, lang: String?): List<Forecast>? {
        return when (location) {
            is WeatherLocation.LatLon -> getWeatherDataInternal(
                location.lat,
                location.lon,
                location.name
            )

            else -> {
                Log.e("OWMWeatherProvider", "Invalid location $location")
                null
            }
        }
    }

    override suspend fun getWeatherData(lat: Double, lon: Double, lang: String?): List<Forecast>? {
        return getWeatherDataInternal(lat, lon, null)
    }

    private suspend fun getWeatherDataInternal(
        lat: Double,
        lon: Double,
        locationName: String?
    ): List<Forecast>? {
        val forecastList = mutableListOf<Forecast>()

        val forecast: MetForecast = apiClient.forecast(lat, lon)

        if (forecast.features.isNullOrEmpty()) {
            Log.e("MetOfficeWeatherProvider", "Forecast response has no features")
            return null
        }

        val feature = forecast.features[0]

        if (feature.geometry == null) {
            Log.e("MetOfficeWeatherProvider", "Forecast response has no feature geometry")
            return null
        }

        if (feature.geometry.coordinates == null) {
            Log.e("MetOfficeWeatherProvider", "Forecast response has no geometry coordinates")
            return null
        }

        if (feature.properties == null) {
            Log.e("MetOfficeWeatherProvider", "Forecast response has no feature properties")
            return null
        }

        val loc = locationName ?: "${feature.properties.location?.name}"
        val timeSeries = feature.properties.timeSeries

        if (timeSeries.isNullOrEmpty()) {
            Log.e("MetOfficeWeatherProvider", "Forecast response has no timeSeries")
            return null
        }

        for (time in timeSeries) {
            forecastList += metToForecast(
                time,
                loc,
                feature.geometry
            ) ?: continue
        }

        return forecastList
    }

    private fun metToForecast(weather: MetForecastTimeSeries, location: String, coordinates: MetForecastGeometry): Forecast? {
        val context = context ?: return null
        return Forecast(
            timestamp = OffsetDateTime.parse(weather.time ?: return null).toInstant().toEpochMilli(),
            condition = "",
            icon = WeatherIcon.Unknown,
            location = location,
            provider = context.getString(R.string.plugin_name),
            temperature = weather.screenTemperature?.C ?: return null
        )
    }

    override suspend fun findLocations(query: String, lang: String): List<WeatherLocation> {
        val geo = apiClient.geo(q = query, limit = 5)

        return geo.mapNotNull {
            val name = it.name ?: return@mapNotNull null
            if ((it.latLong ?: return@mapNotNull null).size < 2) {
                return@mapNotNull null
            }
            WeatherLocation.LatLon(
                name = "$name, ${it.area}",
                lat = it.latLong[0],
                lon = it.latLong[1],
            )
        }
    }

    override suspend fun getPluginState(): PluginState {
        val context = context!!
        apiClient.apiKey.first() ?: return PluginState.SetupRequired(
            Intent(context, SettingsActivity::class.java),
            context.getString(R.string.plugin_state_setup_required)
        )
        return PluginState.Ready()
    }

}