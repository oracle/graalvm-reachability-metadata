/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_turbine.turbine_jvm

import app.cash.turbine.Event
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.Turbine
import app.cash.turbine.awaitComplete
import app.cash.turbine.awaitError
import app.cash.turbine.awaitEvent
import app.cash.turbine.expectMostRecentItem
import app.cash.turbine.expectNoEvents
import app.cash.turbine.plusAssign
import app.cash.turbine.takeComplete
import app.cash.turbine.takeItem
import app.cash.turbine.test
import app.cash.turbine.testIn
import app.cash.turbine.turbineScope
import app.cash.turbine.withTurbineTimeout
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

public class TurbineJvmTest {
    @Test
    fun flowTestConsumesItemsCompletionAndTerminalEvents(): Unit = runBlocking {
        withTimeout(5_000.milliseconds) {
            val source: Flow<String> = flowOf("alpha", "beta")

            source.test(name = "letters") {
                assertThat(awaitEvent()).isEqualTo(Event.Item("alpha"))
                assertThat(awaitItem()).isEqualTo("beta")
                awaitComplete()
                assertThat(awaitEvent()).isEqualTo(Event.Complete)
            }
        }
    }

    @Test
    fun flowTestReportsErrorsAsEvents(): Unit = runBlocking {
        withTimeout(5_000.milliseconds) {
            val failure: IllegalStateException = IllegalStateException("boom")
            val source: Flow<Int> = flow {
                emit(1)
                throw failure
            }

            source.test(timeout = 200.milliseconds, name = "failingFlow") {
                assertThat(awaitItem()).isEqualTo(1)
                assertThat(awaitError()).isSameAs(failure)
            }
        }
    }

    @Test
    fun flowTestSupportsSkippingAndMostRecentItemAssertions(): Unit = runBlocking {
        withTimeout(5_000.milliseconds) {
            flowOf(1, 2, 3, 4).test(name = "numbers") {
                skipItems(2)
                assertThat(expectMostRecentItem()).isEqualTo(4)
                awaitComplete()
            }
        }
    }

    @Test
    fun flowTestFailsWhenEventsRemainUnconsumed(): Unit {
        assertThatThrownBy {
            runBlocking {
                withTimeout(5_000.milliseconds) {
                    flowOf("consumed", "left-behind").test(name = "source") {
                        assertThat(awaitItem()).isEqualTo("consumed")
                    }
                }
            }
        }
            .isInstanceOf(AssertionError::class.java)
            .hasMessageContaining("Unconsumed events found for source")
            .hasMessageContaining("Item(left-behind)")
    }

    @Test
    fun testInCoordinatesMultipleFlowsInsideTurbineScope(): Unit = runBlocking {
        withTimeout(5_000.milliseconds) {
            turbineScope(timeout = 200.milliseconds) {
                val numbers: ReceiveTurbine<Int> = flowOf(1, 2).testIn(this, name = "numbers")
                val words: ReceiveTurbine<String> = flowOf("one", "two").testIn(this, name = "words")

                assertThat(numbers.awaitItem()).isEqualTo(1)
                assertThat(words.awaitItem()).isEqualTo("one")
                assertThat(numbers.awaitItem()).isEqualTo(2)
                numbers.awaitComplete()
                assertThat(words.awaitItem()).isEqualTo("two")
                words.awaitComplete()
            }
        }
    }

    @Test
    fun testInCanCancelAndReturnBufferedEvents(): Unit = runBlocking {
        withTimeout(5_000.milliseconds) {
            turbineScope(timeout = 200.milliseconds) {
                val source: Flow<String> = flowOf("first", "second")
                val turbine: ReceiveTurbine<String> = source.testIn(this, name = "buffered")

                val events: List<Event<String>> = turbine.cancelAndConsumeRemainingEvents()

                assertThat(events).containsExactly(
                    Event.Item("first"),
                    Event.Item("second"),
                    Event.Complete,
                )
            }
        }
    }

    @Test
    fun testInRequiresTurbineScope(): Unit {
        assertThatThrownBy {
            runBlocking {
                withTimeout(5_000.milliseconds) {
                    flowOf("value").testIn(this).cancel()
                }
            }
        }
            .isInstanceOf(AssertionError::class.java)
            .hasMessageContaining("Turbine can only collect flows within a TurbineContext")
    }

