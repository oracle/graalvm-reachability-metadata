/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_json_circe_3

import com.sun.net.httpserver.HttpServer
import io.circe.Codec as CirceCodec
import io.circe.HCursor
import io.circe.Json
import io.circe.derivation.Configuration as CirceConfiguration
import io.circe.derivation.ConfiguredCodec
import io.circe.parser.parse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
import sttp.tapir.generic.Configuration as TapirConfiguration
import sttp.tapir.json.circe.*
import sttp.tapir.server.jdkhttp.JdkHttpServer

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Polymorphic JSON bodies and status-selected JSON error variants: a sealed payment-method
 * hierarchy encoded by circe with a discriminator field that the tapir schema declares too, and
 * `oneOf` error outputs that pick a circe-encoded error type by HTTP status code.
 *
 * A payment service accepts a card, a bank transfer or a wallet as the payment method, stores the
 * payment and returns it. Business failures come back as different JSON error shapes with their
 * own status codes, and the client built from the same endpoints decodes each of them back into
 * the matching error type over real HTTP on a JDK HTTP server.
 */
class PaymentMethodPolymorphicJsonTest {
  import PaymentMethodPolymorphicJsonTest.*

  private var server: HttpServer = null
  private var backend: SttpBackend[Identity, Any] = null
  private var baseUri: Uri = null
  private val ledger: PaymentLedger = new PaymentLedger

  @BeforeEach
  def startServer(): Unit = {
    server = JdkHttpServer()
      .host("localhost")
      .port(0)
      .addEndpoints(ledger.serverEndpoints)
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
  def roundTripsEveryPaymentMethodSubtype(): Unit = {
    val client: SttpClientInterpreter = SttpClientInterpreter()
    val methods: List[PaymentMethod] = List(
      PaymentMethod.Card("4242", "visa"),
      PaymentMethod.BankTransfer("DE89370400440532013000", Some("Invoice 2026-17")),
      PaymentMethod.Wallet("paypal", "ada@example.com")
    )

    val payments: List[Payment] = methods.map { method =>
      client
        .toRequestThrowDecodeFailures(createPayment, Some(baseUri))
        .apply(PaymentRequest(2500L, "EUR", method))
        .send(backend)
        .body
        .fold(error => throw new AssertionError(s"unexpected error $error"), identity)
    }

    assertEquals(methods, payments.map(_.method))
    assertEquals(List(1L, 2L, 3L), payments.map(_.id))
    assertTrue(payments.forall(_.status == "captured"))

    val transfer: Either[PaymentError, Payment] = client
      .toRequestThrowDecodeFailures(getPayment, Some(baseUri))
      .apply(2L)
      .send(backend)
      .body
    assertEquals(Right(payments(1)), transfer)
  }

  @Test
  def writesAndReadsTheDiscriminatorFieldOnTheWire(): Unit = {
    val raw: Response[Either[String, String]] = basicRequest
      .post(uri"$baseUri/payments")
      .contentType(MediaType.ApplicationJson)
      .body("""{"amountCents":1200,"currency":"EUR","method":{"kind":"bank-transfer","iban":"NL91ABNA0417164300"}}""")
      .send(backend)

    assertEquals(StatusCode.Created, raw.code)
    assertTrue(raw.header("Content-Type").exists(_.startsWith("application/json")))
    val json: Json = parse(raw.body.fold(identity, identity)).fold(throw _, identity)
    val method: HCursor = json.hcursor.downField("method").success.getOrElse(throw new AssertionError(json.noSpaces))
    assertEquals(Right("bank-transfer"), method.get[String]("kind"))
    assertEquals(Right("NL91ABNA0417164300"), method.get[String]("iban"))
    assertEquals(Right(None), method.get[Option[String]]("reference"))
    assertEquals(
      Some(PaymentMethod.BankTransfer("NL91ABNA0417164300", None)),
      ledger.find(1L).map(_.method)
    )
  }

  @Test
  def decodesEachErrorVariantByItsStatusCode(): Unit = {
    val client: SttpClientInterpreter = SttpClientInterpreter()

    val overLimit: Response[Either[PaymentError, Payment]] = client
      .toRequestThrowDecodeFailures(createPayment, Some(baseUri))
      .apply(PaymentRequest(250000L, "EUR", PaymentMethod.Card("0005", "mastercard")))
      .send(backend)
    assertEquals(StatusCode.PaymentRequired, overLimit.code)
    assertEquals(Left(PaymentError.InsufficientFunds(PaymentLedger.LimitCents, 250000L)), overLimit.body)

    val blockedWallet: Response[Either[PaymentError, Payment]] = client
      .toRequestThrowDecodeFailures(createPayment, Some(baseUri))
      .apply(PaymentRequest(900L, "EUR", PaymentMethod.Wallet("blocked-pay", "eve@example.com")))
      .send(backend)
    assertEquals(StatusCode.UnprocessableEntity, blockedWallet.code)
    assertEquals(Left(PaymentError.Declined("wallet", "provider blocked-pay is not accepted")), blockedWallet.body)

    val missing: Response[Either[PaymentError, Payment]] = client
      .toRequestThrowDecodeFailures(getPayment, Some(baseUri))
      .apply(77L)
      .send(backend)
    assertEquals(StatusCode.NotFound, missing.code)
    assertEquals(Left(PaymentError.PaymentNotFound(77L)), missing.body)

    assertEquals(0, ledger.size)
  }

  @Test
  def rejectsAnUnknownDiscriminatorValueBeforeTheBusinessLogicRuns(): Unit = {
    val raw: Response[Either[String, String]] = basicRequest
      .post(uri"$baseUri/payments")
      .contentType(MediaType.ApplicationJson)
      .body("""{"amountCents":500,"currency":"EUR","method":{"kind":"crypto","address":"bc1qxy2k"}}""")
      .send(backend)

    assertEquals(StatusCode.BadRequest, raw.code)
    assertEquals(0, ledger.size)
  }

  @Test
  def declaresTheSameDiscriminatorInTheEndpointSchema(): Unit = {
    val schema: Schema[PaymentMethod] = summon[Schema[PaymentMethod]]

    schema.schemaType match {
      case coproduct: SchemaType.SCoproduct[PaymentMethod] =>
        val discriminator: SchemaType.SDiscriminator =
          coproduct.discriminator.getOrElse(throw new AssertionError("no discriminator declared"))
        assertEquals("kind", discriminator.name.encodedName)
        assertEquals(Set("card", "bank-transfer", "wallet"), discriminator.mapping.keySet)
      case other =>
        throw new AssertionError(s"expected a coproduct schema, got $other")
    }
  }
}

object PaymentMethodPolymorphicJsonTest {
  given CirceConfiguration = CirceConfiguration.default.withDiscriminator("kind").withKebabCaseConstructorNames

