/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_fx_coroutines_jvm

import arrow.core.Either
import arrow.fx.coroutines.CountDownLatch
import arrow.fx.coroutines.CyclicBarrier
import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.Race3
import arrow.fx.coroutines.asFlow
import arrow.fx.coroutines.await.ExperimentalAwaitAllApi
import arrow.fx.coroutines.await.awaitAll as arrowAwaitAll
import arrow.fx.coroutines.autoCloseable
import arrow.fx.coroutines.bracketCase
import arrow.fx.coroutines.closeable
import arrow.fx.coroutines.fixedRate
import arrow.fx.coroutines.guaranteeCase
import arrow.fx.coroutines.mapIndexed
import arrow.fx.coroutines.metered
import arrow.fx.coroutines.parMap
import arrow.fx.coroutines.parMapNotNull
import arrow.fx.coroutines.parMapNotNullUnordered
import arrow.fx.coroutines.parMapOrAccumulate
import arrow.fx.coroutines.parZip
import arrow.fx.coroutines.raceN
import arrow.fx.coroutines.repeat
import arrow.fx.coroutines.resource
import arrow.fx.coroutines.resourceScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@OptIn(
    ExperimentalCoroutinesApi::class,
    ExperimentalAwaitAllApi::class,
    ExperimentalTime::class,
    FlowPreview::class,
)
public class ArrowFxCoroutinesJvmTest {
    @Test
    fun iterableParallelMappingPreservesOrderFiltersNullsAndLimitsConcurrency(): Unit = runBlocking {
        withTimeout(2_000) {
            val activeTasks = AtomicInteger(0)
            val maxActiveTasks = AtomicInteger(0)
            val input = (1..8).toList()

            val doubled = input.parMap(concurrency = 2) { value ->
                val active = activeTasks.incrementAndGet()
                maxActiveTasks.updateAndGet { current -> maxOf(current, active) }
                try {
                    delay(10)
                    value * 2
                } finally {
                    activeTasks.decrementAndGet()
                }
            }
            val evenLabels = input.parMapNotNull(concurrency = 3) { value ->
                value.takeIf { it % 2 == 0 }?.let { "even-$it" }
            }

            assertThat(doubled).containsExactly(2, 4, 6, 8, 10, 12, 14, 16)
            assertThat(evenLabels).containsExactly("even-2", "even-4", "even-6", "even-8")
            assertThat(maxActiveTasks.get()).isLessThanOrEqualTo(2)
        }
    }

    @Test
    fun parMapOrAccumulateCombinesRaisedErrorsAcrossParallelTasks(): Unit = runBlocking {
        withTimeout(2_000) {
            val result = listOf(1, 2, 3, 4).parMapOrAccumulate(
                context = Dispatchers.Default,
                concurrency = 2,
                combine = { left: String, right: String -> "$left,$right" },
            ) { value ->
                delay(10)
                if (value % 2 == 0) {
                    "even-$value"
                } else {
                    raise("odd-$value")
                }
            }

            assertThat(result).isEqualTo(Either.Left("odd-1,odd-3"))
        }
    }

    @Test
    fun parZipRunsSuspendingComputationsInParallelAndCombinesResults(): Unit = runBlocking {
        withTimeout(2_000) {
            val firstStarted = CompletableDeferred<Unit>()
            val secondStarted = CompletableDeferred<Unit>()

            val result = parZip(
                Dispatchers.Default,
                {
                    firstStarted.complete(Unit)
                    secondStarted.await()
                    "left"
                },
                {
                    secondStarted.complete(Unit)
                    firstStarted.await()
                    "right"
                },
            ) { left, right -> "$left+$right" }

            assertThat(result).isEqualTo("left+right")
        }
    }

