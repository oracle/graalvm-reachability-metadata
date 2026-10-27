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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Collection parameters bound to `IN ?` lists: a bookshelf looks books up by a selection of ids,
 * filters by sets of genres and years mixed with scalar parameters, tags and deletes whole
 * selections in one statement, and keeps live views over one or two tables refreshed by those
 * bulk writes.
 *
 * `ShelfQueries` is written the way the SQLDelight compiler emits a queries class for a
 * `Shelf.sq` file whose statements take collection parameters: every execution expands the list
 * with `createArguments`, runs the resulting SQL without a cached statement identifier, binds the
 * parameters that follow the list at offsets shifted by its size, and notifies every table the
 * statement touches through `notifyQueries`.
 */
class BookshelfListParameterTest {

    private lateinit var driver: SqlDriver
    private lateinit var shelf: ShelfQueries

    @BeforeEach
    fun openShelf() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShelfSchema.create(driver)
        shelf = ShelfQueries(driver)
        BOOKS.forEach { book -> shelf.addBook(book.title, book.author, book.genre, book.published) }
        shelf.tag(1, "classic")
        shelf.tag(2, "classic")
        shelf.tag(4, "award")
        shelf.tag(6, "classic")
    }

    @AfterEach
    fun closeShelf() {
        driver.close()
    }

    @Test
    fun selectionOfIdsReturnsOnlyTheBooksItNames() {
        assertThat(shelf.booksByIds(listOf(7L, 1L, 3L)).executeAsList().map { it.title })
            .containsExactly("Ubik", "Emma", "Dune")
        assertThat(shelf.booksByIds(setOf(2L)).executeAsOne().title).isEqualTo("Middlemarch")
        assertThat(shelf.booksByIds(listOf(5L, 5L, 99L)).executeAsList().map { it.id })
            .containsExactly(5L)
        assertThat(shelf.booksByIds(emptyList()).executeAsList()).isEmpty()
    }

    @Test
    fun largeSelectionExpandsToOnePlaceholderPerId() {
        val selection: List<Long> = (1L..750L).toList()

        val found: List<Book> = shelf.booksByIds(selection).executeAsList()

        assertThat(found.map { it.id }).containsExactlyElementsOf((1L..BOOKS.size.toLong()).toList())
    }

    @Test
    fun listParametersCombineWithScalarParametersOnEitherSide() {
        val recentGenres: List<String> = shelf.booksInGenresSince(listOf("sf", "fantasy"), since = 1966)
            .executeAsList().map { it.title }
        val byAuthorInYears: List<String> = shelf.booksByAuthorInYears("Le Guin", listOf(1969L, 1974L, 2000L))
            .executeAsList().map { it.title }

        assertThat(recentGenres).containsExactly(
            "A Wizard of Earthsea",
            "Ubik",
            "The Left Hand of Darkness",
            "The Dispossessed",
        )
        assertThat(byAuthorInYears).containsExactly("The Left Hand of Darkness", "The Dispossessed")
        assertThat(shelf.booksInGenresSince(listOf("poetry"), since = 0).executeAsList()).isEmpty()
    }

    @Test
    fun sameQueryReExpandsItsListWhenTheSelectionGrows() {
        val selection: MutableList<Long> = mutableListOf(1L, 2L)
        val selected: Query<Book> = shelf.booksByIds(selection)
        assertThat(selected.executeAsList()).hasSize(2)

        selection += listOf(3L, 4L)

        assertThat(selected.executeAsList().map { it.id }).containsExactly(1L, 2L, 3L, 4L)
    }

    @Test
    fun bulkTagFromSelectionWritesOneRowPerExistingBookAndRefreshesJoinedView() {
        val favorites: Query<Book> = shelf.booksTaggedWithAny(listOf("favorite"))
        val refreshes = RefreshCounter()
        favorites.addListener(refreshes)

        val tagged: Long = shelf.tagSelection("favorite", listOf(3L, 5L, 7L, 42L))

        assertThat(tagged).isEqualTo(3L)
        assertThat(refreshes.count).isEqualTo(1)
        assertThat(favorites.executeAsList().map { it.title })
            .containsExactly("Emma", "A Wizard of Earthsea", "Dune")
        assertThat(shelf.booksTaggedWithAny(listOf("favorite", "award")).executeAsList().map { it.id })
            .containsExactly(3L, 4L, 5L, 7L)
        favorites.removeListener(refreshes)
    }

    @Test
    fun bulkDeleteRefreshesViewsOverEveryTableItTouches() {
        val classics: Query<Book> = shelf.booksTaggedWithAny(listOf("classic"))
        val selection: Query<Book> = shelf.booksByIds(listOf(1L, 2L, 3L))
        val tagCounts: Query<TagCount> = shelf.tagCounts()
        val classicsRefreshes = RefreshCounter()
        val selectionRefreshes = RefreshCounter()
        val tagCountRefreshes = RefreshCounter()
        classics.addListener(classicsRefreshes)
        selection.addListener(selectionRefreshes)
        tagCounts.addListener(tagCountRefreshes)

        val deleted: Long = shelf.deleteBooks(listOf(1L, 6L, 100L))

        assertThat(deleted).isEqualTo(2L)
        assertThat(classicsRefreshes.count).isEqualTo(1)
        assertThat(selectionRefreshes.count).isEqualTo(1)
        assertThat(tagCountRefreshes.count).isEqualTo(1)
        assertThat(classics.executeAsList().map { it.title }).containsExactly("Middlemarch")
        assertThat(selection.executeAsList().map { it.id }).containsExactly(2L, 3L)
        assertThat(tagCounts.executeAsList()).containsExactly(TagCount("award", 1L), TagCount("classic", 1L))

        shelf.retitle(listOf(2L, 3L), suffix = " (annotated)")

        assertThat(classicsRefreshes.count).isEqualTo(2)
        assertThat(selectionRefreshes.count).isEqualTo(2)
        assertThat(tagCountRefreshes.count).isEqualTo(1)
        assertThat(selection.executeAsList().map { it.title })
            .containsExactly("Middlemarch (annotated)", "Emma (annotated)")
        classics.removeListener(classicsRefreshes)
        selection.removeListener(selectionRefreshes)
        tagCounts.removeListener(tagCountRefreshes)
    }

    @Test
    fun bulkWritesInsideTransactionRefreshOnceAfterCommit() {
        val tagCounts: Query<TagCount> = shelf.tagCounts()
        val refreshes = RefreshCounter()
        tagCounts.addListener(refreshes)

        shelf.transaction {
            shelf.tagSelection("reread", listOf(1L, 2L))
            shelf.tagSelection("reread", listOf(3L))
            shelf.deleteBooks(listOf(4L))
            assertThat(refreshes.count).isZero()
        }

        assertThat(refreshes.count).isEqualTo(1)
        assertThat(tagCounts.executeAsList())
            .containsExactly(TagCount("classic", 3L), TagCount("reread", 3L))
        tagCounts.removeListener(refreshes)
    }

    /** One row of the `book` table. */
    data class Book(val id: Long, val title: String, val author: String, val genre: String, val published: Long)

    /** One row of the `tagCounts` query. */
    data class TagCount(val tag: String, val books: Long)

    /** Seed row inserted before each test. */
    class Seed(val title: String, val author: String, val genre: String, val published: Long)

    /** Listener an application view registers to learn that its query must be re-run. */
    class RefreshCounter : Query.Listener {
        var count: Int = 0
            private set

        override fun queryResultsChanged() {
            count++
        }
    }

    /** Schema the SQLDelight compiler would generate from the `CREATE TABLE`s in `Shelf.sq`. */
    object ShelfSchema {
        fun create(driver: SqlDriver) {
            driver.execute(
                null,
                """
                CREATE TABLE book (
                  id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                  title TEXT NOT NULL,
                  author TEXT NOT NULL,
                  genre TEXT NOT NULL,
                  published INTEGER NOT NULL
                )
                """.trimIndent(),
                0,
            )
            driver.execute(
                null,
                """
                CREATE TABLE book_tag (
                  book_id INTEGER NOT NULL REFERENCES book(id) ON DELETE CASCADE,
                  tag TEXT NOT NULL,
                  PRIMARY KEY (book_id, tag)
                )
                """.trimIndent(),
                0,
            )
            driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        }
    }

    /** Hand-written equivalent of the queries class generated for `Shelf.sq`. */
    class ShelfQueries(driver: SqlDriver) : TransacterImpl(driver) {

        fun booksByIds(ids: Collection<Long>): Query<Book> = BooksByIdsQuery(ids, ::mapBook)

        fun booksInGenresSince(genres: Collection<String>, since: Long): Query<Book> =
            BooksInGenresSinceQuery(genres, since, ::mapBook)

        fun booksByAuthorInYears(author: String, years: Collection<Long>): Query<Book> =
            BooksByAuthorInYearsQuery(author, years, ::mapBook)

        fun booksTaggedWithAny(tags: Collection<String>): Query<Book> = BooksTaggedWithAnyQuery(tags, ::mapBook)

        fun tagCounts(): Query<TagCount> = Query(
            TAG_COUNTS_ID,
            arrayOf("book_tag"),
            driver,
            "Shelf.sq",
            "tagCounts",
            "SELECT tag, count(*) FROM book_tag GROUP BY tag ORDER BY tag",
        ) { cursor: SqlCursor -> TagCount(cursor.getString(0)!!, cursor.getLong(1)!!) }

        fun addBook(title: String, author: String, genre: String, published: Long) {
            driver.execute(
                ADD_BOOK_ID,
                "INSERT INTO book (title, author, genre, published) VALUES (?, ?, ?, ?)",
                4,
            ) {
                bindString(0, title)
                bindString(1, author)
                bindString(2, genre)
                bindLong(3, published)
            }
            notifyQueries(ADD_BOOK_ID) { emit -> emit("book") }
        }

        fun tag(bookId: Long, tag: String) {
            driver.execute(TAG_ID, "INSERT INTO book_tag (book_id, tag) VALUES (?, ?)", 2) {
                bindLong(0, bookId)
                bindString(1, tag)
            }
            notifyQueries(TAG_ID) { emit -> emit("book_tag") }
        }

        fun tagSelection(tag: String, bookIds: Collection<Long>): Long {
            val bookIdsIndexes: String = createArguments(count = bookIds.size)
            val inserted: Long = driver.execute(
                null,
                "INSERT INTO book_tag (book_id, tag) SELECT id, ? FROM book WHERE id IN $bookIdsIndexes",
                1 + bookIds.size,
            ) {
                bindString(0, tag)
                bookIds.forEachIndexed { index, bookId -> bindLong(index + 1, bookId) }
            }.value
            notifyQueries(TAG_SELECTION_ID) { emit -> emit("book_tag") }
            return inserted
        }

        fun deleteBooks(ids: Collection<Long>): Long {
            val idsIndexes: String = createArguments(count = ids.size)
            val deleted: Long = driver.execute(null, "DELETE FROM book WHERE id IN $idsIndexes", ids.size) {
                ids.forEachIndexed { index, id -> bindLong(index, id) }
            }.value
            notifyQueries(DELETE_BOOKS_ID) { emit ->
                emit("book")
                emit("book_tag")
            }
            return deleted
        }

        fun retitle(ids: Collection<Long>, suffix: String) {
            val idsIndexes: String = createArguments(count = ids.size)
            driver.execute(null, "UPDATE book SET title = title || ? WHERE id IN $idsIndexes", 1 + ids.size) {
                bindString(0, suffix)
                ids.forEachIndexed { index, id -> bindLong(index + 1, id) }
            }
            notifyQueries(RETITLE_ID) { emit -> emit("book") }
        }

        private fun mapBook(cursor: SqlCursor): Book = Book(
            cursor.getLong(0)!!,
            cursor.getString(1)!!,
            cursor.getString(2)!!,
            cursor.getString(3)!!,
            cursor.getLong(4)!!,
        )

        private inner class BooksByIdsQuery<out T : Any>(
            private val ids: Collection<Long>,
            mapper: (SqlCursor) -> T,
        ) : Query<T>(mapper) {
            override fun addListener(listener: Query.Listener) {
                driver.addListener("book", listener = listener)
            }

            override fun removeListener(listener: Query.Listener) {
                driver.removeListener("book", listener = listener)
            }

            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
                val idsIndexes: String = createArguments(count = ids.size)
                return driver.executeQuery(
                    null,
                    "SELECT id, title, author, genre, published FROM book WHERE id IN $idsIndexes ORDER BY id",
                    mapper,
                    ids.size,
                ) {
                    ids.forEachIndexed { index, id -> bindLong(index, id) }
                }
            }

            override fun toString(): String = "Shelf.sq:booksByIds"
        }

        private inner class BooksInGenresSinceQuery<out T : Any>(
            private val genres: Collection<String>,
            private val since: Long,
            mapper: (SqlCursor) -> T,
        ) : Query<T>(mapper) {
            override fun addListener(listener: Query.Listener) {
                driver.addListener("book", listener = listener)
            }

            override fun removeListener(listener: Query.Listener) {
                driver.removeListener("book", listener = listener)
            }

            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
                val genresIndexes: String = createArguments(count = genres.size)
                return driver.executeQuery(
                    null,
                    """
                    SELECT id, title, author, genre, published FROM book
                    WHERE genre IN $genresIndexes AND published >= ?
                    ORDER BY published, id
                    """.trimIndent(),
                    mapper,
                    genres.size + 1,
                ) {
                    genres.forEachIndexed { index, genre -> bindString(index, genre) }
                    bindLong(genres.size, since)
                }
            }

            override fun toString(): String = "Shelf.sq:booksInGenresSince"
        }

        private inner class BooksByAuthorInYearsQuery<out T : Any>(
            private val author: String,
            private val years: Collection<Long>,
            mapper: (SqlCursor) -> T,
        ) : Query<T>(mapper) {
            override fun addListener(listener: Query.Listener) {
                driver.addListener("book", listener = listener)
            }

            override fun removeListener(listener: Query.Listener) {
                driver.removeListener("book", listener = listener)
            }

            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
                val yearsIndexes: String = createArguments(count = years.size)
                return driver.executeQuery(
                    null,
                    """
                    SELECT id, title, author, genre, published FROM book
                    WHERE author = ? AND published IN $yearsIndexes
                    ORDER BY published
                    """.trimIndent(),
                    mapper,
                    1 + years.size,
                ) {
                    bindString(0, author)
                    years.forEachIndexed { index, year -> bindLong(index + 1, year) }
                }
            }

            override fun toString(): String = "Shelf.sq:booksByAuthorInYears"
        }

        private inner class BooksTaggedWithAnyQuery<out T : Any>(
            private val tags: Collection<String>,
            mapper: (SqlCursor) -> T,
        ) : Query<T>(mapper) {
            override fun addListener(listener: Query.Listener) {
                driver.addListener("book", "book_tag", listener = listener)
            }

            override fun removeListener(listener: Query.Listener) {
                driver.removeListener("book", "book_tag", listener = listener)
            }

            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
                val tagsIndexes: String = createArguments(count = tags.size)
                return driver.executeQuery(
                    null,
                    """
                    SELECT DISTINCT book.id, book.title, book.author, book.genre, book.published
                    FROM book JOIN book_tag ON book_tag.book_id = book.id
                    WHERE book_tag.tag IN $tagsIndexes
                    ORDER BY book.id
                    """.trimIndent(),
                    mapper,
                    tags.size,
                ) {
                    tags.forEachIndexed { index, tag -> bindString(index, tag) }
                }
            }

            override fun toString(): String = "Shelf.sq:booksTaggedWithAny"
        }

        private companion object {
            const val TAG_COUNTS_ID: Int = 1
            const val ADD_BOOK_ID: Int = 2
            const val TAG_ID: Int = 3
            const val TAG_SELECTION_ID: Int = 4
            const val DELETE_BOOKS_ID: Int = 5
            const val RETITLE_ID: Int = 6
        }
    }

    private companion object {
        val BOOKS: List<Seed> = listOf(
            Seed("Ubik", "Philip K. Dick", "sf", 1969),
            Seed("Middlemarch", "George Eliot", "literary", 1871),
            Seed("Emma", "Jane Austen", "literary", 1815),
            Seed("The Dispossessed", "Le Guin", "sf", 1974),
            Seed("A Wizard of Earthsea", "Le Guin", "fantasy", 1968),
            Seed("The Left Hand of Darkness", "Le Guin", "sf", 1969),
            Seed("Dune", "Frank Herbert", "sf", 1965),
        )
    }
}
