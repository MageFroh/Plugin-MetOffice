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
    val maxScreenAirTemp: Double?,
    val minScreenAirTemp: Double?,
    val screenDewPointTemperature: Double?, // not used in kvaesitso
    val feelsLikeTemperature: Double?, // not used in kvaesitso
    val windSpeed10m: Double?,
    val windDirectionFrom10m: Double?,
    val windGustSpeed10m: Double?, // not used in kvaesitso
    val max10mWindGust: Double?, // not used in kvaesitso
    val visibility: Long?, // not used in kvaesitso
    val screenRelativeHumidity: Double?,
    val mslp: Double?,
    val uvIndex: Int?, // not used in kvaesitso
    val significantWeatherCode: Int?,
    val precipitationRate: Double?, // not used in kvaesitso
    val totalPrecipAmount: Double?,
    val totalSnowAmount: Double?, // not used in kvaesitso
    val probOfPrecipitation: Int?,
)
