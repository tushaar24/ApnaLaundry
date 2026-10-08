package com.dailyworks.mylaundry.support.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlin.math.abs

/** A file that might be the dialer's recording of a call. */
data class RecordingCandidate(
    /** content:// URI, as a string so this stays a plain value type. */
    val uri: String,
    val name: String,
    val lastModifiedMs: Long,
    val size: Long,
    val mime: String?,
    /** Folder path when known (MediaStore), used to prefer call-recording folders. */
    val folder: String = "",
)

private val AUDIO_EXT = setOf("m4a", "mp3", "aac", "amr", "wav", "ogg", "opus", "3gp", "awb")

fun extOf(name: String): String = name.substringAfterLast('.', "").lowercase()

fun mimeFor(ext: String): String = when (ext) {
    "m4a", "aac" -> "audio/mp4"
    "mp3" -> "audio/mpeg"
    "amr" -> "audio/amr"
    "awb" -> "audio/amr-wb"
    "wav" -> "audio/wav"
    "ogg", "opus" -> "audio/ogg"
    "3gp" -> "audio/3gpp"
    else -> "application/octet-stream"
}

/**
 * Picks the recording of a call from [candidates]. Dialers write the file
 * when the call ends, so the file must be modified between shortly before the
 * call started and 10 minutes after it ended. Among those, a file name with
 * the dialed number wins (most recorders embed it), then the one written
 * closest to the call's end. Pure — unit-tested.
 */
fun pickRecording(
    candidates: List<RecordingCandidate>,
    phone: String,
    startedAtMs: Long,
    endedAtMs: Long,
): RecordingCandidate? {
    val digits = phone.filter(Char::isDigit).takeLast(10)
    val from = startedAtMs - 15_000
    val to = endedAtMs + 10 * 60_000
    val plausible = candidates.filter {
        it.size > 0 && extOf(it.name) in AUDIO_EXT && it.lastModifiedMs in from..to
    }
    fun nameHasNumber(c: RecordingCandidate): Boolean {
        val n = c.name.filter(Char::isDigit)
        return digits.length == 10 && (n.contains(digits) || n.contains(digits.takeLast(7)))
    }
    fun inCallFolder(c: RecordingCandidate): Boolean {
        val f = (c.folder + "/" + c.name).lowercase()
        return "call" in f || "record" in f
    }
    return plausible.sortedWith(
        compareByDescending<RecordingCandidate> { nameHasNumber(it) }
            .thenByDescending { inCallFolder(it) }
            .thenBy { abs(it.lastModifiedMs - endedAtMs) },
    ).firstOrNull()
}

/**
 * Lists recent audio files from (1) the recordings folder the agent picked
 * (Storage Access Framework, preferred — no broad storage permission) and
 * (2) the shared audio library as a fallback.
 */
class RecordingFinder(private val context: Context) {

    fun candidates(treeUri: String?, sinceMs: Long): List<RecordingCandidate> {
        val fromTree = treeUri?.let { runCatching { listTree(Uri.parse(it), sinceMs) }.getOrNull() }.orEmpty()
        val fromMedia = runCatching { listMediaStore(sinceMs) }.getOrNull().orEmpty()
        return (fromTree + fromMedia).distinctBy { it.name to it.size }
    }

    private fun listTree(tree: Uri, sinceMs: Long): List<RecordingCandidate> {
        val out = mutableListOf<RecordingCandidate>()
        fun walk(docId: String, depth: Int) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            context.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_SIZE,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = c.getString(1).orEmpty()
                    val mime = c.getString(2)
                    val modified = c.getLong(3)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        // Some dialers nest per-date or per-SIM folders.
                        if (depth < 2) walk(id, depth + 1)
                    } else if (modified >= sinceMs) {
                        out += RecordingCandidate(
                            uri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
                            name = name, lastModifiedMs = modified, size = c.getLong(4), mime = mime,
                        )
                    }
                }
            }
        }
        walk(DocumentsContract.getTreeDocumentId(tree), 0)
        return out
    }

    private fun listMediaStore(sinceMs: Long): List<RecordingCandidate> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val folderCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.RELATIVE_PATH
        } else {
            @Suppress("DEPRECATION") MediaStore.Audio.Media.DATA
        }
        val out = mutableListOf<RecordingCandidate>()
        context.contentResolver.query(
            collection,
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.SIZE,
                folderCol,
            ),
            "${MediaStore.Audio.Media.DATE_MODIFIED} >= ?",
            arrayOf((sinceMs / 1000).toString()),
            "${MediaStore.Audio.Media.DATE_MODIFIED} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                out += RecordingCandidate(
                    uri = ContentUris.withAppendedId(collection, c.getLong(0)).toString(),
                    name = c.getString(1).orEmpty(),
                    mime = c.getString(2),
                    lastModifiedMs = c.getLong(3) * 1000,
                    size = c.getLong(4),
                    folder = c.getString(5).orEmpty(),
                )
            }
        }
        return out
    }

    /** Size + display name for a file the agent attached by hand. */
    fun describe(uri: Uri): RecordingCandidate? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_SIZE),
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) return@use null
            RecordingCandidate(
                uri = uri.toString(), name = c.getString(0).orEmpty(), lastModifiedMs = 0,
                size = c.getLong(1), mime = context.contentResolver.getType(uri),
            )
        }
    }.getOrNull()
}
