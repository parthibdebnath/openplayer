package io.github.parthibdebnath.openplayer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PlaylistEntity::class, SongEntity::class, TagEntity::class, SongTagEntity::class, TrackAnalysisEntity::class],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun songDao(): SongDao
    abstract fun tagDao(): TagDao
    abstract fun trackAnalysisDao(): TrackAnalysisDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "openplayer.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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

/**
 * Version 4: tags. Two new tables and nothing changed in the old ones, so a library carries over
 * exactly as it was, with no tags yet. The SQL is Room's own for these entities (app/schemas/4.json).
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL COLLATE NOCASE)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `song_tags` (`songId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, " +
                "PRIMARY KEY(`songId`, `tagId`), FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_tags_tagId` ON `song_tags` (`tagId`)")
    }
}

/**
 * Version 5: the DJ mix's analysis of each song (tempo, beats, key, intro and outro). A new table
 * that starts empty and fills as songs are played; nothing else changes. The SQL is Room's own for
 * the entity (app/schemas/5.json).
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `track_analysis` (`songId` INTEGER NOT NULL, `sizeBytes` INTEGER NOT NULL, " +
                "`lastModified` INTEGER NOT NULL, `version` INTEGER NOT NULL, " +
                "`durationMs` REAL NOT NULL, `bpm` REAL NOT NULL, `beatConfidence` REAL NOT NULL, " +
                "`tempoStable` INTEGER NOT NULL, `introDownbeatMs` REAL NOT NULL, `outroDownbeatMs` REAL NOT NULL, " +
                "`downbeatConfidence` REAL NOT NULL, `musicalKey` INTEGER NOT NULL, `keyConfidence` REAL NOT NULL, " +
                "`audioStartMs` REAL NOT NULL, `audioEndMs` REAL NOT NULL, `introEndMs` REAL NOT NULL, " +
                "`outroStartMs` REAL NOT NULL, `loudnessDb` REAL NOT NULL, PRIMARY KEY(`songId`))",
        )
    }
}
