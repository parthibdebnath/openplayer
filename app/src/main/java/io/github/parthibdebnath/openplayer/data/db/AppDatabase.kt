package io.github.parthibdebnath.openplayer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PlaylistEntity::class, SongEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun songDao(): SongDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "openplayer.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}

/**
 * Version 2: playlists that take in their subfolders, and remembered positions in long tracks.
 * Both are new columns with defaults, so an existing library carries over exactly as it was.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE playlists ADD COLUMN includeSubfolders INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE songs ADD COLUMN resumePositionMs INTEGER NOT NULL DEFAULT 0")
    }
}

/** Version 3: favourite songs. A new column defaulting to "not a favourite", so nothing else changes. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE songs ADD COLUMN isFavourite INTEGER NOT NULL DEFAULT 0")
    }
}
