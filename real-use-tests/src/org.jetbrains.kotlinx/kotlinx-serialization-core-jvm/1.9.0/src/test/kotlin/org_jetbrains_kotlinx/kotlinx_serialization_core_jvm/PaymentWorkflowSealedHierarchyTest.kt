/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_core_jvm

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises closed polymorphism through sealed hierarchies: the generated `SealedClassSerializer`
 * of sealed classes and interfaces, nested and generic sealed types and `data object` subclasses.
 *
 * A payment service persists the state machine of each payment and the commands that drive it,
 * with no `SerializersModule`: every subclass is known to the compiler through the sealed base.
 */
@OptIn(ExperimentalSerializationApi::class)
public class PaymentWorkflowSealedHierarchyTest {

    /** Commands a client sends to the payment service, a sealed interface. */
    @Serializable
    public sealed interface PaymentCommand {
        /** Reserve funds on the customer's card. */
        @Serializable
        @SerialName("authorize")
        public data class Authorize(val paymentId: String, val amountCents: Long) : PaymentCommand

        /** Move reserved funds to the merchant. */
        @Serializable
        @SerialName("capture")
        public data class Capture(val paymentId: String) : PaymentCommand

        /** Ask the service to re-send every pending notification. */
        @Serializable
        @SerialName("flush")
        public data object FlushNotifications : PaymentCommand
    }

    /** States of a payment; the failed states form a nested sealed hierarchy. */
    @Serializable
    public sealed class PaymentState {
        public abstract val paymentId: String

        /** Created but not yet authorized. */
        @Serializable
        @SerialName("pending")
        public data class Pending(override val paymentId: String) : PaymentState()

        /** Funds reserved under an authorization code. */
        @Serializable
        @SerialName("authorized")
        public data class Authorized(
            override val paymentId: String,
            val authCode: String,
            val amountCents: Long,
        ) : PaymentState()

        /** Funds moved; the payment is final. */
        @Serializable
        @SerialName("settled")
        public data class Settled(override val paymentId: String, val amountCents: Long) : PaymentState()

        /** Root of the terminal failure states. */
        @Serializable
        public sealed class Failed : PaymentState() {
            public abstract val retryable: Boolean

            /** The issuer refused the charge. */
            @Serializable
            @SerialName("failed.declined")
            public data class Declined(
                override val paymentId: String,
                val issuerCode: String,
            ) : Failed() {
                override val retryable: Boolean
                    get() = false
            }

            /** The gateway did not answer in time. */
            @Serializable
            @SerialName("failed.timeout")
            public data class GatewayTimeout(
                override val paymentId: String,
                val attempts: Int,
            ) : Failed() {
                override val retryable: Boolean
                    get() = attempts < 3
            }
        }
    }

    /** Result of a command, generic in the value a successful command yields. */
    @Serializable
    public sealed class Outcome<out T> {
        /** The command succeeded with a value. */
        @Serializable
        @SerialName("ok")
        public data class Ok<out T>(val value: T) : Outcome<T>()

        /** The command was rejected before it reached the gateway. */
        @Serializable
        @SerialName("rejected")
        public data class Rejected(val reason: String) : Outcome<Nothing>()
    }

    /** Webhook payloads posted to merchants, using the merchant API's own discriminator key. */
    @Serializable
    @JsonClassDiscriminator("event")
    public sealed class MerchantWebhook {
        /** A payment of the merchant settled. */
        @Serializable
        @SerialName("payment.settled")
        public data class PaymentSettled(val paymentId: String, val amountCents: Long) : MerchantWebhook()

        /** A customer disputed a settled payment. */
        @Serializable
        @SerialName("payment.disputed")
        public data class PaymentDisputed(val paymentId: String, val reason: String) : MerchantWebhook()
    }

    /** One persisted row of a payment's history. */
    @Serializable
    public data class HistoryRow(val sequence: Int, val state: PaymentState)

    /** Applies commands to states; the `when` is exhaustive because the hierarchies are sealed. */
    public class PaymentService {
        private val states: MutableMap<String, PaymentState> = linkedMapOf()

        public fun handle(command: PaymentCommand): Outcome<PaymentState> = when (command) {
            is PaymentCommand.Authorize -> {
                if (command.amountCents <= 0) {
                    Outcome.Rejected("amount must be positive")
                } else {
                    val state: PaymentState = PaymentState.Authorized(
                        command.paymentId,
                        "AUTH-${command.paymentId.uppercase()}",
                        command.amountCents,
                    )
                    states[command.paymentId] = state
                    Outcome.Ok(state)
                }
            }
            is PaymentCommand.Capture -> when (val current: PaymentState? = states[command.paymentId]) {
                is PaymentState.Authorized -> {
                    val state: PaymentState = PaymentState.Settled(current.paymentId, current.amountCents)
                    states[command.paymentId] = state
                    Outcome.Ok(state)
                }
                else -> Outcome.Rejected("payment ${command.paymentId} is not authorized")
            }
            PaymentCommand.FlushNotifications -> Outcome.Ok(PaymentState.Pending("*"))
        }
    }

    private val json: Json = Json

