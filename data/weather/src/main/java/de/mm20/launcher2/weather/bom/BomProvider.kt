package de.mm20.launcher2.weather.bom

import android.content.Context
import android.util.Log
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.preferences.weather.WeatherLocation
import de.mm20.launcher2.weather.Forecast
import de.mm20.launcher2.weather.GeocoderWeatherProvider
import de.mm20.launcher2.weather.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.shredzone.commons.suncalc.SunTimes
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date

internal class BomProvider(
    private val context: Context,
) : GeocoderWeatherProvider(context) {

    private val bomApi = BomApi()

    override suspend fun getWeatherData(location: WeatherLocation): List<Forecast>? {
        return when (location) {
            is WeatherLocation.LatLon -> withContext(Dispatchers.IO) {
                getWeatherData(location.lat, location.lon, location.name)
            }
            else -> {
                Log.e("BomProvider", "Unsupported location type: $location")
                null
            }
        }
    }

    override suspend fun getWeatherData(lat: Double, lon: Double): List<Forecast>? {
        val locationName = getLocationName(lat, lon)
        return withContext(Dispatchers.IO) {
            getWeatherData(lat, lon, locationName)
        }
    }

    private suspend fun getWeatherData(
        lat: Double,
        lon: Double,
        locationName: String
    ): List<Forecast>? {
        try {
            // Step 1: Get location info (nearest station ID and forecast grid)
            val locationResponse = try {
                bomApi.searchByCoordinate(lat, lon)
            } catch (e: Exception) {
                CrashReporter.logException(e)
                return null
            }

            // Check for API errors
            if (locationResponse.errors != null) {
                Log.e("BomProvider", "BOM API error: ${locationResponse.errors.firstOrNull()?.detail}")
                return null
            }

            val place = locationResponse.place
            if (place == null) {
                Log.e("BomProvider", "Location not found or outside Australia: $lat, $lon")
                return null
            }

            val nearestId = place.locationHierarchy?.nearest?.id
            val forecastGrid = place.gridcells?.forecast
            val timezone = place.timezone ?: ZoneId.systemDefault().id

            if (forecastGrid == null) {
                Log.e("BomProvider", "Could not get forecast grid for $lat, $lon")
                return null
            }

            // Step 2: Fetch 3-hourly forecast (has icon codes per 3-hour period)
            val threeHourlyResponse = try {
                bomApi.get3HourlyForecast(forecastGrid.x, forecastGrid.y, timezone)
            } catch (e: Exception) {
                CrashReporter.logException(e)
                return null
            }

            // Build a list of time ranges with icon codes for lookup
            val tz = ZoneId.of(timezone)
            data class IconPeriod(val start: Long, val end: Long, val iconCode: Int?, val precipProb: Int?)
            val iconPeriods = mutableListOf<IconPeriod>()
            threeHourlyResponse.fcst?.forEach { day ->
                day.threeHourly?.forEach { entry ->
                    val start = parseTimestamp(entry.startTimeUtc)
                    val end = parseTimestamp(entry.endTimeUtc)
                    if (start != null && end != null) {
                        iconPeriods.add(IconPeriod(
                            start = start,
                            end = end,
                            iconCode = entry.atm?.surfAir?.weather?.iconCode,
                            precipProb = entry.atm?.surfAir?.precip?.probabilityPercent?.toInt()
                        ))
                    }
                }
            }

            // Helper function to find icon for a timestamp
            fun findIconForTimestamp(timestamp: Long): Pair<Int?, Int?> {
                val period = iconPeriods.find { timestamp >= it.start && timestamp < it.end }
                return Pair(period?.iconCode, period?.precipProb)
            }

            // Step 3: Fetch hourly forecast
            val hourlyResponse = try {
                bomApi.getHourlyForecast(forecastGrid.x, forecastGrid.y, timezone)
            } catch (e: Exception) {
                CrashReporter.logException(e)
                return null
            }

            val forecasts = mutableListOf<Forecast>()
            val updateTime = System.currentTimeMillis()

            // Step 4: Try to get current observations (may fail for some locations)
            // Observations require a BOM station ID (numeric), not a place ID
            val currentObs = if (nearestId != null && nearestId.all { it.isDigit() }) {
                try {
                    bomApi.getCurrentObservations(nearestId).obs
                } catch (e: Exception) {
                    Log.w("BomProvider", "Could not get current observations: ${e.message}")
                    null
                }
            } else {
                Log.d("BomProvider", "No BOM station available for observations at $lat, $lon")
                null
            }

            // Get icon for current time
            val (currentIconCode, currentPrecipProb) = findIconForTimestamp(updateTime)

            // Add current weather from observations if available
            if (currentObs != null && currentObs.temp?.current != null) {
                forecasts.add(
                    Forecast(
                        timestamp = parseTimestamp(currentObs.datetimeUtc) ?: updateTime,
                        temperature = celsiusToKelvin(currentObs.temp.current),
                        humidity = currentObs.temp.humidity,
                        pressure = currentObs.pres?.mslHpa,
                        windSpeed = currentObs.wind?.speedMps,
                        windDirection = ordinalToDirection(currentObs.wind?.directionOrdinal),
                        precipitation = currentObs.precip?.lastHourMm,
                        clouds = currentObs.cloud?.lowLayerOkta?.let { oktasToPercent(it) },
                        precipProbability = currentPrecipProb,
                        icon = iconForCode(currentIconCode),
                        condition = conditionForCode(currentIconCode),
                        night = isNight(updateTime, lat, lon),
                        location = locationName,
                        provider = context.getString(R.string.provider_bom),
                        providerUrl = "http://www.bom.gov.au/",
                        updateTime = updateTime
                    )
                )
            }

            // Add hourly forecasts
            hourlyResponse.fcst?.forEach { day ->
                day.hourly?.forEach { hourly ->
                    val timestamp = parseTimestamp(hourly.timeUtc) ?: return@forEach
                    val surfAir = hourly.atm?.surfAir ?: return@forEach
                    val temp = surfAir.tempCel ?: return@forEach

                    // Find icon from 3-hourly forecast for this timestamp
                    val (iconCode, precipProb) = findIconForTimestamp(timestamp)

                    forecasts.add(
                        Forecast(
                            timestamp = timestamp,
                            temperature = celsiusToKelvin(temp),
                            humidity = surfAir.humidityPercent,
                            windSpeed = surfAir.wind?.speedMps,
                            windDirection = surfAir.wind?.directionDeg,
                            precipProbability = precipProb,
                            icon = iconForCode(iconCode),
                            condition = conditionForCode(iconCode),
                            night = isNight(timestamp, lat, lon),
                            location = locationName,
                            provider = context.getString(R.string.provider_bom),
                            providerUrl = "http://www.bom.gov.au/",
                            updateTime = updateTime
                        )
                    )
                }
            }

            return forecasts.ifEmpty { null }
        } catch (e: Exception) {
            CrashReporter.logException(e)
            return null
        }
    }

    private fun parseTimestamp(timeString: String?): Long? {
        if (timeString == null) return null
        return try {
            ZonedDateTime.parse(timeString, DateTimeFormatter.ISO_DATE_TIME)
                .toInstant()
                .toEpochMilli()
        } catch (e: Exception) {
            try {
                ZonedDateTime.parse(timeString)
                    .toInstant()
                    .toEpochMilli()
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun celsiusToKelvin(celsius: Double): Double = celsius + 273.15

    private fun oktasToPercent(oktas: Int): Int = (oktas * 100) / 8

    private fun ordinalToDirection(ordinal: String?): Double? {
        return when (ordinal?.uppercase()) {
            "N" -> 0.0
            "NNE" -> 22.5
            "NE" -> 45.0
            "ENE" -> 67.5
            "E" -> 90.0
            "ESE" -> 112.5
            "SE" -> 135.0
            "SSE" -> 157.5
            "S" -> 180.0
            "SSW" -> 202.5
            "SW" -> 225.0
            "WSW" -> 247.5
            "W" -> 270.0
            "WNW" -> 292.5
            "NW" -> 315.0
            "NNW" -> 337.5
            else -> null
        }
    }

    private fun isNight(timestamp: Long, lat: Double, lon: Double): Boolean {
        val sunTimes = SunTimes.compute().on(Date(timestamp)).at(lat, lon).execute()
        if (sunTimes.isAlwaysDown) return true
        if (sunTimes.isAlwaysUp) return false

        val set = sunTimes.set
        val rise = sunTimes.rise

        if (set == null && rise != null) {
            return timestamp < rise.toEpochSecond() * 1000
        }

        if (set != null && rise == null) {
            return set.toEpochSecond() * 1000 < timestamp
        }

        if (set == null || rise == null) return false

        if (set.toEpochSecond() < rise.toEpochSecond()) {
            return (set.toEpochSecond() * 1000 < timestamp && timestamp < rise.toEpochSecond() * 1000)
        }

        return !(rise.toEpochSecond() * 1000 < timestamp && timestamp < set.toEpochSecond() * 1000)
    }

    // BOM icon codes mapping
    // See: https://reg.bom.gov.au/info/forecast_icons.shtml
    private fun iconForCode(code: Int?): Int {
        return when (code) {
            1 -> Forecast.CLEAR          // Sunny
            2 -> Forecast.CLEAR          // Clear (night)
            3 -> Forecast.PARTLY_CLOUDY  // Partly cloudy
            4 -> Forecast.CLOUDY         // Cloudy
            6 -> Forecast.HAZE           // Hazy
            8 -> Forecast.SHOWERS        // Light rain
            9 -> Forecast.WIND           // Windy
            10 -> Forecast.FOG           // Fog
            11 -> Forecast.SHOWERS       // Shower
            12 -> Forecast.SHOWERS       // Rain
            13 -> Forecast.HAZE          // Dust
            14 -> Forecast.COLD          // Frost
            15 -> Forecast.SNOW          // Snow
            16 -> Forecast.STORM         // Storm
            17 -> Forecast.SHOWERS       // Light shower
            18 -> Forecast.SHOWERS       // Heavy shower
            else -> Forecast.NONE
        }
    }

    private fun conditionForCode(code: Int?): String {
        return when (code) {
            1 -> context.getString(R.string.weather_condition_clearsky)
            2 -> context.getString(R.string.weather_condition_clearsky)
            3 -> context.getString(R.string.weather_condition_partlycloudy)
            4 -> context.getString(R.string.weather_condition_cloudy)
            6 -> context.getString(R.string.weather_condition_haze)
            8 -> context.getString(R.string.weather_condition_lightrain)
            9 -> context.getString(R.string.weather_condition_wind)
            10 -> context.getString(R.string.weather_condition_fog)
            11 -> context.getString(R.string.weather_condition_rainshowers)
            12 -> context.getString(R.string.weather_condition_rain)
            13 -> context.getString(R.string.weather_condition_haze) // Dust -> Haze
            14 -> context.getString(R.string.weather_condition_snow) // Frost -> Snow (closest)
            15 -> context.getString(R.string.weather_condition_snow)
            16 -> context.getString(R.string.weather_condition_thunderstorm)
            17 -> context.getString(R.string.weather_condition_lightrainshowers)
            18 -> context.getString(R.string.weather_condition_heavyrainshowers)
            else -> context.getString(R.string.weather_condition_unknown)
        }
    }

    companion object {
        internal const val Id = "bom"

        fun isAvailable(context: Context): Boolean {
            // BOM API is publicly available, no API key required
            return true
        }
    }
}
