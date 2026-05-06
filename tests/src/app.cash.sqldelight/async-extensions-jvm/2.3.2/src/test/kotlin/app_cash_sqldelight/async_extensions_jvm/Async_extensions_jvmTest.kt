/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.async_extensions_jvm

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.async.coroutines.awaitCreate
import app.cash.sqldelight.async.coroutines.awaitMigrate
import app.cash.sqldelight.async.coroutines.awaitQuery
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.db.SqlSchema
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class Async_extensions_jvmTest {
    @Test
    fun awaitExecutesStatementBindsValuesAndAwaitsAsyncResult(): Unit = runBlocking {
        withTimeout(5_000) {
            val driver: AsyncRecordingDriver = AsyncRecordingDriver(
                affectedRows = 3L,
                expectedCoroutineName = "statement-await-context",
            )

            val affectedRows: Long = withContext(CoroutineName("statement-await-context")) {
                driver.await(
                    identifier = 11,
                    sql = "UPDATE users SET name = ? WHERE id = ? AND active = ?",
                    parameters = 3,
                ) {
                    bindString(0, "Ada")
                    bindLong(1, 1L)
                    bindBoolean(2, true)
                }
            }

            assertThat(affectedRows).isEqualTo(3L)
            assertThat(driver.executedStatements).containsExactly(
                ExecutedStatement(11, "UPDATE users SET name = ? WHERE id = ? AND active = ?", 3),
            )
            assertThat(driver.boundValues).containsExactly(
                BoundValue(0, "Ada"),
                BoundValue(1, 1L),
                BoundValue(2, true),
            )
        }
    }

    @Test
    fun awaitQueryRunsSuspendingMapperAgainstAsyncCursorAndPreservesBindings(): Unit = runBlocking {
        withTimeout(5_000) {
            val driver: AsyncRecordingDriver = AsyncRecordingDriver(
                rows = listOf(
                    User(1L, "Ada", active = true),
                    User(2L, "Grace", active = false),
                ),
                expectedCoroutineName = "query-await-context",
            )

            val users: List<User> = withContext(CoroutineName("query-await-context")) {
                driver.awaitQuery(
                    identifier = 21,
                    sql = "SELECT id, name, active FROM users WHERE active = ? ORDER BY id",
                    mapper = { cursor: SqlCursor ->
                        val mappedUsers: MutableList<User> = mutableListOf()
                        while (cursor.next().await()) {
                            mappedUsers += User(
                                id = cursor.getLong(0) ?: error("id was null"),
                                name = cursor.getString(1) ?: error("name was null"),
                                active = cursor.getBoolean(2) ?: error("active was null"),
                            )
                        }
                        mappedUsers
                    },
                    parameters = 1,
                ) {
                    bindBoolean(0, true)
                }
            }

            assertThat(users).containsExactly(
                User(1L, "Ada", active = true),
                User(2L, "Grace", active = false),
            )
            assertThat(driver.executedQueries).containsExactly(
                ExecutedStatement(21, "SELECT id, name, active FROM users WHERE active = ? ORDER BY id", 1),
            )
            assertThat(driver.boundValues).containsExactly(BoundValue(0, true))
        }
    }

    @Test
    fun executableQueryAwaitHelpersMapListsAndSingleRowsFromAsyncCursors(): Unit = runBlocking {
        withTimeout(5_000) {
            val listQuery: AsyncUserQuery = AsyncUserQuery(
                rows = listOf(User(1L, "Ada", true), User(2L, "Grace", false)),
                expectedCoroutineName = "executable-list-context",
            )
            val oneQuery: AsyncUserQuery = AsyncUserQuery(
                rows = listOf(User(3L, "Katherine", true)),
                expectedCoroutineName = "executable-one-context",
            )
            val emptyQuery: AsyncUserQuery = AsyncUserQuery(emptyList())

            val listedUsers: List<User> = withContext(CoroutineName("executable-list-context")) {
                listQuery.awaitAsList()
            }
            val singleUser: User = withContext(CoroutineName("executable-one-context")) {
                oneQuery.awaitAsOne()
            }
            val nullableUser: User? = withContext(CoroutineName("executable-one-context")) {
                oneQuery.awaitAsOneOrNull()
            }
            val emptyUser: User? = emptyQuery.awaitAsOneOrNull()

            assertThat(listedUsers).containsExactly(User(1L, "Ada", true), User(2L, "Grace", false))
            assertThat(singleUser).isEqualTo(User(3L, "Katherine", true))
            assertThat(nullableUser).isEqualTo(User(3L, "Katherine", true))
            assertThat(emptyUser).isNull()
            assertThat(listQuery.executeCount).isEqualTo(1)
            assertThat(oneQuery.executeCount).isEqualTo(2)
            assertThat(emptyQuery.executeCount).isEqualTo(1)
        }
    }

    @Test
    fun executableQueryAwaitHelpersReportEmptyAndMultipleRowErrors(): Unit = runBlocking {
        withTimeout(5_000) {
            val emptyQuery: AsyncUserQuery = AsyncUserQuery(emptyList(), label = "emptyUsers")
            val multipleRowsQuery: AsyncUserQuery = AsyncUserQuery(
                rows = listOf(User(1L, "Ada", true), User(2L, "Grace", false)),
                label = "multipleUsers",
            )

            val emptyFailure: Throwable = failureFrom { emptyQuery.awaitAsOne() }
            val multipleRowsFailure: Throwable = failureFrom { multipleRowsQuery.awaitAsOneOrNull() }

            assertThat(emptyFailure)
                .isInstanceOf(NullPointerException::class.java)
                .hasMessageContaining("ResultSet returned null for AsyncUserQuery:emptyUsers")
            assertThat(multipleRowsFailure)
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("ResultSet returned more than 1 row for AsyncUserQuery:multipleUsers")
        }
    }

    @Test
    fun awaitCreateAwaitMigrateAndSynchronousSchemaBridgeAsyncSchemas(): Unit = runBlocking {
        withTimeout(5_000) {
            val driver: AsyncRecordingDriver = AsyncRecordingDriver()
            val events: MutableList<String> = mutableListOf()
            val asyncSchema: RecordingAsyncSchema = RecordingAsyncSchema(
                version = 4L,
                events = events,
                expectedCoroutineName = "schema-await-context",
            )

            withContext(CoroutineName("schema-await-context")) {
                asyncSchema.awaitCreate(driver)
                asyncSchema.awaitMigrate(driver, oldVersion = 1L, newVersion = 4L)
            }

            val synchronousSchema: SqlSchema<QueryResult.Value<Unit>> = RecordingAsyncSchema(
                version = 4L,
                events = events,
                expectedCoroutineName = null,
            ).synchronous()
            val callback: AfterVersion = AfterVersion(3L) { callbackDriver: SqlDriver ->
                assertThat(callbackDriver).isSameAs(driver)
                events += "callback-3"
            }

            val createResult: QueryResult.Value<Unit> = synchronousSchema.create(driver)
            val migrateResult: QueryResult.Value<Unit> = synchronousSchema.migrate(driver, 2L, 4L, callback)

            assertThat(synchronousSchema.version).isEqualTo(4L)
            assertThat(createResult.value).isEqualTo(Unit)
            assertThat(migrateResult.value).isEqualTo(Unit)
            assertThat(events).containsExactly(
                "create",
                "migrate-1-to-4-callbacks-0",
                "create",
                "migrate-2-to-4-callbacks-1",
                "callback-3",
            )
        }
    }

    private suspend fun failureFrom(block: suspend () -> Unit): Throwable {
        var failure: Throwable? = null
        try {
            block()
        } catch (throwable: Throwable) {
            failure = throwable
        }
        return failure ?: error("Expected block to fail")
    }

    private data class User(
        val id: Long,
        val name: String,
        val active: Boolean,
    )

    private data class ExecutedStatement(
        val identifier: Int?,
        val sql: String,
        val parameters: Int,
    )

    private data class BoundValue(
        val index: Int,
        val value: Any?,
    )

    private class AsyncUserQuery(
        private val rows: List<User>,
        private val expectedCoroutineName: String? = null,
        private val label: String = "selectUsers",
    ) : ExecutableQuery<User>({ cursor: SqlCursor ->
        User(
            id = cursor.getLong(0) ?: error("id was null"),
            name = cursor.getString(1) ?: error("name was null"),
            active = cursor.getBoolean(2) ?: error("active was null"),
        )
    }) {
        var executeCount: Int = 0
            private set

        override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
            executeCount++
            return mapper(AsyncUserCursor(rows, expectedCoroutineName))
        }

        override fun toString(): String = "AsyncUserQuery:$label"
    }

    private class AsyncRecordingDriver(
        private val rows: List<User> = emptyList(),
        private val affectedRows: Long = 1L,
        private val expectedCoroutineName: String? = null,
    ) : SqlDriver {
        val executedQueries: MutableList<ExecutedStatement> = mutableListOf()
        val executedStatements: MutableList<ExecutedStatement> = mutableListOf()
        val boundValues: MutableList<BoundValue> = mutableListOf()
        var closeCount: Int = 0
            private set

        override fun <R> executeQuery(
            identifier: Int?,
            sql: String,
            mapper: (SqlCursor) -> QueryResult<R>,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<R> {
            executedQueries += ExecutedStatement(identifier, sql, parameters)
            recordBindings(binders)
            return mapper(AsyncUserCursor(rows, expectedCoroutineName))
        }

        override fun execute(
            identifier: Int?,
            sql: String,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<Long> {
            executedStatements += ExecutedStatement(identifier, sql, parameters)
            recordBindings(binders)
            return QueryResult.AsyncValue {
                assertExpectedCoroutineName(expectedCoroutineName)
                affectedRows
            }
        }

        override fun newTransaction(): QueryResult<Transacter.Transaction> {
            throw UnsupportedOperationException("Transactions are not used by these async extension tests")
        }

        override fun currentTransaction(): Transacter.Transaction? = null

        override fun addListener(vararg queryKeys: String, listener: Query.Listener): Unit = Unit

        override fun removeListener(vararg queryKeys: String, listener: Query.Listener): Unit = Unit

        override fun notifyListeners(vararg queryKeys: String): Unit = Unit

        override fun close(): Unit {
            closeCount++
        }

        private fun recordBindings(binders: (SqlPreparedStatement.() -> Unit)?) {
            if (binders == null) return
            val statement: RecordingPreparedStatement = RecordingPreparedStatement()
            statement.binders()
            boundValues += statement.boundValues
        }
    }

    private class RecordingPreparedStatement : SqlPreparedStatement {
        val boundValues: MutableList<BoundValue> = mutableListOf()

        override fun bindBytes(index: Int, bytes: ByteArray?): Unit {
            boundValues += BoundValue(index, bytes?.copyOf())
        }

        override fun bindLong(index: Int, long: Long?): Unit {
            boundValues += BoundValue(index, long)
        }

        override fun bindDouble(index: Int, double: Double?): Unit {
            boundValues += BoundValue(index, double)
        }

        override fun bindString(index: Int, string: String?): Unit {
            boundValues += BoundValue(index, string)
        }

        override fun bindBoolean(index: Int, boolean: Boolean?): Unit {
            boundValues += BoundValue(index, boolean)
        }
    }

    private class AsyncUserCursor(
        private val rows: List<User>,
        private val expectedCoroutineName: String?,
    ) : SqlCursor {
        private var index: Int = -1

        override fun next(): QueryResult<Boolean> {
            return QueryResult.AsyncValue {
                assertExpectedCoroutineName(expectedCoroutineName)
                index++
                index < rows.size
            }
        }

        override fun getString(index: Int): String? {
            return when (index) {
                1 -> currentRow().name
                else -> null
            }
        }

        override fun getLong(index: Int): Long? {
            return when (index) {
                0 -> currentRow().id
                else -> null
            }
        }

        override fun getBytes(index: Int): ByteArray? = null

        override fun getDouble(index: Int): Double? = null

        override fun getBoolean(index: Int): Boolean? {
            return when (index) {
                2 -> currentRow().active
                else -> null
            }
        }

        private fun currentRow(): User {
            check(index in rows.indices) { "Cursor is not positioned on a row" }
            return rows[index]
        }
    }

    private class RecordingAsyncSchema(
        override val version: Long,
        private val events: MutableList<String>,
        private val expectedCoroutineName: String?,
    ) : SqlSchema<QueryResult.AsyncValue<Unit>> {
        override fun create(driver: SqlDriver): QueryResult.AsyncValue<Unit> {
            return QueryResult.AsyncValue {
                assertExpectedCoroutineName(expectedCoroutineName)
                events += "create"
            }
        }

        override fun migrate(
            driver: SqlDriver,
            oldVersion: Long,
            newVersion: Long,
            vararg callbacks: AfterVersion,
        ): QueryResult.AsyncValue<Unit> {
            return QueryResult.AsyncValue {
                assertExpectedCoroutineName(expectedCoroutineName)
                events += "migrate-$oldVersion-to-$newVersion-callbacks-${callbacks.size}"
                callbacks.sortedBy { callback: AfterVersion -> callback.afterVersion }
                    .forEach { callback: AfterVersion -> callback.block(driver) }
            }
        }
    }

    private companion object {
        private suspend fun assertExpectedCoroutineName(expectedCoroutineName: String?) {
            expectedCoroutineName?.let { name: String ->
                assertThat(currentCoroutineContext()[CoroutineName]?.name).isEqualTo(name)
            }
        }
    }
}
