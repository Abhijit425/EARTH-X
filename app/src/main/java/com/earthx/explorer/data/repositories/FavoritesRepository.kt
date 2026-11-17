package com.earthx.explorer.data.repositories

import com.earthx.explorer.data.database.EarthDatabase
import com.earthx.explorer.data.database.FavoriteEntity
import com.earthx.explorer.data.database.RecentSearchEntity
import com.earthx.explorer.data.model.Place
import kotlinx.coroutines.flow.Flow

class FavoritesRepository(private val db: EarthDatabase) {
    val favorites: Flow<List<FavoriteEntity>> = db.favorites().observeAll()
    val recents: Flow<List<RecentSearchEntity>> = db.recents().observe()

    suspend fun isFavorite(p: Place) = db.favorites().count(p.name, p.kind.name) > 0

    suspend fun toggle(p: Place, collection: String = "Favourites"): Boolean {
        return if (isFavorite(p)) { db.favorites().deleteByName(p.name, p.kind.name); false }
        else {
            db.favorites().insert(FavoriteEntity(name = p.name, kind = p.kind.name, lat = p.lat, lon = p.lon, ref = p.ref,
                collection = collection.ifBlank { "Favourites" }, createdMs = System.currentTimeMillis()))
            true
        }
    }

    suspend fun remove(id: Long) = db.favorites().delete(id)
    suspend fun move(id: Long, collection: String) = db.favorites().move(id, collection.ifBlank { "Favourites" })

    suspend fun addRecent(p: Place) = db.recents().upsert(
        RecentSearchEntity("${p.kind}|${p.name}|${p.ref}", p.name, p.kind.name, p.lat, p.lon, p.ref, p.subtitle, System.currentTimeMillis()))

    suspend fun clearRecents() = db.recents().clear()
}