    @Test
    fun flowOperatorsMapInParallelRepeatIndexAndMeterValues(): Unit = runBlocking {
        withTimeout(2_000) {
            val ordered = flowOf(1, 2, 3)
                .parMap(concurrency = 2) { value ->
                    delay((4 - value) * 10L)
                    value * 10
                }
                .toList()
            val unorderedNonNull = flowOf(1, 2, 3, 4, 5, 6)
                .parMapNotNullUnordered(concurrency = 3) { value ->
                    value.takeIf { it % 2 == 0 }?.times(10)
                }
                .toList()
            val repeated = flowOf("a", "b").repeat().take(5).toList()
            val indexed = flowOf("alpha", "beta", "gamma")
                .mapIndexed { index, value -> "$index:$value" }
                .toList()
            val metered = flowOf(7, 8, 9).metered(Duration.ZERO).toList()
            val ticks = fixedRate(Duration.ZERO).take(3).toList()

            assertThat(ordered).containsExactly(10, 20, 30)
            assertThat(unorderedNonNull).containsExactlyInAnyOrder(20, 40, 60)
            assertThat(repeated).containsExactly("a", "b", "a", "b", "a")
            assertThat(indexed).containsExactly("0:alpha", "1:beta", "2:gamma")
            assertThat(metered).containsExactly(7, 8, 9)
            assertThat(ticks).hasSize(3)
        }
    }

    @Test
    fun racesReturnTheWinningBranchAndCancelLosers(): Unit = runBlocking {
        withTimeout(2_000) {
            val loserExit = CompletableDeferred<ExitCase>()
            val twoWayResult = raceN(
                Dispatchers.Default,
                {
                    guaranteeCase({ awaitCancellation() }) { exitCase ->
                        loserExit.complete(exitCase)
                    }
                },
                { "winner" },
            )

            assertThat(twoWayResult).isEqualTo(Either.Right("winner"))
            assertThat(loserExit.await()).isInstanceOf(ExitCase.Cancelled::class.java)

            val cancelledLosers = AtomicInteger(0)
            val threeWayResult = raceN(
                Dispatchers.Default,
                {
                    guaranteeCase({ awaitCancellation() }) {
                        cancelledLosers.incrementAndGet()
                    }
                },
                {
                    guaranteeCase({ awaitCancellation() }) {
                        cancelledLosers.incrementAndGet()
                    }
                },
                { "third" },
            )

            assertThat(threeWayResult).isEqualTo(Race3.Third("third"))
            assertThat(cancelledLosers.get()).isEqualTo(2)
        }
    }

    @Test
    fun bracketCaseReportsCompletionAndFailureToFinalizers(): Unit = runBlocking {
        withTimeout(2_000) {
            val completedEvents = mutableListOf<String>()
            val value = bracketCase(
                acquire = {
                    completedEvents += "acquire"
                    "handle"
                },
                use = { handle ->
                    completedEvents += "use:$handle"
                    handle.length
                },
                release = { handle, exitCase ->
                    completedEvents += "release:$handle:${exitCase.label()}"
                },
            )

            assertThat(value).isEqualTo(6)
            assertThat(completedEvents).containsExactly("acquire", "use:handle", "release:handle:completed")

            val failure = IllegalStateException("boom")
            val failureEvents = mutableListOf<String>()
            val thrown = runCatching {
                bracketCase(
                    acquire = { "failing-handle" },
                    use = { _: String -> throw failure },
                    release = { handle, exitCase ->
                        failureEvents += "release:$handle:${exitCase.label()}"
                    },
                )
            }.exceptionOrNull()

            assertThat(thrown).isSameAs(failure)
            assertThat(failureEvents).containsExactly("release:failing-handle:failure:boom")
        }
    }

