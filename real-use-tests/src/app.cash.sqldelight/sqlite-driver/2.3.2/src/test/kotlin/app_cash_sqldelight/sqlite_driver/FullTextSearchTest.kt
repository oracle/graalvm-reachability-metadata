/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.sqlite_driver

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.sql.SQLException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Full-text search over FTS5 virtual tables queried through an in-memory `JdbcSqliteDriver`.
 *
 * A note-taking application keeps its notes in a plain table and indexes them in an
 * external-content FTS5 table that triggers keep in sync. Its search screen binds the user's
 * query to `MATCH`, ranks hits with `bm25`, highlights the matched words, and is told to refresh
 * whenever a note changes.
 */
class FullTextSearchTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var notes: NotesDatabase

    @BeforeEach
    fun openNotes() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        notes = NotesDatabase(driver)
        notes.createNoteTable()
    }

    @AfterEach
    fun closeNotes() {
        driver.close()
    }

    @Test
    fun searchRanksNotesByRelevanceAndHighlightsMatches() {
        notes.createSearchIndex()
        notes.transaction {
            notes.addNote(1, "Groceries", "milk, eggs and bread for the weekend")
            notes.addNote(2, "Coffee beans", "coffee from the market; grind the coffee fresh, coffee daily")
            notes.addNote(3, "Weekend plans", "hike in the morning and coffee with friends afterwards")
        }

        val hits: List<Hit> = notes.search("coffee").executeAsList()

        assertEquals(listOf(2L, 3L), hits.map { hit: Hit -> hit.id })
        assertEquals("[coffee] from the market; grind the [coffee] fresh, [coffee] daily", hits[0].highlighted)
        assertEquals("hike in the morning and [coffee] with friends afterwards", hits[1].highlighted)
        assertEquals(1L, notes.search("bread").executeAsOne().id)
        assertEquals(emptyList<Hit>(), notes.search("tea").executeAsList())
    }

    @Test
    fun prefixPhraseColumnAndBooleanQueriesNarrowResults() {
        notes.createSearchIndex()
        notes.transaction {
            notes.addNote(1, "Kotlin coroutines", "structured concurrency with scopes and jobs")
            notes.addNote(2, "Kotlin flows", "cold streams built on coroutines")
            notes.addNote(3, "Java threads", "executors and futures, not coroutines")
            notes.addNote(4, "Reading list", "a book about structured programming")
        }

        assertEquals(setOf(1L, 2L, 3L), notes.search("corout*").executeAsList().ids())
        assertEquals(setOf(1L), notes.search("\"structured concurrency\"").executeAsList().ids())
        assertEquals(setOf(1L, 2L), notes.search("title:kotlin").executeAsList().ids())
        assertEquals(setOf(3L), notes.search("coroutines NOT kotlin").executeAsList().ids())
        assertEquals(setOf(1L, 4L), notes.search("structured OR jobs").executeAsList().ids())
    }

    @Test
    fun editsAndDeletesKeepIndexInSyncAndRefreshTheSearchScreen() {
        notes.createSearchIndex()
        notes.addNote(1, "Trip", "pack the tent and sleeping bag")
        notes.addNote(2, "Shopping", "buy a new tent pole")
        val results: Query<Hit> = notes.search("tent")
        val refreshes: CountingListener = CountingListener()
        results.addListener(refreshes)

        assertEquals(setOf(1L, 2L), results.executeAsList().ids())

        notes.editBody(2, "buy a new backpack")
        assertEquals(setOf(1L), results.executeAsList().ids())
        assertEquals(2L, notes.search("backpack").executeAsOne().id)

        notes.deleteNote(1)
        assertEquals(emptyList<Hit>(), results.executeAsList())
        assertEquals(emptyList<Hit>(), notes.search("sleeping").executeAsList())

        results.removeListener(refreshes)
        notes.addNote(3, "Garage", "the old tent is in the garage")
        assertEquals(setOf(3L), results.executeAsList().ids())
        assertEquals(2, refreshes.changes, "only changes made while subscribed refresh the screen")
    }

    @Test
    fun rebuildIndexesNotesWrittenBeforeSearchWasAdded() {
        notes.addNote(1, "Old note", "written before the app had search")
        notes.addNote(2, "Another old note", "search arrives in a later release")

        notes.createSearchIndex()
        assertEquals(emptyList<Hit>(), notes.search("search").executeAsList())

        notes.rebuildSearchIndex()
        assertEquals(setOf(1L, 2L), notes.search("search").executeAsList().ids())
        assertEquals(2L, notes.search("release").executeAsOne().id)
    }

    @Test
    fun malformedSearchSyntaxIsReportedAsSqlError() {
        notes.createSearchIndex()
        notes.addNote(1, "Note", "some text")

        val failure: SQLException = assertThrows(SQLException::class.java) {
            notes.search("\"unterminated").executeAsList()
        }

        assertTrue(failure.message.orEmpty().contains("unterminated string"), failure.message)
        assertEquals(1L, notes.search("text").executeAsOne().id)
    }

    private fun List<Hit>.ids(): Set<Long> = map { hit: Hit -> hit.id }.toSet()

    data class Hit(val id: Long, val title: String, val highlighted: String)

    class CountingListener : Query.Listener {
        var changes: Int = 0
            private set

        override fun queryResultsChanged() {
            changes++
        }
    }

    /** The notes database, written the way SQLDelight generates a database and its queries. */
    class NotesDatabase(driver: SqlDriver) : TransacterImpl(driver) {

        fun createNoteTable() {
            driver.execute(null, "CREATE TABLE note (id INTEGER PRIMARY KEY, title TEXT NOT NULL, body TEXT NOT NULL)", 0)
        }

        fun createSearchIndex() {
            driver.execute(
                null,
                "CREATE VIRTUAL TABLE note_fts USING fts5(title, body, content='note', content_rowid='id')",
                0,
            )
            driver.execute(
                null,
                """
                CREATE TRIGGER note_ai AFTER INSERT ON note BEGIN
                  INSERT INTO note_fts(rowid, title, body) VALUES (new.id, new.title, new.body);
                END
                """.trimIndent(),
                0,
            )
            driver.execute(
                null,
                """
                CREATE TRIGGER note_ad AFTER DELETE ON note BEGIN
                  INSERT INTO note_fts(note_fts, rowid, title, body) VALUES ('delete', old.id, old.title, old.body);
                END
                """.trimIndent(),
                0,
            )
            driver.execute(
                null,
                """
                CREATE TRIGGER note_au AFTER UPDATE ON note BEGIN
                  INSERT INTO note_fts(note_fts, rowid, title, body) VALUES ('delete', old.id, old.title, old.body);
                  INSERT INTO note_fts(rowid, title, body) VALUES (new.id, new.title, new.body);
                END
                """.trimIndent(),
                0,
            )
        }

        fun rebuildSearchIndex() {
            driver.execute(REBUILD, "INSERT INTO note_fts(note_fts) VALUES ('rebuild')", 0)
        }

        fun search(term: String): Query<Hit> = SearchQuery(term)

        fun addNote(id: Long, title: String, body: String) {
            driver.execute(ADD, "INSERT INTO note (id, title, body) VALUES (?, ?, ?)", 3) {
                bindLong(0, id)
                bindString(1, title)
                bindString(2, body)
            }
            notifyQueries(ADD) { emit: (String) -> Unit -> emit("note") }
        }

        fun editBody(id: Long, body: String) {
            driver.execute(EDIT, "UPDATE note SET body = ? WHERE id = ?", 2) {
                bindString(0, body)
                bindLong(1, id)
            }
            notifyQueries(EDIT) { emit: (String) -> Unit -> emit("note") }
        }

        fun deleteNote(id: Long) {
            driver.execute(DELETE, "DELETE FROM note WHERE id = ?", 1) {
                bindLong(0, id)
            }
            notifyQueries(DELETE) { emit: (String) -> Unit -> emit("note") }
        }

        /** A parameterized search query shaped like the inner query classes SQLDelight generates. */
        private inner class SearchQuery(private val term: String) : Query<Hit>({ cursor: SqlCursor ->
            Hit(cursor.getLong(0)!!, cursor.getString(1)!!, cursor.getString(2)!!)
        }) {

            override fun addListener(listener: Query.Listener) {
                driver.addListener("note", listener = listener)
            }

            override fun removeListener(listener: Query.Listener) {
                driver.removeListener("note", listener = listener)
            }

            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
                driver.executeQuery(
                    SEARCH,
                    """
                    SELECT note.id, note.title, highlight(note_fts, 1, '[', ']')
                    FROM note_fts JOIN note ON note.id = note_fts.rowid
                    WHERE note_fts MATCH ?
                    ORDER BY bm25(note_fts), note.id
                    """.trimIndent(),
                    mapper,
                    1,
                ) {
                    bindString(0, term)
                }
        }

        private companion object {
            const val SEARCH: Int = 1
            const val ADD: Int = 2
            const val EDIT: Int = 3
            const val DELETE: Int = 4
            const val REBUILD: Int = 5
        }
    }
}
