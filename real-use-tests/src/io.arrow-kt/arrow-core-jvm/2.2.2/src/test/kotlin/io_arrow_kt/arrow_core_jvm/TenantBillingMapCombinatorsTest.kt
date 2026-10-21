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
import arrow.core.combine
import arrow.core.filterIsInstance
import arrow.core.filterOption
import arrow.core.flatMapValues
import arrow.core.fold
import arrow.core.getOrNone
import arrow.core.mapValuesNotNull
import arrow.core.mapValuesOrAccumulate
import arrow.core.raise.ensure
import arrow.core.unzip
import arrow.core.zip
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Map transformation and merge combinators (`combine`, `fold`, multi-way `zip`,
 * `flatMapValues`, `mapValuesNotNull`, `filterIsInstance`, `filterOption`, `getOrNone`,
 * `unzip` and `mapValuesOrAccumulate` on `Map`).
 *
 * A SaaS billing service closes a month per SKU: usage meters from two nodes are merged
 * with `combine` and totalled with `fold`, each SKU is priced from the price book of the
 * region it is sold in through `flatMapValues`, charges come from a three-way `zip` of
 * usage, price and discount, and the finished invoice lines are split into net and tax
 * with `unzip`. Plan settings are read out of a heterogeneous map and raw quota strings
 * are parsed, while `mapValuesOrAccumulate` rejects a malformed import with every problem
 * listed.
 */
class TenantBillingMapCombinatorsTest {

    sealed interface PlanSetting {
        data class Flag(val enabled: Boolean) : PlanSetting

        data class Limit(val max: Int) : PlanSetting

        data class Label(val text: String) : PlanSetting
    }

    data class InvoiceLine(val units: Long, val unitPriceCents: Long, val discountPercent: Int)

    /** Prices a month of usage per SKU and splits each charge into net and tax. */
    class MonthlyBilling(
        private val skuRegion: Map<String, String>,
        private val priceBooks: Map<String, Map<String, Long>>,
        private val discounts: Map<String, Int>,
        private val taxPercent: Long,
    ) {
        fun mergedUsage(nodeA: Map<String, Long>, nodeB: Map<String, Long>): Map<String, Long> =
            nodeA.combine(nodeB) { first: Long, second: Long -> first + second }

        fun unitPrices(): Map<String, Long> =
            skuRegion.flatMapValues { (_, region): Map.Entry<String, String> ->
                priceBooks[region].orEmpty()
            }

        fun charges(usage: Map<String, Long>): Map<String, Long> =
            usage.zip(unitPrices(), discounts) { _: String, units: Long, price: Long, discount: Int ->
                units * price * (100 - discount) / 100
            }

        fun netAndTax(charges: Map<String, Long>): Pair<Map<String, Long>, Map<String, Long>> =
            charges.mapValues { (_, cents): Map.Entry<String, Long> ->
                val tax: Long = cents * taxPercent / 100
                cents to tax
            }.unzip()
    }

    private val billing: MonthlyBilling = MonthlyBilling(
        skuRegion = mapOf("api-calls" to "eu", "storage-gb" to "eu", "gpu-hours" to "us", "sms" to "apac"),
        priceBooks = mapOf(
            "eu" to mapOf("api-calls" to 2L, "storage-gb" to 25L, "gpu-hours" to 999L),
            "us" to mapOf("api-calls" to 3L, "gpu-hours" to 250L),
        ),
        discounts = mapOf("api-calls" to 10, "storage-gb" to 0, "gpu-hours" to 20, "sms" to 0),
        taxPercent = 20L,
    )

    private val nodeA: Map<String, Long> = mapOf("api-calls" to 12_000L, "storage-gb" to 40L, "gpu-hours" to 3L)
    private val nodeB: Map<String, Long> = mapOf("api-calls" to 8_000L, "gpu-hours" to 2L, "sms" to 150L)

    @Test
    fun mergesMetersFromBothNodesAndTotalsThem() {
        val usage: Map<String, Long> = billing.mergedUsage(nodeA, nodeB)

        assertEquals(
            mapOf("api-calls" to 20_000L, "storage-gb" to 40L, "gpu-hours" to 5L, "sms" to 150L),
            usage,
        )
        val totalUnits: Long = usage.fold(0L) { acc: Long, (_, units): Map.Entry<String, Long> -> acc + units }
        assertEquals(20_195L, totalUnits)
        assertEquals(Some(5L), usage.getOrNone("gpu-hours"))
        assertEquals(None, usage.getOrNone("bandwidth-gb"))
    }