    @Test
    fun resourcesReleaseInReverseOrderAndCloseJvmResources(): Unit = runBlocking {
        withTimeout(2_000) {
            val releaseEvents = mutableListOf<String>()
            val result = resourceScope {
                val first = install({ "first" }) { name, exitCase ->
                    releaseEvents += "release:$name:${exitCase.label()}"
                }
                val second = resource({ "second" }) { name, exitCase ->
                    releaseEvents += "release:$name:${exitCase.label()}"
                }.bind()
                onRelease { exitCase -> releaseEvents += "scope:${exitCase.label()}" }
                "$first+$second"
            }

            assertThat(result).isEqualTo("first+second")
            assertThat(releaseEvents).containsExactly(
                "scope:completed",
                "release:second:completed",
                "release:first:completed",
            )

            var closeable: TrackedCloseable? = null
            var autoCloseable: TrackedAutoCloseable? = null
            resourceScope {
                val managedCloseable = closeable {
                    TrackedCloseable().also { closeable = it }
                }
                val managedAutoCloseable = autoCloseable {
                    TrackedAutoCloseable().also { autoCloseable = it }
                }
                assertThat(managedCloseable.closed).isFalse()
                assertThat(managedAutoCloseable.closed).isFalse()
            }

            assertThat(closeable?.closed).isTrue()
            assertThat(autoCloseable?.closed).isTrue()

            val flowReleases = AtomicInteger(0)
            val values = resource({ "flow-value" }) { _, _ -> flowReleases.incrementAndGet() }
                .asFlow()
                .toList()
            assertThat(values).containsExactly("flow-value")
            assertThat(flowReleases.get()).isEqualTo(1)
        }
    }

    @Test
    fun countDownLatchSuspendsUntilTheCountReachesZero(): Unit = runBlocking {
        assertThatThrownBy { CountDownLatch(0) }
            .isInstanceOf(IllegalArgumentException::class.java)

        withTimeout(2_000) {
            val latch = CountDownLatch(2)
            val completed = CompletableDeferred<Unit>()
            val waiter = async {
                latch.await()
                completed.complete(Unit)
            }

            assertThat(latch.count()).isEqualTo(2)
            latch.countDown()
            assertThat(latch.count()).isEqualTo(1)
            assertThat(withTimeoutOrNull(50) { completed.await() }).isNull()

            latch.countDown()
            completed.await()
            waiter.await()
            latch.countDown()
            assertThat(latch.count()).isZero()
        }
    }

    @Test
    fun cyclicBarrierReleasesAwaitersAndCanBeReused(): Unit = runBlocking {
        assertThatThrownBy { CyclicBarrier(0) }
            .isInstanceOf(IllegalArgumentException::class.java)

        withTimeout(2_000) {
            val cycles = AtomicInteger(0)
            val barrier = CyclicBarrier(capacity = 3) { cycles.incrementAndGet() }

            val firstCycle = (1..3).map { participant ->
                async {
                    barrier.await()
                    "first-$participant"
                }
            }.awaitAll()
            val secondCycle = (1..3).map { participant ->
                async {
                    barrier.await()
                    "second-$participant"
                }
            }.awaitAll()

            assertThat(firstCycle).containsExactlyInAnyOrder("first-1", "first-2", "first-3")
            assertThat(secondCycle).containsExactlyInAnyOrder("second-1", "second-2", "second-3")
            assertThat(cycles.get()).isEqualTo(2)
        }
    }

    @Test
    fun awaitAllScopeAwaitingOneDeferredAwaitsRegisteredSiblings(): Unit = runBlocking {
        withTimeout(2_000) {
            val slowGate = CompletableDeferred<Unit>()
            val fastIsAboutToAwait = CompletableDeferred<Unit>()
            val result = async {
                arrowAwaitAll {
                    val fast = async { "fast" }
                    async {
                        slowGate.await()
                        "slow"
                    }
                    fastIsAboutToAwait.complete(Unit)
                    fast.await()
                }
            }

            fastIsAboutToAwait.await()
            assertThat(withTimeoutOrNull(50) { result.await() }).isNull()
            slowGate.complete(Unit)
            assertThat(result.await()).isEqualTo("fast")
        }
    }

    private fun ExitCase.label(): String = when (this) {
        ExitCase.Completed -> "completed"
        is ExitCase.Cancelled -> "cancelled"
        is ExitCase.Failure -> "failure:${failure.message}"
    }

    private class TrackedCloseable : Closeable {
        var closed: Boolean = false
            private set

        override fun close() {
            closed = true
        }
    }

    private class TrackedAutoCloseable : AutoCloseable {
        var closed: Boolean = false
            private set

        override fun close() {
            closed = true
        }
    }
}
