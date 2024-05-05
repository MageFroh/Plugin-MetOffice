package de.mm20.launcher2.plugin.metoffice

import android.content.Context
import android.util.Log
import de.mm20.launcher2.plugin.metoffice.api.MetForecast
import de.mm20.launcher2.plugin.metoffice.api.MetGeo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.path
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.io.IOException

class MetOfficeApiClient(
    private val context: Context
) {

    @OptIn(ExperimentalSerializationApi::class)
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            })
        }
    }

    enum class MetForecastType {
        HOURLY,
        THREE_HOURLY,
        DAILY
    }

    suspend fun forecast(
        lat: Double,
        lon: Double,
        forecastType: MetForecastType = MetForecastType.HOURLY,
        appid: String? = null
    ): MetForecast {
        val apiKey = appid ?: apiKey.first() ?: throw IllegalArgumentException("No API key provided")
        val endpoint = when (forecastType) {
            MetForecastType.HOURLY -> "hourly"
            MetForecastType.THREE_HOURLY -> "three-hourly"
            MetForecastType.DAILY -> "daily"
        }
        val response = client.get {
            url {
                protocol = URLProtocol.HTTPS
                host = "data.hub.api.metoffice.gov.uk"
                path("sitespecific", "v0", "point", endpoint)
                parameters["dataSource"] = "BD1"
                parameters["excludeParameterMetadata"] = "true"
                parameters["includeLocationName"] = "true"
                parameters["latitude"] = lat.toString()
                parameters["longitude"] = lon.toString()
            }
            headers {
                append("apikey", apiKey)
            }
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            throw IllegalArgumentException("Unauthorized. Invalid API key?; body ${response.bodyAsText()}")
        } else if (response.status != HttpStatusCode.OK) {
            throw IOException("API error: status ${response.status.value}; body ${response.bodyAsText()}")
        }
        return response.body()
    }

    suspend fun geo(
        q: String,
        limit: Int? = null,
    ): Array<MetGeo> {
        val response = client.get {
            url {
                protocol = URLProtocol.HTTPS
                host = "www.metoffice.gov.uk"
                path("plain-rest-services", "location-search")
                parameters["searchTerm"] = q
                parameters["filter"] = "exclude-marine-offshore"
                if (limit != null) parameters["max"] = limit.toString()
            }
        }
        if (response.status != HttpStatusCode.OK) {
            throw IOException("API error: status ${response.status.value}; body ${response.bodyAsText()}")
        }
        return response.body()
    }

    suspend fun setApiKey(apiKey: String) {
        context.dataStore.updateData {
            it.copy(apiKey = apiKey)
        }
    }

    suspend fun testApiKey(apiKey: String): Boolean {
        try {
            forecast(
                lat = 51.5,
                lon = 0.0,
                appid = apiKey
            )
            return true
        } catch (e: IllegalArgumentException) {
            Log.e("MetOfficeApiClient", "Invalid API key", e)
            return false
        }
    }

    val apiKey: Flow<String?> = context.dataStore.data.map { it.apiKey }

}