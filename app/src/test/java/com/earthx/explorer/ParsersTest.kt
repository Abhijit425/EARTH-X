package com.earthx.explorer

import com.earthx.explorer.data.parse.Parsers
import com.earthx.explorer.data.repositories.GeoRepository
import com.earthx.explorer.data.repositories.QuakeRepository
import com.earthx.explorer.data.repositories.WeatherRepository
import com.earthx.explorer.data.model.QuakeRange
import com.earthx.explorer.data.model.Place
import com.earthx.explorer.data.model.PlaceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {
    private val usgs = """
    {"type":"FeatureCollection","metadata":{"generated":1700000000000,"count":3},
     "features":[
      {"type":"Feature","id":"us7000abcd","properties":{"mag":5.6,"place":"10 km S of Somewhere","time":1699990000000,"updated":1699995000000,
        "tsunami":1,"sig":500,"magType":"mww","status":"reviewed","net":"us","url":"https://earthquake.usgs.gov/x","felt":12,"alert":"green"},
        "geometry":{"type":"Point","coordinates":[142.5,38.1,35.2]}},
      {"type":"Feature","id":"ak123","properties":{"mag":null,"place":null,"title":"Unknown","time":1699980000000},
        "geometry":{"type":"Point","coordinates":[-150.1,61.2,null]}},
      {"type":"Feature","id":"bad","properties":{"mag":3.0},"geometry":{"type":"Point","coordinates":[200.0,95.0,10]}}
     ]}""".trimIndent()

    @Test fun usgs_parsesValidAndSkipsInvalid() {
        val (gen, list) = Parsers.usgs(usgs)
        assertEquals(1700000000000L, gen)
        assertEquals(2, list.size)
        val q = list[0]
        assertEquals("us7000abcd", q.id)
        assertEquals(5.6, q.magnitude!!, 1e-9)
        assertEquals(35.2, q.depthKm!!, 1e-9)
        assertEquals(38.1, q.lat, 1e-9)
        assertEquals(142.5, q.lon, 1e-9)
        assertTrue(q.tsunami)
        assertEquals("mww", q.magType)
        assertNull(list[1].magnitude)
        assertNull(list[1].depthKm)
        assertEquals("Unknown", list[1].place)
    }

    @Test fun usgs_feedUrls() {
        assertEquals("https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_week.geojson", QuakeRepository.url(QuakeRange.WEEK))
    }

    private val meteo = """
    {"latitude":22.5,"longitude":88.375,"timezone":"Asia/Kolkata","utc_offset_seconds":19800,
     "current":{"time":"2024-05-01T14:30","temperature_2m":33.2,"relative_humidity_2m":61,"apparent_temperature":39.0,"is_day":1,
       "precipitation":0.0,"weather_code":2,"cloud_cover":45,"pressure_msl":1004.1,"wind_speed_10m":12.3,"wind_direction_10m":200},
     "hourly":{"time":["2024-05-01T14:00","2024-05-01T15:00"],"temperature_2m":[33.0,32.5],"precipitation_probability":[10,null],"weather_code":[2,3]},
     "daily":{"time":["2024-05-01"],"weather_code":[95],"temperature_2m_max":[35.1],"temperature_2m_min":[27.0],"precipitation_sum":[4.2],
       "sunrise":["2024-05-01T05:08"],"sunset":["2024-05-01T18:05"]}}""".trimIndent()

    @Test fun openMeteo_local() {
        val w = Parsers.localWeather(meteo, 123L, fromCache = false)
        assertEquals("Asia/Kolkata", w.timeZone)
        assertEquals(19800, w.utcOffsetSeconds)
        assertEquals(33.2, w.current!!.temperatureC!!, 1e-9)
        assertEquals(2, w.current!!.weatherCode)
        assertEquals(true, w.current!!.isDay)
        assertEquals(2, w.hourly.size)
        assertNull(w.hourly[1].precipProb)
        assertEquals(95, w.daily[0].weatherCode)
        assertFalse(w.fromCache)
    }

    @Test fun openMeteo_gridMerge() {
        val lats = listOf(0.0); val lons = listOf(0.0, 15.0, 30.0)
        val chunk1 = """[{"current":{"time":"2024-05-01T12:00","temperature_2m":25.0,"cloud_cover":10,"precipitation":0,"wind_speed_10m":5,"wind_direction_10m":90,"weather_code":1}},
                         {"current":{"temperature_2m":26.0,"cloud_cover":20,"precipitation":0.2,"wind_speed_10m":6,"wind_direction_10m":180,"weather_code":61}}]"""
        val chunk2 = """{"current":{"temperature_2m":null,"cloud_cover":30,"precipitation":0,"wind_speed_10m":7,"wind_direction_10m":270,"weather_code":3}}"""
        val g = Parsers.weatherGrid(listOf(chunk1, chunk2), lats, lons, 1L, false)
        assertEquals(3, g.temp.size)
        assertEquals(26.0, g.temp[1]!!, 1e-9)
        assertNull(g.temp[2])
        assertEquals(61, g.code[1])
        assertEquals("2024-05-01T12:00", g.validTime)
    }

    @Test fun openMeteo_gridUrlsCoverAllPoints() {
        val urls = WeatherRepository.gridUrls()
        val total = urls.sumOf { u -> u.substringAfter("latitude=").substringBefore("&").split(",").size }
        assertEquals(WeatherRepository.GRID_LATS.size * WeatherRepository.GRID_LONS.size, total)
        assertTrue(urls.all { it.startsWith("https://api.open-meteo.com/v1/forecast?") && it.length < 4000 })
    }

    @Test fun search_ranksExactAndAccents() {
        val places = listOf(
            Place("São Paulo", PlaceKind.CITY, -23.5, -46.6, rank = 50.0),
            Place("Paris", PlaceKind.CAPITAL, 48.8, 2.3, rank = 70.0),
            Place("Paraguay", PlaceKind.COUNTRY, -23.0, -58.0, rank = 110.0),
        )
        val idx = places.map { GeoRepository.normalize(it.name) to it }
        assertEquals("Paris", GeoRepository.search(idx, "paris").first().name)
        assertEquals("São Paulo", GeoRepository.search(idx, "sao pau").first().name)
        assertEquals(2, GeoRepository.search(idx, "par").size)
        assertTrue(GeoRepository.search(idx, "  ").isEmpty())
    }
}
