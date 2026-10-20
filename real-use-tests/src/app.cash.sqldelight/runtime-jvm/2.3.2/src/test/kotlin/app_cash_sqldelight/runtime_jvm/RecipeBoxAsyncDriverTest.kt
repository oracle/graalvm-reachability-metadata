/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.runtime_jvm

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import app.cash.sqldelight.SuspendingTransacter
import app.cash.sqldelight.SuspendingTransacterImpl
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.JdbcCursor
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Asynchronous driver contract: a recipe box runs on an application-written `SqlDriver` adapter
 * that returns lazy `QueryResult.AsyncValue` results, confines every JDBC call to one database
 * thread, and implements `SuspendingTransacter.TransactionDispatcher` so whole suspending
 * transactions run there.
 *
 * The scenario creates its schema through `SqlSchema<QueryResult.AsyncValue<Unit>>`, reads rows
 * through an asynchronous cursor whose `next()` must be awaited, shows that nothing executes until
 * a result is awaited, that the synchronous `executeAsList`/`value` accessors are rejected for an
 * async driver, and that commit, rollback, listener notification and transaction callbacks all
 * happen on the database thread.
 */
class RecipeBoxAsyncDriverTest {

    private lateinit var databaseThread: ExecutorService
    private lateinit var driver: SerialAsyncDriver
    private lateinit var recipes: RecipeQueries

