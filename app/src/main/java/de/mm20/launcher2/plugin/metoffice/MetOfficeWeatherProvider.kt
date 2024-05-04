package de.mm20.launcher2.plugin.metoffice

import android.content.Intent
import android.util.Log
import de.mm20.launcher2.plugin.config.WeatherPluginConfig
import de.mm20.launcher2.plugin.metoffice.api.MetForecast
import de.mm20.launcher2.plugin.metoffice.api.MetForecastGeometry
import de.mm20.launcher2.plugin.metoffice.api.MetForecastTimeSeries
import de.mm20.launcher2.plugin.metoffice.api.MetWeatherCode
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

    private fun metToCondition(code: MetWeatherCode?): WeatherCondition {
        return when (code) {
            null -> WeatherCondition(WeatherIcon.Unknown)
            MetWeatherCode.TRACE_RAIN -> WeatherCondition(WeatherIcon.Drizzle)
            MetWeatherCode.CLEAR_NIGHT -> WeatherCondition(WeatherIcon.Clear, true)
            MetWeatherCode.SUNNY_DAY -> WeatherCondition(WeatherIcon.Clear, false)
            MetWeatherCode.PARTLY_CLOUDY_NIGHT -> WeatherCondition(WeatherIcon.PartlyCloudy, true)
            MetWeatherCode.PARTLY_CLOUDY_DAY -> WeatherCondition(WeatherIcon.PartlyCloudy, false)
            MetWeatherCode.MIST -> WeatherCondition(WeatherIcon.Haze)
            MetWeatherCode.FOG -> WeatherCondition(WeatherIcon.Fog)
            MetWeatherCode.CLOUDY -> WeatherCondition(WeatherIcon.Cloudy)
            MetWeatherCode.OVERCAST -> WeatherCondition(WeatherIcon.MostlyCloudy)
            MetWeatherCode.LIGHT_RAIN_SHOWER_NIGHT -> WeatherCondition(WeatherIcon.Showers, true)
            MetWeatherCode.LIGHT_RAIN_SHOWER_DAY -> WeatherCondition(WeatherIcon.Showers, false)
            MetWeatherCode.DRIZZLE -> WeatherCondition(WeatherIcon.Drizzle)
            MetWeatherCode.LIGHT_RAIN -> WeatherCondition(WeatherIcon.Showers)
            MetWeatherCode.HEAVY_RAIN_SHOWER_NIGHT -> WeatherCondition(WeatherIcon.Storm, true)
            MetWeatherCode.HEAVY_RAIN_SHOWER_DAY -> WeatherCondition(WeatherIcon.Storm, false)
            MetWeatherCode.HEAVY_RAIN -> WeatherCondition(WeatherIcon.Storm)
            MetWeatherCode.SLEET_SHOWER_NIGHT -> WeatherCondition(WeatherIcon.Sleet, true)
            MetWeatherCode.SLEET_SHOWER_DAY -> WeatherCondition(WeatherIcon.Sleet, false)
            MetWeatherCode.SLEET -> WeatherCondition(WeatherIcon.Sleet)
            MetWeatherCode.HAIL_SHOWER_NIGHT -> WeatherCondition(WeatherIcon.Hail, true)
            MetWeatherCode.HAIL_SHOWER_DAY -> WeatherCondition(WeatherIcon.Hail, false)
            MetWeatherCode.HAIL -> WeatherCondition(WeatherIcon.Hail)
            MetWeatherCode.LIGHT_SNOW_SHOWER_NIGHT -> WeatherCondition(WeatherIcon.Snow, true)
            MetWeatherCode.LIGHT_SNOW_SHOWER_DAY -> WeatherCondition(WeatherIcon.Snow, false)
            MetWeatherCode.LIGHT_SNOW -> WeatherCondition(WeatherIcon.Snow)
            MetWeatherCode.HEAVY_SNOW_SHOWER_NIGHT -> WeatherCondition(WeatherIcon.Snow, true)
            MetWeatherCode.HEAVY_SNOW_SHOWER_DAY -> WeatherCondition(WeatherIcon.Snow, false)
            MetWeatherCode.HEAVY_SNOW -> WeatherCondition(WeatherIcon.Snow)
            MetWeatherCode.THUNDER_SHOWER_NIGHT -> WeatherCondition(WeatherIcon.ThunderstormWithRain, true)
            MetWeatherCode.THUNDER_SHOWER_DAY -> WeatherCondition(WeatherIcon.ThunderstormWithRain, false)
            MetWeatherCode.THUNDER -> WeatherCondition(WeatherIcon.Thunderstorm)
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