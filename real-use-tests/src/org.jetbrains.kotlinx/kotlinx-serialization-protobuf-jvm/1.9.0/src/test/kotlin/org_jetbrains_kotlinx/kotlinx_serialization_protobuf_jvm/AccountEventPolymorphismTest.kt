/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_protobuf_jvm

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Open polymorphism over an interface hierarchy registered in a `SerializersModule` of a `ProtoBuf` instance.
 *
 * A ledger service appends account events of an open `DomainEvent` interface to an event store
 * as protobuf messages and replays them to rebuild balances. Every concrete event is registered
 * with the format's serializers module under a stable serial name; the service checks that a
 * mixed stream round-trips, that each event travels as a type name plus a nested message, that
 * an unregistered event is refused, and that an older consumer falls back to a default event.
 */
@OptIn(ExperimentalSerializationApi::class)
class AccountEventPolymorphismTest {

    interface DomainEvent {
        val accountId: String
    }

    @Serializable
    @SerialName("account.opened")
    data class AccountOpened(
        @ProtoNumber(1) override val accountId: String,
        @ProtoNumber(2) val owner: String,
    ) : DomainEvent

    @Serializable
    @SerialName("funds.deposited")
    data class FundsDeposited(
        @ProtoNumber(1) override val accountId: String,
        @ProtoNumber(2) val amountCents: Long,
    ) : DomainEvent

    @Serializable
    @SerialName("funds.withdrawn")
    data class FundsWithdrawn(
        @ProtoNumber(1) override val accountId: String,
        @ProtoNumber(2) val amountCents: Long,
        @ProtoNumber(3) val reference: String = "",
    ) : DomainEvent

    @Serializable
    @SerialName("account.frozen")
    data class AccountFrozen(
        @ProtoNumber(1) override val accountId: String,
        @ProtoNumber(2) val reason: String,
    ) : DomainEvent

    /** What an older consumer builds from an event type it was compiled without. */
    @Serializable
    data class UnrecognizedEvent(
        @ProtoNumber(1) override val accountId: String = "",
    ) : DomainEvent

    @Serializable
    data class StoredEvent(
        @ProtoNumber(1) val sequence: Long,
        @ProtoNumber(2) val event: DomainEvent,
    )

    @Serializable
    data class EventStream(
        @ProtoNumber(1) val streamId: String,
        @ProtoNumber(2) val events: List<StoredEvent>,
    )

    /** The type-tagged message the ledger puts on the wire for every [DomainEvent]. */
    @Serializable
    class TaggedEvent(
        @ProtoNumber(1) val type: String,
        @ProtoNumber(2) val value: ByteArray,
    )

    private val ledgerFormat: ProtoBuf = ProtoBuf {
        serializersModule = SerializersModule {
            polymorphic(DomainEvent::class) {
                subclass(AccountOpened::class)
                subclass(FundsDeposited::class)
                subclass(FundsWithdrawn::class)
                subclass(AccountFrozen::class)
            }
        }
    }

    /** A consumer built before withdrawals and freezes existed. */
    private val legacyConsumerFormat: ProtoBuf = ProtoBuf {
        serializersModule = SerializersModule {
            polymorphic(DomainEvent::class) {
                subclass(AccountOpened::class)
                subclass(FundsDeposited::class)
                defaultDeserializer { UnrecognizedEvent.serializer() }
            }
        }
    }

