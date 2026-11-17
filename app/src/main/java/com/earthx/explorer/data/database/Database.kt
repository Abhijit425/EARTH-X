package com.earthx.explorer.data.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
    val lat: Double,
    val lon: Double,
    val ref: String?,
    val collection: String,
    val note: String = "",
    val createdMs: Long,
)

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey val key: String,
    val name: String,
    val kind: String,
    val lat: Double,
    val lon: Double,
    val ref: String?,
    val subtitle: String,
    val usedMs: Long,
)

/** Metadata for cached network payloads. Bodies live in files (they can exceed SQLite's cursor window). */
@Entity(tableName = "cache_entries")
data class CacheEntry(
    @PrimaryKey val key: String,
    val fileName: String,
    val retrievedMs: Long,
    val bytes: Long,
    val source: String,
)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY collection, createdMs DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(f: FavoriteEntity): Long

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM favorites WHERE name = :name AND kind = :kind")
    suspend fun count(name: String, kind: String): Int

    @Query("DELETE FROM favorites WHERE name = :name AND kind = :kind")
    suspend fun deleteByName(name: String, kind: String)

    @Query("UPDATE favorites SET collection = :collection WHERE id = :id")
    suspend fun move(id: Long, collection: String)
}

@Dao
interface RecentDao {
    @Query("SELECT * FROM recent_searches ORDER BY usedMs DESC LIMIT 20")
    fun observe(): Flow<List<RecentSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(r: RecentSearchEntity)

    @Query("DELETE FROM recent_searches")
    suspend fun clear()
}

@Dao
interface CacheDao {
    @Query("SELECT * FROM cache_entries WHERE `key` = :key")
    suspend fun get(key: String): CacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(e: CacheEntry)

    @Query("SELECT * FROM cache_entries ORDER BY retrievedMs ASC")
    suspend fun all(): List<CacheEntry>

    @Query("DELETE FROM cache_entries WHERE `key` = :key")
    suspend fun remove(key: String)

    @Query("DELETE FROM cache_entries")
    suspend fun clear()

    @Query("SELECT COALESCE(SUM(bytes), 0) FROM cache_entries")
    suspend fun totalBytes(): Long

    @Query("SELECT * FROM cache_entries WHERE source = :ns ORDER BY retrievedMs ASC")
    suspend fun allIn(ns: String): List<CacheEntry>

    @Query("SELECT COALESCE(SUM(bytes), 0) FROM cache_entries WHERE source = :ns")
    suspend fun totalBytesIn(ns: String): Long

    @Query("DELETE FROM cache_entries WHERE source = :ns")
    suspend fun clearIn(ns: String)
}

@Database(entities = [FavoriteEntity::class, RecentSearchEntity::class, CacheEntry::class], version = 1, exportSchema = true)
abstract class EarthDatabase : RoomDatabase() {
    abstract fun favorites(): FavoriteDao
    abstract fun recents(): RecentDao
    abstract fun cache(): CacheDao

    companion object {
        fun create(context: Context): EarthDatabase =
            Room.databaseBuilder(context, EarthDatabase::class.java, "earthx.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
