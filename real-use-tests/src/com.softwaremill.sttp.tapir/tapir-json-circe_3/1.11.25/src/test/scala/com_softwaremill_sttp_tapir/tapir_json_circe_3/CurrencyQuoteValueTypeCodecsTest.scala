/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_json_circe_3

import com.sun.net.httpserver.HttpServer
import io.circe.Codec as CirceCodec
import io.circe.Decoder
import io.circe.Encoder
import io.circe.HCursor
import io.circe.Json
import io.circe.KeyDecoder
import io.circe.KeyEncoder
import io.circe.parser.parse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sttp.client3.HttpClientSyncBackend
import sttp.client3.Identity
import sttp.client3.Response
import sttp.client3.SttpBackend
import sttp.client3.basicRequest
import sttp.model.MediaType
import sttp.model.StatusCode
import sttp.model.Uri
import sttp.model.Uri.UriContext
import sttp.tapir.*
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.json.circe.*
import sttp.tapir.server.jdkhttp.JdkHttpServer

import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import scala.math.BigDecimal.RoundingMode

/**
 * Hand-written circe codecs for domain value types inside JSON bodies: a currency code wrapper
 * encoded as a bare string and used as a JSON object key, money encoded as one `"12.50 EUR"`
 * string, and an enum encoded by a lowercase code, each paired with a tapir schema describing the
 * same wire shape, on a live JDK HTTP server called through the sttp client interpreter.
 *
 * A currency desk publishes daily rate tables and quotes conversions with a tier-dependent fee.
 * The value types never appear as JSON objects on the wire, so a malformed amount or a lowercase
 * currency is rejected by the circe decoders before the desk sees the request.
 */
class CurrencyQuoteValueTypeCodecsTest {
  import CurrencyQuoteValueTypeCodecsTest.*

  private var server: HttpServer = null
  private var backend: SttpBackend[Identity, Any] = null
  private var baseUri: Uri = null
  private val desk: CurrencyDesk = new CurrencyDesk

  @BeforeEach
  def startServer(): Unit = {
    server = JdkHttpServer()
      .host("localhost")
      .port(0)
      .addEndpoints(desk.serverEndpoints)
      .start()
    backend = HttpClientSyncBackend()
    baseUri = uri"http://localhost:${server.getAddress.getPort}"
  }

  @AfterEach
  def stopServer(): Unit = {
    if (backend != null) backend.close()
    if (server != null) server.stop(0)
  }

  @Test
  def publishesARateTableAndQuotesAConversionThroughTheTypedClient(): Unit = {
    val client: SttpClientInterpreter = SttpClientInterpreter()
    val table: RateTable = RateTable(Eur, TradingDay, Map(Usd -> BigDecimal("1.0825"), Chf -> BigDecimal("0.9410")))

    val published: RateTable = client
      .toRequestThrowDecodeFailures(publishRates, Some(baseUri))
      .apply(table)
      .send(backend)
      .body
      .fold(error => throw new AssertionError(s"unexpected error $error"), identity)
    assertEquals(table, published)

    val fetched: Either[DeskError, RateTable] = client
      .toRequestThrowDecodeFailures(ratesForDay, Some(baseUri))
      .apply(TradingDay)
      .send(backend)
      .body
    assertEquals(Right(table), fetched)

    val quote: Quote = client
      .toRequestThrowDecodeFailures(quoteConversion, Some(baseUri))
      .apply(QuoteRequest(Money(BigDecimal("250.00"), Eur), Usd, Tier.Premium))
      .send(backend)
      .body
      .fold(error => throw new AssertionError(s"unexpected error $error"), identity)

    assertEquals(Money(BigDecimal("250.00"), Eur), quote.original)
    assertEquals(Money(BigDecimal("270.62"), Usd), quote.converted)
    assertEquals(Money(BigDecimal("0.27"), Usd), quote.fee)
    assertEquals(BigDecimal("1.0825"), quote.rate)
    assertEquals(Tier.Premium, quote.tier)
  }

