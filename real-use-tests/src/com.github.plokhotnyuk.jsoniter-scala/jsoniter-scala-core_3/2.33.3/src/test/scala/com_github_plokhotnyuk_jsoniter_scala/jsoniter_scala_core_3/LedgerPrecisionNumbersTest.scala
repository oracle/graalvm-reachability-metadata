/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_plokhotnyuk_jsoniter_scala.jsoniter_scala_core_3

import com.github.plokhotnyuk.jsoniter_scala.core.JsonReader
import com.github.plokhotnyuk.jsoniter_scala.core.JsonReaderException
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.core.JsonWriter
import com.github.plokhotnyuk.jsoniter_scala.core.ReaderConfig
import com.github.plokhotnyuk.jsoniter_scala.core.readFromString
import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import com.github.plokhotnyuk.jsoniter_scala.macros.CodecMakerConfig
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import com.github.plokhotnyuk.jsoniter_scala.macros.stringified
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

import java.math.MathContext

import scala.collection.immutable.SortedMap

import LedgerPrecisionNumbersTest.*

/**
 * Arbitrary-precision and stringified numbers with configurable precision, scale and digit
 * limits: a ledger service exchanges transfers whose amounts and nonces exceed `Double` and
 * `Long`, quotes them as JSON strings for JavaScript clients, rounds over-precise amounts,
 * rejects numbers that exceed its digit and scale limits, and keys balances by big account ids.
 */
class LedgerPrecisionNumbersTest {

  private val readerWithoutDump: ReaderConfig = ReaderConfig.withAppendHexDumpToParseException(false)

  @Test
  def stringifiedTransfersKeepEveryDigitForJavaScriptClients(): Unit = {
    val transfer: Transfer = Transfer(
      9007199254740993L,
      BigDecimal("12345678901234567890.123456789"),
      BigInt("340282366920938463463374607431768211457"),
      0.1
    )

    val json: String = writeToString(transfer)

    assertEquals(
      """{"id":"9007199254740993","amount":"12345678901234567890.123456789",""" +
        """"nonce":"340282366920938463463374607431768211457","feeRate":"0.1"}""",
      json
    )
    assertEquals(transfer, readFromString[Transfer](json))
  }

  @Test
  def stringifiedFieldsRejectBareNumbers(): Unit = {
    val error: JsonReaderException = assertThrows(
      classOf[JsonReaderException],
      () => readFromString[Transfer]("""{"id":42,"amount":"1e3","nonce":"7","feeRate":"2.5E-3"}""", readerWithoutDump)
    )

    assertEquals("expected '\"', offset: 0x00000006", error.getMessage)
    assertEquals(
      Transfer(42L, BigDecimal(1000), BigInt(7), 0.0025),
      readFromString[Transfer]("""{"id":"42","amount":"1e3","nonce":"7","feeRate":"2.5E-3"}""")
    )
  }

  @Test
  def overPreciseAmountsAreRoundedToTheConfiguredPrecision(): Unit = {
    val quote: Quote = readFromString[Quote]("""{"rate":3.14159265358979323846264338327950288419716939937510}""")

    assertEquals(BigDecimal("3.1415926535897932384626433832795028842"), quote.rate)
    assertEquals(38, quote.rate.precision)
    assertEquals(MathContext.DECIMAL128.getRoundingMode, quote.rate.mc.getRoundingMode)
  }

  @Test
  def numbersBeyondTheDigitLimitsAreRejected(): Unit = {
    val tooManyDigits: String = "1" * 41
    val amountError: JsonReaderException = assertThrows(
      classOf[JsonReaderException],
      () => readFromString[Limited](s"""{"amount":$tooManyDigits,"nonce":"1"}""", readerWithoutDump)
    )
    val nonceError: JsonReaderException = assertThrows(
      classOf[JsonReaderException],
      () => readFromString[Limited](s"""{"amount":1,"nonce":"$tooManyDigits"}""", readerWithoutDump)
    )

    assertEquals("value exceeds limit for number of digits, offset: 0x00000031", amountError.getMessage)
    assertEquals("value exceeds limit for number of digits, offset: 0x0000003c", nonceError.getMessage)
    val accepted: Limited = readFromString[Limited](s"""{"amount":${"1" * 39},"nonce":"${"9" * 39}"}""")
    // Within the digit limit, amounts are still rounded to the default 34-digit precision.
    assertEquals(Limited(BigDecimal("1.111111111111111111111111111111111E+38"), BigInt("9" * 39)), accepted)
    assertEquals(34, accepted.amount.precision)
  }

