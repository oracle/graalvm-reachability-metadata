/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_core_jvm

import arrow.core.Ior
import arrow.core.align
import arrow.core.interleave
import arrow.core.left
import arrow.core.padZip
import arrow.core.right
import arrow.core.rightPadZip
import arrow.core.salign
import arrow.core.separateEither
import arrow.core.unalign
import arrow.core.zip
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Collection alignment combinators on `Map` and `Iterable` (`align`, `padZip`, `salign`,
 * `unalign`, `zip`).
 *
 * A warehouse reconciles its physical stock count against the quantities booked in the ERP
 * ledger. The two snapshots rarely hold the same SKUs: `align` pairs them key by key into
 * `Ior` values, so every SKU lands in exactly one bucket (only counted, only booked, or both).
 * Counts from two shifts are merged with `salign`, booked-versus-counted deltas come from
 * `zip`, and the outbound dock lines up expected and scanned parcels with the list variants
 * of the same combinators.
 */
class StockReconciliationAlignmentTest {

    sealed interface Finding {
        val sku: String

        data class Matched(override val sku: String, val quantity: Int) : Finding

        data class CountMismatch(override val sku: String, val counted: Int, val booked: Int) :
            Finding

        data class Unbooked(override val sku: String, val counted: Int) : Finding

        data class Missing(override val sku: String, val booked: Int) : Finding
    }

    data class DockLine(val position: Int, val expected: String?, val scanned: String?)

    private val morningShift: Map<String, Int> = mapOf("SKU-A" to 6, "SKU-B" to 4)

    private val eveningShift: Map<String, Int> = mapOf("SKU-A" to 4, "SKU-C" to 7)

    private val ledger: Map<String, Int> = mapOf("SKU-A" to 10, "SKU-B" to 6, "SKU-D" to 3)

    private fun counted(): Map<String, Int> = morningShift.salign(eveningShift, Int::plus)

    private fun reconcile(counted: Map<String, Int>, booked: Map<String, Int>): Map<String, Finding> =
        counted.align(booked) { (sku, entry) ->
            when (entry) {
                is Ior.Left -> Finding.Unbooked(sku, entry.value)
                is Ior.Right -> Finding.Missing(sku, entry.value)
                is Ior.Both ->
                    if (entry.leftValue == entry.rightValue) {
                        Finding.Matched(sku, entry.leftValue)
                    } else {
                        Finding.CountMismatch(sku, entry.leftValue, entry.rightValue)
                    }
            }
        }

    @Test
    fun shiftCountsAreMergedPerSku() {
        assertEquals(mapOf("SKU-A" to 10, "SKU-B" to 4, "SKU-C" to 7), counted())
    }

    @Test
    fun everySkuLandsInExactlyOneReconciliationBucket() {
        val findings: Map<String, Finding> = reconcile(counted(), ledger)

        assertEquals(
            mapOf(
                "SKU-A" to Finding.Matched("SKU-A", 10),
                "SKU-B" to Finding.CountMismatch("SKU-B", counted = 4, booked = 6),
                "SKU-C" to Finding.Unbooked("SKU-C", 7),
                "SKU-D" to Finding.Missing("SKU-D", 3),
            ),
            findings,
        )
        val needsAttention: List<String> =
            findings.values.filterNot { it is Finding.Matched }.map { it.sku }.sorted()
        assertEquals(listOf("SKU-B", "SKU-C", "SKU-D"), needsAttention)
    }

    @Test
    fun alignedSnapshotSplitsBackIntoBothSources() {
        val stock: Map<String, Int> = counted()
        val aligned: Map<String, Ior<Int, Int>> = stock.align(ledger)

        assertEquals(Ior.Both(4, 6), aligned["SKU-B"])
        assertEquals(Ior.Left(7), aligned["SKU-C"])
        assertEquals(Ior.Right(3), aligned["SKU-D"])

        val (countedBack: Map<String, Int>, bookedBack: Map<String, Int>) = aligned.unalign()
        assertEquals(stock, countedBack)
        assertEquals(ledger, bookedBack)
    }

    @Test
    fun deltasCoverOnlySkusPresentInBothSnapshots() {
        val deltas: Map<String, Int> = counted().zip(ledger) { _, have, booked -> have - booked }
        assertEquals(mapOf("SKU-A" to 0, "SKU-B" to -2), deltas)

        val adjustments: Map<String, String> =
            counted().padZip(
                ledger,
                { sku, have -> "book $have of $sku" },
                { sku, booked -> "write off $booked of $sku" },
                { sku, have, booked -> "adjust $sku by ${have - booked}" },
            )
        assertEquals(
            mapOf(
                "SKU-A" to "adjust SKU-A by 0",
                "SKU-B" to "adjust SKU-B by -2",
                "SKU-C" to "book 7 of SKU-C",
                "SKU-D" to "write off 3 of SKU-D",
            ),
            adjustments,
        )
    }

    @Test
    fun dockLinesUpExpectedAndScannedParcels() {
        val expected: List<String> = listOf("P-100", "P-101", "P-102")
        val scanned: List<String> = listOf("P-100", "P-105", "P-102", "P-107")

        val lines: List<DockLine> =
            expected.padZip(scanned).mapIndexed { index, (want, got) -> DockLine(index + 1, want, got) }
        assertEquals(
            listOf(
                DockLine(1, "P-100", "P-100"),
                DockLine(2, "P-101", "P-105"),
                DockLine(3, "P-102", "P-102"),
                DockLine(4, null, "P-107"),
            ),
            lines,
        )

        val positional: List<Ior<String, String>> = expected.align(scanned)
        assertEquals(Ior.Right("P-107"), positional.last())
        val (expectedBack: List<String?>, scannedBack: List<String?>) = positional.unalign()
        assertEquals(listOf("P-100", "P-101", "P-102", null), expectedBack)
        assertEquals(scanned, scannedBack)

        val unscanned: List<Pair<String, String?>> = expected.rightPadZip(scanned.take(2))
        assertEquals(listOf("P-100" to "P-100", "P-101" to "P-105", "P-102" to null), unscanned)
    }

    @Test
    fun pickersAreInterleavedAndBadBarcodesSeparated() {
        val pickerOne: List<String> = listOf("SKU-A", "SKU-B", "SKU-C")
        val pickerTwo: List<String> = listOf("SKU-D", "SKU-E", "SKU-F", "SKU-G")
        assertEquals(
            listOf("SKU-A", "SKU-D", "SKU-B", "SKU-E", "SKU-C", "SKU-F", "SKU-G"),
            pickerOne.interleave(pickerTwo),
        )

        val barcodes: List<String> = listOf("4006381333931", "bad-scan", "5901234123457", "")
        val (rejected: List<String>, accepted: List<Long>) =
            barcodes.separateEither { code: String ->
                if (code.length == 13 && code.all(Char::isDigit)) {
                    code.toLong().right()
                } else {
                    "rejected '$code'".left()
                }
            }
        assertEquals(listOf("rejected 'bad-scan'", "rejected ''"), rejected)
        assertEquals(listOf(4006381333931L, 5901234123457L), accepted)
    }
}
