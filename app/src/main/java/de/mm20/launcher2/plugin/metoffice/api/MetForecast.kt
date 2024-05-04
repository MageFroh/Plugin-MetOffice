package de.mm20.launcher2.plugin.metoffice.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

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
    val significantWeatherCode: MetWeatherCode?,
    val precipitationRate: Double?, // not used in kvaesitso
    val totalPrecipAmount: Double?,
    val totalSnowAmount: Double?, // not used in kvaesitso
    val probOfPrecipitation: Int?,
)

// https://www.metoffice.gov.uk/services/data/datapoint/code-definitions
@Serializable(with = MetWeatherCodeSerializer::class)
enum class MetWeatherCode(val code: Int) {
    TRACE_RAIN(-1),
    CLEAR_NIGHT(0),
    SUNNY_DAY(1),
    PARTLY_CLOUDY_NIGHT(2),
    PARTLY_CLOUDY_DAY(3),
    // 4: Not Used
    MIST(5),
    FOG(6),
    CLOUDY(7),
    OVERCAST(8),
    LIGHT_RAIN_SHOWER_NIGHT(9),
    LIGHT_RAIN_SHOWER_DAY(10),
    DRIZZLE(11),
    LIGHT_RAIN(12),
    HEAVY_RAIN_SHOWER_NIGHT(13),
    HEAVY_RAIN_SHOWER_DAY(14),
    HEAVY_RAIN(15),
    SLEET_SHOWER_NIGHT(16),
    SLEET_SHOWER_DAY(17),
    SLEET(18),
    HAIL_SHOWER_NIGHT(19),
    HAIL_SHOWER_DAY(20),
    HAIL(21),
    LIGHT_SNOW_SHOWER_NIGHT(22),
    LIGHT_SNOW_SHOWER_DAY(23),
    LIGHT_SNOW(24),
    HEAVY_SNOW_SHOWER_NIGHT(25),
    HEAVY_SNOW_SHOWER_DAY(26),
    HEAVY_SNOW(27),
    THUNDER_SHOWER_NIGHT(28),
    THUNDER_SHOWER_DAY(29),
    THUNDER(30);

    companion object {
        fun fromCode(code: Int): MetWeatherCode? = entries.find { it.code == code }
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = MetWeatherCode::class)
object MetWeatherCodeSerializer : KSerializer<MetWeatherCode?> {
    override fun serialize(encoder: Encoder, value: MetWeatherCode?) {
        value?.let { encoder.encodeInt(it.code) }
    }

    override fun deserialize(decoder: Decoder): MetWeatherCode? {
        val code = decoder.decodeInt()
        return MetWeatherCode.fromCode(code)
    }
}
