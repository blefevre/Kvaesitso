package de.mm20.launcher2.weather.bom

import de.mm20.launcher2.serialization.Json
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.path
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

// Error response
@Serializable
internal data class BomErrorResponse(
    val errors: List<BomError>? = null,
) {
    @Serializable
    internal data class BomError(
        val code: String? = null,
        val detail: String? = null,
    )
}

// Location search response
@Serializable
internal data class BomLocationResponse(
    val place: Place? = null,
    val errors: List<BomErrorResponse.BomError>? = null,
) {
    @Serializable
    internal data class Place(
        val name: String? = null,
        val timezone: String? = null,
        @SerialName("location_hierarchy") val locationHierarchy: LocationHierarchy? = null,
        val gridcells: Gridcells? = null,
    )

    @Serializable
    internal data class LocationHierarchy(
        val nearest: Nearest? = null,
    )

    @Serializable
    internal data class Nearest(
        val id: String,
        val name: String? = null,
    )

    @Serializable
    internal data class Gridcells(
        val forecast: ForecastGrid? = null,
    )

    @Serializable
    internal data class ForecastGrid(
        val x: Int,
        val y: Int,
    )
}

// Observations response
@Serializable
internal data class BomObservationsResponse(
    val stn: Station? = null,
    val obs: Observations? = null,
) {
    @Serializable
    internal data class Station(
        val identity: StationIdentity? = null,
        val location: StationLocation? = null,
    )

    @Serializable
    internal data class StationIdentity(
        @SerialName("bom_stn_name") val name: String? = null,
    )

    @Serializable
    internal data class StationLocation(
        @SerialName("lat_dec_deg") val lat: Double? = null,
        @SerialName("long_dec_deg") val lon: Double? = null,
        val timezone: String? = null,
    )

    @Serializable
    internal data class Observations(
        @SerialName("datetime_utc") val datetimeUtc: String? = null,
        val temp: Temperature? = null,
        val pres: Pressure? = null,
        val wind: Wind? = null,
        val precip: Precipitation? = null,
        val cloud: Cloud? = null,
    )

    @Serializable
    internal data class Temperature(
        @SerialName("dry_bulb_1min_cel") val current: Double? = null,
        @SerialName("apparent_1min_cel") val apparent: Double? = null,
        @SerialName("dew_pnt_1min_cel") val dewPoint: Double? = null,
        @SerialName("rel_hum_percent") val humidity: Double? = null,
    )

    @Serializable
    internal data class Pressure(
        @SerialName("msl_hpa") val mslHpa: Double? = null,
    )

    @Serializable
    internal data class Wind(
        @SerialName("speed_10m_mps") val speedMps: Double? = null,
        @SerialName("dirn_10m_ord") val directionOrdinal: String? = null,
        @SerialName("gust_dirn_10m_deg_t") val gustDirectionDeg: Double? = null,
    )

    @Serializable
    internal data class Precipitation(
        @SerialName("1h_total_mm") val lastHourMm: Double? = null,
        @SerialName("since_0900lct_total_mm") val since0900Mm: Double? = null,
    )

    @Serializable
    internal data class Cloud(
        @SerialName("low_layer_cover_amt_okta") val lowLayerOkta: Int? = null,
        @SerialName("total_cover_amt_text") val totalCoverText: String? = null,
    )
}

// Hourly forecast response
@Serializable
internal data class BomHourlyForecastResponse(
    val meta: ForecastMeta? = null,
    val fcst: List<HourlyForecastDay>? = null,
) {
    @Serializable
    internal data class ForecastMeta(
        @SerialName("issue_time_utc") val issueTimeUtc: String? = null,
        @SerialName("local_timezone") val localTimezone: String? = null,
    )

    @Serializable
    internal data class HourlyForecastDay(
        @SerialName("date_utc") val dateUtc: String? = null,
        @SerialName("1hourly") val hourly: List<HourlyEntry>? = null,
    )

    @Serializable
    internal data class HourlyEntry(
        @SerialName("time_utc") val timeUtc: String? = null,
        val atm: Atmosphere? = null,
    )

    @Serializable
    internal data class Atmosphere(
        @SerialName("surf_air") val surfAir: SurfaceAir? = null,
    )

    @Serializable
    internal data class SurfaceAir(
        @SerialName("temp_cel") val tempCel: Double? = null,
        @SerialName("temp_apparent_cel") val tempApparentCel: Double? = null,
        @SerialName("temp_dew_pt_cel") val dewPointCel: Double? = null,
        @SerialName("hum_relative_percent") val humidityPercent: Double? = null,
        val wind: HourlyWind? = null,
    )

    @Serializable
    internal data class HourlyWind(
        @SerialName("speed_10m_avg_mps") val speedMps: Double? = null,
        @SerialName("dirn_10m_deg_t") val directionDeg: Double? = null,
        @SerialName("gust_speed_10m_max_mps") val gustSpeedMps: Double? = null,
    )
}