  given TapirConfiguration = TapirConfiguration.default.withDiscriminator("kind").withKebabCaseDiscriminatorValues

  sealed trait PaymentMethod derives ConfiguredCodec, Schema

  object PaymentMethod {
    final case class Card(last4: String, network: String) extends PaymentMethod

    final case class BankTransfer(iban: String, reference: Option[String] = None) extends PaymentMethod

    final case class Wallet(provider: String, accountEmail: String) extends PaymentMethod
  }

  final case class PaymentRequest(amountCents: Long, currency: String, method: PaymentMethod)
      derives CirceCodec.AsObject, Schema

  final case class Payment(id: Long, amountCents: Long, currency: String, method: PaymentMethod, status: String)
      derives CirceCodec.AsObject, Schema

  sealed trait PaymentError

  object PaymentError {
    final case class InsufficientFunds(limitCents: Long, requestedCents: Long) extends PaymentError
        derives CirceCodec.AsObject, Schema

    final case class Declined(methodKind: String, reason: String) extends PaymentError
        derives CirceCodec.AsObject, Schema

    final case class PaymentNotFound(paymentId: Long) extends PaymentError derives CirceCodec.AsObject, Schema
  }

  val paymentErrors: EndpointOutput.OneOf[PaymentError, PaymentError] = oneOf[PaymentError](
    oneOfVariant(StatusCode.PaymentRequired, jsonBody[PaymentError.InsufficientFunds]),
    oneOfVariant(StatusCode.UnprocessableEntity, jsonBody[PaymentError.Declined]),
    oneOfVariant(StatusCode.NotFound, jsonBody[PaymentError.PaymentNotFound])
  )

  val createPayment: PublicEndpoint[PaymentRequest, PaymentError, Payment, Any] = endpoint.post
    .in("payments")
    .in(jsonBody[PaymentRequest])
    .out(statusCode(StatusCode.Created))
    .out(jsonBody[Payment])
    .errorOut(paymentErrors)

  val getPayment: PublicEndpoint[Long, PaymentError, Payment, Any] = endpoint.get
    .in("payments" / path[Long]("paymentId"))
    .out(jsonBody[Payment])
    .errorOut(paymentErrors)

  /** In-memory payment storage that applies the limit and the wallet rule before capturing. */
  final class PaymentLedger {
    private val payments: ConcurrentHashMap[Long, Payment] = new ConcurrentHashMap[Long, Payment]()
    private val nextId: AtomicLong = new AtomicLong()

    def size: Int = payments.size()

    def find(id: Long): Option[Payment] = Option(payments.get(id))

    val serverEndpoints = List(
      createPayment.handle(capture),
      getPayment.handle(id => find(id).toRight(PaymentError.PaymentNotFound(id)))
    )

    private def capture(request: PaymentRequest): Either[PaymentError, Payment] = request.method match {
      case _ if request.amountCents > PaymentLedger.LimitCents =>
        Left(PaymentError.InsufficientFunds(PaymentLedger.LimitCents, request.amountCents))
      case PaymentMethod.Wallet(provider, _) if PaymentLedger.BlockedProviders.contains(provider) =>
        Left(PaymentError.Declined("wallet", s"provider $provider is not accepted"))
      case method =>
        val id: Long = nextId.incrementAndGet()
        val payment: Payment = Payment(id, request.amountCents, request.currency, method, "captured")
        payments.put(id, payment)
        Right(payment)
    }
  }

  object PaymentLedger {
    val LimitCents: Long = 100000L
    val BlockedProviders: Set[String] = Set("blocked-pay")
  }
}
