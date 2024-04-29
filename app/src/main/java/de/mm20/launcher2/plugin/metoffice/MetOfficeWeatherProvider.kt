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
import de.mm20.launcher2.sdk.weather.hPa
import de.mm20.launcher2.sdk.weather.m_s
import de.mm20.launcher2.sdk.weather.mm
import kotlinx.coroutines.flow.first
import java.time.OffsetDateTime
import kotlin.math.roundToInt

class MetOfficeWeatherProvider : WeatherProvider(
    WeatherPluginConfig(300 * 1000L) // 360 calls per day is 240 sec between calls
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
        val pressure = if (weather.mslp != null) {
            weather.mslp.toDouble() / 100
        } else {
            null
        }
        val condition = metToCondition(weather.significantWeatherCode)
        return Forecast(
            timestamp = OffsetDateTime.parse(weather.time ?: return null).toInstant().toEpochMilli(),
            temperature = weather.screenTemperature?.C ?: return null,
            condition = "", //todo
            icon = condition.icon,
            night = condition.night,
            minTemp = weather.minScreenAirTemp?.C,
            maxTemp = weather.maxScreenAirTemp?.C,
            pressure = pressure?.hPa,
            humidity = weather.screenRelativeHumidity?.roundToInt(),
            windSpeed = weather.windSpeed10m?.m_s,
            windDirection = weather.windDirectionFrom10m,
            precipitation = weather.totalPrecipAmount?.mm,
            rainProbability = weather.probOfPrecipitation,
            clouds = null, // Not in Met API
            location = location,
            provider = context.getString(R.string.plugin_name),
            providerUrl = null, //todo
        )
    }

    data class WeatherCondition(val icon: WeatherIcon, val night: Boolean = false)

    private fun metToCondition(id: Int?): WeatherCondition {
        // https://www.metoffice.gov.uk/services/data/datapoint/code-definitions
        return when (id) {
            null -> WeatherCondition(WeatherIcon.Unknown)
            -1 -> WeatherCondition(WeatherIcon.Drizzle) // Trace rain
            0 -> WeatherCondition(WeatherIcon.Clear, true)
            1 -> WeatherCondition(WeatherIcon.Clear, false)
            2 -> WeatherCondition(WeatherIcon.PartlyCloudy, true)
            3 -> WeatherCondition(WeatherIcon.PartlyCloudy, false)
            5 -> WeatherCondition(WeatherIcon.Haze)
            6 -> WeatherCondition(WeatherIcon.Fog)
            7 -> WeatherCondition(WeatherIcon.Cloudy)
            8 -> WeatherCondition(WeatherIcon.MostlyCloudy)
            9 -> WeatherCondition(WeatherIcon.Showers, true)
            10 -> WeatherCondition(WeatherIcon.Showers, false)
            11 -> WeatherCondition(WeatherIcon.Drizzle)
            12 -> WeatherCondition(WeatherIcon.Showers)
            13 -> WeatherCondition(WeatherIcon.Storm, true)
            14 -> WeatherCondition(WeatherIcon.Storm, false)
            15 -> WeatherCondition(WeatherIcon.Storm)
            16 -> WeatherCondition(WeatherIcon.Sleet, true)
            17 -> WeatherCondition(WeatherIcon.Sleet, false)
            18 -> WeatherCondition(WeatherIcon.Sleet)
            19 -> WeatherCondition(WeatherIcon.Hail, true)
            20 -> WeatherCondition(WeatherIcon.Hail, false)
            21 -> WeatherCondition(WeatherIcon.Hail)
            22 -> WeatherCondition(WeatherIcon.Snow, true)
            23 -> WeatherCondition(WeatherIcon.Snow, false)
            24 -> WeatherCondition(WeatherIcon.Snow)
            25 -> WeatherCondition(WeatherIcon.Snow, true)
            26 -> WeatherCondition(WeatherIcon.Snow, false)
            27 -> WeatherCondition(WeatherIcon.Snow)
            28 -> WeatherCondition(WeatherIcon.ThunderstormWithRain, true)
            29 -> WeatherCondition(WeatherIcon.ThunderstormWithRain, false)
            30 -> WeatherCondition(WeatherIcon.Thunderstorm)
            else -> WeatherCondition(WeatherIcon.Unknown)
        }
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