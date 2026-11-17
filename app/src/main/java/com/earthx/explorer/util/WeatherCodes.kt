package com.earthx.explorer.util

/** WMO weather interpretation codes as documented by Open-Meteo. */
object WeatherCodes {
    fun describe(code: Int?): String = when (code) {
        null -> "Unknown"
        0 -> "Clear sky"
        1 -> "Mainly clear"; 2 -> "Partly cloudy"; 3 -> "Overcast"
        45 -> "Fog"; 48 -> "Depositing rime fog"
        51 -> "Light drizzle"; 53 -> "Moderate drizzle"; 55 -> "Dense drizzle"
        56 -> "Light freezing drizzle"; 57 -> "Dense freezing drizzle"
        61 -> "Slight rain"; 63 -> "Moderate rain"; 65 -> "Heavy rain"
        66 -> "Light freezing rain"; 67 -> "Heavy freezing rain"
        71 -> "Slight snowfall"; 73 -> "Moderate snowfall"; 75 -> "Heavy snowfall"; 77 -> "Snow grains"
        80 -> "Slight rain showers"; 81 -> "Moderate rain showers"; 82 -> "Violent rain showers"
        85 -> "Slight snow showers"; 86 -> "Heavy snow showers"
        95 -> "Thunderstorm"; 96 -> "Thunderstorm, slight hail"; 99 -> "Thunderstorm, heavy hail"
        else -> "Code $code"
    }

    fun icon(code: Int?, isDay: Boolean? = true): String = when (code) {
        0 -> if (isDay == false) "🌙" else "☀️"
        1 -> if (isDay == false) "🌙" else "🌤️"
        2 -> "⛅"; 3 -> "☁️"; 45, 48 -> "🌫️"
        51, 53, 80 -> "🌦️"; 55, 56, 57, 61, 63, 65, 66, 67, 81 -> "🌧️"
        71, 73, 85 -> "🌨️"; 75, 77, 86 -> "❄️"
        82, 95, 96, 99 -> "⛈️"
        else -> "•"
    }

    fun compass(deg: Double?): String {
        if (deg == null) return "—"
        val dirs = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        return dirs[(((deg % 360) + 360) % 360 / 22.5 + 0.5).toInt() % 16]
    }
}
