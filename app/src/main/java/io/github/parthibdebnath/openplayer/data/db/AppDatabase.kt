package io.github.parthibdebnath.openplayer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PlaylistEntity::class, SongEntity::class, TagEntity::class, SongTagEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun songDao(): SongDao
    abstract fun tagDao(): TagDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "openplayer.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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
