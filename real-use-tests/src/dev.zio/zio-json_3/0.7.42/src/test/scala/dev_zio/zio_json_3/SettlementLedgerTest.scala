/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_json_3

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import zio.json.*
import zio.json.ast.Json

/** Exercises arbitrary-precision numeric codecs for reconciling a settlement ledger. */
class SettlementLedgerTest {
  final case class Entry(reference: String, atomicUnits: BigInt, settlementUsd: BigDecimal)
  final case class Settlement(batch: String, atomicUnits: BigInt, settlementUsd: BigDecimal, entries: Int)

  given JsonCodec[Entry] = DeriveJsonCodec.gen[Entry]
  given JsonCodec[Settlement] = DeriveJsonCodec.gen[Settlement]

  @Test
  @Timeout(30)
  def reconcileLargeTransfersWithoutLosingUnitsOrCents(): Unit = {
    val incoming: String = """
      [
        {
          "reference": "deposit",
          "atomicUnits": 922337203685477580812345678901,
          "settlementUsd": 9007199254740993.01
        },
        {"reference": "adjustment", "atomicUnits": 17, "settlementUsd": 0.09},
        {
          "reference": "withdrawal",
          "atomicUnits": -922337203685477580812345678900,
          "settlementUsd": -9007199254740000.00
        }
      ]
      """
    val ledger: List[Entry] = incoming.fromJson[List[Entry]].fold(
      error => fail[List[Entry]](error),
      identity
    )
    assertEquals(
      List(
        Entry("deposit", BigInt("922337203685477580812345678901"), BigDecimal("9007199254740993.01")),
        Entry("adjustment", BigInt(17), BigDecimal("0.09")),
        Entry("withdrawal", BigInt("-922337203685477580812345678900"), BigDecimal("-9007199254740000.00"))
      ),
      ledger,
      "Individual transfers must retain all digits before reconciliation"
    )

    val settlement: Settlement = Settlement(
      "batch-42",
      ledger.map(_.atomicUnits).sum,
      ledger.map(_.settlementUsd).sum,
      ledger.size
    )
    assertEquals(BigInt(18), settlement.atomicUnits)
    assertEquals(BigDecimal("993.10"), settlement.settlementUsd)
    assertEquals(3, settlement.entries)

    val published: String = settlement.toJson
    val expected: String =
      """{"batch":"batch-42","atomicUnits":18,"settlementUsd":993.10,"entries":3}"""
    assertEquals(
      expected.fromJson[Json],
      published.fromJson[Json],
      "Settlement amounts must be JSON numbers, not quoted decimal strings"
    )
    assertEquals(Right(settlement), published.fromJson[Settlement])
    assertEquals(Right(ledger), ledger.toJson.fromJson[List[Entry]])

    val fractionalUnits: String =
      """{"reference":"invalid","atomicUnits":1.5,"settlementUsd":10.00}"""
    val invalidAmount: String =
      """{"reference":"invalid","atomicUnits":1,"settlementUsd":true}"""
    val rejectedUnits: Either[String, Entry] = fractionalUnits.fromJson[Entry]
    val rejectedAmount: Either[String, Entry] = invalidAmount.fromJson[Entry]
    assertTrue(rejectedUnits.isLeft, "Fractional atomic units must not be rounded into a transfer")
    assertTrue(rejectedAmount.isLeft, "A non-numeric amount must not enter the settlement ledger")
    assertTrue(rejectedAmount.swap.toOption.get.contains("settlementUsd"))
  }
}
