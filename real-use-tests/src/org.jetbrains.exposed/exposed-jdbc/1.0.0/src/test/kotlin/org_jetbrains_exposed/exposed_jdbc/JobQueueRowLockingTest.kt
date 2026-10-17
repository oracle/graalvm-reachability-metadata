/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_jdbc

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Pessimistic row locking with `forUpdate`: a PostgreSQL job queue lets competing workers claim
 * batches with `FOR UPDATE OF jobs SKIP LOCKED` over a join, probes held rows with `NOWAIT`, and
 * holds `FOR SHARE` locks that block claims but not other readers.
 */
class JobQueueRowLockingTest {

    object Queues : Table("queues") {
        val name = varchar("name", 32)
        val priority = integer("priority")

        override val primaryKey = PrimaryKey(name)
    }

    object Jobs : Table("jobs") {
        val id = integer("id").autoIncrement()
        val queue = reference("queue", Queues.name)
        val payload = varchar("payload", 64)
        val status = varchar("status", 16).default(PENDING)
        val worker = varchar("worker", 32).nullable()

        override val primaryKey = PrimaryKey(id)
    }

    data class ClaimedJob(val id: Int, val status: String, val worker: String?)

    companion object {
        private const val PASSWORD = "queue-secret"
        private const val PENDING = "pending"
        private const val CLAIMED = "claimed"
        private const val LOCK_NOT_AVAILABLE = "55P03"
        private val containerName: String = "real-use-exposed-jdbc-queue-" + UUID.randomUUID()
        private val CLAIM_LOCK = ForUpdateOption.PostgreSQL.ForUpdate(ForUpdateOption.PostgreSQL.MODE.SKIP_LOCKED, Jobs)
        private val PROBE_LOCK = ForUpdateOption.PostgreSQL.ForUpdate(ForUpdateOption.PostgreSQL.MODE.NO_WAIT)
        private val REPORT_LOCK = ForUpdateOption.PostgreSQL.ForShare(ForUpdateOption.PostgreSQL.MODE.NO_WAIT)
        private var port: Int = 0

        private val jdbcUrl: String
            get() = "jdbc:postgresql://127.0.0.1:$port/postgres"

        @BeforeAll
        @JvmStatic
        fun startPostgres() {
            port = ServerSocket(0).use { it.localPort }
            docker(
                "run", "--rm", "-d", "--name", containerName,
                "-e", "POSTGRES_PASSWORD=$PASSWORD",
                "-p", "127.0.0.1:$port:5432",
                "postgres:18-alpine",
            )
            awaitConnection(TimeUnit.SECONDS.toMillis(120))
        }

        @AfterAll
        @JvmStatic
        fun stopPostgres() {
            docker("rm", "-f", containerName)
        }

        private fun docker(vararg args: String) {
            val process = ProcessBuilder(listOf("docker") + args).inheritIO().start()
            check(process.waitFor(5, TimeUnit.MINUTES)) { "docker ${args.first()} timed out" }
            check(process.exitValue() == 0) { "docker ${args.first()} failed with ${process.exitValue()}" }
        }

        /** Polls with a bounded deadline until the server accepts connections. */
        private fun awaitConnection(timeoutMillis: Long) {
            val deadline = System.currentTimeMillis() + timeoutMillis
            var lastFailure: Exception? = null
            while (System.currentTimeMillis() < deadline) {
                try {
                    DriverManager.getConnection(jdbcUrl, "postgres", PASSWORD).use { connection ->
                        connection.createStatement().use { it.execute("SELECT 1") }
                    }
                    return
                } catch (e: Exception) {
                    lastFailure = e
                    Thread.sleep(250)
                }
            }
            throw IllegalStateException("PostgreSQL did not become reachable", lastFailure)
        }
    }

    /** A lock conflict is an answer, not a transient failure, so no statement is retried. */
    private val database: Database by lazy {
        Database.connect(
            url = jdbcUrl,
            driver = "org.postgresql.Driver",
            user = "postgres",
            password = PASSWORD,
            databaseConfig = DatabaseConfig { defaultMaxAttempts = 1 },
        )
    }

    /** The highest-priority pending jobs a worker may take, skipping rows other workers hold. */
    private fun claimQuery(size: Int): Query =
        (Jobs innerJoin Queues)
            .select(Jobs.id)
            .where { Jobs.status eq PENDING }
            .orderBy(Queues.priority to SortOrder.DESC, Jobs.id to SortOrder.ASC)
            .limit(size)
            .forUpdate(CLAIM_LOCK)

