/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.runtime_jvm

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.SQLException

/**
 * Full-text search through SQLite FTS5 virtual tables: a notes app keeps an external-content
 * FTS5 index in sync with its `note` table through triggers, ranks `MATCH` results with `bm25`
 * and renders `highlight` and `snippet` excerpts, answers prefix, phrase, boolean, column-scoped
 * and stemmed queries, and keeps a live search view refreshed as notes are written, edited,
 * deleted, or a bulk import rolls back.
 *
 * `NotesQueries` is written the way the SQLDelight compiler emits a queries class for a
 * `Notes.sq` file with an `fts5` virtual table: every write emits both the content table and the
 * index table, because the triggers change both.
 */
class NoteSearchFullTextTest {

    private lateinit var driver: SqlDriver
    private lateinit var notes: NotesQueries

    @BeforeEach
    fun openDatabase() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        NotesSchema.create(driver)
        notes = NotesQueries(driver)
    }

    @AfterEach
    fun closeDatabase() {
        driver.close()
    }

    @Test
    fun searchRanksTitleMatchesFirstAndRendersExcerpts() {
        val groceries: Long = notes.addNote(
            "Groceries",
            "Buy oat milk and coffee beans before the kotlin meetup on Thursday evening downtown",
        )
        val coroutines: Long = notes.addNote(
            "Kotlin coroutines",
            "Structured concurrency keeps every launched job inside its scope",
        )
        notes.addNote("Garden", "Water the tomatoes and repot the basil")

        val hits: List<SearchHit> = notes.search("kotlin").executeAsList()

        assertThat(hits.map { it.id }).containsExactly(coroutines, groceries)
        assertThat(hits[0].title).isEqualTo("<b>Kotlin</b> coroutines")
        assertThat(hits[1].title).isEqualTo("Groceries")
        assertThat(hits[1].excerpt).contains("[kotlin]").startsWith("...")
        assertThat(hits[0].score).isLessThan(hits[1].score)
        assertThat(notes.countMatches("kotlin").executeAsOne()).isEqualTo(2L)
        assertThat(notes.countMatches("tomatoes OR beans").executeAsOne()).isEqualTo(2L)
        assertThat(notes.search("python").executeAsList()).isEmpty()
    }

    @Test
    fun queryLanguageSupportsPrefixPhraseBooleanColumnAndStemming() {
        val release: Long = notes.addNote("Release notes", "Draft the release notes for version two")
        val java: Long = notes.addNote("Java interop", "Calling kotlin code from java needs JvmStatic")
        val kotlin: Long = notes.addNote("Kotlin tips", "Prefer val over var and run the linter")
        val marathon: Long = notes.addNote("Marathon", "She runs every morning before work")

        assertThat(notes.searchIds("rele*")).containsExactly(release)
        assertThat(notes.searchIds("\"release notes\"")).containsExactly(release)
        assertThat(notes.searchIds("\"notes release\"")).isEmpty()
        assertThat(notes.searchIds("kotlin NOT java")).containsExactly(kotlin)
        assertThat(notes.searchIds("title:kotlin")).containsExactly(kotlin)
        assertThat(notes.searchIds("body:kotlin")).containsExactly(java)
        assertThat(notes.searchIds("running")).containsExactlyInAnyOrder(kotlin, marathon)
        assertThat(notes.searchIds("NEAR(draft version, 4)")).containsExactly(release)
    }

    @Test
    fun liveSearchFollowsWritesEditsAndDeletesThroughTriggers() {
        val meeting: Long = notes.addNote("Standup", "Discuss the flaky deployment pipeline")
        val view = LiveSearch(notes.search("deployment"))
        assertThat(view.rows.map { it.id }).containsExactly(meeting)

        val runbook: Long = notes.addNote("Runbook", "Rollback steps for a failed deployment")
        assertThat(view.refreshes).isEqualTo(1)
        assertThat(view.rows.map { it.id }).containsExactlyInAnyOrder(meeting, runbook)

        notes.editBody(meeting, "Discuss the holiday rota")
        assertThat(view.refreshes).isEqualTo(2)
        assertThat(view.rows.map { it.id }).containsExactly(runbook)
        assertThat(notes.searchIds("holiday")).containsExactly(meeting)

        notes.deleteNote(runbook)
        assertThat(view.refreshes).isEqualTo(3)
        assertThat(view.rows).isEmpty()
        assertThat(notes.searchIds("rollback")).isEmpty()

        notes.checkIndexIntegrity()
        view.close()
    }

    @Test
    fun bulkImportIndexesOnCommitAndLeavesIndexUntouchedOnRollback() {
        notes.addNote("Reading list", "Designing data intensive applications")
        val view = LiveSearch(notes.search("applications"))

        val imported: List<Long> = notes.importNotes(
            listOf(
                "Architecture" to "Event sourcing for financial applications",
                "Testing" to "Property based tests for parser applications",
            ),
        )
        assertThat(view.refreshes).isEqualTo(1)
        assertThat(view.rows.map { it.id }).containsAll(imported).hasSize(3)

        assertThatThrownBy {
            notes.importNotes(
                listOf(
                    "Mobile" to "Offline first mobile applications",
                    "Testing" to "Duplicate title breaks the unique constraint",
                ),
            )
        }.isInstanceOf(SQLException::class.java)

        assertThat(view.refreshes).isEqualTo(1)
        assertThat(notes.searchIds("mobile")).isEmpty()
        assertThat(notes.countMatches("applications").executeAsOne()).isEqualTo(3L)
        notes.checkIndexIntegrity()
        view.close()
    }

    @Test
    fun rebuildBackfillsIndexAddedByLaterSchemaVersion() {
        val legacyDriver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        NotesSchema.createContentTable(legacyDriver)
        val legacyNotes = NotesQueries(legacyDriver)
        legacyNotes.addNote("Legacy", "Written before the app shipped search")
        legacyNotes.addNote("Ideas", "Copied from the first release")

        NotesSchema.addSearchIndex(legacyDriver)
        assertThat(legacyNotes.searchIds("legacy")).isEmpty()

        val view = LiveSearch(legacyNotes.search("legacy"))
        legacyNotes.rebuildIndex()

        assertThat(view.refreshes).isEqualTo(1)
        assertThat(view.rows.map { it.title }).containsExactly("<b>Legacy</b>")
        assertThat(legacyNotes.countMatches("search OR copied").executeAsOne()).isEqualTo(2L)
        legacyNotes.addNote("Fresh", "Indexed by the trigger right away")
        assertThat(legacyNotes.searchIds("fresh")).hasSize(1)
        legacyNotes.checkIndexIntegrity()
        view.close()
        legacyDriver.close()
    }

    @Test
    fun malformedSearchSyntaxIsRejectedBySqlite() {
        notes.addNote("Quotes", "An unbalanced quote is a syntax error")

        assertThatThrownBy { notes.search("\"unbalanced").executeAsList() }
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("unterminated string")
        assertThat(notes.searchIds("unbalanced")).hasSize(1)
    }

    /** One ranked search result: highlighted title, body excerpt and `bm25` score. */
    data class SearchHit(val id: Long, val title: String, val excerpt: String, val score: Double)

    /** Search screen model that re-runs its query whenever SQLDelight reports a change. */
    class LiveSearch(private val query: Query<SearchHit>) : Query.Listener {
        var rows: List<SearchHit> = query.executeAsList()
            private set
        var refreshes: Int = 0
            private set

        init {
            query.addListener(this)
        }

        override fun queryResultsChanged() {
            refreshes++
            rows = query.executeAsList()
        }

        fun close() {
            query.removeListener(this)
        }
    }

    /** Schema the SQLDelight compiler would generate from the statements in `Notes.sq`. */
    object NotesSchema {
        fun create(driver: SqlDriver) {
            createContentTable(driver)
            addSearchIndex(driver)
        }

        /** Version 1 of the app stored notes without a search index. */
        fun createContentTable(driver: SqlDriver) {
            driver.execute(
                null,
                """
                CREATE TABLE note (
                  id INTEGER NOT NULL PRIMARY KEY,
                  title TEXT NOT NULL UNIQUE,
                  body TEXT NOT NULL
                )
                """.trimIndent(),
                0,
            )
        }

        /** Version 2 adds the FTS5 index and the triggers that keep it in sync. */
        fun addSearchIndex(driver: SqlDriver) {
            listOf(
                """
                CREATE VIRTUAL TABLE note_fts USING fts5(
                  title, body, content = 'note', content_rowid = 'id', tokenize = 'porter unicode61'
                )
                """,
                """
                CREATE TRIGGER note_ai AFTER INSERT ON note BEGIN
                  INSERT INTO note_fts (rowid, title, body) VALUES (new.id, new.title, new.body);
                END
                """,
                """
                CREATE TRIGGER note_ad AFTER DELETE ON note BEGIN
                  INSERT INTO note_fts (note_fts, rowid, title, body)
                  VALUES ('delete', old.id, old.title, old.body);
                END
                """,
                """
                CREATE TRIGGER note_au AFTER UPDATE ON note BEGIN
                  INSERT INTO note_fts (note_fts, rowid, title, body)
                  VALUES ('delete', old.id, old.title, old.body);
                  INSERT INTO note_fts (rowid, title, body) VALUES (new.id, new.title, new.body);
                END
                """,
            ).forEach { sql: String -> driver.execute(null, sql.trimIndent(), 0) }
        }
    }

    /** Hand-written equivalent of the queries class generated for `Notes.sq`. */
    class NotesQueries(driver: SqlDriver) : TransacterImpl(driver) {

        fun search(term: String): Query<SearchHit> = SearchQuery(term) { cursor: SqlCursor ->
            SearchHit(
                cursor.getLong(0)!!,
                cursor.getString(1)!!,
                cursor.getString(2)!!,
                cursor.getDouble(3)!!,
            )
        }

        fun searchIds(term: String): List<Long> = search(term).executeAsList().map { it.id }

        fun countMatches(term: String): Query<Long> = CountQuery(term) { cursor: SqlCursor ->
            cursor.getLong(0)!!
        }

        fun addNote(title: String, body: String): Long = transactionWithResult {
            insert(title, body)
            lastInsertRowId()
        }

        fun importNotes(rows: List<Pair<String, String>>): List<Long> = transactionWithResult {
            rows.map { (title: String, body: String) ->
                insert(title, body)
                lastInsertRowId()
            }
        }

        fun editBody(id: Long, body: String) {
            driver.execute(EDIT_BODY_ID, "UPDATE note SET body = ? WHERE id = ?", 2) {
                bindString(0, body)
                bindLong(1, id)
            }
            notifyQueries(EDIT_BODY_ID) { emit -> emit("note"); emit("note_fts") }
        }

        fun deleteNote(id: Long) {
            driver.execute(DELETE_ID, "DELETE FROM note WHERE id = ?", 1) {
                bindLong(0, id)
            }
            notifyQueries(DELETE_ID) { emit -> emit("note"); emit("note_fts") }
        }

        fun rebuildIndex() {
            driver.execute(REBUILD_ID, "INSERT INTO note_fts (note_fts) VALUES ('rebuild')", 0)
            notifyQueries(REBUILD_ID) { emit -> emit("note_fts") }
        }

        /** Runs the FTS5 `integrity-check` command, which fails if index and content disagree. */
        fun checkIndexIntegrity() {
            driver.execute(
                null,
                "INSERT INTO note_fts (note_fts, rank) VALUES ('integrity-check', 1)",
                0,
            )
        }

        private fun insert(title: String, body: String) {
            driver.execute(INSERT_ID, "INSERT INTO note (title, body) VALUES (?, ?)", 2) {
                bindString(0, title)
                bindString(1, body)
            }
            notifyQueries(INSERT_ID) { emit -> emit("note"); emit("note_fts") }
        }

        private fun lastInsertRowId(): Long = driver.executeQuery(
            LAST_ROW_ID,
            "SELECT last_insert_rowid()",
            { cursor: SqlCursor ->
                check(cursor.next().value) { "last_insert_rowid() returned no row" }
                QueryResult.Value(cursor.getLong(0)!!)
            },
            0,
        ).value

        private inner class SearchQuery<out T : Any>(
            private val term: String,
            mapper: (SqlCursor) -> T,
        ) : Query<T>(mapper) {

            override fun addListener(listener: Listener) {
                driver.addListener("note", "note_fts", listener = listener)
            }

            override fun removeListener(listener: Listener) {
                driver.removeListener("note", "note_fts", listener = listener)
            }

            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
                driver.executeQuery(
                    SEARCH_ID,
                    """
                    SELECT note.id,
                           highlight(note_fts, 0, '<b>', '</b>'),
                           snippet(note_fts, 1, '[', ']', '...', 5),
                           bm25(note_fts, 10.0, 1.0)
                    FROM note_fts
                    JOIN note ON note.id = note_fts.rowid
                    WHERE note_fts MATCH ?
                    ORDER BY bm25(note_fts, 10.0, 1.0), note.id
                    """.trimIndent(),
                    mapper,
                    1,
                ) {
                    bindString(0, term)
                }

            override fun toString(): String = "Notes.sq:search"
        }

        private inner class CountQuery<out T : Any>(
            private val term: String,
            mapper: (SqlCursor) -> T,
        ) : Query<T>(mapper) {

            override fun addListener(listener: Listener) {
                driver.addListener("note_fts", listener = listener)
            }

            override fun removeListener(listener: Listener) {
                driver.removeListener("note_fts", listener = listener)
            }

            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
                driver.executeQuery(
                    COUNT_ID,
                    "SELECT count(*) FROM note_fts WHERE note_fts MATCH ?",
                    mapper,
                    1,
                ) {
                    bindString(0, term)
                }

            override fun toString(): String = "Notes.sq:countMatches"
        }

        private companion object {
            const val SEARCH_ID: Int = 1
            const val COUNT_ID: Int = 2
            const val INSERT_ID: Int = 3
            const val LAST_ROW_ID: Int = 4
            const val EDIT_BODY_ID: Int = 5
            const val DELETE_ID: Int = 6
            const val REBUILD_ID: Int = 7
        }
    }
}
