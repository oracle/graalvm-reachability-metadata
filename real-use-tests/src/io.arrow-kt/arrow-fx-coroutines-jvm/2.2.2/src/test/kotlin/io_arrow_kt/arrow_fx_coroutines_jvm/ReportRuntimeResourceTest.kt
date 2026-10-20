/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_fx_coroutines_jvm

import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.ManagedCoroutineScope
import arrow.fx.coroutines.ManagedSupervisorScope
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.allocate
import arrow.fx.coroutines.asFlow
import arrow.fx.coroutines.closeable
import arrow.fx.coroutines.executor
import arrow.fx.coroutines.fixedThreadPoolContext
import arrow.fx.coroutines.resource
import arrow.fx.coroutines.resourceScope
import arrow.fx.coroutines.singleThreadContext
import java.io.Closeable
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises Arrow's runtime `Resource` extensions: managed thread-pool dispatchers, managed
 * coroutine scopes for background jobs, and the `allocate` and `asFlow` bridges out of a scope.
 *
 * A report service renders pages on dispatchers it owns for the lifetime of a `resourceScope`,
 * flushes audit entries from a background job the scope joins on release, hands its runtime to a
 * non-suspending host through `allocate`, and streams catalog snapshots opened per collection.
 */
public class ReportRuntimeResourceTest {

    /** A catalog snapshot the report reads from, closed when its resource is released. */
    public class CatalogSnapshot(
        public val version: Int,
        private val events: MutableList<String>,
    ) : Closeable {

        public val closed: AtomicReference<Boolean> = AtomicReference(false)

        init {
            events.add("open v$version")
        }

        public fun skus(): List<String> {
            check(!closed.get()) { "snapshot v$version is closed" }
            events.add("read v$version")
            return listOf("SKU-$version-A", "SKU-$version-B")
        }

        override fun close() {
            closed.set(true)
            events.add("close v$version")
        }
    }

    /** The rendering runtime a report service needs while it is running. */
    public class RenderRuntime(
        public val renderer: ExecutorCoroutineDispatcher,
        public val snapshot: CatalogSnapshot,
    ) {

        public suspend fun renderPage(page: Int): String =
            withContext(renderer) { "page $page of ${snapshot.skus().size} skus" }
    }

    /** A host with a blocking start/stop lifecycle that owns a `RenderRuntime` between them. */
    @OptIn(DelicateCoroutinesApi::class)
    public class ReportHost(private val runtime: Resource<RenderRuntime>) {

        private var started: Pair<RenderRuntime, suspend (ExitCase) -> Unit>? = null

        public fun start(): RenderRuntime = runBlocking {
            check(started == null) { "host already started" }
            val allocated: Pair<RenderRuntime, suspend (ExitCase) -> Unit> = runtime.allocate()
            started = allocated
            allocated.first
        }

        public fun render(page: Int): String = runBlocking {
            val active: RenderRuntime = checkNotNull(started) { "host not started" }.first
            active.renderPage(page)
        }

        public fun stop() {
            val active: Pair<RenderRuntime, suspend (ExitCase) -> Unit> =
                checkNotNull(started) { "host not started" }
            started = null
            runBlocking { active.second(ExitCase.Completed) }
        }
    }

    /** The current worker thread's name without the coroutine debug-mode suffix. */
    private fun workerName(): String = Thread.currentThread().name.substringBefore(" @")

    @Test
    fun rendersOnOwnedDispatchersAndShutsThemDownOnRelease(): Unit = runBlocking {
        val renderThreads: MutableSet<String> = ConcurrentHashMap.newKeySet()
        val exportThreadCounter: AtomicInteger = AtomicInteger()
        val exportFactory: ThreadFactory = ThreadFactory { task: Runnable ->
            Thread(task, "report-export-${exportThreadCounter.incrementAndGet()}")
        }
        lateinit var renderDispatcher: ExecutorCoroutineDispatcher
        lateinit var archiveDispatcher: ExecutorCoroutineDispatcher
        lateinit var exportService: ExecutorService

        val archived: List<String> = resourceScope {
            renderDispatcher = fixedThreadPoolContext(2, "report-render")
            archiveDispatcher = singleThreadContext("report-archive")
            val exportDispatcher: ExecutorCoroutineDispatcher = executor {
                Executors.newFixedThreadPool(2, exportFactory).also { exportService = it }
            }

            val pages: List<String> = coroutineScope {
                (1..6).map { page: Int ->
                    async(renderDispatcher) {
                        renderThreads.add(workerName())
                        "invoice-page-$page"
                    }
                }.awaitAll()
            }
            val archiveThread: String = withContext(archiveDispatcher) { workerName() }
            val exportThread: String = withContext(exportDispatcher) { workerName() }

            assertEquals("report-archive", archiveThread)
            assertTrue(exportThread.startsWith("report-export-"), exportThread)
            assertFalse((renderDispatcher.executor as ExecutorService).isShutdown)
            assertFalse(exportService.isShutdown)
            pages
        }

        assertEquals((1..6).map { "invoice-page-$it" }, archived)
        assertTrue(renderThreads.isNotEmpty())
        assertTrue(renderThreads.all { it.startsWith("report-render") }, renderThreads.toString())
        assertTrue((renderDispatcher.executor as ExecutorService).isShutdown)
        assertTrue((archiveDispatcher.executor as ExecutorService).isShutdown)
        assertTrue(exportService.isTerminated)
    }