    /** Claims a batch and runs [whileHolding] before the claiming transaction commits. */
    private fun claimBatch(worker: String, size: Int, whileHolding: () -> Unit = {}): List<Int> =
        transaction(database) {
            val ids: List<Int> = claimQuery(size).map { it[Jobs.id] }
            if (ids.isNotEmpty()) {
                Jobs.update({ Jobs.id inList ids }) {
                    it[status] = CLAIMED
                    it[Jobs.worker] = worker
                }
            }
            whileHolding()
            ids
        }

    /** Reads one job only if its row lock can be taken immediately. */
    private fun probeJob(id: Int): ClaimedJob = transaction(database) {
        Jobs.selectAll().where { Jobs.id eq id }.forUpdate(PROBE_LOCK).single().let {
            ClaimedJob(it[Jobs.id], it[Jobs.status], it[Jobs.worker])
        }
    }

    private fun CountDownLatch.awaitBounded() {
        check(await(60, TimeUnit.SECONDS)) { "latch was not released in time" }
    }

    @Test
    fun workersClaimDisjointBatchesAndProbeHeldRowsWithoutWaiting() {
        transaction(database) {
            SchemaUtils.create(Queues, Jobs)
            Queues.insert {
                it[name] = "billing"
                it[priority] = 5
            }
            Queues.insert {
                it[name] = "email"
                it[priority] = 1
            }
            Jobs.batchInsert(
                listOf("email" to "welcome", "billing" to "invoice-1", "email" to "digest", "billing" to "invoice-2", "email" to "reminder"),
            ) { (queue: String, payload: String) ->
                this[Jobs.queue] = queue
                this[Jobs.payload] = payload
            }
        }

        val claimSql: String = transaction(database) { claimQuery(2).prepareSQL(this, prepared = false) }
        assertThat(claimSql).endsWith("FOR UPDATE OF jobs SKIP LOCKED")
        val plainSql: String = transaction(database) { claimQuery(2).notForUpdate().prepareSQL(this, prepared = false) }
        assertThat(plainSql).doesNotContain("FOR UPDATE")

        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        try {
            val holding = CountDownLatch(1)
            val release = CountDownLatch(1)
            val workerA: Future<List<Int>> = executor.submit<List<Int>> {
                claimBatch("worker-a", 2) {
                    holding.countDown()
                    release.awaitBounded()
                }
            }
            holding.awaitBounded()

            // Worker A holds both billing jobs, so worker B moves on to the oldest email jobs.
            assertThat(claimBatch("worker-b", 2)).containsExactly(1, 3)

            val conflict: Throwable = catchThrowable { probeJob(2) }
            assertThat(conflict).isInstanceOf(ExposedSQLException::class.java)
            assertThat((conflict as ExposedSQLException).sqlState).isEqualTo(LOCK_NOT_AVAILABLE)

            // `OF jobs` locks only job rows: the joined queue row stays free for an admin to lock.
            val billingPriority: Int = transaction(database) {
                Queues.selectAll().where { Queues.name eq "billing" }.forUpdate(PROBE_LOCK).single()[Queues.priority]
            }
            assertThat(billingPriority).isEqualTo(5)

            release.countDown()
            assertThat(workerA.get(60, TimeUnit.SECONDS)).containsExactly(2, 4)
            assertThat(probeJob(2)).isEqualTo(ClaimedJob(2, CLAIMED, "worker-a"))
            assertThat(probeJob(3)).isEqualTo(ClaimedJob(3, CLAIMED, "worker-b"))

            val sharing = CountDownLatch(1)
            val finishReport = CountDownLatch(1)
            val report: Future<String> = executor.submit<String> {
                transaction(database) {
                    val payload: String = Jobs.selectAll().where { Jobs.id eq 5 }.forUpdate(REPORT_LOCK).single()[Jobs.payload]
                    sharing.countDown()
                    finishReport.awaitBounded()
                    payload
                }
            }
            sharing.awaitBounded()

            // A shared lock admits another reader but keeps the last pending job from being claimed.
            val sharedRead: String = transaction(database) {
                Jobs.selectAll().where { Jobs.id eq 5 }.forUpdate(REPORT_LOCK).single()[Jobs.status]
            }
            assertThat(sharedRead).isEqualTo(PENDING)
            assertThat(claimBatch("worker-b", 2)).isEmpty()

            finishReport.countDown()
            assertThat(report.get(60, TimeUnit.SECONDS)).isEqualTo("reminder")
            assertThat(claimBatch("worker-b", 2)).containsExactly(5)
        } finally {
            executor.shutdownNow()
        }

        val board: Map<String?, List<Int>> = transaction(database) {
            Jobs.selectAll().where { Jobs.status eq CLAIMED }.orderBy(Jobs.id)
                .map { it[Jobs.worker] to it[Jobs.id] }
                .groupBy({ it.first }, { it.second })
        }
        assertThat(board).isEqualTo(mapOf("worker-a" to listOf(2, 4), "worker-b" to listOf(1, 3, 5)))
    }
}
