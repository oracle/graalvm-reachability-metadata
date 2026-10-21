/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.jsonMapper
import tools.jackson.module.kotlin.kotlinModule
import tools.jackson.module.kotlin.readValue

/**
 * Polymorphic sealed-class hierarchies whose subtypes, including object singletons, are
 * discovered without a subtype list, as a shipment tracker exchanges its event stream.
 */
public class ShipmentEventSealedHierarchyTest {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    sealed class ShipmentEvent {
        abstract val shipmentId: String

        @JsonTypeName("created")
        data class Created(
            override val shipmentId: String,
            val origin: String,
            val weightGrams: Int,
        ) : ShipmentEvent()

        @JsonTypeName("scanned")
        data class Scanned(
            override val shipmentId: String,
            val hub: String,
            val damaged: Boolean = false,
        ) : ShipmentEvent()

        @JsonTypeName("delivered")
        data class Delivered(override val shipmentId: String, val signedBy: String?) :
            ShipmentEvent()
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    sealed interface TrackerCommand {
        @JsonTypeName("pause")
        object Pause : TrackerCommand

        @JsonTypeName("resume")
        object Resume : TrackerCommand

        @JsonTypeName("reroute")
        data class Reroute(val shipmentId: String, val toHub: String) : TrackerCommand
    }

    data class EventBatch(
        val tracker: String,
        val events: List<ShipmentEvent>,
        val pending: TrackerCommand?,
    )

    private val mapper: JsonMapper = jsonMapper {
        addModule(kotlinModule { enable(KotlinFeature.SingletonSupport) })
    }

    @Test
    fun writesEachSubtypeWithItsTypeName(): Unit {
        val batch = EventBatch(
            tracker = "tracker-eu-1",
            events = listOf(
                ShipmentEvent.Created(shipmentId = "s-1", origin = "Rotterdam", weightGrams = 1250),
                ShipmentEvent.Scanned(shipmentId = "s-1", hub = "Utrecht"),
                ShipmentEvent.Delivered(shipmentId = "s-1", signedBy = null),
            ),
            pending = TrackerCommand.Pause,
        )

        val tree: JsonNode = mapper.readTree(mapper.writeValueAsString(batch))

        val events: JsonNode = tree.get("events")
        assertThat(events.values().map { it.get("type").asString() })
            .containsExactly("created", "scanned", "delivered")
        assertThat(events.get(0).get("weightGrams").intValue()).isEqualTo(1250)
        assertThat(events.get(1).get("damaged").booleanValue()).isFalse()
        assertThat(events.get(2).get("signedBy").isNull).isTrue()
        assertThat(tree.get("pending").get("kind").asString()).isEqualTo("pause")
    }

    @Test
    fun readsAMixedEventStreamIntoTheMatchingSubtypes(): Unit {
        val body = """
            [
              {"type": "created", "shipmentId": "s-9", "origin": "Antwerp", "weightGrams": 800},
              {"type": "scanned", "shipmentId": "s-9", "hub": "Liege", "damaged": true},
              {"type": "scanned", "shipmentId": "s-9", "hub": "Cologne"},
              {"type": "delivered", "shipmentId": "s-9", "signedBy": "J. Peeters"}
            ]
        """.trimIndent()

        val events: List<ShipmentEvent> = mapper.readValue(body)

        val summary: List<String> = events.map { event ->
            when (event) {
                is ShipmentEvent.Created -> "created at ${event.origin}"
                is ShipmentEvent.Scanned ->
                    "scanned at ${event.hub}" + if (event.damaged) " (damaged)" else ""
                is ShipmentEvent.Delivered -> "delivered to ${event.signedBy}"
            }
        }
        assertThat(summary).containsExactly(
            "created at Antwerp",
            "scanned at Liege (damaged)",
            "scanned at Cologne",
            "delivered to J. Peeters",
        )
        assertThat(events.map { it.shipmentId }.distinct()).containsExactly("s-9")
    }

    @Test
    fun resolvesObjectCommandsToTheirSingletonInstances(): Unit {
        val commands: List<TrackerCommand> = mapper.readValue(
            """
                [
                  {"kind": "resume"},
                  {"kind": "reroute", "shipmentId": "s-3", "toHub": "Ghent"},
                  {"kind": "pause"}
                ]
            """.trimIndent(),
        )

        assertThat(commands[0]).isSameAs(TrackerCommand.Resume)
        assertThat(commands[1])
            .isEqualTo(TrackerCommand.Reroute(shipmentId = "s-3", toHub = "Ghent"))
        assertThat(commands[2]).isSameAs(TrackerCommand.Pause)
    }

    @Test
    fun roundTripsABatchThroughTheSealedBaseTypes(): Unit {
        val batch = EventBatch(
            tracker = "tracker-us-2",
            events = listOf(
                ShipmentEvent.Scanned(shipmentId = "s-4", hub = "Memphis", damaged = true),
                ShipmentEvent.Delivered(shipmentId = "s-4", signedBy = "R. Diaz"),
            ),
            pending = TrackerCommand.Reroute(shipmentId = "s-5", toHub = "Louisville"),
        )

        val echoed: EventBatch = mapper.readValue(mapper.writeValueAsString(batch))

        assertThat(echoed).isEqualTo(batch)
        val idle: EventBatch = mapper.readValue(
            mapper.writeValueAsString(
                batch.copy(events = emptyList(), pending = TrackerCommand.Resume),
            ),
        )
        assertThat(idle.pending).isSameAs(TrackerCommand.Resume)
        assertThat(idle.events).isEmpty()
    }
}
