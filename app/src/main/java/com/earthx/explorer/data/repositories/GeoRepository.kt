package com.earthx.explorer.data.repositories

import android.content.Context
import com.earthx.explorer.data.model.City
import com.earthx.explorer.data.model.Country
import com.earthx.explorer.data.model.Curated
import com.earthx.explorer.data.model.Place
import com.earthx.explorer.data.model.PlaceKind
import com.earthx.explorer.data.parse.Parsers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.Normalizer
import kotlin.math.ln

/** Offline geography bundled in the APK (assets/web/data). */
class GeoRepository(private val context: Context) {

    data class Bundle(
        val countries: List<Country>,
        val countryById: Map<String, Country>,
        val cities: List<City>,
        val geography: List<Place>,
        val curated: Curated,
        val index: List<Pair<String, Place>>, // normalised name -> place
    )

    private val mutex = Mutex()
    @Volatile private var bundle: Bundle? = null

    private fun asset(name: String) = context.assets.open("web/data/$name").bufferedReader().use { it.readText() }

    suspend fun load(): Bundle = bundle ?: mutex.withLock {
        bundle ?: withContext(Dispatchers.IO) {
            val countries = Parsers.countries(asset("countries.json"))
            val cities = Parsers.cities(asset("cities.json"))
            val geo = Parsers.geography(asset("geography.json"))
            val curated = Parsers.curated(asset("curated.json"))
            val places = buildPlaces(countries, cities, geo, curated)
            Bundle(countries, countries.associateBy { it.id }, cities, geo, curated, places.map { normalize(it.name) to it })
                .also { bundle = it }
        }
    }

    companion object {
        fun normalize(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

        fun buildPlaces(countries: List<Country>, cities: List<City>, geo: List<Place>, curated: Curated): List<Place> {
            val out = ArrayList<Place>()
            countries.forEach {
                out += Place(it.name, PlaceKind.COUNTRY, it.lat, it.lon, "${it.flag} ${it.continent}".trim(), it.id, 100.0 + ln((it.areaKm2 ?: 1.0) + 1.0))
            }
            cities.forEach {
                val kind = if (it.isCapital) PlaceKind.CAPITAL else PlaceKind.CITY
                out += Place(it.name, kind, it.lat, it.lon, listOf(it.admin1, it.countryName).filter { s -> s.isNotBlank() }.distinct().joinToString(", "),
                    it.index.toString(), (if (it.isCapital) 60.0 else 40.0) + ln(it.population.toDouble() + 1.0), 120.0)
            }
            curated.continents.forEach { out += Place(it.name, PlaceKind.CONTINENT, it.lat, it.lon, "Continent · ${it.area}", null, 150.0, 9000.0) }
            curated.peaks.forEach { out += Place(it.name, PlaceKind.PEAK, it.lat, it.lon, "%,d m · %s".format(it.elevationM, it.range), null, 70.0, 60.0) }
            curated.trenches.forEach { out += Place(it.name, PlaceKind.TRENCH, it.lat, it.lon, "≈%,d m deep · %s Ocean".format(it.depthM, it.ocean), null, 65.0, 900.0) }
            out += geo.filter { it.kind != PlaceKind.CONTINENT }
            return out
        }

        /** Ranked search: exact > prefix > word-prefix > contains; ties broken by place importance. */
        fun search(index: List<Pair<String, Place>>, query: String, limit: Int = 40): List<Place> {
            val q = normalize(query)
            if (q.isEmpty()) return emptyList()
            return index.asSequence().mapNotNull { (n, p) ->
                val score = when {
                    n == q -> 1000.0
                    n.startsWith(q) -> 600.0
                    n.split(' ').any { it.startsWith(q) } -> 400.0
                    q.length >= 3 && n.contains(q) -> 200.0
                    else -> return@mapNotNull null
                }
                (score + p.rank) to p
            }.sortedByDescending { it.first }.map { it.second }
                .distinctBy { "${it.name}|${it.kind}|${(it.lat * 10).toInt()}" }.take(limit).toList()
        }
    }

    suspend fun search(query: String, limit: Int = 40): List<Place> = search(load().index, query, limit)
}
