/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.runtime_jvm

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.OptimisticLockException
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Optimistic locking with `OptimisticLockException`: a wiki keeps a lock version on every page,
 * rejects a save made from a stale snapshot, lets an editor rebase and retry after a conflict,
 * and rolls a multi-page publish back when one page lost the race.
 *
 * `PageQueries` is written the way the SQLDelight compiler emits a queries class for a `Page.sq`
 * file whose `version INTEGER AS VALUE LOCK` column guards every `UPDATE`, so the scenario drives
 * the same runtime calls a generated database does.
 */
class WikiPageOptimisticLockTest {

    private lateinit var driver: SqlDriver
    private lateinit var pages: PageQueries

    @BeforeEach
    fun openDatabase() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        PageSchema.create(driver)
        pages = PageQueries(driver)
        pages.create("home", "Home", "Welcome")
        pages.create("faq", "FAQ", "No questions yet")
    }

    @AfterEach
    fun closeDatabase() {
        driver.close()
    }

    @Test
    fun saveFromStaleSnapshotIsRejectedAndKeepsWinningEdit() {
        val alice: Page = pages.page("home").executeAsOne()
        val bob: Page = pages.page("home").executeAsOne()

        val saved: Page = pages.update(alice.copy(body = "Welcome, readers"))

        assertThat(saved.version).isEqualTo(PageVersion(2))
        assertThatThrownBy { pages.update(bob.copy(title = "Start here")) }
            .isInstanceOf(OptimisticLockException::class.java)
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("UPDATE on page failed because optimistic lock version did not match")
        assertThat(pages.page("home").executeAsOne())
            .isEqualTo(Page("home", "Home", "Welcome, readers", PageVersion(2)))
    }

    @Test
    fun liveQueryIsNotifiedOnlyForSavesThatWinTheLock() {
        val snapshot: Page = pages.page("home").executeAsOne()
        val livePage: Query<Page> = pages.page("home")
        val refreshes: MutableList<Page> = mutableListOf()
        val listener = Query.Listener { refreshes += livePage.executeAsOne() }
        livePage.addListener(listener)

        pages.update(snapshot.copy(body = "First"))
        assertThatThrownBy { pages.update(snapshot.copy(body = "Second")) }
            .isInstanceOf(OptimisticLockException::class.java)
        livePage.removeListener(listener)
        pages.update(pages.page("home").executeAsOne().copy(body = "Third"))

        assertThat(refreshes).containsExactly(Page("home", "Home", "First", PageVersion(2)))
        assertThat(pages.page("home").executeAsOne().version).isEqualTo(PageVersion(3))
    }

    @Test
    fun editorRebasesOnLatestVersionAfterConflict() {
        val editor = PageEditor(pages)
        val staleSnapshot: Page = pages.page("faq").executeAsOne()
        pages.update(staleSnapshot.copy(body = "Q: Is it free?\nA: Yes"))

        val saved: Page = editor.edit(staleSnapshot) { page: Page ->
            page.copy(body = page.body + "\nQ: Does it run offline?\nA: Yes")
        }

        assertThat(editor.conflicts).isEqualTo(1)
        assertThat(saved).isEqualTo(
            Page("faq", "FAQ", "Q: Is it free?\nA: Yes\nQ: Does it run offline?\nA: Yes", PageVersion(3)),
        )
        assertThat(pages.page("faq").executeAsOne()).isEqualTo(saved)
    }

    @Test
    fun conflictDuringBatchPublishRollsBackEveryPage() {
        val home: Page = pages.page("home").executeAsOne()
        val staleFaq: Page = pages.page("faq").executeAsOne()
        pages.update(staleFaq.copy(body = "Edited elsewhere"))
        val audit: MutableList<String> = mutableListOf()

        assertThatThrownBy {
            pages.transaction {
                afterCommit { audit += "published" }
                afterRollback { audit += "publish aborted" }
                pages.update(home.copy(title = "Home (v2)"))
                pages.update(staleFaq.copy(title = "FAQ (v2)"))
            }
        }.isInstanceOf(OptimisticLockException::class.java)

        assertThat(audit).containsExactly("publish aborted")
        assertThat(pages.versions().executeAsList()).containsExactly(
            PageSummary("faq", "FAQ", PageVersion(2)),
            PageSummary("home", "Home", PageVersion(1)),
        )
    }

    /** Lock version of a page, the value class the compiler generates for a `LOCK` column. */
    @JvmInline
    value class PageVersion(val value: Long)

    /** One row of the `page` table. */
    data class Page(val slug: String, val title: String, val body: String, val version: PageVersion)

    /** Projection used by the page index. */
    data class PageSummary(val slug: String, val title: String, val version: PageVersion)

    /** Application service that retries a stale save against the latest stored page. */
    class PageEditor(private val pages: PageQueries) {
        var conflicts: Int = 0
            private set

        fun edit(snapshot: Page, maxAttempts: Int = 3, change: (Page) -> Page): Page {
            var base: Page = snapshot
            var lastConflict: OptimisticLockException? = null
            repeat(maxAttempts) {
                try {
                    return pages.update(change(base))
                } catch (conflict: OptimisticLockException) {
                    conflicts++
                    lastConflict = conflict
                    base = pages.page(base.slug).executeAsOne()
                }
            }
            throw checkNotNull(lastConflict)
        }
    }

    /** Schema the SQLDelight compiler would generate from the `CREATE TABLE` in `Page.sq`. */
    object PageSchema {
        fun create(driver: SqlDriver) {
            driver.execute(
                null,
                """
                CREATE TABLE page (
                  slug TEXT NOT NULL PRIMARY KEY,
                  title TEXT NOT NULL,
                  body TEXT NOT NULL,
                  version INTEGER NOT NULL
                )
                """.trimIndent(),
                0,
            )
        }
    }

    /** Hand-written equivalent of the queries class generated for `Page.sq`. */
    class PageQueries(driver: SqlDriver) : TransacterImpl(driver) {

        fun page(slug: String): Query<Page> = PageQuery(slug) { cursor: SqlCursor ->
            Page(
                cursor.getString(0)!!,
                cursor.getString(1)!!,
                cursor.getString(2)!!,
                VERSION_ADAPTER.decode(cursor.getLong(3)!!),
            )
        }

        fun versions(): Query<PageSummary> = Query(
            VERSIONS_ID,
            arrayOf("page"),
            driver,
            "Page.sq",
            "versions",
            "SELECT slug, title, version FROM page ORDER BY slug",
        ) { cursor: SqlCursor ->
            PageSummary(cursor.getString(0)!!, cursor.getString(1)!!, VERSION_ADAPTER.decode(cursor.getLong(2)!!))
        }

        fun create(slug: String, title: String, body: String) {
            driver.execute(CREATE_ID, "INSERT INTO page (slug, title, body, version) VALUES (?, ?, ?, ?)", 4) {
                bindString(0, slug)
                bindString(1, title)
                bindString(2, body)
                bindLong(3, VERSION_ADAPTER.encode(PageVersion(1)))
            }
            notifyQueries(CREATE_ID) { emit -> emit("page") }
        }

        /** Saves [page] only if its version is still the stored one, and returns the saved row. */
        fun update(page: Page): Page {
            val next = PageVersion(page.version.value + 1)
            val updated: Long = driver.execute(
                UPDATE_ID,
                "UPDATE page SET title = ?, body = ?, version = ? WHERE slug = ? AND version = ?",
                5,
            ) {
                bindString(0, page.title)
                bindString(1, page.body)
                bindLong(2, VERSION_ADAPTER.encode(next))
                bindString(3, page.slug)
                bindLong(4, VERSION_ADAPTER.encode(page.version))
            }.value
            if (updated == 0L) {
                throw OptimisticLockException("UPDATE on page failed because optimistic lock version did not match")
            }
            notifyQueries(UPDATE_ID) { emit -> emit("page") }
            return page.copy(version = next)
        }

        private inner class PageQuery<T : Any>(
            private val slug: String,
            mapper: (SqlCursor) -> T,
        ) : Query<T>(mapper) {
            override fun addListener(listener: Listener) {
                driver.addListener("page", listener = listener)
            }

            override fun removeListener(listener: Listener) {
                driver.removeListener("page", listener = listener)
            }

            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
                driver.executeQuery(PAGE_ID, "SELECT slug, title, body, version FROM page WHERE slug = ?", mapper, 1) {
                    bindString(0, slug)
                }

            override fun toString(): String = "Page.sq:page"
        }

        private companion object {
            const val PAGE_ID: Int = 1
            const val VERSIONS_ID: Int = 2
            const val CREATE_ID: Int = 3
            const val UPDATE_ID: Int = 4

            val VERSION_ADAPTER: ColumnAdapter<PageVersion, Long> = object : ColumnAdapter<PageVersion, Long> {
                override fun decode(databaseValue: Long): PageVersion = PageVersion(databaseValue)

                override fun encode(value: PageVersion): Long = value.value
            }
        }
    }
}
