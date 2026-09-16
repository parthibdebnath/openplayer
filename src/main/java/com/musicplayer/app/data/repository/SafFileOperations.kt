package com.musicplayer.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

/** Low-level SAF helpers used by [PlaylistRepository] for copying/moving files between folders. */
object SafFileOperations {

    /** Returns the existing removedSongs child folder, creating it if absent. */
    fun getOrCreateRemovedSongsFolder(context: Context, root: DocumentFile): DocumentFile? {
        root.listFiles().firstOrNull { it.isDirectory && it.name == REMOVED_SONGS_FOLDER_NAME }?.let { return it }
        return root.createDirectory(REMOVED_SONGS_FOLDER_NAME)
    }

    /**
     * Moves [source] (a direct child of [sourceParent]) into [targetParent]. Tries the fast
     * native SAF move first; falls back to copy-then-delete for providers that don't support it.
     */
    fun moveInto(context: Context, source: DocumentFile, sourceParent: DocumentFile, targetParent: DocumentFile): Boolean {
        val resolver = context.contentResolver
        val moved = runCatching {
            DocumentsContract.moveDocument(resolver, source.uri, sourceParent.uri, targetParent.uri) != null
        }.getOrDefault(false)
        if (moved) return true

        return runCatching {
            val name = source.name ?: return@runCatching false
            val mime = source.type ?: "application/octet-stream"
            val newFile = targetParent.createFile(mime, name) ?: return@runCatching false
            resolver.openInputStream(source.uri).use { input ->
                resolver.openOutputStream(newFile.uri).use { output ->
                    if (input == null || output == null) return@runCatching false
                    input.copyTo(output)
                }
            }
            source.delete()
        }.getOrDefault(false)
    }

    /** Copies an arbitrary source document (picked via ACTION_OPEN_DOCUMENT) into [targetParent]. */
    fun copyInto(context: Context, sourceUri: Uri, targetParent: DocumentFile): DocumentFile? {
        val resolver = context.contentResolver
        val sourceDoc = DocumentFile.fromSingleUri(context, sourceUri) ?: return null
        val name = sourceDoc.name ?: return null
        val mime = sourceDoc.type ?: "application/octet-stream"
        val newFile = targetParent.createFile(mime, name) ?: return null
        val success = runCatching {
            resolver.openInputStream(sourceUri).use { input ->
                resolver.openOutputStream(newFile.uri).use { output ->
                    if (input == null || output == null) return@runCatching false
                    input.copyTo(output)
                    true
                }
            }
        }.getOrDefault(false)
        if (!success) {
            newFile.delete()
            return null
        }
        return newFile
    }
}