    @Test
    fun replaysMixedEventStreamIntoBalances() {
        val stream = EventStream(
            streamId = "ledger-eu-1",
            events = listOf(
                StoredEvent(1, AccountOpened("acc-1", "Ada")),
                StoredEvent(2, FundsDeposited("acc-1", 25_000)),
                StoredEvent(3, AccountOpened("acc-2", "Grace")),
                StoredEvent(4, FundsWithdrawn("acc-1", 7_500, "invoice-17")),
                StoredEvent(5, FundsDeposited("acc-2", 1_200)),
                StoredEvent(6, AccountFrozen("acc-2", "kyc review")),
            ),
        )

        val bytes: ByteArray = ledgerFormat.encodeToByteArray(stream)
        val restored: EventStream = ledgerFormat.decodeFromByteArray(bytes)

        assertEquals(stream, restored)
        assertEquals(mapOf("acc-1" to 17_500L, "acc-2" to 1_200L), balances(restored.events))
        val frozen: List<String> = restored.events.map { it.event }.filterIsInstance<AccountFrozen>().map { it.accountId }
        assertEquals(listOf("acc-2"), frozen)
    }

    @Test
    fun encodesEachEventAsTypeNameAndNestedMessage() {
        val event: DomainEvent = FundsWithdrawn("acc-9", 300, "atm")

        val bytes: ByteArray = ledgerFormat.encodeToByteArray(event)
        val tagged: TaggedEvent = ledgerFormat.decodeFromByteArray(bytes)

        assertEquals("funds.withdrawn", tagged.type)
        assertEquals(event, ledgerFormat.decodeFromByteArray<FundsWithdrawn>(tagged.value))
        assertEquals(event, ledgerFormat.decodeFromByteArray<DomainEvent>(bytes))
    }

    @Test
    fun refusesEventsMissingFromTheModule() {
        val unregistered: DomainEvent = UnrecognizedEvent("acc-3")
        val encodeFailure: SerializationException = assertThrows {
            ledgerFormat.encodeToByteArray(StoredEvent(1, unregistered))
        }
        assertTrue(encodeFailure.message!!.contains("UnrecognizedEvent"), encodeFailure.message)

        val forged: ByteArray = ledgerFormat.encodeToByteArray(
            TaggedEvent("account.merged", ledgerFormat.encodeToByteArray(AccountOpened("acc-3", "Linus"))),
        )
        val decodeFailure: SerializationException = assertThrows {
            ledgerFormat.decodeFromByteArray<DomainEvent>(forged)
        }
        assertTrue(decodeFailure.message!!.contains("account.merged"), decodeFailure.message)
    }

    @Test
    fun legacyConsumerFallsBackToDefaultEventForNewTypes() {
        val stream = EventStream(
            streamId = "ledger-us-2",
            events = listOf(
                StoredEvent(10, AccountOpened("acc-7", "Barbara")),
                StoredEvent(11, FundsDeposited("acc-7", 9_000)),
                StoredEvent(12, FundsWithdrawn("acc-7", 4_000)),
                StoredEvent(13, AccountFrozen("acc-7", "chargeback")),
            ),
        )
        val bytes: ByteArray = ledgerFormat.encodeToByteArray(stream)

        val seenByLegacy: EventStream = legacyConsumerFormat.decodeFromByteArray(bytes)

        assertEquals(listOf(10L, 11L, 12L, 13L), seenByLegacy.events.map { it.sequence })
        assertEquals(AccountOpened("acc-7", "Barbara"), seenByLegacy.events[0].event)
        assertEquals(FundsDeposited("acc-7", 9_000), seenByLegacy.events[1].event)
        assertEquals(UnrecognizedEvent("acc-7"), seenByLegacy.events[2].event)
        assertEquals(UnrecognizedEvent("acc-7"), seenByLegacy.events[3].event)
        assertEquals(mapOf("acc-7" to 9_000L), balances(seenByLegacy.events))
    }

    private fun balances(events: List<StoredEvent>): Map<String, Long> {
        val balances: MutableMap<String, Long> = linkedMapOf()
        for (stored in events.sortedBy { it.sequence }) {
            when (val event: DomainEvent = stored.event) {
                is AccountOpened -> balances[event.accountId] = 0L
                is FundsDeposited -> balances.merge(event.accountId, event.amountCents, Long::plus)
                is FundsWithdrawn -> balances.merge(event.accountId, -event.amountCents, Long::plus)
                else -> Unit
            }
        }
        return balances
    }
}
