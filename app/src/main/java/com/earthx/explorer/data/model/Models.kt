package com.earthx.explorer.data.model

/** Country metadata bundled from Natural Earth (public domain) + world-countries (ODbL). */
data class Country(
    val id: String,
    val name: String,
    val shortName: String,
    val official: String?,
    val iso2: String?,
    val flag: String,
    val capital: String,
    val capitalLat: Double?,
    val capitalLon: Double?,
    val population: Long?,
    val populationYear: Int?,
    val areaKm2: Double?,
    val areaComputed: Boolean,
    val continent: String,
    val subregion: String,
    val lat: Double,
    val lon: Double,
    val currencies: List<String>,
    val languages: List<String>,
    val landlocked: Boolean?,
    val borders: List<String>,
    val tld: String?,
    val callingCode: String?,
    val gdpMillionUsd: Long?,
    val type: String?,
    val sovereign: String?,
    val bbox: List<Double>,
    val timeZone: String,
)

data class City(
    val index: Int,
    val name: String,
    val countryId: String,
    val countryName: String,
    val admin1: String,
    val lat: Double,
    val lon: Double,
    val population: Long,
    val isCapital: Boolean,
    val timeZone: String,
)

enum class PlaceKind(val label: String) {
    COUNTRY("Country"), CAPITAL("Capital city"), CITY("City"), CONTINENT("Continent"), MOUNTAIN("Mountain range"),
    PLATEAU("Plateau"), DESERT("Desert"), OCEAN("Ocean"), SEA("Sea / gulf"), PEAK("Summit"), TRENCH("Ocean trench"),
    LANDMARK("Landmark"), REGION("Region"), STATE("State / province"), LOCATION("Location");

    companion object {
        fun fromGeo(k: String) = when (k) {
            "mountain" -> MOUNTAIN; "plateau" -> PLATEAU; "desert" -> DESERT; "ocean" -> OCEAN; "sea" -> SEA
            "continent" -> CONTINENT; "landmark" -> LANDMARK; else -> REGION
        }
    }
}

/** A searchable thing with a location. `ref` is a country id or city index where applicable. */
data class Place(
    val name: String,
    val kind: PlaceKind,
    val lat: Double,
    val lon: Double,
    val subtitle: String = "",
    val ref: String? = null,
    val rank: Double = 0.0,
    val altKm: Double? = null,
)

data class Quake(
    val id: String,
    val magnitude: Double?,
    val magType: String?,
    val depthKm: Double?,
    val lat: Double,
    val lon: Double,
    val timeMs: Long,
    val updatedMs: Long?,
    val place: String,
    val url: String?,
    val tsunami: Boolean,
    val alert: String?,
    val felt: Int?,
    val significance: Int?,
    val status: String?,
    val network: String?,
)

enum class QuakeRange(val label: String, val feed: String) {
    HOUR("Last hour", "all_hour"), DAY("Last 24 hours", "all_day"), WEEK("Last 7 days", "all_week"), MONTH("Last 30 days", "all_month")
}

data class QuakeFeed(val range: QuakeRange, val events: List<Quake>, val generatedMs: Long, val retrievedMs: Long, val fromCache: Boolean)

/** Open-Meteo current conditions (model analysis) + forecast for one location. */
data class LocalWeather(
    val lat: Double,
    val lon: Double,
    val timeZone: String?,
    val utcOffsetSeconds: Int?,
    val current: CurrentWeather?,
    val hourly: List<HourlyPoint>,
    val daily: List<DailyPoint>,
    val retrievedMs: Long,
    val fromCache: Boolean,
)

data class CurrentWeather(
    val time: String,
    val temperatureC: Double?,
    val apparentC: Double?,
    val humidity: Double?,
    val precipitationMm: Double?,
    val cloudCover: Double?,
    val windSpeedKmh: Double?,
    val windDirDeg: Double?,
    val weatherCode: Int?,
    val isDay: Boolean?,
    val pressureHpa: Double?,
)

data class HourlyPoint(val time: String, val temperatureC: Double?, val precipProb: Double?, val weatherCode: Int?)
data class DailyPoint(val date: String, val maxC: Double?, val minC: Double?, val precipMm: Double?, val weatherCode: Int?, val sunrise: String?, val sunset: String?)

/** Regular global grid of current values for globe visualisation layers. Row-major (lat, then lon). */
data class WeatherGrid(
    val lats: List<Double>,
    val lons: List<Double>,
    val temp: List<Double?>,
    val cloud: List<Double?>,
    val precip: List<Double?>,
    val windSpeed: List<Double?>,
    val windDir: List<Double?>,
    val code: List<Int?>,
    val validTime: String?,
    val retrievedMs: Long,
    val fromCache: Boolean,
)

enum class WeatherLayer(val key: String, val label: String) {
    NONE("none", "Off"), TEMPERATURE("temperature", "Temperature"), CLOUDS("clouds", "Cloud cover"),
    PRECIPITATION("precipitation", "Rainfall"), WIND("wind", "Wind"), MARKERS("markers", "Conditions")
}

data class GibsLayer(val id: String, val label: String, val daily: Boolean)

val GIBS_LAYERS = listOf(
    GibsLayer("VIIRS_SNPP_CorrectedReflectance_TrueColor", "VIIRS SNPP true colour · daily · 250 m", true),
    GibsLayer("VIIRS_NOAA20_CorrectedReflectance_TrueColor", "VIIRS NOAA-20 true colour · daily · 250 m", true),
    GibsLayer("MODIS_Terra_CorrectedReflectance_TrueColor", "MODIS Terra true colour · daily · 250 m", true),
    GibsLayer("MODIS_Aqua_CorrectedReflectance_TrueColor", "MODIS Aqua true colour · daily · 250 m", true),
    GibsLayer("BlueMarble_NextGeneration", "Blue Marble NG · cloud-free composite · 500 m", false),
    GibsLayer("BlueMarble_ShadedRelief_Bathymetry", "Shaded relief + ocean bathymetry · 500 m", false),
    GibsLayer("VIIRS_Black_Marble", "Black Marble night lights · 2016", false),
)

data class CuratedFact(val key: String, val value: String)
data class Peak(val name: String, val lat: Double, val lon: Double, val elevationM: Int, val range: String)
data class Trench(val name: String, val lat: Double, val lon: Double, val depthM: Int, val ocean: String)
data class Continent(val name: String, val lat: Double, val lon: Double, val area: String, val fact: String)
data class Topic(val id: String, val title: String, val body: String)

data class Curated(
    val earthFacts: List<CuratedFact>,
    val peaks: List<Peak>,
    val trenches: List<Trench>,
    val continents: List<Continent>,
    val topics: List<Topic>,
    val sources: Map<String, String>,
)
