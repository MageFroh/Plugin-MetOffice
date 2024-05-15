package de.mm20.launcher2.plugin.metoffice

import android.content.Intent
import android.util.Log
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import com.fonfon.kgeohash.GeoHash
import de.mm20.launcher2.plugin.config.WeatherPluginConfig
import de.mm20.launcher2.plugin.metoffice.api.MetForecast
import de.mm20.launcher2.plugin.metoffice.api.MetForecastGeometry
import de.mm20.launcher2.plugin.metoffice.api.MetForecastTimeSeries
import de.mm20.launcher2.plugin.metoffice.api.MetWeatherCode
import de.mm20.launcher2.sdk.PluginState
import de.mm20.launcher2.sdk.weather.C
import de.mm20.launcher2.sdk.weather.Forecast
import de.mm20.launcher2.sdk.weather.Temperature
import de.mm20.launcher2.sdk.weather.WeatherIcon
import de.mm20.launcher2.sdk.weather.WeatherLocation
import de.mm20.launcher2.sdk.weather.WeatherProvider
import de.mm20.launcher2.sdk.weather.hPa
import de.mm20.launcher2.sdk.weather.m_s
import de.mm20.launcher2.sdk.weather.mm
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

class MetOfficeWeatherProvider : WeatherProvider(
    WeatherPluginConfig(500 * 1000L) // 360 calls per day is 240 sec between calls, but we do 2 calls per request
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
        val forecast = getWeatherDataInternal(lat, lon, locationName, MetOfficeApiClient.MetForecastType.HOURLY)
        val threeHourlyForecast = getWeatherDataInternal(lat, lon, locationName, MetOfficeApiClient.MetForecastType.THREE_HOURLY)

        if (forecast.isNullOrEmpty()) {
            Log.w("MetOfficeWeatherProvider", "No hourly forecast available")
            return threeHourlyForecast
        }
        if (threeHourlyForecast.isNullOrEmpty()) {
            Log.w("MetOfficeWeatherProvider", "No 3-hourly forecast available")
            return forecast
        }

        val lastHourlyForecast = forecast.last().timestamp
        threeHourlyForecast.stream()
            .filter { it.timestamp >= lastHourlyForecast + 3600 }
            .forEach { forecast.add(it) }
        Log.d("MetOfficeWeatherProvider", "${forecast.size} entries returned in total")
        return forecast
    }

    private suspend fun getWeatherDataInternal(
        lat: Double,
        lon: Double,
        locationName: String?,
        type: MetOfficeApiClient.MetForecastType
    ): MutableList<Forecast>? {
        Log.i("MetOfficeWeatherProvider", "Fetching $type forecast for $lat, $lon")
        val forecastList = mutableListOf<Forecast>()

        val forecast: MetForecast = apiClient.forecast(lat, lon, forecastType = type)

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

        val minTimestamp = Instant.now().minus(1, ChronoUnit.HOURS).toEpochMilli()
        for (time in timeSeries) {
            val newForecast = metToForecast(
                time,
                loc,
                feature.geometry
            ) ?: continue

            if (newForecast.timestamp > minTimestamp) {
                forecastList += newForecast
            }
        }

        Log.d("MetOfficeWeatherProvider", "${forecastList.size} entries fetched")
        return forecastList
    }

    private fun metToForecast(weather: MetForecastTimeSeries, location: String, geometry: MetForecastGeometry): Forecast? {
        val context = context ?: return null
        val pressure = if (weather.mslp != null) {
            weather.mslp.toDouble() / 100
        } else {
            null
        }

        val condition = metToCondition(weather.significantWeatherCode)

        val temperature: Temperature? = if (weather.screenTemperature != null) {
            weather.screenTemperature.C
        } else if (weather.maxScreenAirTemp != null && weather.minScreenAirTemp != null) {
            ((weather.maxScreenAirTemp + weather.minScreenAirTemp) / 2).C
        } else {
            null
        }

        Log.d("MetOfficeWeatherProvider", "${weather.time}: ${condition.text}, $temperature")

        return Forecast(
            timestamp = OffsetDateTime.parse(weather.time ?: return null).toInstant().toEpochMilli(),
            temperature = temperature ?: return null,
            condition = condition.text,
            icon = condition.icon,
            night = condition.night,
            minTemp = weather.minScreenAirTemp?.C,
            maxTemp = weather.maxScreenAirTemp?.C,
            pressure = pressure?.hPa,
            humidity = weather.screenRelativeHumidity?.roundToInt(),
            windSpeed = weather.windSpeed10m?.m_s,
            windDirection = weather.windDirectionFrom10m,
            precipitation = weather.totalPrecipAmount?.mm,
            rainProbability = weather.probOfPrecipitation?.let { (ceil(weather.probOfPrecipitation / 10.0) * 10).toInt() },
            clouds = null, // Not in Met API
            location = location,
            provider = context.getString(R.string.plugin_name),
            providerUrl = metGeometryToMetUrl(geometry),
        )
    }

    data class WeatherCondition(val text: String, val icon: WeatherIcon, val night: Boolean = false)

    private fun metToCondition(code: MetWeatherCode?): WeatherCondition {
        val text = code?.name?.replace("_", " ")?.toLowerCase(Locale.current) ?: "Unknown"
        return when (code) {
            null -> WeatherCondition(text, WeatherIcon.Unknown)
            MetWeatherCode.TRACE_RAIN -> WeatherCondition(text, WeatherIcon.Drizzle)
            MetWeatherCode.CLEAR_NIGHT -> WeatherCondition(text, WeatherIcon.Clear, true)
            MetWeatherCode.SUNNY_DAY -> WeatherCondition(text, WeatherIcon.Clear, false)
            MetWeatherCode.PARTLY_CLOUDY_NIGHT -> WeatherCondition(text, WeatherIcon.PartlyCloudy, true)
            MetWeatherCode.PARTLY_CLOUDY_DAY -> WeatherCondition(text, WeatherIcon.PartlyCloudy, false)
            MetWeatherCode.MIST -> WeatherCondition(text, WeatherIcon.Haze)
            MetWeatherCode.FOG -> WeatherCondition(text, WeatherIcon.Fog)
            MetWeatherCode.CLOUDY -> WeatherCondition(text, WeatherIcon.MostlyCloudy)
            MetWeatherCode.OVERCAST -> WeatherCondition(text, WeatherIcon.Cloudy)
            MetWeatherCode.LIGHT_RAIN_SHOWER_NIGHT -> WeatherCondition(text, WeatherIcon.Showers, true)
            MetWeatherCode.LIGHT_RAIN_SHOWER_DAY -> WeatherCondition(text, WeatherIcon.Showers, false)
            MetWeatherCode.DRIZZLE -> WeatherCondition(text, WeatherIcon.Drizzle)
            MetWeatherCode.LIGHT_RAIN -> WeatherCondition(text, WeatherIcon.Showers)
            MetWeatherCode.HEAVY_RAIN_SHOWER_NIGHT -> WeatherCondition(text, WeatherIcon.Showers, true)
            MetWeatherCode.HEAVY_RAIN_SHOWER_DAY -> WeatherCondition(text, WeatherIcon.Showers, false)
            MetWeatherCode.HEAVY_RAIN -> WeatherCondition(text, WeatherIcon.Showers)
            MetWeatherCode.SLEET_SHOWER_NIGHT -> WeatherCondition(text, WeatherIcon.Sleet, true)
            MetWeatherCode.SLEET_SHOWER_DAY -> WeatherCondition(text, WeatherIcon.Sleet, false)
            MetWeatherCode.SLEET -> WeatherCondition(text, WeatherIcon.Sleet)
            MetWeatherCode.HAIL_SHOWER_NIGHT -> WeatherCondition(text, WeatherIcon.Hail, true)
            MetWeatherCode.HAIL_SHOWER_DAY -> WeatherCondition(text, WeatherIcon.Hail, false)
            MetWeatherCode.HAIL -> WeatherCondition(text, WeatherIcon.Hail)
            MetWeatherCode.LIGHT_SNOW_SHOWER_NIGHT -> WeatherCondition(text, WeatherIcon.Snow, true)
            MetWeatherCode.LIGHT_SNOW_SHOWER_DAY -> WeatherCondition(text, WeatherIcon.Snow, false)
            MetWeatherCode.LIGHT_SNOW -> WeatherCondition(text, WeatherIcon.Snow)
            MetWeatherCode.HEAVY_SNOW_SHOWER_NIGHT -> WeatherCondition(text, WeatherIcon.Snow, true)
            MetWeatherCode.HEAVY_SNOW_SHOWER_DAY -> WeatherCondition(text, WeatherIcon.Snow, false)
            MetWeatherCode.HEAVY_SNOW -> WeatherCondition(text, WeatherIcon.Snow)
            MetWeatherCode.THUNDER_SHOWER_NIGHT -> WeatherCondition(text, WeatherIcon.ThunderstormWithRain, true)
            MetWeatherCode.THUNDER_SHOWER_DAY -> WeatherCondition(text, WeatherIcon.ThunderstormWithRain, false)
            MetWeatherCode.THUNDER -> WeatherCondition(text, WeatherIcon.Thunderstorm)
        }
    }

    private fun metGeometryToMetUrl(geometry: MetForecastGeometry): String? {
        if (geometry.coordinates == null) return null
        if (geometry.coordinates.size < 2) return null
        val geoHash = GeoHash(geometry.coordinates[1], geometry.coordinates[0])
        return "https://www.metoffice.gov.uk/weather/forecast/${geoHash}"
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