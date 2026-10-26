package com.local.videoplayer.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "video_meta")
data class VideoMetaEntity(
    @PrimaryKey val id: Long,
    val lastPosition: Long = 0L,
    val isFavorite: Boolean = false,
    val isWatched: Boolean = false,
    val playCount: Int = 0,
    val userNotes: String? = null
)

@Dao
interface VideoMetaDao {
    @Query("SELECT * FROM video_meta WHERE id = :videoId")
    suspend fun getMeta(videoId: Long): VideoMetaEntity?

    @Query("SELECT * FROM video_meta")
    fun getAllMeta(): Flow<List<VideoMetaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(meta: VideoMetaEntity)
}

@Database(entities = [VideoMetaEntity::class], version = 1, exportSchema = false)
abstract class VideoDatabase : RoomDatabase() {
    abstract fun videoMetaDao(): VideoMetaDao
}