  @Test
  def writesValueTypesAsStringsAndObjectKeysOnTheWire(): Unit = {
    desk.publish(RateTable(Eur, TradingDay, Map(Usd -> BigDecimal("1.0825"))))

    val rates: Response[Either[String, String]] = basicRequest
      .get(uri"$baseUri/rates/2026-10-16")
      .send(backend)
    assertEquals(StatusCode.Ok, rates.code)
    val ratesCursor: HCursor = parseBody(rates).hcursor
    assertEquals(Right("EUR"), ratesCursor.get[String]("base"))
    assertEquals(Right("2026-10-16"), ratesCursor.get[String]("day"))
    assertEquals(Right(List("USD")), ratesCursor.downField("rates").keys.map(_.toList).toRight(()))
    assertEquals(Right(BigDecimal("1.0825")), ratesCursor.downField("rates").get[BigDecimal]("USD"))

    val quote: Response[Either[String, String]] = basicRequest
      .post(uri"$baseUri/quotes")
      .contentType(MediaType.ApplicationJson)
      .body("""{"original":"1000.00 EUR","target":"USD","tier":"standard"}""")
      .send(backend)
    assertEquals(StatusCode.Ok, quote.code)
    val quoteCursor: HCursor = parseBody(quote).hcursor
    assertEquals(Right("1000.00 EUR"), quoteCursor.get[String]("original"))
    assertEquals(Right("1082.50 USD"), quoteCursor.get[String]("converted"))
    assertEquals(Right("10.82 USD"), quoteCursor.get[String]("fee"))
    assertEquals(Right("standard"), quoteCursor.get[String]("tier"))
  }

  @Test
  def rejectsMalformedValueTypesBeforeTheDeskQuotes(): Unit = {
    desk.publish(RateTable(Eur, TradingDay, Map(Usd -> BigDecimal("1.0825"))))

    val malformedAmount: Response[Either[String, String]] = postQuote(
      """{"original":"a thousand euros","target":"USD","tier":"standard"}"""
    )
    val lowercaseCurrency: Response[Either[String, String]] = postQuote(
      """{"original":"1000.00 EUR","target":"usd","tier":"standard"}"""
    )
    val unknownTier: Response[Either[String, String]] = postQuote(
      """{"original":"1000.00 EUR","target":"USD","tier":"platinum"}"""
    )

    assertEquals(StatusCode.BadRequest, malformedAmount.code)
    assertEquals(StatusCode.BadRequest, lowercaseCurrency.code)
    assertEquals(StatusCode.BadRequest, unknownTier.code)
    assertEquals(0, desk.quotesIssued)
  }

  @Test
  def reportsAMissingRateAsADeskErrorCarryingTheCurrencyCodes(): Unit = {
    desk.publish(RateTable(Eur, TradingDay, Map(Usd -> BigDecimal("1.0825"))))

    val response: Response[Either[DeskError, Quote]] = SttpClientInterpreter()
      .toRequestThrowDecodeFailures(quoteConversion, Some(baseUri))
      .apply(QuoteRequest(Money(BigDecimal("80.00"), Eur), Chf, Tier.Standard))
      .send(backend)

    assertEquals(StatusCode.UnprocessableEntity, response.code)
    assertEquals(Left(DeskError("no-rate", List(Eur, Chf))), response.body)
  }

  private def postQuote(body: String): Response[Either[String, String]] =
    basicRequest
      .post(uri"$baseUri/quotes")
      .contentType(MediaType.ApplicationJson)
      .body(body)
      .send(backend)

  private def parseBody(response: Response[Either[String, String]]): Json =
    parse(response.body.fold(identity, identity)).fold(throw _, identity)
}

object CurrencyQuoteValueTypeCodecsTest {

  /** An ISO 4217 code; on the wire it is the bare three-letter string, also as an object key. */
  final case class CurrencyCode(code: String)

  object CurrencyCode {
    private val Pattern: String = "^[A-Z]{3}$"

    def parse(raw: String): Either[String, CurrencyCode] =
      if (raw.matches(Pattern)) Right(CurrencyCode(raw)) else Left(s"'$raw' is not an ISO 4217 currency code")

    given Encoder[CurrencyCode] = Encoder.encodeString.contramap(_.code)
    given Decoder[CurrencyCode] = Decoder.decodeString.emap(parse)
    given KeyEncoder[CurrencyCode] = KeyEncoder.encodeKeyString.contramap(_.code)
    given KeyDecoder[CurrencyCode] = KeyDecoder.instance(raw => parse(raw).toOption)
    given Schema[CurrencyCode] = Schema
      .string[CurrencyCode]
      .validate(Validator.pattern[String](Pattern).contramap(_.code))
      .description("ISO 4217 currency code")
  }

  /** An amount with its currency; on the wire it is one string such as `12.50 EUR`. */
  final case class Money(amount: BigDecimal, currency: CurrencyCode)

  object Money {
    def parse(raw: String): Either[String, Money] = raw.split(' ') match {
      case Array(amount, currency) =>
        for {
          value <- amount.toDoubleOption.map(_ => BigDecimal(amount)).toRight(s"'$amount' is not an amount")
          code <- CurrencyCode.parse(currency)
        } yield Money(value, code)
      case _ => Left(s"'$raw' is not formatted as '<amount> <currency>'")
    }

