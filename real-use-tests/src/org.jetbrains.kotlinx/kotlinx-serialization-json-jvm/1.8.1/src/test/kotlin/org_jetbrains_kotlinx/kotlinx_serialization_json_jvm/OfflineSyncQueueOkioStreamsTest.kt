/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_json_jvm

import java.nio.file.Path
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeBufferedSourceToSequence
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import okio.Buffer
import okio.BufferedSink
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.buffer
import okio.gzip
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Exercises the Okio integration of `Json`: `encodeToBufferedSink`, `decodeFromBufferedSource`,
 * and lazy `decodeBufferedSourceToSequence` over Okio buffers, files, and gzip streams.
 *
 * A mobile client keeps an offline queue of pending edits: it appends each edit to a
 * newline-delimited journal through an Okio `FileSystem`, replays the journal lazily on reconnect,
 * stores a gzip-compressed snapshot of the merged state, and restores that snapshot on restart.
 */
@OptIn(ExperimentalSerializationApi::class)
public class OfflineSyncQueueOkioStreamsTest {

    @Serializable
    public enum class Op {
        @SerialName("upsert") UPSERT,
        @SerialName("delete") DELETE,
    }

    @Serializable
    public data class PendingEdit(
        val seq: Long,
        val op: Op,
        val noteId: String,
        val title: String? = null,
        val tags: List<String> = emptyList(),
    )

    @Serializable
    public data class Note(val title: String, val tags: List<String>)

    @Serializable
    public data class Snapshot(
        @SerialName("last_seq") val lastSeq: Long,
        val notes: Map<String, Note>,
    )

    /** Persists pending edits to a journal file and merges them into a snapshot on replay. */
    public class SyncQueue(private val fileSystem: FileSystem, private val dir: okio.Path, private val json: Json) {
        private val journal: okio.Path = dir / "journal.ndjson"
        private val snapshotFile: okio.Path = dir / "snapshot.json.gz"

        public fun enqueue(edit: PendingEdit) {
            fileSystem.appendingSink(journal).buffer().use { sink: BufferedSink ->
                json.encodeToBufferedSink(PendingEdit.serializer(), edit, sink)
                sink.writeUtf8("\n")
            }
        }

        public fun journalLines(): List<String> = fileSystem.read(journal) { readUtf8() }.lines().filter(String::isNotEmpty)

        public fun replay(base: Snapshot): Snapshot {
            val notes: MutableMap<String, Note> = base.notes.toSortedMap()
            var lastSeq: Long = base.lastSeq
            fileSystem.read(journal) {
                json.decodeBufferedSourceToSequence<PendingEdit>(this, DecodeSequenceMode.WHITESPACE_SEPARATED)
                    .filter { it.seq > base.lastSeq }
                    .forEach { edit ->
                        when (edit.op) {
                            Op.UPSERT -> notes[edit.noteId] = Note(edit.title ?: "", edit.tags)
                            Op.DELETE -> notes.remove(edit.noteId)
                        }
                        lastSeq = edit.seq
                    }
            }
            return Snapshot(lastSeq, notes.toMap())
        }

        public fun saveSnapshot(snapshot: Snapshot) {
            fileSystem.sink(snapshotFile).gzip().buffer().use { sink: BufferedSink ->
                json.encodeToBufferedSink(snapshot, sink)
            }
            fileSystem.delete(journal)
        }

        public fun loadSnapshot(): Snapshot = fileSystem.source(snapshotFile).gzip().buffer().use { source ->
            json.decodeFromBufferedSource(Snapshot.serializer(), source)
        }

        public fun hasJournal(): Boolean = fileSystem.exists(journal)
    }

