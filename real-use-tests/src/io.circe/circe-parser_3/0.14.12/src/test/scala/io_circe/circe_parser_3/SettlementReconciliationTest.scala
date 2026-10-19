/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_parser_3

import io.circe.Decoder
import io.circe.parser.decode
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises exact JSON numeric decoding for financial settlement reconciliation. */
@Timeout(60)
class SettlementReconciliationTest {
  private final case class Posting(externalId: BigInt, amount: BigDecimal)

  private given Decoder[Posting] =
    Decoder.forProduct2("externalId", "amount")(Posting.apply)

  @Test
  def reconcilesLargeCreditsAndRefundsWithoutFloatingPointRounding(): Unit = {
    val settlement: String =
      """[
        |  {"externalId": 9223372036854775808123, "amount": 9007199254740993.01},
        |  {"externalId": 9223372036854775808124, "amount": 9e-2},
        |  {"externalId": 9223372036854775808125, "amount": -9007199254740990.10}
        |]""".stripMargin

    val postings: Vector[Posting] =
      decode[Vector[Posting]](settlement).fold(error => throw error, identity)

    assertEquals(
      Vector(
        Posting(BigInt("9223372036854775808123"), BigDecimal("9007199254740993.01")),
        Posting(BigInt("9223372036854775808124"), BigDecimal("0.09")),
        Posting(BigInt("9223372036854775808125"), BigDecimal("-9007199254740990.10"))
      ),
      postings
    )

    val postingIndex: Map[BigInt, BigDecimal] =
      postings.map(posting => posting.externalId -> posting.amount).toMap
    assertEquals(3, postingIndex.size)
    assertEquals(Some(BigDecimal("0.09")), postingIndex.get(BigInt("9223372036854775808124")))

    val netSettlement: BigDecimal = postings.map(_.amount).sum
    assertEquals(BigDecimal("3.00"), netSettlement)
    assertEquals("3.00", netSettlement.setScale(2).bigDecimal.toPlainString)
  }

  @Test
  def rejectsTheWholeBatchRatherThanTruncatingAFractionalPostingIdentifier(): Unit = {
    val settlement: String =
      """[
        |  {"externalId": 9223372036854775808123, "amount": 12.50},
        |  {"externalId": 9223372036854775808124.5, "amount": -2.50}
        |]""".stripMargin

    val ledger: Map[BigInt, BigDecimal] = Map(BigInt("100") -> BigDecimal("7.50"))
    val updatedLedger: Either[io.circe.Error, Map[BigInt, BigDecimal]] =
      decode[Vector[Posting]](settlement).map { postings =>
        ledger ++ postings.map(posting => posting.externalId -> posting.amount)
      }

    assertTrue(updatedLedger.isLeft, "Fractional identifiers must not be silently rounded")
    assertEquals(ledger, updatedLedger.getOrElse(ledger))
  }
}
