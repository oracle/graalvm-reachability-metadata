/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.coroutines_extensions_jvm

import app.cash.sqldelight.Query
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneNotNull
import app.cash.sqldelight.coroutines.mapToOneOrDefault
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class Coroutines_extensions_jvmTest {
    @Test
    fun asFlowEmitsInitialQueryAndRemovesListenerAfterCancellation(): Unit = runBlocking {
        withTimeout(5_000) {
            val query: MutableUserQuery = MutableUserQuery(listOf(User(1, "Ada")))

            val emittedQueries: List<Query<User>> = query.asFlow().take(1).toList()

            assertThat(emittedQueries).containsExactly(query)
            assertThat(query.listenerCount).isZero()
            assertThat(query.addedListenerCount).isEqualTo(1)
            assertThat(query.removedListenerCount).isEqualTo(1)
        }
    }

    @Test
    fun asFlowEmitsMappedListsForSubsequentQueryNotifications(): Unit = runBlocking {
        withTimeout(5_000) {
            val query: MutableUserQuery = MutableUserQuery(listOf(User(1, "Ada")))
            val snapshots: MutableList<List<User>> = mutableListOf()

            query.asFlow()
                .mapToList(Dispatchers.Unconfined)
                .onEach { snapshot: List<User> ->
                    snapshots += snapshot
                    when (snapshots.size) {
                        1 -> query.replaceRows(User(2, "Grace"))
                        2 -> query.replaceRows(User(3, "Linus"), User(4, "Margaret"))
                    }
                }
                .take(3)
                .collect()

            assertThat(snapshots).containsExactly(
                listOf(User(1, "Ada")),
                listOf(User(2, "Grace")),
                listOf(User(3, "Linus"), User(4, "Margaret")),
            )
            assertThat(query.listenerCount).isZero()
        }
    }

    @Test
    fun asFlowIsColdAndReusableAcrossCollections(): Unit = runBlocking {
        withTimeout(5_000) {
            val query: MutableUserQuery = MutableUserQuery(listOf(User(1, "Ada")))
            val usersFlow: Flow<List<User>> = query.asFlow().mapToList(Dispatchers.Unconfined)

            assertThat(query.addedListenerCount).isZero()
            assertThat(query.executeCount).isZero()

            query.replaceRows(User(2, "Grace"))
            val firstSnapshot: List<User> = usersFlow.take(1).single()

            query.replaceRows(User(3, "Linus"), User(4, "Margaret"))
            val secondSnapshot: List<User> = usersFlow.take(1).single()

            assertThat(firstSnapshot).containsExactly(User(2, "Grace"))
            assertThat(secondSnapshot).containsExactly(User(3, "Linus"), User(4, "Margaret"))
            assertThat(query.addedListenerCount).isEqualTo(2)
            assertThat(query.removedListenerCount).isEqualTo(2)
            assertThat(query.listenerCount).isZero()
            assertThat(query.executeCount).isEqualTo(2)
        }
    }

    @Test
    fun mapToOneReturnsTheOnlyRow(): Unit = runBlocking {
        withTimeout(5_000) {
            val query: MutableUserQuery = MutableUserQuery(listOf(User(1, "Ada")))

            val user: User = flowOf(query).mapToOne(Dispatchers.Unconfined).single()

            assertThat(user).isEqualTo(User(1, "Ada"))
            assertThat(query.executeCount).isEqualTo(1)
        }
    }

    @Test
    fun mapToOneOrNullAndDefaultHandleEmptyResults(): Unit = runBlocking {
        withTimeout(5_000) {
            val emptyQuery: MutableUserQuery = MutableUserQuery(emptyList())
            val defaultUser: User = User(99, "default")

            val nullableUser: User? = flowOf(emptyQuery).mapToOneOrNull(Dispatchers.Unconfined).single()
            val defaultedUser: User = flowOf(emptyQuery)
                .mapToOneOrDefault(defaultUser, Dispatchers.Unconfined)
                .single()

            assertThat(nullableUser).isNull()
            assertThat(defaultedUser).isEqualTo(defaultUser)
            assertThat(emptyQuery.executeCount).isEqualTo(2)
        }
    }

    @Test
    fun mapToOneOrNullAndDefaultReturnExistingRows(): Unit = runBlocking {
        withTimeout(5_000) {
            val query: MutableUserQuery = MutableUserQuery(listOf(User(1, "Ada")))
            val defaultUser: User = User(99, "default")

            val nullableUser: User? = flowOf(query).mapToOneOrNull(Dispatchers.Unconfined).single()
            val defaultedUser: User = flowOf(query)
                .mapToOneOrDefault(defaultUser, Dispatchers.Unconfined)
                .single()

            assertThat(nullableUser).isEqualTo(User(1, "Ada"))
            assertThat(defaultedUser).isEqualTo(User(1, "Ada"))
            assertThat(defaultedUser).isNotEqualTo(defaultUser)
            assertThat(query.executeCount).isEqualTo(2)
        }
    }

    @Test
    fun mapToOneNotNullFiltersEmptyResults(): Unit = runBlocking {
        withTimeout(5_000) {
            val emptyQuery: MutableUserQuery = MutableUserQuery(emptyList())
            val firstQuery: MutableUserQuery = MutableUserQuery(listOf(User(1, "Ada")))
            val secondQuery: MutableUserQuery = MutableUserQuery(listOf(User(2, "Grace")))

            val users: List<User> = flowOf(emptyQuery, firstQuery, secondQuery, emptyQuery)
                .mapToOneNotNull(Dispatchers.Unconfined)
                .toList()

            assertThat(users).containsExactly(User(1, "Ada"), User(2, "Grace"))
        }
    }

    @Test
    fun mapToListSupportsAsyncQueryResultsAndRunsInProvidedCoroutineContext(): Unit = runBlocking {
        withTimeout(5_000) {
            val query: MutableUserQuery = MutableUserQuery(
                rows = listOf(User(1, "Ada"), User(2, "Grace")),
                asyncCursor = true,
                expectedCoroutineName = "sqldelight-map-context",
            )

            val users: List<User> = flowOf(query)
                .mapToList(Dispatchers.Unconfined + CoroutineName("sqldelight-map-context"))
                .single()

            assertThat(users).containsExactly(User(1, "Ada"), User(2, "Grace"))
            assertThat(query.executeCount).isEqualTo(1)
        }
    }

    @Test
    fun mapToOnePropagatesEmptyAndMultipleRowErrors(): Unit = runBlocking {
        withTimeout(5_000) {
            val emptyQuery: MutableUserQuery = MutableUserQuery(emptyList())
            val multipleRowsQuery: MutableUserQuery = MutableUserQuery(
                listOf(User(1, "Ada"), User(2, "Grace")),
            )

            val emptyFailure: Throwable = failureFrom {
                flowOf(emptyQuery).mapToOne(Dispatchers.Unconfined).single()
            }
            val multipleRowsFailure: Throwable = failureFrom {
                flowOf(multipleRowsQuery).mapToOne(Dispatchers.Unconfined).single()
            }

            assertThat(emptyFailure)
                .isInstanceOf(NullPointerException::class.java)
                .hasMessageContaining("ResultSet returned null")
            assertThat(multipleRowsFailure)
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("ResultSet returned more than 1 row")
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
    )

    private class MutableUserQuery(
        rows: List<User>,
        private val asyncCursor: Boolean = false,
        private val expectedCoroutineName: String? = null,
    ) : Query<User>({ cursor: SqlCursor ->
        User(
            id = cursor.getLong(0) ?: error("id column was null"),
            name = cursor.getString(1) ?: error("name column was null"),
        )
    }) {
        private val listeners: LinkedHashSet<Listener> = linkedSetOf()
        private var currentRows: List<User> = rows

        var addedListenerCount: Int = 0
            private set

        var removedListenerCount: Int = 0
            private set

        var executeCount: Int = 0
            private set

        val listenerCount: Int
            get() = listeners.size

        override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
            executeCount++
            return mapper(UserCursor(currentRows, asyncCursor, expectedCoroutineName))
        }

        override fun addListener(listener: Listener): Unit {
            addedListenerCount++
            listeners += listener
        }

        override fun removeListener(listener: Listener): Unit {
            removedListenerCount++
            listeners -= listener
        }

        fun replaceRows(vararg rows: User): Unit {
            currentRows = rows.toList()
            listeners.toList().forEach { listener: Listener -> listener.queryResultsChanged() }
        }
    }

    private class UserCursor(
        private val rows: List<User>,
        private val asyncCursor: Boolean,
        private val expectedCoroutineName: String?,
    ) : SqlCursor {
        private var index: Int = -1

        override fun next(): QueryResult<Boolean> {
            return if (asyncCursor) {
                QueryResult.AsyncValue {
                    expectedCoroutineName?.let { name: String ->
                        assertThat(currentCoroutineContext()[CoroutineName]?.name).isEqualTo(name)
                    }
                    advance()
                }
            } else {
                QueryResult.Value(advance())
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

        override fun getBoolean(index: Int): Boolean? = null

        private fun advance(): Boolean {
            index++
            return index < rows.size
        }

        private fun currentRow(): User {
            check(index in rows.indices) { "Cursor is not positioned on a row" }
            return rows[index]
        }
    }
}