    @Test
    fun standaloneTurbineSupportsNonSuspendingItemAndCompletionReads(): Unit {
        val turbine: Turbine<String> = Turbine(name = "standalone")

        turbine.expectNoEvents()
        turbine.add("a")
        turbine += "b"

        assertThat(turbine.takeEvent()).isEqualTo(Event.Item("a"))
        assertThat(turbine.takeItem()).isEqualTo("b")
        turbine.close()
        turbine.takeComplete()
        turbine.ensureAllEventsConsumed()
    }

    @Test
    fun standaloneTurbineReportsErrorsAndClosedAdds(): Unit {
        val failure: IllegalArgumentException = IllegalArgumentException("bad input")
        val turbine: Turbine<String> = Turbine(name = "errors")

        turbine.add("before-error")
        turbine.close(failure)

        assertThat(turbine.takeItem()).isEqualTo("before-error")
        assertThat(turbine.takeError()).isSameAs(failure)
        assertThatThrownBy { turbine.add("after-close") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("closed Turbine named errors")
    }

    @Test
    fun standaloneTurbineDrainsMostRecentItemAndReportsUnconsumedEvents(): Unit {
        val turbine: Turbine<Int> = Turbine(name = "counter")

        turbine.add(1)
        turbine.add(2)
        turbine.add(3)

        assertThat(turbine.expectMostRecentItem()).isEqualTo(3)
        turbine.expectNoEvents()
        turbine.add(4)

        assertThatThrownBy { turbine.ensureAllEventsConsumed() }
            .isInstanceOf(AssertionError::class.java)
            .hasMessageContaining("Unconsumed events found for counter")
            .hasMessageContaining("Item(4)")
    }

    @Test
    fun receiveChannelExtensionsExposeEventsItemsCompletionAndErrors(): Unit {
        val completed: Channel<String> = Channel(Channel.UNLIMITED)
        completed.trySend("ready").getOrThrow()
        completed.close()

        assertThat(completed.takeItem()).isEqualTo("ready")
        completed.takeComplete()

        runBlocking {
            withTimeout(5_000.milliseconds) {
                val failed: Channel<String> = Channel(Channel.UNLIMITED)
                val failure: IllegalStateException = IllegalStateException("channel failed")
                failed.close(failure)

                assertThat(failed.awaitError()).isSameAs(failure)
            }
        }
    }

    @Test
    fun receiveChannelExtensionsAwaitAndValidateBufferedEvents(): Unit = runBlocking {
        withTimeout(5_000.milliseconds) {
            val channel: Channel<String> = Channel(Channel.UNLIMITED)
            channel.trySend("old").getOrThrow()
            channel.trySend("new").getOrThrow()

            assertThat(channel.expectMostRecentItem()).isEqualTo("new")
            channel.expectNoEvents()

            channel.trySend("next").getOrThrow()
            assertThat(channel.awaitEvent()).isEqualTo(Event.Item("next"))
            channel.close()
            channel.awaitComplete()
        }
    }

    @Test
    fun timeoutAppliesToAwaitingTurbines(): Unit {
        assertThatThrownBy {
            runBlocking {
                withTimeout(5_000.milliseconds) {
                    withTurbineTimeout(20.milliseconds) {
                        val turbine: Turbine<String> = Turbine(name = "quiet")
                        turbine.awaitItem()
                    }
                }
            }
        }
            .isInstanceOf(AssertionError::class.java)
            .hasMessageContaining("No value produced for quiet")
    }

    @Test
    fun flowTestUsesExplicitTimeoutForFlowsThatDoNotEmit(): Unit {
        val source: Flow<String> = flow { awaitCancellation() }

        assertThatThrownBy {
            runBlocking {
                withTimeout(5_000.milliseconds) {
                    source.test(timeout = 20.milliseconds, name = "silent") {
                        awaitItem()
                    }
                }
            }
        }
            .isInstanceOf(AssertionError::class.java)
            .hasMessageContaining("No value produced for silent")
    }
}
