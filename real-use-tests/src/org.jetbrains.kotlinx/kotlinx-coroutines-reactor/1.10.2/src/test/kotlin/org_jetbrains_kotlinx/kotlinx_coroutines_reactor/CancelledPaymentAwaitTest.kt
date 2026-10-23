/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_reactor

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks

/**
 * Exercises cancellable Mono awaiting for a checkout waiting on payment notifications.
 * Abandoning checkout removes its pending listener without recording an unpaid order as completed.
 */
@Timeout(60)
class CancelledPaymentAwaitTest {
    @Test
    fun releasesAnAbandonedPaymentListenerAndCompletesTheNextCheckout(): Unit {
        runBlocking {
            withTimeout(10000) {
                val inbox: PaymentInbox = PaymentInbox()
                val checkout: Checkout = Checkout(inbox)
                val abandoned: Deferred<Receipt> = async(start = CoroutineStart.UNDISPATCHED) {
                    checkout.complete("ORDER-101")
                }

                assertEquals(setOf("ORDER-101"), inbox.waitingOrders())
                abandoned.cancelAndJoin()

                assertTrue(inbox.waitingOrders().isEmpty())
                assertTrue(checkout.completedOrders.isEmpty())

                val accepted: Deferred<Receipt> = async(start = CoroutineStart.UNDISPATCHED) {
                    checkout.complete("ORDER-102")
                }
                assertEquals(setOf("ORDER-102"), inbox.waitingOrders())
                inbox.confirm("ORDER-102", Payment("PAY-202", 4250))

                assertEquals(Receipt("ORDER-102", "PAY-202", 4250), accepted.await())
                assertEquals(listOf("ORDER-102"), checkout.completedOrders)
                assertTrue(inbox.waitingOrders().isEmpty())
            }
        }
    }

    private class Checkout(private val inbox: PaymentInbox) {
        val completedOrders: MutableList<String> = mutableListOf()

        suspend fun complete(orderId: String): Receipt {
            val payment: Payment = inbox.listen(orderId).awaitSingle()
            completedOrders.add(orderId)
            return Receipt(orderId, payment.reference, payment.amountCents)
        }
    }

    private class PaymentInbox {
        private val pending: ConcurrentHashMap<String, Sinks.One<Payment>> = ConcurrentHashMap()

        fun listen(orderId: String): Mono<Payment> = Mono.using(
            {
                val signal: Sinks.One<Payment> = Sinks.one()
                check(pending.putIfAbsent(orderId, signal) == null) { "Checkout is already waiting" }
                Registration(orderId, signal)
            },
            { registration -> registration.signal.asMono() },
            { registration -> pending.remove(registration.orderId, registration.signal) }
        )

        fun confirm(orderId: String, payment: Payment): Unit {
            val signal: Sinks.One<Payment> = checkNotNull(pending[orderId])
            check(signal.tryEmitValue(payment) == Sinks.EmitResult.OK)
        }

        fun waitingOrders(): Set<String> = pending.keys.toSet()
    }

    private data class Registration(val orderId: String, val signal: Sinks.One<Payment>)

    private data class Payment(val reference: String, val amountCents: Int)

    private data class Receipt(val orderId: String, val paymentReference: String, val amountCents: Int)
}
