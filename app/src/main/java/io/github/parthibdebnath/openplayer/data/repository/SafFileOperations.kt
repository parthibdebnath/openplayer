package io.github.parthibdebnath.openplayer.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

/** Low-level SAF helpers used by [PlaylistRepository] for copying/moving files between folders. */
object SafFileOperations {

    /** Returns the existing removedSongs child folder, creating it if absent. */
    fun getOrCreateRemovedSongsFolder(context: Context, root: DocumentFile): DocumentFile? {
        findChildFolder(context, root, REMOVED_SONGS_FOLDER_NAME)?.let { return it }
        return root.createDirectory(REMOVED_SONGS_FOLDER_NAME)
    }

    /**
     * Finds a sub-folder by name with a single query. `DocumentFile.findFile` lists the folder and
     * then asks for each child's name separately, which in a folder of a couple of thousand songs
     * is a couple of thousand round trips to the storage provider.
     */
    private fun findChildFolder(context: Context, parent: DocumentFile, name: String): DocumentFile? {
        val parentUri = parent.uri
        val childrenUri = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, DocumentsContract.getDocumentId(parentUri))
        }.getOrNull() ?: return null
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val childId = runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                var found: String? = null
                while (found == null && cursor.moveToNext()) {
                    if (cursor.getString(1) == name && cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        found = cursor.getString(0)
                    }
                }
                found
            }
        }.getOrNull() ?: return null
        return DocumentFile.fromTreeUri(context, DocumentsContract.buildDocumentUriUsingTree(parentUri, childId))
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

        val name = source.name ?: return false
        val mime = source.type ?: "application/octet-stream"
        val newFile = targetParent.createFile(mime, name) ?: return false
        val copied = runCatching {
            resolver.openInputStream(source.uri).use { input ->
                resolver.openOutputStream(newFile.uri).use { output ->
                    if (input == null || output == null) return@runCatching false
                    input.copyTo(output)
                    true
                }
            }
        }.getOrDefault(false)
        // A failed copy leaves the original where it was; the half-written copy is removed so the
        // removed-songs folder doesn't collect broken files.
        if (!copied) {
            runCatching { newFile.delete() }
            return false
        }
        if (!runCatching { source.delete() }.getOrDefault(false)) {
            // Couldn't delete the original: undo the copy rather than leave the song in both places.
            runCatching { newFile.delete() }
            return false
        }
        return true
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
            runCatching { newFile.delete() }
            return null
        }
        return newFile
    }
}
