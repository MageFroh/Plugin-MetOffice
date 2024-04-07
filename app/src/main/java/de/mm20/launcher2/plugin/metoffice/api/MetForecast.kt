package de.mm20.launcher2.plugin.metoffice.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.Date

@Serializable
data class MetForecast(
    val features: List<MetForecastFeatures>?,
)

@Serializable
data class MetForecastFeatures(
    val geometry: MetForecastGeometry?,
    val properties: MetForecastProperties?,
)

@Serializable
data class MetForecastGeometry(
    val coordinates: List<Double>?,
)

@Serializable
data class MetForecastProperties(
    val location: MetForecastLocation?,
    val modelRunDate: String?,
    val timeSeries: List<MetForecastTimeSeries>?,
)

@Serializable
data class MetForecastLocation(
    val name: String?,
)

@Serializable
data class MetForecastTimeSeries(
    val time: String?,
    val screenTemperature: Double?,
    val feelsLikeTemperature: Double?,
    val windSpeed10m: Double?,
    val windDirectionFrom10m: Double?,
    val screenRelativeHumidity: Double?,
    val mslp: Int?,
    val significantWeatherCode: Int?,
    val precipitationRate: Double?,
    val probOfPrecipitation: Int?,
)
