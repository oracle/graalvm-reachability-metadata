/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import zio.{Runtime, Scope, Unsafe, ZIO}
import zio.http.*
import zio.http.endpoint.Endpoint
import zio.schema.{DeriveSchema, Schema}
import zio.schema.codec.JsonCodec.*

/** Exercises ZIO HTTP typed endpoint codecs for a delivery-quote API. */
class DeliveryQuoteEndpointTest {
  case class QuoteRequest(destination: String, weightGrams: Int)
  object QuoteRequest {
    implicit val schema: Schema[QuoteRequest] = DeriveSchema.gen[QuoteRequest]
  }

  case class DeliveryQuote(service: String, priceCents: Int, deliveryDays: Int)
  object DeliveryQuote {
    implicit val schema: Schema[DeliveryQuote] = DeriveSchema.gen[DeliveryQuote]
  }

  case class QuoteRejected(code: String, message: String)
  object QuoteRejected {
    implicit val schema: Schema[QuoteRejected] = DeriveSchema.gen[QuoteRejected]
  }

  @Test
  @Timeout(60)
  def quotesTypedRequestsAndReturnsStructuredBusinessErrors(): Unit = {
    val endpoint = Endpoint(Method.POST / "delivery" / "quotes")
      .query[Boolean]("express")
      .in[QuoteRequest]
      .out[DeliveryQuote]
      .outError[QuoteRejected](Status.UnprocessableEntity)
    val routes: Routes[Any, Nothing] = Routes(
      endpoint.implementEither { case (express, parcel) => quote(express, parcel) }
    )

    val program: ZIO[Scope, Throwable, Unit] = for {
      express <- routes.runZIO(quoteRequest(true, QuoteRequest("US", 1250)))
      expressQuote <- express.body.to[DeliveryQuote]
      _ <- ZIO.succeed {
        assertEquals(Status.Ok, express.status)
        assertEquals(Some("application/json"), express.rawHeader("Content-Type"))
        assertEquals(DeliveryQuote("express", 1550, 1), expressQuote)
      }
      standard <- routes.runZIO(quoteRequest(false, QuoteRequest("US", 1250)))
      standardQuote <- standard.body.to[DeliveryQuote]
      _ <- ZIO.succeed {
        assertEquals(Status.Ok, standard.status)
        assertEquals(DeliveryQuote("standard", 850, 4), standardQuote)
      }
      unavailable <- routes.runZIO(quoteRequest(false, QuoteRequest("ZZ", 1250)))
      rejection <- unavailable.body.to[QuoteRejected]
      _ <- ZIO.succeed {
        assertEquals(Status.UnprocessableEntity, unavailable.status)
        assertEquals(
          QuoteRejected("unsupported_destination", "Delivery to ZZ is not available"),
          rejection
        )
      }
      invalidWeight <- routes.runZIO(quoteRequest(false, QuoteRequest("US", 0)))
      weightRejection <- invalidWeight.body.to[QuoteRejected]
      _ <- ZIO.succeed {
        assertEquals(Status.UnprocessableEntity, invalidWeight.status)
        assertEquals(QuoteRejected("invalid_weight", "Weight must be positive"), weightRejection)
      }
      malformed <- routes.runZIO(
        Request.post(
          "/delivery/quotes",
          Body.fromString("""{"destination":"US","weightGrams":"heavy"}""")
        ).addQueryParam("express", "false")
          .addHeader(Header.ContentType(MediaType.application.json))
      )
      _ <- ZIO.succeed(assertEquals(Status.BadRequest, malformed.status))
    } yield ()

    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(ZIO.scoped(program)).getOrThrowFiberFailure()
    }
  }

  private def quoteRequest(express: Boolean, parcel: QuoteRequest): Request =
    Request.post("/delivery/quotes", Body.from(parcel))
      .addQueryParam("express", express.toString)
      .addHeader(Header.ContentType(MediaType.application.json))

  private def quote(
      express: Boolean,
      parcel: QuoteRequest
  ): Either[QuoteRejected, DeliveryQuote] =
    if (parcel.destination != "US") {
      Left(QuoteRejected(
        "unsupported_destination",
        s"Delivery to ${parcel.destination} is not available"
      ))
    } else if (parcel.weightGrams <= 0) {
      Left(QuoteRejected("invalid_weight", "Weight must be positive"))
    } else {
      val weightUnits: Int = (parcel.weightGrams + 499) / 500
      val price: Int = 400 + weightUnits * 150 + (if (express) 700 else 0)
      Right(DeliveryQuote(if (express) "express" else "standard", price, if (express) 1 else 4))
    }
}
