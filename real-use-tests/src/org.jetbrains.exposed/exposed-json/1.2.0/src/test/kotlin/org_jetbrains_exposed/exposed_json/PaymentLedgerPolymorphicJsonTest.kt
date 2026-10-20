/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.json.json
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Test

/**
 * Polymorphic json columns with explicit serializers: a payment ledger stores sealed
 * payment-method and adjustment hierarchies through `json(name, format, serializer)` with a
 * custom class discriminator, and reads every subtype back intact.
 */
class PaymentLedgerPolymorphicJsonTest {

    @Serializable
    sealed interface PaymentMethod {

        @Serializable
        @SerialName("card")
        data class Card(val brand: String, val last4: String, val expires: String) : PaymentMethod

        @Serializable
        @SerialName("sepa")
        data class SepaDebit(val iban: String, val mandateId: String) : PaymentMethod

        @Serializable
        @SerialName("wallet")
        data class Wallet(val provider: String) : PaymentMethod

        @Serializable
        @SerialName("cash")
        data object CashOnDelivery : PaymentMethod
    }

    @Serializable
    sealed class Adjustment {
        abstract val cents: Long

        @Serializable
        @SerialName("refund")
        data class Refund(override val cents: Long, val reason: String) : Adjustment()

        @Serializable
        @SerialName("fee")
        data class Fee(override val cents: Long, val code: String) : Adjustment()
    }

    object Payments : Table("ledger_payments") {
        private val format = Json { classDiscriminator = "kind" }

        val id = integer("id").autoIncrement()
        val reference = varchar("reference", 32).uniqueIndex()
        val amountCents = long("amount_cents")
        val method = json("payment_method", format, PaymentMethod.serializer())
        val adjustments = json("adjustments", format, ListSerializer(Adjustment.serializer()))

        override val primaryKey = PrimaryKey(id)
    }

    data class Payment(
        val reference: String,
        val amountCents: Long,
        val method: PaymentMethod,
        val adjustments: List<Adjustment>,
    ) {
        val netCents: Long
            get() = amountCents - adjustments.sumOf {
                when (it) {
                    is Adjustment.Refund -> it.cents
                    is Adjustment.Fee -> -it.cents
                }
            }
    }

    @Test
    fun storesAndReadsBackEveryPaymentSubtype() {
        val database = Database.connect(
            url = "jdbc:h2:mem:payment_ledger;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        val payments: List<Payment> = listOf(
            Payment("PAY-1", 4_999, PaymentMethod.Card("visa", "4242", "2029-04"), emptyList()),
            Payment(
                "PAY-2",
                12_000,
                PaymentMethod.SepaDebit("DE89370400440532013000", "MNDT-77"),
                listOf(Adjustment.Fee(150, "sepa-processing")),
            ),
            Payment("PAY-3", 2_500, PaymentMethod.Wallet("paypal"), emptyList()),
            Payment("PAY-4", 3_200, PaymentMethod.CashOnDelivery, emptyList()),
        )

        transaction(database) {
            SchemaUtils.create(Payments)
            payments.forEach { payment ->
                Payments.insert {
                    it[reference] = payment.reference
                    it[amountCents] = payment.amountCents
                    it[method] = payment.method
                    it[adjustments] = payment.adjustments
                }
            }
        }

        val rawMethod: String = transaction(database) {
            exec("SELECT payment_method FROM ledger_payments WHERE reference = 'PAY-2'") { rs ->
                rs.next()
                rs.getString(1)
            }!!
        }
        assertThat(rawMethod).contains("\"kind\":\"sepa\"").contains("\"mandateId\":\"MNDT-77\"")

        val stored: Map<String, Payment> = transaction(database) {
            Payments.selectAll().orderBy(Payments.id).associate { row ->
                row[Payments.reference] to Payment(
                    row[Payments.reference],
                    row[Payments.amountCents],
                    row[Payments.method],
                    row[Payments.adjustments],
                )
            }
        }
        assertThat(stored.values).containsExactlyElementsOf(payments)
        assertThat(stored.getValue("PAY-4").method).isSameAs(PaymentMethod.CashOnDelivery)

        val methodLabels: List<String> = stored.values.map { payment ->
            when (val method = payment.method) {
                is PaymentMethod.Card -> "${method.brand} ending ${method.last4}"
                is PaymentMethod.SepaDebit -> "SEPA ${method.iban.takeLast(4)}"
                is PaymentMethod.Wallet -> method.provider
                PaymentMethod.CashOnDelivery -> "cash"
            }
        }
        assertThat(methodLabels).containsExactly("visa ending 4242", "SEPA 3000", "paypal", "cash")
        assertThat(stored.getValue("PAY-2").netCents).isEqualTo(12_150)

        val refund = Adjustment.Refund(1_000, "damaged item")
        val updatedRows: Int = transaction(database) {
            Payments.update({ Payments.reference eq "PAY-1" }) {
                it[method] = PaymentMethod.Wallet("apple-pay")
                it[adjustments] = listOf(refund, Adjustment.Fee(30, "chargeback"))
            }
        }
        assertThat(updatedRows).isEqualTo(1)

        val reloaded: Payment = transaction(database) {
            val row = Payments.selectAll().where { Payments.reference eq "PAY-1" }.single()
            Payment(row[Payments.reference], row[Payments.amountCents], row[Payments.method], row[Payments.adjustments])
        }
        assertThat(reloaded.method).isEqualTo(PaymentMethod.Wallet("apple-pay"))
        assertThat(reloaded.adjustments).hasSize(2)
        assertThat(reloaded.adjustments.first()).isInstanceOf(Adjustment.Refund::class.java).isEqualTo(refund)
        assertThat(reloaded.adjustments.last()).isEqualTo(Adjustment.Fee(30, "chargeback"))
        assertThat(reloaded.netCents).isEqualTo(4_029)

        transaction(database) { SchemaUtils.drop(Payments) }
    }
}
