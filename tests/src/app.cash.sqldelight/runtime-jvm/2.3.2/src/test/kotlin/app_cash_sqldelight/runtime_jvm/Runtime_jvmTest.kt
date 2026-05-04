/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.runtime_jvm

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.Query
import app.cash.sqldelight.SuspendingTransacter
import app.cash.sqldelight.SuspendingTransacterImpl
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.Closeable
import app.cash.sqldelight.db.OptimisticLockException
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.db.use
import app.cash.sqldelight.logs.LogSqliteDriver
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

public class Runtime_jvmTest {
    @Test
    fun enumColumnAdapterEncodesAndDecodesEnumNames(): Unit {
        val adapter: ColumnAdapter<AccountStatus, String> = EnumColumnAdapter()

        assertThat(adapter.encode(AccountStatus.Active)).isEqualTo("Active")
        assertThat(adapter.decode("Suspended")).isEqualTo(AccountStatus.Suspended)
        assertThatThrownBy { adapter.decode("missing") }
            .isInstanceOf(NoSuchElementException::class.java)
    }

    @Test
    fun queryResultValueAndAsyncValueExposeSynchronousAndSuspendingResults(): Unit {
        val value: QueryResult.Value<String> = QueryResult.Value("ready")
        val asyncValue: QueryResult.AsyncValue<String> = QueryResult.AsyncValue { "loaded" }

        assertThat(value.value).isEqualTo("ready")
        assertThat(runSuspend { value.await() }).isEqualTo("ready")
        assertThat(runSuspend { asyncValue.await() }).isEqualTo("loaded")
        assertThat(QueryResult.Unit.value).isEqualTo(Unit)
        assertThatThrownBy { asyncValue.value }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("asynchronous")
            .hasMessageContaining("generateAsync = true")
    }

