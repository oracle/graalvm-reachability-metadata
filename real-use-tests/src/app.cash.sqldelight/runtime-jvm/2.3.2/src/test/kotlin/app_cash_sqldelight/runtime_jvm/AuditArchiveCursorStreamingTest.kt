/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.runtime_jvm

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.MessageDigest

/**
 * Cursor streaming and result cardinality on non-observable `ExecutableQuery`s: an audit
 * archive streams its log row by row into a CSV export and a payload digest, stops reading
 * as soon as it finds what it is looking for, pages through the log by keyset, and relies on
 * `executeAsOne` and `executeAsOneOrNull` to reject lookups that match too few or too many rows.
 *
 * `ArchiveQueries` is written the way the SQLDelight compiler emits a queries class for an
 * `AuditLog.sq` file whose reads nobody observes, so they are `ExecutableQuery`s that run
 * once per call and never register listeners.
 */
class AuditArchiveCursorStreamingTest {

    private lateinit var driver: SqlDriver
    private lateinit var archive: ArchiveQueries

    @BeforeEach
    fun openArchive() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ArchiveSchema.create(driver)
        archive = ArchiveQueries(driver)
        ENTRIES.forEach { entry -> archive.record(entry.actor, entry.action, entry.payload) }
    }

    @AfterEach
    fun closeArchive() {
        driver.close()
    }

    @Test
    fun exportStreamsEveryRowIntoCsvWithoutBuildingAList() {
        val csv = StringBuilder("id,actor,action,bytes\n")

        val exported: Long = archive.everything().execute { cursor: SqlCursor ->
            var rows = 0L
            while (cursor.next().value) {
                val payload: ByteArray = cursor.getBytes(3)!!
                csv.append(cursor.getLong(0)).append(',')
                    .append(cursor.getString(1)).append(',')
                    .append(cursor.getString(2)).append(',')
                    .append(payload.size).append('\n')
                rows++
            }
            QueryResult.Value(rows)
        }.value

        assertThat(exported).isEqualTo(ENTRIES.size.toLong())
        assertThat(csv.lines().filter { it.isNotEmpty() }).hasSize(ENTRIES.size + 1)
        assertThat(csv.toString()).startsWith(
            """
            id,actor,action,bytes
            1,alice,login,2
            2,bob,login,2
            3,alice,export,6
            """.trimIndent(),
        )
        assertThat(csv.toString()).endsWith("10,alice,logout,0\n")
    }

    @Test
    fun payloadDigestMatchesDigestOfOriginalBlobs() {
        val expected = MessageDigest.getInstance("SHA-256")
        ENTRIES.forEach { entry -> expected.update(entry.payload) }

        val digest: ByteArray = archive.everything().execute { cursor: SqlCursor ->
            val streamed = MessageDigest.getInstance("SHA-256")
            while (cursor.next().value) {
                streamed.update(cursor.getBytes(3)!!)
            }
            QueryResult.Value(streamed.digest())
        }.value

        assertThat(digest).isEqualTo(expected.digest())
    }

    @Test
    fun searchStopsReadingOnceTheFirstMatchIsFound() {
        var rowsRead = 0
        val everything: ExecutableQuery<AuditEntry> = archive.everything()

        val firstDeletion: AuditEntry? = everything.execute { cursor: SqlCursor ->
            var found: AuditEntry? = null
            while (found == null && cursor.next().value) {
                rowsRead++
                val entry: AuditEntry = everything.mapper(cursor)
                if (entry.action == "delete") {
                    found = entry
                }
            }
            QueryResult.Value(found)
        }.value

        assertThat(firstDeletion).isNotNull
        assertThat(firstDeletion!!.id).isEqualTo(5L)
        assertThat(firstDeletion.actor).isEqualTo("carol")
        assertThat(rowsRead).isEqualTo(5)
    }

    @Test
    fun keysetPagingVisitsEveryRowOnceAndPicksUpRowsAppendedMidway() {
        val pages = mutableListOf<List<Long>>()
        var lastSeen = 0L

        while (true) {
            val page: List<AuditEntry> = archive.pageAfter(lastSeen, limit = 4).executeAsList()
            if (page.isEmpty()) {
                break
            }
            pages += page.map { it.id }
            lastSeen = page.last().id
            if (pages.size == 1) {
                archive.record("dave", "login", byteArrayOf(9))
            }
        }

        assertThat(pages).containsExactly(
            listOf(1L, 2L, 3L, 4L),
            listOf(5L, 6L, 7L, 8L),
            listOf(9L, 10L, 11L),
        )
        assertThat(archive.latest().executeAsOne().actor).isEqualTo("dave")
    }

    @Test
    fun singleRowLookupsEnforceCardinality() {
        assertThat(archive.entriesBy("carol").executeAsOneOrNull()?.action).isEqualTo("delete")
        assertThat(archive.entriesBy("erin").executeAsOneOrNull()).isNull()
        assertThat(archive.entriesBy("frank").executeAsOne().action).isEqualTo("rotate-keys")

        assertThatThrownBy { archive.entriesBy("erin").executeAsOne() }
            .isInstanceOf(NullPointerException::class.java)
            .hasMessageContaining("AuditLog.sq:entriesBy")
        assertThatThrownBy { archive.entriesBy("alice").executeAsOneOrNull() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("more than 1 row")
        assertThatThrownBy { archive.entriesBy("alice").executeAsOne() }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun purgeReportsAffectedRowsAndCountQueryReRunsOnEveryCall() {
        val count: ExecutableQuery<Long> = archive.count()
        assertThat(count.executeAsOne()).isEqualTo(10L)

        val purged: Long = archive.purgeActor("alice")

        assertThat(purged).isEqualTo(4L)
        assertThat(count.executeAsOne()).isEqualTo(6L)
        assertThat(archive.purgeActor("alice")).isZero()
        assertThat(archive.entriesBy("alice").executeAsList()).isEmpty()
    }

    /** One row of the `audit_entry` table. */
    data class AuditEntry(val id: Long, val actor: String, val action: String, val payload: ByteArray) {
        override fun equals(other: Any?): Boolean = other is AuditEntry && other.id == id &&
            other.actor == actor && other.action == action && other.payload.contentEquals(payload)

        override fun hashCode(): Int = id.hashCode()
    }

    /** Seed row recorded before each test. */
    class Seed(val actor: String, val action: String, val payload: ByteArray)

    /** Schema the SQLDelight compiler would generate from the `CREATE TABLE` in `AuditLog.sq`. */
    object ArchiveSchema {
        fun create(driver: SqlDriver) {
            driver.execute(
                null,
                """
                CREATE TABLE audit_entry (
                  id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                  actor TEXT NOT NULL,
                  action TEXT NOT NULL,
                  payload BLOB NOT NULL
                )
                """.trimIndent(),
                0,
            )
        }
    }

    /** Hand-written equivalent of the queries class generated for `AuditLog.sq`. */
    class ArchiveQueries(private val driver: SqlDriver) {

        fun everything(): ExecutableQuery<AuditEntry> = Query(
            EVERYTHING_ID,
            driver,
            "AuditLog.sq",
            "everything",
            "SELECT id, actor, action, payload FROM audit_entry ORDER BY id",
            ::mapEntry,
        )

        fun latest(): ExecutableQuery<AuditEntry> = Query(
            LATEST_ID,
            driver,
            "AuditLog.sq",
            "latest",
            "SELECT id, actor, action, payload FROM audit_entry ORDER BY id DESC LIMIT 1",
            ::mapEntry,
        )

        fun count(): ExecutableQuery<Long> = Query(
            COUNT_ID,
            driver,
            "AuditLog.sq",
            "count",
            "SELECT count(*) FROM audit_entry",
        ) { cursor: SqlCursor -> cursor.getLong(0)!! }

        fun entriesBy(actor: String): ExecutableQuery<AuditEntry> = EntriesByQuery(actor, ::mapEntry)

        fun pageAfter(afterId: Long, limit: Long): ExecutableQuery<AuditEntry> =
            PageAfterQuery(afterId, limit, ::mapEntry)

        fun record(actor: String, action: String, payload: ByteArray) {
            driver.execute(RECORD_ID, "INSERT INTO audit_entry (actor, action, payload) VALUES (?, ?, ?)", 3) {
                bindString(0, actor)
                bindString(1, action)
                bindBytes(2, payload)
            }
        }

        fun purgeActor(actor: String): Long =
            driver.execute(PURGE_ID, "DELETE FROM audit_entry WHERE actor = ?", 1) {
                bindString(0, actor)
            }.value

        private fun mapEntry(cursor: SqlCursor): AuditEntry =
            AuditEntry(cursor.getLong(0)!!, cursor.getString(1)!!, cursor.getString(2)!!, cursor.getBytes(3)!!)

        private inner class EntriesByQuery<out T : Any>(
            private val actor: String,
            mapper: (SqlCursor) -> T,
        ) : ExecutableQuery<T>(mapper) {
            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(
                ENTRIES_BY_ID,
                "SELECT id, actor, action, payload FROM audit_entry WHERE actor = ? ORDER BY id",
                mapper,
                1,
            ) {
                bindString(0, actor)
            }

            override fun toString(): String = "AuditLog.sq:entriesBy"
        }

        private inner class PageAfterQuery<out T : Any>(
            private val afterId: Long,
            private val limit: Long,
            mapper: (SqlCursor) -> T,
        ) : ExecutableQuery<T>(mapper) {
            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(
                PAGE_AFTER_ID,
                "SELECT id, actor, action, payload FROM audit_entry WHERE id > ? ORDER BY id LIMIT ?",
                mapper,
                2,
            ) {
                bindLong(0, afterId)
                bindLong(1, limit)
            }

            override fun toString(): String = "AuditLog.sq:pageAfter"
        }

        private companion object {
            const val EVERYTHING_ID: Int = 1
            const val LATEST_ID: Int = 2
            const val COUNT_ID: Int = 3
            const val ENTRIES_BY_ID: Int = 4
            const val PAGE_AFTER_ID: Int = 5
            const val RECORD_ID: Int = 6
            const val PURGE_ID: Int = 7
        }
    }

    private companion object {
        val ENTRIES: List<Seed> = listOf(
            Seed("alice", "login", byteArrayOf(1, 2)),
            Seed("bob", "login", byteArrayOf(3, 4)),
            Seed("alice", "export", "report".toByteArray()),
            Seed("bob", "update", byteArrayOf(5)),
            Seed("carol", "delete", "row-42".toByteArray()),
            Seed("bob", "delete", "row-43".toByteArray()),
            Seed("frank", "rotate-keys", ByteArray(32) { it.toByte() }),
            Seed("alice", "update", byteArrayOf(6, 7, 8)),
            Seed("bob", "logout", ByteArray(0)),
            Seed("alice", "logout", ByteArray(0)),
        )
    }
}