// 3-hourly forecast response (has icon codes)
@Serializable
internal data class Bom3HourlyForecastResponse(
    val meta: ThreeHourlyMeta? = null,
    val fcst: List<ThreeHourlyForecastDay>? = null,
) {
    @Serializable
    internal data class ThreeHourlyMeta(
        @SerialName("issue_time_utc") val issueTimeUtc: String? = null,
        @SerialName("local_timezone") val localTimezone: String? = null,
    )

    @Serializable
    internal data class ThreeHourlyForecastDay(
        @SerialName("date_utc") val dateUtc: String? = null,
        @SerialName("3hourly") val threeHourly: List<ThreeHourlyEntry>? = null,
    )

    @Serializable
    internal data class ThreeHourlyEntry(
        @SerialName("start_time_utc") val startTimeUtc: String? = null,
        @SerialName("end_time_utc") val endTimeUtc: String? = null,
        val atm: ThreeHourlyAtmosphere? = null,
    )

    @Serializable
    internal data class ThreeHourlyAtmosphere(
        @SerialName("surf_air") val surfAir: ThreeHourlySurfaceAir? = null,
    )

    @Serializable
    internal data class ThreeHourlySurfaceAir(
        val weather: ThreeHourlyWeather? = null,
        val precip: ThreeHourlyPrecip? = null,
    )

    @Serializable
    internal data class ThreeHourlyWeather(
        @SerialName("icon_code") val iconCode: Int? = null,
    )

    @Serializable
    internal data class ThreeHourlyPrecip(
        @SerialName("precip_any_probability_percent") val probabilityPercent: Double? = null,
    )
}

// Daily forecast response
@Serializable
internal data class BomDailyForecastResponse(
    val meta: DailyMeta? = null,
    val fcst: DailyForecastContainer? = null,
) {
    @Serializable
    internal data class DailyMeta(
        @SerialName("issue_time_utc") val issueTimeUtc: String? = null,
        @SerialName("local_timezone") val localTimezone: String? = null,
    )

    @Serializable
    internal data class DailyForecastContainer(
        val daily: List<DailyEntry>? = null,
    )

    @Serializable
    internal data class DailyEntry(
        @SerialName("date_utc") val dateUtc: String? = null,
        val atm: DailyAtmosphere? = null,
    )

    @Serializable
    internal data class DailyAtmosphere(
        @SerialName("surf_air") val surfAir: DailySurfaceAir? = null,
    )

    @Serializable
    internal data class DailySurfaceAir(
        @SerialName("temp_max_cel") val tempMaxCel: Double? = null,
        @SerialName("temp_min_cel") val tempMinCel: Double? = null,
        val precip: DailyPrecip? = null,
        val weather: DailyWeather? = null,
    )

    @Serializable
    internal data class DailyPrecip(
        @SerialName("any_probability_percent") val probabilityPercent: Double? = null,
        @SerialName("exceeding_50percentchance_total_mm") val likelyAmountMm: Double? = null,
    )

    @Serializable
    internal data class DailyWeather(
        @SerialName("icon_code") val iconCode: Int? = null,
    )
}

internal class BomApi {

    private val httpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(Json.Lenient)
            }
            defaultRequest {
                url("https://api.bom.gov.au/apikey/v1/")
                header(HttpHeaders.UserAgent, USER_AGENT)
            }
        }
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    suspend fun searchByCoordinate(lat: Double, lon: Double): BomLocationResponse {
        return httpClient.get {
            url {
                path("locations", "places", "search")
                parameter("coordinate", String.format(Locale.ROOT, "\"%.3f,%.3f\"", lon, lat))
            }
        }.body()
    }

    suspend fun getCurrentObservations(nearestId: String): BomObservationsResponse {
        return httpClient.get {
            url {
                path("observations", "latest", nearestId, "atm", "surf_air")
                parameter("include_qc_results", "false")
            }
        }.body()
    }

    suspend fun getHourlyForecast(x: Int, y: Int, timezone: String): BomHourlyForecastResponse {
        return httpClient.get {
            url {
                path("forecasts", "1hourly", x.toString(), y.toString())
                parameter("timezone", timezone)
            }
        }.body()
    }

    suspend fun get3HourlyForecast(x: Int, y: Int, timezone: String): Bom3HourlyForecastResponse {
        return httpClient.get {
            url {
                path("forecasts", "3hourly", x.toString(), y.toString())
                parameter("timezone", timezone)
            }
        }.body()
    }

    suspend fun getDailyForecast(x: Int, y: Int, timezone: String): BomDailyForecastResponse {
        return httpClient.get {
            url {
                path("forecasts", "daily", x.toString(), y.toString())
                parameter("timezone", timezone)
            }
        }.body()
    }
}