    @Test
    fun executableQueryMapsListsSingleRowsAndEmptyResults(): Unit {
        val driver: RecordingSqlDriver = RecordingSqlDriver(
            records = listOf(
                Record(1, "Ada", 12.5, active = true, payload = byteArrayOf(1, 2)),
                Record(2, "Grace", 42.0, active = false, payload = byteArrayOf(3, 4)),
            ),
        )
        val query = Query(
            identifier = 7,
            driver = driver,
            fileName = "Users.sq",
            label = "selectAll",
            query = "SELECT id, name FROM users",
        ) { cursor: SqlCursor ->
            User(
                id = cursor.getLong(0) ?: error("id was null"),
                name = cursor.getString(1) ?: error("name was null"),
            )
        }

        assertThat(query.toString()).isEqualTo("Users.sq:selectAll")
        assertThat(query.executeAsList()).containsExactly(User(1, "Ada"), User(2, "Grace"))
        assertThat(driver.executedQueries.single()).isEqualTo(
            ExecutedQuery(7, "SELECT id, name FROM users", parameters = 0),
        )

        driver.records = listOf(Record(3, "Margaret", 7.0, active = true, payload = byteArrayOf(5)))
        assertThat(query.executeAsOne()).isEqualTo(User(3, "Margaret"))
        assertThat(query.executeAsOneOrNull()).isEqualTo(User(3, "Margaret"))

        driver.records = emptyList()
        assertThat(query.executeAsOneOrNull()).isNull()
        assertThatThrownBy { query.executeAsOne() }
            .isInstanceOf(NullPointerException::class.java)
            .hasMessageContaining("ResultSet returned null for Users.sq:selectAll")

        driver.records = listOf(
            Record(4, "Katherine", 1.0, active = true, payload = byteArrayOf(6)),
            Record(5, "Annie", 2.0, active = true, payload = byteArrayOf(7)),
        )
        assertThatThrownBy { query.executeAsOneOrNull() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("ResultSet returned more than 1 row for Users.sq:selectAll")
    }

    @Test
    fun listenableQueryRegistersAndRemovesDriverListeners(): Unit {
        val driver: RecordingSqlDriver = RecordingSqlDriver(
            records = listOf(Record(1, "Ada", 12.5, active = true, payload = byteArrayOf(1))),
        )
        val query: Query<User> = Query(
            identifier = 8,
            queryKeys = arrayOf("users", "accounts"),
            driver = driver,
            fileName = "Users.sq",
            label = "selectById",
            query = "SELECT id, name FROM users WHERE id = ?",
        ) { cursor: SqlCursor ->
            User(cursor.getLong(0) ?: 0, cursor.getString(1) ?: "")
        }
        var notifications: Int = 0
        val listener = Query.Listener { notifications++ }

        query.addListener(listener)
        driver.notifyListeners("users")
        query.removeListener(listener)
        driver.notifyListeners("accounts")

        assertThat(query.executeAsOne()).isEqualTo(User(1, "Ada"))
        assertThat(notifications).isEqualTo(1)
        assertThat(driver.addedListenerKeys).containsExactly(listOf("users", "accounts"))
        assertThat(driver.removedListenerKeys).containsExactly(listOf("users", "accounts"))
    }

    @Test
    fun rawSqlQueryFactoriesExecuteAndListenWithoutGeneratedLabels(): Unit {
        val executableSql = "SELECT id, name FROM users ORDER BY name"
        val executableDriver: RecordingSqlDriver = RecordingSqlDriver(
            records = listOf(
                Record(1, "Ada", 12.5, active = true, payload = byteArrayOf(1)),
                Record(2, "Grace", 42.0, active = false, payload = byteArrayOf(2)),
            ),
        )
        val executableQuery = Query(
            identifier = 21,
            driver = executableDriver,
            query = executableSql,
        ) { cursor: SqlCursor ->
            User(
                id = cursor.getLong(0) ?: error("id was null"),
                name = cursor.getString(1) ?: error("name was null"),
            )
        }

        val rowCount: Int = executableQuery.execute { cursor: SqlCursor ->
            var count = 0
            while (cursor.next().value) {
                count++
            }
            QueryResult.Value(count)
        }.value

        assertThat(executableQuery.toString()).isEqualTo("unknown:unknown")
        assertThat(rowCount).isEqualTo(2)
        assertThat(executableDriver.executedQueries).containsExactly(ExecutedQuery(21, executableSql, parameters = 0))

        val listenableSql = "SELECT id, name FROM users WHERE active = 1"
        val listenableDriver: RecordingSqlDriver = RecordingSqlDriver(
            records = listOf(Record(3, "Katherine", 7.0, active = true, payload = byteArrayOf(3))),
        )
        val listenableQuery: Query<User> = Query(
            identifier = 22,
            queryKeys = arrayOf("users"),
            driver = listenableDriver,
            query = listenableSql,
        ) { cursor: SqlCursor ->
            User(cursor.getLong(0) ?: 0, cursor.getString(1) ?: "")
        }
        var notifications = 0
        val listener = Query.Listener { notifications++ }

        listenableQuery.addListener(listener)
        listenableDriver.notifyListeners("users")
        listenableQuery.removeListener(listener)
        listenableDriver.notifyListeners("users")

        assertThat(listenableQuery.toString()).isEqualTo("unknown:unknown")
        assertThat(listenableQuery.executeAsOne()).isEqualTo(User(3, "Katherine"))
        assertThat(notifications).isEqualTo(1)
        assertThat(listenableDriver.addedListenerKeys).containsExactly(listOf("users"))
        assertThat(listenableDriver.removedListenerKeys).containsExactly(listOf("users"))
        assertThat(listenableDriver.executedQueries).containsExactly(ExecutedQuery(22, listenableSql, parameters = 0))
    }

    @Test
    fun sqlCursorAndPreparedStatementSupportAllPrimitiveColumnTypes(): Unit {
        val driver: RecordingSqlDriver = RecordingSqlDriver(
            records = listOf(Record(10, "Ada", 99.5, active = true, payload = byteArrayOf(9, 8, 7))),
        )

        val mapped: CursorSnapshot = driver.executeQuery(
            identifier = 11,
            sql = "SELECT * FROM users WHERE id = ? AND active = ?",
            mapper = { cursor: SqlCursor ->
                assertThat(cursor.next().value).isTrue()
                QueryResult.Value(
                    CursorSnapshot(
                        id = cursor.getLong(0),
                        name = cursor.getString(1),
                        score = cursor.getDouble(2),
                        active = cursor.getBoolean(3),
                        payload = cursor.getBytes(4),
                    ),
                )
            },
            parameters = 2,
            binders = {
                bindLong(0, 10)
                bindBoolean(1, true)
            },
        ).value

        assertThat(mapped.id).isEqualTo(10L)
        assertThat(mapped.name).isEqualTo("Ada")
        assertThat(mapped.score).isEqualTo(99.5)
        assertThat(mapped.active).isTrue()
        assertThat(mapped.payload).containsExactly(*byteArrayOf(9, 8, 7))
        assertThat(driver.boundValues).containsExactly(10L, true)
    }

    @Test
    fun transacterCommitsRollsBackAndDefersNotificationsUntilOuterCommit(): Unit {
        val driver: RecordingSqlDriver = RecordingSqlDriver()
        val transacter: RecordingTransacter = RecordingTransacter(driver)
        val events: MutableList<String> = mutableListOf()
        val listener = Query.Listener { events += "listener" }
        driver.addListener("users", listener = listener)

        val committedValue: String = transacter.transactionWithResult {
            afterCommit { events += "outer-commit" }
            afterRollback { events += "outer-rollback" }
            transacter.notifyUserTables(identifier = 100)
            assertThat(events).isEmpty()

            transaction {
                afterCommit { events += "inner-commit" }
                transacter.notifyUserTables(identifier = 101)
            }
            assertThat(events).isEmpty()
            "committed"
        }

        assertThat(committedValue).isEqualTo("committed")
        assertThat(events).containsExactly("listener", "outer-commit", "inner-commit")
        assertThat(driver.transactions).hasSize(2)
        assertThat(driver.transactions.map { transaction: RecordingTransaction -> transaction.endedSuccessfully })
            .containsExactly(true, true)
        assertThat(driver.notifiedKeys).containsExactly(listOf("users", "audit_log"))
    }

    @Test
    fun transacterRollbackRunsRollbackHooksAndReturnsRollbackValue(): Unit {
        val driver: RecordingSqlDriver = RecordingSqlDriver()
        val transacter: RecordingTransacter = RecordingTransacter(driver)
        val events: MutableList<String> = mutableListOf()

        val result: String = transacter.transactionWithResult {
            afterCommit { events += "commit" }
            afterRollback { events += "rollback" }
            rollback("fallback")
        }

        assertThat(result).isEqualTo("fallback")
        assertThat(events).containsExactly("rollback")
        assertThat(driver.transactions.single().endedSuccessfully).isFalse()
    }

    @Test
    fun transacterPropagatesFailuresAndRejectsNoEnclosingNestedTransactions(): Unit {
        val failingDriver: RecordingSqlDriver = RecordingSqlDriver()
        val failingTransacter: RecordingTransacter = RecordingTransacter(failingDriver)
        val events: MutableList<String> = mutableListOf()

        assertThatThrownBy {
            failingTransacter.transaction {
                afterRollback { events += "rollback" }
                throw IllegalArgumentException("boom")
            }
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("boom")
        assertThat(events).containsExactly("rollback")
        assertThat(failingDriver.transactions.single().endedSuccessfully).isFalse()

        val nestedDriver: RecordingSqlDriver = RecordingSqlDriver()
        val nestedTransacter: RecordingTransacter = RecordingTransacter(nestedDriver)
        assertThatThrownBy {
            nestedTransacter.transaction {
                nestedTransacter.transaction(noEnclosing = true) {
                    error("should not run")
                }
            }
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Already in a transaction")
    }

    @Test
    fun transacterUtilityMethodsCreateArgumentListsAndNotifyImmediatelyOutsideTransactions(): Unit {
        val driver: RecordingSqlDriver = RecordingSqlDriver()
        val transacter: RecordingTransacter = RecordingTransacter(driver)
        var notifications: Int = 0
        driver.addListener("users", listener = Query.Listener { notifications++ })

        transacter.notifyUserTables(identifier = 44)

        assertThat(notifications).isEqualTo(1)
        assertThat(driver.notifiedKeys).containsExactly(listOf("users", "audit_log"))
        assertThat(transacter.argumentList(0)).isEqualTo("()")
        assertThat(transacter.argumentList(1)).isEqualTo("(?)")
        assertThat(transacter.argumentList(4)).isEqualTo("(?,?,?,?)")
    }

    @Test
    fun transactionCallbacksAreConfinedToTheTransactionThread(): Unit {
        val driver: RecordingSqlDriver = RecordingSqlDriver()
        val transacter: RecordingTransacter = RecordingTransacter(driver)
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        var ownerThreadHookRan: Boolean = false

        try {
            transacter.transaction {
                val failure: Future<Throwable?> = executor.submit<Throwable?> {
                    runCatching { afterCommit { error("cross-thread hook should not be registered") } }.exceptionOrNull()
                }

                assertThat(failure.get(5, TimeUnit.SECONDS)).isInstanceOf(IllegalStateException::class.java)
                afterCommit { ownerThreadHookRan = true }
            }
        } finally {
            executor.shutdownNow()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }

        assertThat(ownerThreadHookRan).isTrue()
        assertThat(driver.transactions.single().endedSuccessfully).isTrue()
    }

    @Test
    fun suspendingTransacterUsesDispatcherAndAwaitsTransactionResults(): Unit {
        val driver: DispatchingSqlDriver = DispatchingSqlDriver()
        val transacter: RecordingSuspendingTransacter = RecordingSuspendingTransacter(driver)
        val events: MutableList<String> = mutableListOf()

        val result: String = runSuspend {
            transacter.transactionWithResult {
                afterCommit { events += "commit" }
                "async-result"
            }
        }

        assertThat(result).isEqualTo("async-result")
        assertThat(events).containsExactly("commit")
        assertThat(driver.dispatchCount).isEqualTo(1)
        assertThat(driver.transactions.single().endedSuccessfully).isTrue()
    }

    @Test
    fun sqlSchemaAfterVersionCallbacksAndOptimisticLockExceptionExposePublicState(): Unit {
        val driver: RecordingSqlDriver = RecordingSqlDriver()
        val schema = object : SqlSchema<QueryResult.Value<Unit>> {
            override val version: Long = 3

            override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
                driver.execute(null, "CREATE TABLE users", parameters = 0)
                return QueryResult.Unit
            }

            override fun migrate(
                driver: SqlDriver,
                oldVersion: Long,
                newVersion: Long,
                vararg callbacks: AfterVersion,
            ): QueryResult.Value<Unit> {
                driver.execute(null, "ALTER TABLE users ADD COLUMN active INTEGER", parameters = 0)
                callbacks
                    .filter { callback: AfterVersion -> callback.afterVersion in (oldVersion + 1)..newVersion }
                    .forEach { callback: AfterVersion -> callback.block(driver) }
                return QueryResult.Unit
            }
        }
        val migrationEvents: MutableList<String> = mutableListOf()

        schema.create(driver)
        schema.migrate(
            driver,
            1,
            schema.version,
            AfterVersion(2) { callbackDriver: SqlDriver ->
                migrationEvents += "after-2"
                callbackDriver.execute(null, "UPDATE users SET active = 1", parameters = 0)
            },
            AfterVersion(4) { _: SqlDriver -> migrationEvents += "after-4" },
        )
        val cause = IllegalStateException("write conflict")
        val exception = OptimisticLockException("stale row", cause)

        assertThat(schema.version).isEqualTo(3L)
        assertThat(migrationEvents).containsExactly("after-2")
        assertThat(driver.executedStatements.map { statement: ExecutedQuery -> statement.sql }).containsExactly(
            "CREATE TABLE users",
            "ALTER TABLE users ADD COLUMN active INTEGER",
            "UPDATE users SET active = 1",
        )
        assertThat(exception).hasMessage("stale row").hasCause(cause)
    }

    @Test
    fun logSqliteDriverLogsOperationsParametersListenersAndTransactionOutcomes(): Unit {
        val delegate: RecordingSqlDriver = RecordingSqlDriver(
            records = listOf(Record(1, "Ada", 10.0, active = true, payload = byteArrayOf(1))),
        )
        val logs: MutableList<String> = mutableListOf()
        val loggingDriver = LogSqliteDriver(delegate) { message: String -> logs += message }

        loggingDriver.execute(
            identifier = 1,
            sql = "UPDATE users SET name = ? WHERE id = ?",
            parameters = 2,
            binders = {
                bindString(0, "Ada")
                bindLong(1, 1)
            },
        )
        loggingDriver.executeQuery(
            identifier = 2,
            sql = "SELECT id FROM users",
            mapper = { cursor: SqlCursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0))
            },
            parameters = 1,
            binders = {
                bindBoolean(0, true)
            },
        )
        val listener = Query.Listener { }
        loggingDriver.addListener("users", listener = listener)
        loggingDriver.notifyListeners("users")
        loggingDriver.removeListener("users", listener = listener)

        val transacter = RecordingTransacter(loggingDriver)
        transacter.transaction { }
        transacter.transaction { rollback() }
        loggingDriver.close()

        assertThat(logs).contains(
            "EXECUTE\n UPDATE users SET name = ? WHERE id = ?",
            " [Ada, 1]",
            "QUERY\n SELECT id FROM users",
            " [true]",
            "NOTIFYING LISTENERS OF [users]",
            "TRANSACTION BEGIN",
            "TRANSACTION COMMIT",
            "TRANSACTION ROLLBACK",
            "CLOSE CONNECTION",
        )
        assertThat(logs.any { message: String -> message.contains("LISTENING TO [users]") }).isTrue()
        assertThat(delegate.closeCount).isEqualTo(1)
    }

