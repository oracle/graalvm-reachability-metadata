/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_cbor_jvm

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Exercises CBOR polymorphic serialization of sealed hierarchies and of open hierarchies
 * registered in a `SerializersModule`.
 *
 * A payment ledger appends heterogeneous events to a CBOR journal and replays it; each
 * event carries its serial name so the replay restores the concrete type, and a plugin
 * notification channel resolves its open `Notifier` interface through a module.
 */
@OptIn(ExperimentalSerializationApi::class)
public class PaymentEventPolymorphicCborTest {

    @Serializable
    public sealed class PaymentEvent {
        public abstract val paymentId: String
    }

    @Serializable
    @SerialName("authorized")
    public data class Authorized(override val paymentId: String, val amountCents: Long) : PaymentEvent()

    @Serializable
    @SerialName("captured")
    public data class Captured(override val paymentId: String, val capturedCents: Long) : PaymentEvent()

    @Serializable
    @SerialName("refunded")
    public data class Refunded(
        override val paymentId: String,
        val refundedCents: Long,
        val reason: String? = null,
    ) : PaymentEvent()

    @Serializable
    public data class Journal(val ledger: String, val events: List<PaymentEvent>)

    public interface Notifier {
        public val target: String
    }

    @Serializable
    @SerialName("email")
    public data class EmailNotifier(override val target: String, val subjectPrefix: String) : Notifier

    @Serializable
    @SerialName("webhook")
    public data class WebhookNotifier(override val target: String, val retries: Int) : Notifier

    @Serializable
    public data class Subscription(val paymentId: String, val notifiers: List<Notifier>)

    @Test
    fun replaysAJournalOfSealedEvents(): Unit {
        val journal: Journal = Journal(
            ledger = "eu-west",
            events = listOf(
                Authorized("pay-1", 12_500L),
                Captured("pay-1", 12_500L),
                Refunded("pay-1", 2_500L, "damaged item"),
                Authorized("pay-2", 990L),
            ),
        )

        val replayed: Journal = Cbor.decodeFromByteArray<Journal>(Cbor.encodeToByteArray(journal))
        val balanceCents: Long = replayed.events.sumOf { event: PaymentEvent ->
            when (event) {
                is Authorized -> 0L
                is Captured -> event.capturedCents
                is Refunded -> -event.refundedCents
            }
        }

        assertEquals(journal, replayed)
        assertEquals(listOf("pay-1", "pay-1", "pay-1", "pay-2"), replayed.events.map { it.paymentId })
        assertEquals(10_000L, balanceCents)
        assertEquals("damaged item", (replayed.events[2] as Refunded).reason)
    }

    @Test
    fun writesTheSerialNameBeforeTheEventFields(): Unit {
        val interoperable: Cbor = Cbor { useDefiniteLengthEncoding = true }
        val expected: ByteArray = byteArrayOf(
            0x82.toByte(),
            0x68, *"captured".encodeToByteArray(),
            0xA2.toByte(),
            0x69, *"paymentId".encodeToByteArray(),
            0x61, 'p'.code.toByte(),
            0x6D, *"capturedCents".encodeToByteArray(),
            0x05,
        )

        val event: PaymentEvent = Captured("p", 5L)
        val encoded: ByteArray = interoperable.encodeToByteArray<PaymentEvent>(event)

        assertArrayEquals(expected, encoded)
        assertEquals(event, interoperable.decodeFromByteArray<PaymentEvent>(encoded))
    }

    @Test
    fun resolvesOpenNotifiersThroughASerializersModule(): Unit {
        val module: SerializersModule = SerializersModule {
            polymorphic(Notifier::class) {
                subclass(EmailNotifier::class)
                subclass(WebhookNotifier::class)
            }
        }
        val pluginCbor: Cbor = Cbor { serializersModule = module }
        val subscription: Subscription = Subscription(
            paymentId = "pay-7",
            notifiers = listOf(
                EmailNotifier("billing@example.com", "[ledger]"),
                WebhookNotifier("https://hooks.example.com/pay", 3),
            ),
        )

        val restored: Subscription = pluginCbor.decodeFromByteArray<Subscription>(
            pluginCbor.encodeToByteArray(subscription),
        )

        assertEquals(subscription, restored)
        assertEquals(3, (restored.notifiers[1] as WebhookNotifier).retries)
        assertThrows(SerializationException::class.java) {
            Cbor.encodeToByteArray(subscription)
        }
    }
}