    @Test
    fun pricesEachSkuFromItsOwnRegionAndDropsUnpricedOnes() {
        val prices: Map<String, Long> = billing.unitPrices()

        // gpu-hours is sold in "us", so the "eu" price never leaks in; "apac" has no book.
        assertEquals(mapOf("api-calls" to 2L, "storage-gb" to 25L, "gpu-hours" to 250L), prices)
    }

    @Test
    fun chargesOnlySkusThatHaveUsagePriceAndDiscountThenSplitsTax() {
        val charges: Map<String, Long> = billing.charges(billing.mergedUsage(nodeA, nodeB))

        assertEquals(mapOf("api-calls" to 36_000L, "storage-gb" to 1_000L, "gpu-hours" to 1_000L), charges)

        val (net: Map<String, Long>, tax: Map<String, Long>) = billing.netAndTax(charges)
        assertEquals(charges, net)
        assertEquals(mapOf("api-calls" to 7_200L, "storage-gb" to 200L, "gpu-hours" to 200L), tax)
        val invoiceTotal: Long = net.combine(tax) { cents: Long, vat: Long -> cents + vat }
            .fold(0L) { acc: Long, entry: Map.Entry<String, Long> -> acc + entry.value }
        assertEquals(45_600L, invoiceTotal)
    }

    @Test
    fun readsTypedPlanSettingsAndParsesRawQuotas() {
        val settings: Map<String, PlanSetting> = mapOf(
            "sso" to PlanSetting.Flag(true),
            "seats" to PlanSetting.Limit(25),
            "tier" to PlanSetting.Label("business"),
            "projects" to PlanSetting.Limit(100),
            "audit-log" to PlanSetting.Flag(false),
        )

        val limits: Map<String, PlanSetting.Limit> = settings.filterIsInstance<String, PlanSetting.Limit>()
        assertEquals(mapOf("seats" to 25, "projects" to 100), limits.mapValues { it.value.max })

        val enabledFlags: Map<String, Boolean> =
            settings.mapValuesNotNull { (_, setting): Map.Entry<String, PlanSetting> ->
                (setting as? PlanSetting.Flag)?.enabled?.takeIf { it }
            }
        assertEquals(mapOf("sso" to true), enabledFlags)

        val rawQuotas: Map<String, String> = mapOf("api-calls" to "50000", "storage-gb" to "unlimited", "sms" to "500")
        val quotas: Map<String, Long> = rawQuotas.mapValuesNotNull { it.value.toLongOrNull() }
        assertEquals(mapOf("api-calls" to 50_000L, "sms" to 500L), quotas)

        val overrides: Map<String, Option<Long>> = mapOf("api-calls" to Some(80_000L), "sms" to None)
        assertEquals(mapOf("api-calls" to 80_000L), overrides.filterOption())
    }

    @Test
    fun importsInvoiceLinesOrReportsEveryMalformedOne() {
        fun import(raw: Map<String, InvoiceLine>): Either<String, Map<String, Long>> =
            raw.mapValuesOrAccumulate({ first: String, second: String -> "$first; $second" }) {
                    (sku, line): Map.Entry<String, InvoiceLine> ->
                ensure(line.units >= 0) { "$sku: negative units" }
                ensure(line.discountPercent in 0..100) { "$sku: discount ${line.discountPercent}%" }
                line.units * line.unitPriceCents * (100 - line.discountPercent) / 100
            }

        val clean: Either<String, Map<String, Long>> = import(
            mapOf("api-calls" to InvoiceLine(1_000L, 2L, 10), "storage-gb" to InvoiceLine(4L, 25L, 0)),
        )
        assertEquals(Either.Right(mapOf("api-calls" to 1_800L, "storage-gb" to 100L)), clean)

        val broken: Either<String, Map<String, Long>> = import(
            mapOf(
                "api-calls" to InvoiceLine(-5L, 2L, 0),
                "storage-gb" to InvoiceLine(4L, 25L, 0),
                "gpu-hours" to InvoiceLine(1L, 250L, 140),
            ),
        )
        assertTrue(broken.isLeft())
        assertEquals("api-calls: negative units; gpu-hours: discount 140%", broken.leftOrNull())
    }
}