    @Test
    fun closeableUseClosesResourcesOnSuccessAndFailure(): Unit {
        val success: TestCloseable = TestCloseable()
        val failure: TestCloseable = TestCloseable()

        val result: String = success.use { closeable: TestCloseable ->
            assertThat(closeable.closed).isFalse()
            "used"
        }

        assertThat(result).isEqualTo("used")
        assertThat(success.closed).isTrue()
        assertThatThrownBy {
            failure.use { throw IllegalStateException("body failed") }
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("body failed")
        assertThat(failure.closed).isTrue()
    }

    private enum class AccountStatus {
        Active,
        Suspended,
    }

    private data class User(
        val id: Long,
        val name: String,
    )

    private data class Record(
        val id: Long,
        val name: String,
        val score: Double,
        val active: Boolean,
        val payload: ByteArray,
    )

    private data class CursorSnapshot(
        val id: Long?,
        val name: String?,
        val score: Double?,
        val active: Boolean?,
        val payload: ByteArray?,
    )

    private data class ExecutedQuery(
        val identifier: Int?,
        val sql: String,
        val parameters: Int,
    )

    private class RecordingCursor(
        private val records: List<Record>,
    ) : SqlCursor {
        private var index: Int = -1

        override fun next(): QueryResult<Boolean> {
            index++
            return QueryResult.Value(index < records.size)
        }

        override fun getString(index: Int): String? {
            return when (index) {
                1 -> currentRecord().name
                else -> null
            }
        }

        override fun getLong(index: Int): Long? {
            return when (index) {
                0 -> currentRecord().id
                else -> null
            }
        }

        override fun getBytes(index: Int): ByteArray? {
            return when (index) {
                4 -> currentRecord().payload
                else -> null
            }
        }

        override fun getDouble(index: Int): Double? {
            return when (index) {
                2 -> currentRecord().score
                else -> null
            }
        }

        override fun getBoolean(index: Int): Boolean? {
            return when (index) {
                3 -> currentRecord().active
                else -> null
            }
        }

        private fun currentRecord(): Record {
            check(index in records.indices) { "Cursor is not positioned on a row" }
            return records[index]
        }
    }

    private class RecordingStatement : SqlPreparedStatement {
        val values: MutableList<Any?> = mutableListOf()

        override fun bindBytes(index: Int, bytes: ByteArray?): Unit {
            values += bytes
        }

        override fun bindLong(index: Int, long: Long?): Unit {
            values += long
        }

        override fun bindDouble(index: Int, double: Double?): Unit {
            values += double
        }

        override fun bindString(index: Int, string: String?): Unit {
            values += string
        }

        override fun bindBoolean(index: Int, boolean: Boolean?): Unit {
            values += boolean
        }
    }

    private open class RecordingSqlDriver(
        records: List<Record> = emptyList(),
    ) : SqlDriver {
        var records: List<Record> = records
        val executedQueries: MutableList<ExecutedQuery> = mutableListOf()
        val executedStatements: MutableList<ExecutedQuery> = mutableListOf()
        val boundValues: MutableList<Any?> = mutableListOf()
        val addedListenerKeys: MutableList<List<String>> = mutableListOf()
        val removedListenerKeys: MutableList<List<String>> = mutableListOf()
        val notifiedKeys: MutableList<List<String>> = mutableListOf()
        val transactions: MutableList<RecordingTransaction> = mutableListOf()
        var closeCount: Int = 0
            private set

        private val activeTransactions: ArrayDeque<RecordingTransaction> = ArrayDeque()
        private val listenersByKey: MutableMap<String, MutableSet<Query.Listener>> = linkedMapOf()

        override fun <R> executeQuery(
            identifier: Int?,
            sql: String,
            mapper: (SqlCursor) -> QueryResult<R>,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<R> {
            executedQueries += ExecutedQuery(identifier, sql, parameters)
            binders?.let { binder: SqlPreparedStatement.() -> Unit ->
                val statement = RecordingStatement()
                statement.binder()
                boundValues.addAll(statement.values)
            }
            return mapper(RecordingCursor(records))
        }

        override fun execute(
            identifier: Int?,
            sql: String,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<Long> {
            executedStatements += ExecutedQuery(identifier, sql, parameters)
            binders?.let { binder: SqlPreparedStatement.() -> Unit ->
                val statement = RecordingStatement()
                statement.binder()
                boundValues.addAll(statement.values)
            }
            return QueryResult.Value(1L)
        }

        override fun newTransaction(): QueryResult<Transacter.Transaction> {
            val transaction = RecordingTransaction(this, activeTransactions.peekLast())
            activeTransactions.addLast(transaction)
            transactions += transaction
            return QueryResult.Value(transaction)
        }

        override fun currentTransaction(): Transacter.Transaction? = activeTransactions.peekLast()

        override fun addListener(vararg queryKeys: String, listener: Query.Listener): Unit {
            addedListenerKeys += queryKeys.toList()
            queryKeys.forEach { key: String ->
                listenersByKey.getOrPut(key) { linkedSetOf() } += listener
            }
        }

        override fun removeListener(vararg queryKeys: String, listener: Query.Listener): Unit {
            removedListenerKeys += queryKeys.toList()
            queryKeys.forEach { key: String -> listenersByKey[key]?.remove(listener) }
        }

        override fun notifyListeners(vararg queryKeys: String): Unit {
            notifiedKeys += queryKeys.toList()
            queryKeys
                .flatMap { key: String -> listenersByKey[key].orEmpty() }
                .toSet()
                .forEach { listener: Query.Listener -> listener.queryResultsChanged() }
        }

        override fun close(): Unit {
            closeCount++
        }

        fun finish(transaction: RecordingTransaction): Unit {
            if (activeTransactions.peekLast() === transaction) {
                activeTransactions.removeLast()
            } else {
                activeTransactions.remove(transaction)
            }
        }
    }

    private class DispatchingSqlDriver : RecordingSqlDriver(), SuspendingTransacter.TransactionDispatcher {
        var dispatchCount: Int = 0
            private set

        override suspend fun <R> dispatch(transaction: suspend () -> R): R {
            dispatchCount++
            return transaction()
        }
    }

    private class RecordingTransaction(
        private val driver: RecordingSqlDriver,
        enclosing: Transacter.Transaction?,
    ) : Transacter.Transaction() {
        protected override val enclosingTransaction: Transacter.Transaction? = enclosing
        var endedSuccessfully: Boolean? = null
            private set

        protected override fun endTransaction(successful: Boolean): QueryResult<Unit> {
            endedSuccessfully = successful
            driver.finish(this)
            return QueryResult.Unit
        }
    }

    private class RecordingTransacter(driver: SqlDriver) : TransacterImpl(driver) {
        fun notifyUserTables(identifier: Int): Unit {
            notifyQueries(identifier) { table: (String) -> Unit ->
                table("users")
                table("audit_log")
            }
        }

        fun argumentList(count: Int): String = createArguments(count)
    }

    private class RecordingSuspendingTransacter(driver: SqlDriver) : SuspendingTransacterImpl(driver)

    private class TestCloseable : Closeable {
        var closed: Boolean = false
            private set

        override fun close(): Unit {
            closed = true
        }
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        val latch = CountDownLatch(1)
        var completed: Boolean = false
        var value: T? = null
        var failure: Throwable? = null
        block.startCoroutine(
            object : Continuation<T> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<T>): Unit {
                    result.fold(
                        onSuccess = { successfulValue: T -> value = successfulValue },
                        onFailure = { throwable: Throwable -> failure = throwable },
                    )
                    completed = true
                    latch.countDown()
                }
            },
        )
        check(latch.await(5, TimeUnit.SECONDS)) { "Suspending block did not complete" }
        check(completed) { "Suspending block did not resume" }
        failure?.let { throwable: Throwable -> throw throwable }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }
}