    private val json: Json = Json {
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    private val edits: List<PendingEdit> = listOf(
        PendingEdit(1, Op.UPSERT, "n-1", "Groceries", listOf("home")),
        PendingEdit(2, Op.UPSERT, "n-2", "Caf\u00e9 r\u00e9sum\u00e9", listOf("travel", "fr")),
        PendingEdit(3, Op.UPSERT, "n-3", "Draft"),
        PendingEdit(4, Op.DELETE, "n-3"),
        PendingEdit(5, Op.UPSERT, "n-1", "Groceries (weekly)", listOf("home", "recurring")),
    )

    @Test
    fun journalIsAppendedAndReplayedLazilyFromDisk(@TempDir tmp: Path) {
        val queue: SyncQueue = SyncQueue(FileSystem.SYSTEM, tmp.toOkioPath(), json)
        edits.forEach(queue::enqueue)

        val lines: List<String> = queue.journalLines()
        assertEquals(5, lines.size)
        assertEquals("""{"seq":4,"op":"delete","noteId":"n-3"}""", lines[3])
        assertEquals(
            "{\"seq\":2,\"op\":\"upsert\",\"noteId\":\"n-2\",\"title\":\"Caf\u00e9 r\u00e9sum\u00e9\",\"tags\":[\"travel\",\"fr\"]}",
            lines[1],
        )

        val merged: Snapshot = queue.replay(Snapshot(0, emptyMap()))
        assertEquals(5, merged.lastSeq)
        assertEquals(setOf("n-1", "n-2"), merged.notes.keys)
        assertEquals(Note("Groceries (weekly)", listOf("home", "recurring")), merged.notes["n-1"])
        assertEquals("Caf\u00e9 r\u00e9sum\u00e9", merged.notes.getValue("n-2").title)

        val fromCheckpoint: Snapshot = queue.replay(Snapshot(3, mapOf("n-3" to Note("Draft", emptyList()))))
        assertEquals(5, fromCheckpoint.lastSeq)
        assertEquals(setOf("n-1"), fromCheckpoint.notes.keys)
    }

    @Test
    fun gzipSnapshotSurvivesRestartAndClearsJournal(@TempDir tmp: Path) {
        val dir: okio.Path = tmp.toOkioPath()
        val queue: SyncQueue = SyncQueue(FileSystem.SYSTEM, dir, json)
        edits.forEach(queue::enqueue)
        val merged: Snapshot = queue.replay(Snapshot(0, emptyMap()))
        queue.saveSnapshot(merged)

        assertTrue(!queue.hasJournal())
        val header: ByteArray = FileSystem.SYSTEM.read(dir / "snapshot.json.gz") { readByteArray(2) }
        assertEquals(0x1f, header[0].toInt() and 0xff)
        assertEquals(0x8b, header[1].toInt() and 0xff)

        val restarted: SyncQueue = SyncQueue(FileSystem.SYSTEM, dir, json)
        val restored: Snapshot = restarted.loadSnapshot()
        assertEquals(merged, restored)

        restarted.enqueue(PendingEdit(6, Op.DELETE, "n-2"))
        val next: Snapshot = restarted.replay(restored)
        assertEquals(6, next.lastSeq)
        assertEquals(setOf("n-1"), next.notes.keys)
    }

    @Test
    fun inMemoryBufferCarriesUploadBatchAndRejectsTruncatedPayload() {
        val upload: Buffer = Buffer()
        json.encodeToBufferedSink(edits, upload)
        val wire: String = upload.snapshot().utf8()
        assertTrue(wire.startsWith("""[{"seq":1,"op":"upsert","noteId":"n-1","title":"Groceries","tags":["home"]}"""), wire)

        val received: List<PendingEdit> = json.decodeFromBufferedSource(upload)
        assertEquals(edits, received)
        assertEquals(0L, upload.size)

        val arrayWrapped: Buffer = Buffer().writeUtf8(wire)
        val firstTwo: List<Long> = json.decodeBufferedSourceToSequence<PendingEdit>(arrayWrapped, DecodeSequenceMode.ARRAY_WRAPPED)
            .take(2)
            .map(PendingEdit::seq)
            .toList()
        assertEquals(listOf(1L, 2L), firstTwo)

        val truncated: Buffer = Buffer().writeUtf8(wire.substring(0, wire.length / 2))
        assertThrows(SerializationException::class.java) {
            json.decodeFromBufferedSource<List<PendingEdit>>(truncated)
        }
    }
}