  @Test
  def amountsBeyondTheScaleLimitAreRejected(): Unit = {
    val error: JsonReaderException = assertThrows(
      classOf[JsonReaderException],
      () => readFromString[Limited]("""{"amount":1e-50,"nonce":"1"}""", readerWithoutDump)
    )

    assertEquals("value exceeds limit for scale, offset: 0x0000000e", error.getMessage)
    assertEquals(BigDecimal("1E-19"), readFromString[Limited]("""{"amount":1e-19,"nonce":"1"}""").amount)
  }

  @Test
  def balancesAreKeyedByBigAccountNumbersAndQuotedAmounts(): Unit = {
    val balances: Balances = Balances(
      SortedMap(
        BigInt("100000000000000000000001") -> BigDecimal("-0.000001"),
        BigInt("999999999999999999999999") -> BigDecimal("12500000000000000000000.50")
      )
    )

    val json: String = writeToString(balances)

    assertEquals(
      """{"100000000000000000000001":"-0.000001","999999999999999999999999":"12500000000000000000000.50"}""",
      json
    )
    val decoded: Balances = readFromString[Balances](json)
    assertEquals(balances, decoded)
    assertEquals(BigDecimal("12500000000000000000000.499999"), decoded.total)
  }

  @Test
  def balanceAmountsMustBeQuoted(): Unit = {
    val error: JsonReaderException = assertThrows(
      classOf[JsonReaderException],
      () => readFromString[Balances]("""{"1":2.5}""", readerWithoutDump)
    )

    assertEquals("expected '\"', offset: 0x00000005", error.getMessage)
  }
}

object LedgerPrecisionNumbersTest {

  final case class Transfer(
    @stringified id: Long,
    @stringified amount: BigDecimal,
    @stringified nonce: BigInt,
    @stringified feeRate: Double
  )

  final case class Quote(rate: BigDecimal)

  final case class Limited(amount: BigDecimal, @stringified nonce: BigInt)

  /** Account balances, ordered by account number. */
  final case class Balances(byAccount: SortedMap[BigInt, BigDecimal]) {
    def total: BigDecimal = byAccount.values.sum
  }

  /** Every numeric field travels as a JSON string so browsers never lose precision. */
  given transferCodec: JsonValueCodec[Transfer] = JsonCodecMaker.make

  /** Rounds rates to 38 significant digits instead of the default 34. */
  given quoteCodec: JsonValueCodec[Quote] =
    JsonCodecMaker.make(CodecMakerConfig.withBigDecimalPrecision(38))

  /** Accepts untrusted amounts below 40 digits and scale 20, and nonces below 40 digits. */
  given limitedCodec: JsonValueCodec[Limited] =
    JsonCodecMaker.make(
      CodecMakerConfig
        .withBigDecimalDigitsLimit(40)
        .withBigDecimalScaleLimit(20)
        .withBigIntDigitsLimit(40)
    )

  /** Writes balances as an object keyed by account number with quoted, unrounded amounts. */
  given balancesCodec: JsonValueCodec[Balances] = new JsonValueCodec[Balances] {
    private val keyDigitsLimit: Int = 30
    private val amountScaleLimit: Int = 18
    private val amountDigitsLimit: Int = 40

    override def nullValue: Balances = null

    override def decodeValue(in: JsonReader, default: Balances): Balances =
      if (in.isNextToken('{')) {
        val entries: collection.mutable.Builder[(BigInt, BigDecimal), SortedMap[BigInt, BigDecimal]] =
          SortedMap.newBuilder
        if (!in.isNextToken('}')) {
          in.rollbackToken()
          while ({
            val account: BigInt = in.readKeyAsBigInt(keyDigitsLimit)
            val amount: BigDecimal =
              in.readStringAsBigDecimal(null, MathContext.UNLIMITED, amountScaleLimit, amountDigitsLimit)
            entries += account -> amount
            in.isNextToken(',')
          }) ()
          if (!in.isCurrentToken('}')) in.objectEndOrCommaError()
        }
        Balances(entries.result())
      } else in.readNullOrTokenError(default, '{')

    override def encodeValue(x: Balances, out: JsonWriter): Unit = {
      out.writeObjectStart()
      x.byAccount.foreach { (account: BigInt, amount: BigDecimal) =>
        out.writeKey(account)
        out.writeValAsString(amount)
      }
      out.writeObjectEnd()
    }
  }
}