    @Test
    fun commandsFromClientsDecodeIntoTheSealedInterfaceAndDriveTheService() {
        val wire: String = """
            [
              {"type":"authorize","paymentId":"p-1","amountCents":4200},
              {"type":"capture","paymentId":"p-1"},
              {"type":"capture","paymentId":"p-2"},
              {"type":"authorize","paymentId":"p-3","amountCents":0},
              {"type":"flush"}
            ]
        """.trimIndent()
        val commands: List<PaymentCommand> = json.decodeFromString(wire)
        assertEquals(
            listOf(
                PaymentCommand.Authorize("p-1", 4200),
                PaymentCommand.Capture("p-1"),
                PaymentCommand.Capture("p-2"),
                PaymentCommand.Authorize("p-3", 0),
                PaymentCommand.FlushNotifications,
            ),
            commands,
        )
        assertSame(PaymentCommand.FlushNotifications, commands.last())

        val service: PaymentService = PaymentService()
        val outcomes: List<Outcome<PaymentState>> = commands.map(service::handle)
        val outcomesSerializer: KSerializer<List<Outcome<PaymentState>>> =
            ListSerializer(Outcome.serializer(PaymentState.serializer()))
        assertEquals(
            """[{"type":"ok","value":{"type":"authorized","paymentId":"p-1","authCode":"AUTH-P-1","amountCents":4200}},""" +
                """{"type":"ok","value":{"type":"settled","paymentId":"p-1","amountCents":4200}},""" +
                """{"type":"rejected","reason":"payment p-2 is not authorized"},""" +
                """{"type":"rejected","reason":"amount must be positive"},""" +
                """{"type":"ok","value":{"type":"pending","paymentId":"*"}}]""",
            json.encodeToString(outcomesSerializer, outcomes),
        )
    }

    @Test
    fun nestedSealedStatesKeepTheirSubclassThroughJsonAndCbor() {
        val history: List<HistoryRow> = listOf(
            HistoryRow(1, PaymentState.Pending("p-9")),
            HistoryRow(2, PaymentState.Failed.GatewayTimeout("p-9", attempts = 1)),
            HistoryRow(3, PaymentState.Failed.GatewayTimeout("p-9", attempts = 3)),
            HistoryRow(4, PaymentState.Failed.Declined("p-9", issuerCode = "51")),
        )

        val text: String = json.encodeToString(history)
        assertEquals(
            """[{"sequence":1,"state":{"type":"pending","paymentId":"p-9"}},""" +
                """{"sequence":2,"state":{"type":"failed.timeout","paymentId":"p-9","attempts":1}},""" +
                """{"sequence":3,"state":{"type":"failed.timeout","paymentId":"p-9","attempts":3}},""" +
                """{"sequence":4,"state":{"type":"failed.declined","paymentId":"p-9","issuerCode":"51"}}]""",
            text,
        )
        assertEquals(history, json.decodeFromString<List<HistoryRow>>(text))

        val bytes: ByteArray = Cbor.encodeToByteArray(history)
        val restored: List<HistoryRow> = Cbor.decodeFromByteArray(bytes)
        assertEquals(history, restored)
        val retryable: List<Boolean> = restored.map { row: HistoryRow ->
            (row.state as? PaymentState.Failed)?.retryable ?: false
        }
        assertEquals(listOf(false, true, false, false), retryable)

        val failure: PaymentState.Failed = json.decodeFromString(
            PaymentState.Failed.serializer(),
            """{"type":"failed.declined","paymentId":"p-4","issuerCode":"05"}""",
        )
        assertEquals(PaymentState.Failed.Declined("p-4", "05"), failure)
    }

    @Test
    fun sealedDescriptorsListEverySubclassUnderItsSerialName() {
        val descriptor: SerialDescriptor = PaymentState.serializer().descriptor
        assertEquals(PolymorphicKind.SEALED, descriptor.kind)
        assertEquals(listOf("type", "value"), (0 until descriptor.elementsCount).map(descriptor::getElementName))

        val subclassNames: Set<String> = descriptor.getElementDescriptor(1).elementDescriptors
            .map(SerialDescriptor::serialName)
            .toSet()
        assertEquals(
            setOf("pending", "authorized", "settled", "failed.declined", "failed.timeout"),
            subclassNames,
        )

        val failedNames: Set<String> = PaymentState.Failed.serializer().descriptor.getElementDescriptor(1)
            .elementDescriptors.map(SerialDescriptor::serialName).toSet()
        assertEquals(setOf("failed.declined", "failed.timeout"), failedNames)

        val commandDescriptor: SerialDescriptor = PaymentCommand.serializer().descriptor
        assertEquals(PolymorphicKind.SEALED, commandDescriptor.kind)
        assertTrue(
            commandDescriptor.getElementDescriptor(1).elementDescriptors.any { element: SerialDescriptor ->
                element.serialName == "flush"
            },
        )
    }

    @Test
    fun webhooksUseTheDeclaredDiscriminatorAndRejectUnknownEvents() {
        val webhooks: List<MerchantWebhook> = listOf(
            MerchantWebhook.PaymentSettled("p-1", 4200),
            MerchantWebhook.PaymentDisputed("p-1", "item not received"),
        )
        val text: String = json.encodeToString(webhooks)
        assertEquals(
            """[{"event":"payment.settled","paymentId":"p-1","amountCents":4200},""" +
                """{"event":"payment.disputed","paymentId":"p-1","reason":"item not received"}]""",
            text,
        )
        assertEquals(webhooks, json.decodeFromString<List<MerchantWebhook>>(text))

        val unknown: SerializationException = assertThrows(SerializationException::class.java) {
            json.decodeFromString<MerchantWebhook>("""{"event":"payment.refunded","paymentId":"p-1"}""")
        }
        assertTrue(unknown.message!!.contains("payment.refunded"), unknown.message)

        val wrongBranch: SerializationException = assertThrows(SerializationException::class.java) {
            json.decodeFromString(PaymentState.Failed.serializer(), """{"type":"settled","paymentId":"p-1","amountCents":1}""")
        }
        assertTrue(wrongBranch.message!!.contains("settled"), wrongBranch.message)
    }
}
