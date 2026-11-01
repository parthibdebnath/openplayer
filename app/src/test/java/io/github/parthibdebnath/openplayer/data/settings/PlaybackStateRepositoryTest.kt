package io.github.parthibdebnath.openplayer.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlaybackStateRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("playback_state.preferences_pb") }
    }
    private val positionStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("playback_position.preferences_pb") }
    }

    private val legacyPositionKey = stringPreferencesKey("saved_position")
    private val queueKey = stringPreferencesKey("saved_queue")

    @After fun tearDown() = scope.cancel()

    private fun repository() = PlaybackStateRepository(queueStore, positionStore)

    @Test fun positionSavedAlongsideTheQueueIsMovedToItsOwnFile() = runBlocking {
        // What earlier versions left behind: both in one file.
        queueStore.edit {
            it[queueKey] = """{"currentSongId":7,"poolSongIds":[7,8],"upcomingSongIds":[8]}"""
            it[legacyPositionKey] = """{"songId":7,"positionMs":61000}"""
        }
        val repository = repository()
        assertEquals(SavedPosition(7, 61_000), repository.loadPosition())
        assertEquals(7L, repository.loadQueue()?.currentSongId)
        assertNull(queueStore.data.first()[legacyPositionKey])
        assertEquals("""{"songId":7,"positionMs":61000}""", positionStore.data.first()[legacyPositionKey])
    }

    @Test fun migrationAlsoRunsWhenTheQueueIsReadFirst() = runBlocking {
        queueStore.edit { it[legacyPositionKey] = """{"songId":3,"positionMs":5000}""" }
        val repository = repository()
        repository.loadQueue()
        assertNull(queueStore.data.first()[legacyPositionKey])
        assertEquals(SavedPosition(3, 5_000), repository.loadPosition())
    }

    @Test fun aNewerPositionIsNotOverwrittenByTheOldOne() = runBlocking {
        queueStore.edit { it[legacyPositionKey] = """{"songId":1,"positionMs":1000}""" }
        positionStore.edit { it[legacyPositionKey] = """{"songId":2,"positionMs":2000}""" }
        assertEquals(SavedPosition(2, 2_000), repository().loadPosition())
        assertNull(queueStore.data.first()[legacyPositionKey])
    }

    @Test fun savingThePositionLeavesTheQueueFileAlone() = runBlocking {
        val repository = repository()
        repository.saveQueue(SavedQueue(currentSongId = 1, poolSongIds = listOf(1, 2)))
        val queueBefore = queueStore.data.first()
        repository.savePosition(SavedPosition(1, 42))
        assertEquals(queueBefore, queueStore.data.first())
        assertEquals(SavedPosition(1, 42), repository.loadPosition())
    }

    @Test fun nothingSavedMeansNothingLoaded() = runBlocking {
        val repository = repository()
        assertNull(repository.loadQueue())
        assertNull(repository.loadPosition())
    }

    @Test fun savedHistoryIsCappedToTheMostRecent() = runBlocking {
        val repository = repository()
        val history = (1L..5_000L).toList()
        repository.saveQueue(SavedQueue(currentSongId = 9_999, historySongIds = history))
        val saved = repository.loadQueue()!!.historySongIds
        assertEquals(PlaybackStateRepository.MAX_SAVED_HISTORY, saved.size)
        assertEquals(history.takeLast(PlaybackStateRepository.MAX_SAVED_HISTORY), saved)
    }

    @Test fun queuesFromBeforeSourceKeysStillLoad() = runBlocking {
        queueStore.edit { it[queueKey] = """{"currentSongId":4,"upcomingSongIds":[5,6],"shuffled":true,"someRetiredField":1}""" }
        val queue = repository().loadQueue()!!
        assertEquals(listOf(5L, 6L), queue.upcomingSongIds)
        assertNull(queue.sourceKey)
    }

    @Test fun queueRoundTripsWithItsSourceKey() = runBlocking {
        val repository = repository()
        val queue = SavedQueue(currentSongId = 1, upcomingSongIds = listOf(3, 2), shuffled = true, sourceKey = "list:5")
        repository.saveQueue(queue)
        assertEquals(queue, repository.loadQueue())
    }
}