    @Test
    fun managedScopeJoinsBackgroundFlushBeforeReleaseCompletes(): Unit = runBlocking {
        val flushed: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val releases: MutableList<String> = Collections.synchronizedList(mutableListOf())
        lateinit var flusher: Job

        resourceScope {
            onRelease { releases.add("background joined with ${flushed.size} entries") }
            val background = ManagedCoroutineScope(Dispatchers.Default)
            val auditQueue: Channel<String> = Channel(Channel.UNLIMITED)
            onRelease { exitCase: ExitCase ->
                releases.add("queue closed on ${exitCase::class.simpleName}")
                auditQueue.close()
            }

            flusher = background.launch {
                for (entry: String in auditQueue) {
                    flushed.add(entry)
                }
            }
            auditQueue.send("render invoice-1")
            auditQueue.send("render invoice-2")
            auditQueue.send("archive invoice-1")
        }

        assertTrue(flusher.isCompleted)
        assertFalse(flusher.isCancelled)
        assertEquals(listOf("render invoice-1", "render invoice-2", "archive invoice-1"), flushed)
        assertEquals(
            listOf("queue closed on Completed", "background joined with 3 entries"),
            releases,
        )
    }

    @Test
    fun supervisorScopeIsolatesFailedJobsAndCancelsSurvivorsWhenTheScopeFails(): Unit =
        runBlocking {
            val reportedFailures: MutableList<String> = Collections.synchronizedList(mutableListOf())
            val handler = CoroutineExceptionHandler { _, failure: Throwable ->
                reportedFailures.add(failure.message ?: failure::class.java.name)
            }
            val heartbeatStarted: CompletableDeferred<Unit> = CompletableDeferred()
            val heartbeatEnded: CompletableDeferred<Throwable?> = CompletableDeferred()
            lateinit var heartbeat: Job
            lateinit var thumbnailer: Job

            val failure: IllegalStateException = assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    resourceScope {
                        val workers = ManagedSupervisorScope(Dispatchers.Default + handler)
                        heartbeat = workers.launch {
                            try {
                                heartbeatStarted.complete(Unit)
                                awaitCancellation()
                            } catch (cancelled: Throwable) {
                                heartbeatEnded.complete(cancelled)
                                throw cancelled
                            }
                        }
                        thumbnailer = workers.launch {
                            error("thumbnail renderer crashed")
                        }

                        withTimeout(10_000) {
                            thumbnailer.join()
                            heartbeatStarted.await()
                        }
                        assertTrue(heartbeat.isActive, "sibling of a failed job keeps running")
                        error("report archive unavailable")
                    }
                }
            }

            assertEquals("report archive unavailable", failure.message)
            assertEquals(listOf("thumbnail renderer crashed"), reportedFailures)
            assertTrue(heartbeat.isCancelled)
            assertTrue(heartbeat.isCompleted)
            assertTrue(heartbeatEnded.isCompleted)
            assertTrue(heartbeatEnded.await() is CancellationException)
        }

    @Test
    fun blockingHostOwnsAnAllocatedRuntimeUntilItStops(): Unit {
        val events: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val releasedWith: MutableList<ExitCase> = Collections.synchronizedList(mutableListOf())
        val runtime: Resource<RenderRuntime> = resource {
            val renderer: ExecutorCoroutineDispatcher = singleThreadContext("hosted-render")
            val snapshot: CatalogSnapshot = closeable { CatalogSnapshot(7, events) }
            onRelease { exitCase: ExitCase -> releasedWith.add(exitCase) }
            RenderRuntime(renderer, snapshot)
        }
        val host = ReportHost(runtime)

        val started: RenderRuntime = host.start()
        val first: String = host.render(1)
        val second: String = host.render(2)
        assertFalse(started.snapshot.closed.get())
        assertFalse((started.renderer.executor as ExecutorService).isShutdown)
        host.stop()

        assertEquals("page 1 of 2 skus", first)
        assertEquals("page 2 of 2 skus", second)
        assertEquals(listOf<ExitCase>(ExitCase.Completed), releasedWith)
        assertTrue(started.snapshot.closed.get())
        assertTrue((started.renderer.executor as ExecutorService).isShutdown)
        assertEquals(listOf("open v7", "read v7", "read v7", "close v7"), events)
        assertThrows(IllegalStateException::class.java) { host.render(3) }
    }

    @Test
    fun snapshotFlowOpensAFreshResourcePerCollection(): Unit = runBlocking {
        val events: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val versions: AtomicInteger = AtomicInteger()
        val snapshots: Resource<CatalogSnapshot> = resource {
            closeable { CatalogSnapshot(versions.incrementAndGet(), events) }
        }

        val firstReport: List<List<String>> = snapshots.asFlow().map { it.skus() }.toList()
        val secondReport: List<List<String>> = snapshots.asFlow().map { it.skus() }.toList()

        assertEquals(listOf(listOf("SKU-1-A", "SKU-1-B")), firstReport)
        assertEquals(listOf(listOf("SKU-2-A", "SKU-2-B")), secondReport)
        assertEquals(
            listOf("open v1", "read v1", "close v1", "open v2", "read v2", "close v2"),
            events,
        )
    }
}