    @BeforeEach
    fun openRecipeBox(): Unit = runBlocking {
        databaseThread = Executors.newSingleThreadExecutor { task -> Thread(task, DATABASE_THREAD) }
        driver = SerialAsyncDriver(
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY),
            databaseThread.asCoroutineDispatcher(),
        )
        RecipeSchema.create(driver).await()
        recipes = RecipeQueries(driver)
    }

    @AfterEach
    fun closeRecipeBox() {
        driver.close()
        databaseThread.shutdownNow()
    }

    @Test
    fun queriesAreAwaitedWhileJdbcWorkStaysOnTheDatabaseThread(): Unit = runBlocking {
        recipes.add("Pad Thai", "thai", 25)
        recipes.add("Green Curry", "thai", 40)
        recipes.add("Carbonara", "italian", 20)

        assertThat(recipes.byCuisine("thai").awaitAsList()).containsExactly(
            Recipe("Green Curry", "thai", 40),
            Recipe("Pad Thai", "thai", 25),
        )
        assertThat(recipes.byName("Carbonara").awaitAsOneOrNull()).isEqualTo(Recipe("Carbonara", "italian", 20))
        assertThat(recipes.byName("Ramen").awaitAsOneOrNull()).isNull()
        assertThat(recipes.count().awaitAsOneOrNull()).isEqualTo(3L)

        assertThat(currentThreadName()).isNotEqualTo(DATABASE_THREAD)
        assertThat(driver.jdbcThreads).containsExactly(DATABASE_THREAD)
    }

    @Test
    fun asyncResultsDoNothingUntilAwaited(): Unit = runBlocking {
        val pendingInsert: QueryResult<Long> = driver.execute(
            null,
            "INSERT INTO recipe (name, cuisine, minutes) VALUES ('Shakshuka', 'levantine', 30)",
            0,
        )
        val pendingCount: QueryResult<Long?> = recipes.count().execute { cursor ->
            QueryResult.AsyncValue { if (cursor.next().await()) cursor.getLong(0) else null }
        }

        assertThat(recipes.count().awaitAsOneOrNull()).isZero()
        assertThat(pendingInsert.await()).isEqualTo(1L)
        assertThat(pendingCount.await()).isEqualTo(1L)

        // An AsyncValue is not memoized: every await runs the statement again.
        val slowDown: QueryResult<Long> = driver.execute(
            null,
            "UPDATE recipe SET minutes = minutes + 5 WHERE name = 'Shakshuka'",
            0,
        )
        assertThat(recipes.byName("Shakshuka").awaitAsOneOrNull()?.minutes).isEqualTo(30L)
        assertThat(slowDown.await()).isEqualTo(1L)
        assertThat(slowDown.await()).isEqualTo(1L)
        assertThat(recipes.byName("Shakshuka").awaitAsOneOrNull()?.minutes).isEqualTo(40L)
    }

    @Test
    fun synchronousAccessToAnAsyncDriverIsRejected(): Unit = runBlocking {
        recipes.add("Tom Yum", "thai", 35)

        assertThatThrownBy { recipes.byCuisine("thai").executeAsList() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("generateAsync = true")
        assertThatThrownBy { recipes.byName("Tom Yum").executeAsOne() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("asynchronous")
        assertThatThrownBy { driver.execute(null, "DELETE FROM recipe", 0).value }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("generateAsync = true")

        assertThat(recipes.byCuisine("thai").awaitAsList()).containsExactly(Recipe("Tom Yum", "thai", 35))
    }

    @Test
    fun transactionsAreDispatchedToTheDatabaseThread(): Unit = runBlocking {
        val thaiQuery: Query<Recipe> = recipes.byCuisine("thai")
        val refreshes = AtomicInteger()
        val listener = Query.Listener { refreshes.incrementAndGet() }
        thaiQuery.addListener(listener)
        val events: MutableList<String> = mutableListOf()

        recipes.transaction {
            events += "body on ${currentThreadName()}"
            recipes.add("Massaman", "thai", 90)
            recipes.add("Larb", "thai", 20)
            afterCommit { events += "committed on ${currentThreadName()}" }
            afterRollback { events += "rolled back" }
        }
        assertThat(events).containsExactly("body on $DATABASE_THREAD", "committed on $DATABASE_THREAD")
        assertThat(refreshes.get()).isEqualTo(1)
        assertThat(thaiQuery.awaitAsList().map { it.name }).containsExactly("Larb", "Massaman")

        events.clear()
        val outcome: String = recipes.transactionWithResult {
            recipes.add("Khao Soi", "thai", 60)
            afterRollback { events += "rolled back on ${currentThreadName()}" }
            if (recipes.count().awaitAsOneOrNull()!! > 2L) rollback("pantry full") else "saved"
        }
        assertThat(outcome).isEqualTo("pantry full")
        assertThat(events).containsExactly("rolled back on $DATABASE_THREAD")
        assertThat(refreshes.get()).isEqualTo(1)

        assertThatThrownBy {
            runBlocking {
                recipes.transaction {
                    recipes.add("Som Tam", "thai", 15)
                    recipes.add("Som Tam", "thai", 15)
                }
            }
        }.hasMessageContaining("UNIQUE")
        assertThat(thaiQuery.awaitAsList().map { it.name }).containsExactly("Larb", "Massaman")
        assertThat(refreshes.get()).isEqualTo(1)

        thaiQuery.removeListener(listener)
        recipes.add("Panang", "thai", 45)
        assertThat(refreshes.get()).isEqualTo(1)
        assertThat(driver.jdbcThreads).containsExactly(DATABASE_THREAD)
    }

    data class Recipe(val name: String, val cuisine: String, val minutes: Long)

    /** Collects every row of an async query, like `awaitAsList` from SQLDelight's async extensions. */
    private suspend fun <T : Any> ExecutableQuery<T>.awaitAsList(): List<T> = execute { cursor ->
        QueryResult.AsyncValue {
            val rows: MutableList<T> = mutableListOf()
            while (cursor.next().await()) rows += mapper(cursor)
            rows
        }
    }.await()

    /** Reads at most one row of an async query, like `awaitAsOneOrNull`. */
    private suspend fun <T : Any> ExecutableQuery<T>.awaitAsOneOrNull(): T? = execute { cursor ->
        QueryResult.AsyncValue {
            if (!cursor.next().await()) return@AsyncValue null
            val row: T = mapper(cursor)
            check(!cursor.next().await()) { "ResultSet returned more than 1 row for $this" }
            row
        }
    }.await()

    /**
     * Async adapter over a blocking JDBC driver: every result is a lazy `AsyncValue` that hops to
     * the database dispatcher, and rows are buffered there so the mapper may suspend on `next()`.
     */
    class SerialAsyncDriver(
        private val delegate: JdbcDriver,
        private val dispatcher: CoroutineDispatcher,
    ) : SqlDriver, SuspendingTransacter.TransactionDispatcher {

        val jdbcThreads: MutableSet<String> = ConcurrentHashMap.newKeySet()

        override suspend fun <R> dispatch(transaction: suspend () -> R): R = withContext(dispatcher) { transaction() }

        override fun <R> executeQuery(
            identifier: Int?,
            sql: String,
            mapper: (SqlCursor) -> QueryResult<R>,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<R> = QueryResult.AsyncValue {
            val rows: List<Array<Any?>> = onDatabaseThread {
                delegate.executeQuery(identifier, sql, { cursor -> QueryResult.Value(buffer(cursor)) }, parameters, binders)
                    .value
            }
            mapper(BufferedCursor(rows)).await()
        }

        override fun execute(
            identifier: Int?,
            sql: String,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<Long> = QueryResult.AsyncValue {
            onDatabaseThread { delegate.execute(identifier, sql, parameters, binders).value }
        }

        override fun newTransaction(): QueryResult<Transacter.Transaction> = QueryResult.AsyncValue {
            onDatabaseThread { delegate.newTransaction().value }
        }

        override fun currentTransaction(): Transacter.Transaction? = delegate.currentTransaction()

        override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
            delegate.addListener(*queryKeys, listener = listener)
        }

        override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {
            delegate.removeListener(*queryKeys, listener = listener)
        }

        override fun notifyListeners(vararg queryKeys: String) {
            delegate.notifyListeners(*queryKeys)
        }

        override fun close() {
            delegate.close()
        }

        private suspend fun <T> onDatabaseThread(work: () -> T): T = withContext(dispatcher) {
            jdbcThreads += currentThreadName()
            work()
        }

        private fun buffer(cursor: SqlCursor): List<Array<Any?>> {
            val resultSet = (cursor as JdbcCursor).resultSet
            val columns: Int = resultSet.metaData.columnCount
            val rows: MutableList<Array<Any?>> = mutableListOf()
            while (resultSet.next()) rows += Array(columns) { resultSet.getObject(it + 1) }
            return rows
        }
    }

    /** Cursor over prefetched rows whose `next()` is asynchronous, as on a non-blocking driver. */
    class BufferedCursor(private val rows: List<Array<Any?>>) : SqlCursor {
        private var position: Int = -1

        override fun next(): QueryResult<Boolean> = QueryResult.AsyncValue { ++position < rows.size }

        override fun getString(index: Int): String? = rows[position][index] as String?

        override fun getLong(index: Int): Long? = (rows[position][index] as Number?)?.toLong()

        override fun getBytes(index: Int): ByteArray? = rows[position][index] as ByteArray?

        override fun getDouble(index: Int): Double? = (rows[position][index] as Number?)?.toDouble()

        override fun getBoolean(index: Int): Boolean? = getLong(index)?.let { it != 0L }
    }

    /** Async schema, as generated for a database configured with `generateAsync = true`. */
    object RecipeSchema : SqlSchema<QueryResult.AsyncValue<Unit>> {
        override val version: Long = 1

        override fun create(driver: SqlDriver): QueryResult.AsyncValue<Unit> = QueryResult.AsyncValue {
            driver.execute(
                null,
                """
                CREATE TABLE recipe (
                  name TEXT NOT NULL UNIQUE,
                  cuisine TEXT NOT NULL,
                  minutes INTEGER NOT NULL
                )
                """.trimIndent(),
                0,
            ).await()
            driver.execute(null, "CREATE INDEX recipe_cuisine ON recipe (cuisine)", 0).await()
        }

        override fun migrate(
            driver: SqlDriver,
            oldVersion: Long,
            newVersion: Long,
            vararg callbacks: AfterVersion,
        ): QueryResult.AsyncValue<Unit> = QueryResult.AsyncValue {
            check(oldVersion == newVersion) { "RecipeBox has a single schema version" }
        }
    }

    /** Hand-written equivalent of the async queries class generated for `Recipe.sq`. */
    class RecipeQueries(driver: SqlDriver) : SuspendingTransacterImpl(driver) {

        fun byCuisine(cuisine: String): Query<Recipe> = RecipeQuery(
            BY_CUISINE_ID,
            "SELECT name, cuisine, minutes FROM recipe WHERE cuisine = ? ORDER BY name",
            cuisine,
        )

        fun byName(name: String): Query<Recipe> = RecipeQuery(
            BY_NAME_ID,
            "SELECT name, cuisine, minutes FROM recipe WHERE name = ?",
            name,
        )

        fun count(): Query<Long> = Query(
            COUNT_ID,
            arrayOf("recipe"),
            driver,
            "Recipe.sq",
            "count",
            "SELECT count(*) FROM recipe",
        ) { cursor: SqlCursor -> cursor.getLong(0)!! }

        suspend fun add(name: String, cuisine: String, minutes: Long) {
            driver.execute(ADD_ID, "INSERT INTO recipe (name, cuisine, minutes) VALUES (?, ?, ?)", 3) {
                bindString(0, name)
                bindString(1, cuisine)
                bindLong(2, minutes)
            }.await()
            notifyQueries(ADD_ID) { emit -> emit("recipe") }
        }

        /** Parameterized query, generated as an inner class because it binds one argument. */
        private inner class RecipeQuery(
            private val identifier: Int,
            private val sql: String,
            private val argument: String,
        ) : Query<Recipe>({ cursor: SqlCursor ->
            Recipe(cursor.getString(0)!!, cursor.getString(1)!!, cursor.getLong(2)!!)
        }) {
            override fun addListener(listener: Query.Listener) {
                driver.addListener("recipe", listener = listener)
            }

            override fun removeListener(listener: Query.Listener) {
                driver.removeListener("recipe", listener = listener)
            }

            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
                driver.executeQuery(identifier, sql, mapper, 1) { bindString(0, argument) }

            override fun toString(): String = "Recipe.sq:$identifier"
        }

        private companion object {
            const val BY_CUISINE_ID: Int = 1
            const val BY_NAME_ID: Int = 2
            const val COUNT_ID: Int = 3
            const val ADD_ID: Int = 4
        }
    }

    private companion object {
        const val DATABASE_THREAD: String = "recipe-db"

        /** Thread name without the ` @coroutine#N` suffix coroutine debug mode appends. */
        fun currentThreadName(): String = Thread.currentThread().name.substringBefore(" @")
    }
}
