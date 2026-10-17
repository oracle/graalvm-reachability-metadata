/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_core_jvm

import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.filterOption
import arrow.core.firstOrNone
import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.none
import arrow.core.raise.nullable
import arrow.core.raise.option
import arrow.core.singleOrNone
import arrow.core.some
import arrow.core.toOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The `option {}` and `nullable {}` DSLs over `SingletonRaise` together with `Option` combinators.
 *
 * A storefront checkout looks up everything it needs from in-memory stores where any piece
 * may be absent: the customer, their default address, a shippable destination country, a
 * loyalty discount. The lookup chain is written once in `option {}` (or `nullable {}` when
 * the caller wants a plain nullable value), binding `Option` and nullable values alike and
 * short-circuiting to `None` on the first gap. A missing default address is recovered with
 * the first shippable address on file, batch lookups use `bindAll`, and the results
 * are turned into user-facing text with `map`, `filter`, `fold`, `getOrElse` and `toEither`.
 */
class CheckoutAddressOptionLookupTest {

    data class Address(val id: String, val street: String, val city: String, val country: String)

    data class Customer(
        val id: String,
        val name: String,
        val defaultAddressId: String?,
        val addresses: List<Address>,
        val loyaltyCode: String?,
    )

    private val shippableCountries: Set<String> = setOf("DE", "FR", "NL")

    private val promotions: Map<String, Int> = mapOf("GOLD" to 15, "SILVER" to 5, "BROKEN" to 90)

    private val customers: Map<String, Customer> = listOf(
        Customer(
            id = "c-1",
            name = "Ada",
            defaultAddressId = "a-2",
            addresses = listOf(
                Address("a-1", "Hauptstrasse 1", "Berlin", "DE"),
                Address("a-2", "Rue de Rivoli 7", "Paris", "FR"),
            ),
            loyaltyCode = "GOLD",
        ),
        Customer(
            id = "c-2",
            name = "Linus",
            defaultAddressId = null,
            addresses = listOf(
                Address("a-3", "Main Street 4", "Boston", "US"),
                Address("a-4", "Damrak 12", "Amsterdam", "NL"),
            ),
            loyaltyCode = "BROKEN",
        ),
        Customer(
            id = "c-3",
            name = "Grace",
            defaultAddressId = "a-5",
            addresses = listOf(Address("a-5", "Broadway 1", "New York", "US")),
            loyaltyCode = null,
        ),
    ).associateBy { it.id }

    private fun findCustomer(id: String): Option<Customer> = customers.getOrNone(id)

    private fun shippable(address: Address): Boolean = address.country in shippableCountries

    /** Resolves the delivery address, falling back to the first shippable one on file. */
    private fun deliveryAddress(customerId: String): Option<Address> = option {
        val customer: Customer = findCustomer(customerId).bind()
        recover({
            val addressId: String = customer.defaultAddressId.bind()
            val address: Address = customer.addresses.firstOrNone { it.id == addressId }.bind()
            ensure(shippable(address))
            address
        }) {
            customer.addresses.firstOrNone(::shippable).bind()
        }
    }

    /** Loyalty discount as a plain nullable percentage, rejecting out-of-policy codes. */
    private fun discountPercent(customerId: String): Int? = nullable {
        val customer: Customer = findCustomer(customerId).bind()
        val code: String = ensureNotNull(customer.loyaltyCode)
        val percent: Int = promotions[code].bind()
        ensure(percent in 1..50)
        percent
    }

    private fun shippingLabel(customerId: String): String =
        option {
            val customer: Customer = findCustomer(customerId).bind()
            val address: Address = deliveryAddress(customerId).bind()
            "${customer.name}, ${address.street}, ${address.city} (${address.country})"
        }.getOrElse { "no deliverable address for $customerId" }

    @Test
    fun defaultAddressIsUsedWhenItIsShippable() {
        assertEquals(Some(Address("a-2", "Rue de Rivoli 7", "Paris", "FR")), deliveryAddress("c-1"))
        assertEquals("Ada, Rue de Rivoli 7, Paris (FR)", shippingLabel("c-1"))
    }

    @Test
    fun missingDefaultAddressIsRecoveredWithTheFirstShippableOne() {
        val address: Option<Address> = deliveryAddress("c-2")

        assertTrue(address.isSome { it.country == "NL" })
        assertEquals("Linus, Damrak 12, Amsterdam (NL)", shippingLabel("c-2"))
    }

    @Test
    fun unshippableOrUnknownCustomersHaveNoDeliveryAddress() {
        assertEquals(None, deliveryAddress("c-3"))
        assertEquals(none<Address>(), deliveryAddress("c-404"))
        assertEquals("no deliverable address for c-3", shippingLabel("c-3"))

        val reason: Either<String, Address> =
            deliveryAddress("c-404").toEither { "unknown customer c-404" }
        assertEquals(Either.Left("unknown customer c-404"), reason)
    }

    @Test
    fun loyaltyDiscountIsANullableLookupChain() {
        assertEquals(15, discountPercent("c-1"))
        assertNull(discountPercent("c-2"), "a 90% code is outside the discount policy")
        assertNull(discountPercent("c-3"), "no loyalty code")
        assertNull(discountPercent("c-404"), "unknown customer")

        val total: Int = 200
        val payable: Int = discountPercent("c-1").toOption()
            .map { percent -> total - total * percent / 100 }
            .getOrElse { total }
        assertEquals(170, payable)
    }

    @Test
    fun batchLookupBindsEveryOptionOrFailsAsAWhole() {
        val allKnown: Option<List<String>> = option {
            listOf("c-1", "c-2").map(::findCustomer).bindAll().map { it.name }
        }
        val oneMissing: Option<List<String>> = option {
            listOf("c-1", "c-404").map(::findCustomer).bindAll().map { it.name }
        }

        assertEquals(Some(listOf("Ada", "Linus")), allKnown)
        assertEquals(None, oneMissing)
    }

    @Test
    fun deliveryReportSkipsCustomersWithoutAnAddress() {
        val ids: List<String> = listOf("c-1", "c-2", "c-3", "c-404")
        val cities: List<String> = ids.map { id -> deliveryAddress(id).map { it.city } }.filterOption()
        assertEquals(listOf("Paris", "Amsterdam"), cities)

        val onlyFrench: Option<Address> = ids
            .map(::deliveryAddress)
            .filterOption()
            .singleOrNone { it.country == "FR" }
        val summary: String = onlyFrench
            .filter { it.city == "Paris" }
            .fold({ "no Paris delivery" }, { "Paris delivery to ${it.street}" })
        assertEquals("Paris delivery to Rue de Rivoli 7", summary)

        val visited: MutableList<String> = mutableListOf()
        "c-2".some()
            .flatMap(::deliveryAddress)
            .onSome { visited += it.id }
            .onNone { visited += "none" }
        none<String>()
            .flatMap(::deliveryAddress)
            .onSome { visited += it.id }
            .onNone { visited += "none" }
        assertEquals(listOf("a-4", "none"), visited)
    }
}
