/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_events_jvm

import io.ktor.events.EventDefinition
import io.ktor.events.Events
import io.ktor.events.raiseCatching
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test

public class KtorEventsJvmTest {
    @Test
    fun eventDefinitionsRouteTypedPayloadsToTheirOwnHandlers() {
        val events: Events = Events()
        val textEvent: EventDefinition<String> = EventDefinition()
        val numberEvent: EventDefinition<Int> = EventDefinition()
        val observed: MutableList<String> = mutableListOf()

        assertThatCode { events.raise(textEvent, "no subscribers") }.doesNotThrowAnyException()

        events.subscribe(textEvent) { value: String ->
            observed += "text=$value"
        }
        events.subscribe(numberEvent) { value: Int ->
            observed += "number=${value * 2}"
        }

        events.raise(textEvent, "alpha")
        events.raise(numberEvent, 21)

        assertThat(observed).containsExactly("text=alpha", "number=42")
    }

    @Test
    fun handlersAreInvokedInSubscriptionOrderUntilTheirDisposableHandleIsDisposed() {
        val events: Events = Events()
        val definition: EventDefinition<String> = EventDefinition()
        val observed: MutableList<String> = mutableListOf()

        val firstHandle = events.subscribe(definition) { value: String ->
            observed += "first:$value"
        }
        events.subscribe(definition) { value: String ->
            observed += "second:$value"
        }

        events.raise(definition, "one")
        firstHandle.dispose()
        firstHandle.dispose()
        events.raise(definition, "two")

        assertThat(observed).containsExactly(
            "first:one",
            "second:one",
            "second:two"
        )
    }

    @Test
    fun unsubscribeRemovesMatchingHandlerRegistrationsWithoutAffectingOtherHandlers() {
        val events: Events = Events()
        val definition: EventDefinition<String> = EventDefinition()
        val observed: MutableList<String> = mutableListOf()
        val repeatedHandler: (String) -> Unit = { value: String -> observed += "repeated:$value" }
        val retainedHandler: (String) -> Unit = { value: String -> observed += "retained:$value" }

        events.subscribe(definition, repeatedHandler)
        events.subscribe(definition, retainedHandler)
        events.subscribe(definition, repeatedHandler)

        events.unsubscribe(definition, repeatedHandler)
        events.raise(definition, "payload")
        events.unsubscribe(definition, repeatedHandler)
        events.raise(definition, "again")

        assertThat(observed).containsExactly("retained:payload", "retained:again")
    }

    @Test
    fun equalCustomEventDefinitionsShareHandlersAndDifferentDefinitionsRemainIsolated() {
        val events: Events = Events()
        val primaryDefinition: EventDefinition<String> = NamedEventDefinition("shared")
        val equalDefinition: EventDefinition<String> = NamedEventDefinition("shared")
        val otherDefinition: EventDefinition<String> = NamedEventDefinition("other")
        val observed: MutableList<String> = mutableListOf()

        events.subscribe(primaryDefinition) { value: String ->
            observed += value
        }

        events.raise(equalDefinition, "from equal key")
        events.raise(otherDefinition, "from other key")

        assertThat(observed).containsExactly("from equal key")
    }

    @Test
    fun raiseRunsAllHandlersAndRethrowsFirstFailureWithLaterFailuresSuppressed() {
        val events: Events = Events()
        val definition: EventDefinition<Unit> = EventDefinition()
        val observed: MutableList<String> = mutableListOf()
        val firstFailure = IllegalStateException("first failure")
        val secondFailure = IllegalArgumentException("second failure")

        events.subscribe(definition) {
            observed += "before failures"
        }
        events.subscribe(definition) {
            observed += "first failing handler"
            throw firstFailure
        }
        events.subscribe(definition) {
            observed += "second failing handler"
            throw secondFailure
        }
        events.subscribe(definition) {
            observed += "after failures"
        }

        val thrown: Throwable = catchThrowable { events.raise(definition, Unit) }

        assertThat(thrown).isSameAs(firstFailure)
        assertThat(thrown.suppressed).containsExactly(secondFailure)
        assertThat(observed).containsExactly(
            "before failures",
            "first failing handler",
            "second failing handler",
            "after failures"
        )
    }

    @Test
    fun raiseCatchingRunsRemainingHandlersAndSuppressesTheAggregatedFailure() {
        val events: Events = Events()
        val definition: EventDefinition<String> = EventDefinition()
        val observed: MutableList<String> = mutableListOf()

        events.subscribe(definition) { value: String ->
            observed += "failing:$value"
            throw IllegalStateException("boom")
        }
        events.subscribe(definition) { value: String ->
            observed += "after:$value"
        }

        assertThatCode { events.raiseCatching(definition, "payload") }.doesNotThrowAnyException()

        assertThat(observed).containsExactly("failing:payload", "after:payload")
    }

    private class NamedEventDefinition<T>(private val name: String) : EventDefinition<T>() {
        override fun equals(other: Any?): Boolean = other is NamedEventDefinition<*> && name == other.name

        override fun hashCode(): Int = name.hashCode()
    }
}