    given Encoder[Money] = Encoder.encodeString.contramap(money =>
      s"${money.amount.setScale(2, RoundingMode.HALF_EVEN).bigDecimal.toPlainString} ${money.currency.code}"
    )
    given Decoder[Money] = Decoder.decodeString.emap(parse)
    given Schema[Money] = Schema.string[Money].description("Amount and currency, such as '12.50 EUR'")
  }

  /** The pricing tier; on the wire it is the lowercase code. */
  enum Tier(val code: String) {
    case Standard extends Tier("standard")
    case Premium extends Tier("premium")
  }

  object Tier {
    given Encoder[Tier] = Encoder.encodeString.contramap(_.code)
    given Decoder[Tier] = Decoder.decodeString.emap(raw =>
      Tier.values.find(_.code == raw).toRight(s"'$raw' is not a pricing tier")
    )
    given Schema[Tier] = Schema.derivedEnumeration[Tier](encode = Some((tier: Tier) => tier.code))
  }

  given Schema[Map[CurrencyCode, BigDecimal]] = Schema.schemaForMap[CurrencyCode, BigDecimal](_.code)

  final case class RateTable(base: CurrencyCode, day: LocalDate, rates: Map[CurrencyCode, BigDecimal])
      derives CirceCodec.AsObject, Schema

  final case class QuoteRequest(original: Money, target: CurrencyCode, tier: Tier) derives CirceCodec.AsObject, Schema

  final case class Quote(original: Money, converted: Money, fee: Money, rate: BigDecimal, tier: Tier)
      derives CirceCodec.AsObject, Schema

  final case class DeskError(reason: String, currencies: List[CurrencyCode]) derives CirceCodec.AsObject, Schema

  val Eur: CurrencyCode = CurrencyCode("EUR")
  val Usd: CurrencyCode = CurrencyCode("USD")
  val Chf: CurrencyCode = CurrencyCode("CHF")
  val TradingDay: LocalDate = LocalDate.of(2026, 10, 16)

  val publishRates: PublicEndpoint[RateTable, DeskError, RateTable, Any] = endpoint.put
    .in("rates")
    .in(jsonBody[RateTable])
    .out(jsonBody[RateTable])
    .errorOut(statusCode(StatusCode.BadRequest))
    .errorOut(jsonBody[DeskError])

  val ratesForDay: PublicEndpoint[LocalDate, DeskError, RateTable, Any] = endpoint.get
    .in("rates" / path[LocalDate]("day"))
    .out(jsonBody[RateTable])
    .errorOut(statusCode(StatusCode.NotFound))
    .errorOut(jsonBody[DeskError])

  val quoteConversion: PublicEndpoint[QuoteRequest, DeskError, Quote, Any] = endpoint.post
    .in("quotes")
    .in(jsonBody[QuoteRequest])
    .out(jsonBody[Quote])
    .errorOut(statusCode(StatusCode.UnprocessableEntity))
    .errorOut(jsonBody[DeskError])

  /** In-memory rate tables keyed by trading day, quoting against the latest one. */
  final class CurrencyDesk {
    private val tables: ConcurrentHashMap[LocalDate, RateTable] = new ConcurrentHashMap[LocalDate, RateTable]()
    @volatile private var latest: Option[RateTable] = None
    @volatile private var issued: Int = 0

    def quotesIssued: Int = issued

    def publish(table: RateTable): RateTable = synchronized {
      tables.put(table.day, table)
      latest = Some(table)
      table
    }

    def quote(request: QuoteRequest): Either[DeskError, Quote] = synchronized {
      val rate: Option[BigDecimal] = latest
        .filter(_.base == request.original.currency)
        .flatMap(_.rates.get(request.target))
      rate match {
        case None => Left(DeskError("no-rate", List(request.original.currency, request.target)))
        case Some(value) =>
          val converted: BigDecimal = (request.original.amount * value).setScale(2, RoundingMode.HALF_EVEN)
          val feeRate: BigDecimal = if (request.tier == Tier.Premium) BigDecimal("0.001") else BigDecimal("0.01")
          val fee: BigDecimal = (converted * feeRate).setScale(2, RoundingMode.HALF_EVEN)
          issued += 1
          Right(
            Quote(request.original, Money(converted, request.target), Money(fee, request.target), value, request.tier)
          )
      }
    }

    val serverEndpoints = List(
      publishRates.handle(table =>
        if (table.rates.contains(table.base)) Left(DeskError("base-in-rates", List(table.base)))
        else Right(publish(table))
      ),
      ratesForDay.handle(day => Option(tables.get(day)).toRight(DeskError("no-table", Nil))),
      quoteConversion.handle(quote)
    )
  }
}
