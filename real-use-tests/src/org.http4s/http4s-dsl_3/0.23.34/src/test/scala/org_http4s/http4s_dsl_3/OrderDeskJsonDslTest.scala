/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_dsl_3

import cats.effect.Concurrent
import cats.effect.IO
import cats.effect.Ref
import cats.effect.Resource
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import com.comcast.ip4s.*
import io.circe.Codec
import io.circe.Json
import io.circe.syntax.*
import org.http4s.EntityDecoder
import org.http4s.HttpRoutes
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.Uri
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.`Content-Type`
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * JSON entity handling in a tagless-final service that mixes in `Http4sDsl[F]` with circe codecs.
 *
 * An order desk is written once against an abstract effect `F[_]`, as libraries of routes usually
 * are, and only instantiated with `IO` when it is served. Its routes decode JSON request bodies
 * into case classes with `jsonOf`, answer with JSON entities through the DSL response generators
 * (`Created`, `Ok`, `Accepted`), and map failures to JSON error bodies: malformed JSON becomes
 * `400 Bad Request`, a semantically invalid order `422 Unprocessable Content`, an unknown id
 * `404 Not Found`, and shipping an order twice `409 Conflict`. The service runs on an Ember server
 * and is called by an Ember client that reads every response back as JSON.
 */
class OrderDeskJsonDslTest {
  import OrderDeskJsonDslTest.*

  @Test
  def orderDeskExchangesJsonEntitiesAndErrorBodies(): Unit = {
    val outcome: Observed = serveAndCall { (client: Client[IO], base: Uri) =>
      val orders: Uri = base / "orders"
      def post(body: String): Request[IO] =
        Request[IO](Method.POST, orders)
          .withEntity(body)
          .withContentType(`Content-Type`(MediaType.application.json))

      for {
        created <- exchange(client, post(NewOrder("kettle", 2).asJson.noSpaces))
        _ <- exchange(client, post(NewOrder("teapot", 1).asJson.noSpaces))
        malformed <- exchange(client, post("""{"item": "kettle", "quantity": """))
        invalid <- exchange(client, post(NewOrder("cup", 0).asJson.noSpaces))
        fetched <- exchange(client, Request[IO](Method.GET, orders / "1"))
        shipped <- exchange(client, Request[IO](Method.POST, orders / "1" / "ship"))
        shippedAgain <- exchange(client, Request[IO](Method.POST, orders / "1" / "ship"))
        missing <- exchange(client, Request[IO](Method.GET, orders / "42"))
        listed <- exchange(client, Request[IO](Method.GET, orders))
      } yield Observed(created, malformed, invalid, fetched, shipped, shippedAgain, missing, listed)
    }

    val kettle: Order = Order(1L, "kettle", 2, shipped = false)
    val teapot: Order = Order(2L, "teapot", 1, shipped = false)

    assertEquals(Exchange(Status.Created, Some(MediaType.application.json), kettle.asJson), outcome.created)
    assertEquals(Status.BadRequest, outcome.malformed.status)
    assertEquals(Some(MediaType.application.json), outcome.malformed.mediaType)
    assertEquals(Some("malformed-json"), outcome.malformed.body.as[ErrorBody].toOption.map(_.code))
    assertEquals(
      Exchange(
        Status.UnprocessableContent,
        Some(MediaType.application.json),
        ErrorBody("invalid-quantity", "quantity must be positive, was 0").asJson
      ),
      outcome.invalid
    )
    assertEquals(Exchange(Status.Ok, Some(MediaType.application.json), kettle.asJson), outcome.fetched)
    assertEquals(
      Exchange(Status.Accepted, Some(MediaType.application.json), kettle.copy(shipped = true).asJson),
      outcome.shipped
    )
    assertEquals(
      Exchange(
        Status.Conflict,
        Some(MediaType.application.json),
        ErrorBody("already-shipped", "order 1 has already shipped").asJson
      ),
      outcome.shippedAgain
    )
    assertEquals(
      Exchange(Status.NotFound, Some(MediaType.application.json), ErrorBody("unknown-order", "no order 42").asJson),
      outcome.missing
    )
    assertEquals(Status.Ok, outcome.listed.status)
    assertEquals(Right(List(kettle.copy(shipped = true), teapot)), outcome.listed.body.as[List[Order]])
  }
}

object OrderDeskJsonDslTest {

  /** Order fields a client submits; the desk assigns the id. */
  final case class NewOrder(item: String, quantity: Int) derives Codec.AsObject

  /** An order as the desk stores and returns it. */
  final case class Order(id: Long, item: String, quantity: Int, shipped: Boolean) derives Codec.AsObject

  /** The JSON body every failure response carries. */
  final case class ErrorBody(code: String, message: String) derives Codec.AsObject

  /** One response as the client observed it. */
  final case class Exchange(status: Status, mediaType: Option[MediaType], body: Json)

  /** What the client saw at each step of the order lifecycle. */
  final case class Observed(
      created: Exchange,
      malformed: Exchange,
      invalid: Exchange,
      fetched: Exchange,
      shipped: Exchange,
      shippedAgain: Exchange,
      missing: Exchange,
      listed: Exchange
  )

  /** In-memory order book; the next id grows monotonically. */
  final case class OrderBook(nextId: Long, orders: Map[Long, Order])

  /** Order routes written against any concurrent effect, with the DSL mixed in for `F`. */
  final class OrderDeskRoutes[F[_]: Concurrent](book: Ref[F, OrderBook]) extends Http4sDsl[F] {

    private given EntityDecoder[F, NewOrder] = jsonOf[F, NewOrder]

    /** Outcome of trying to ship a stored order. */
    private enum Shipping {
      case Shipped(order: Order)
      case AlreadyShipped
      case Unknown
    }

    val routes: HttpRoutes[F] = HttpRoutes.of[F] {
      case request @ POST -> Root / "orders" =>
        request.attemptAs[NewOrder].value.flatMap {
          case Left(failure) =>
            BadRequest(ErrorBody("malformed-json", failure.message).asJson)
          case Right(NewOrder(_, quantity)) if quantity <= 0 =>
            UnprocessableContent(ErrorBody("invalid-quantity", s"quantity must be positive, was $quantity").asJson)
          case Right(NewOrder(item, quantity)) =>
            book
              .modify { state =>
                val order: Order = Order(state.nextId, item, quantity, shipped = false)
                (OrderBook(state.nextId + 1, state.orders + (order.id -> order)), order)
              }
              .flatMap(order => Created(order.asJson))
        }

      case GET -> Root / "orders" =>
        book.get.flatMap(state => Ok(state.orders.values.toList.sortBy(_.id).asJson))

      case GET -> Root / "orders" / LongVar(id) =>
        book.get.flatMap(_.orders.get(id).fold(unknown(id))(order => Ok(order.asJson)))

      case POST -> Root / "orders" / LongVar(id) / "ship" =>
        book
          .modify { state =>
            state.orders.get(id) match {
              case None => (state, Shipping.Unknown)
              case Some(order) if order.shipped => (state, Shipping.AlreadyShipped)
              case Some(order) =>
                val sent: Order = order.copy(shipped = true)
                (state.copy(orders = state.orders.updated(id, sent)), Shipping.Shipped(sent))
            }
          }
          .flatMap {
            case Shipping.Shipped(order) => Accepted(order.asJson)
            case Shipping.AlreadyShipped => Conflict(ErrorBody("already-shipped", s"order $id has already shipped").asJson)
            case Shipping.Unknown => unknown(id)
          }
    }

    private def unknown(id: Long): F[Response[F]] =
      NotFound(ErrorBody("unknown-order", s"no order $id").asJson)
  }

  /** Starts the desk on an ephemeral loopback port and runs `scenario` against it. */
  def serveAndCall[A](scenario: (Client[IO], Uri) => IO[A]): A = {
    val running: Resource[IO, (Client[IO], Uri)] = for {
      book <- Resource.eval(Ref.of[IO, OrderBook](OrderBook(1L, Map.empty)))
      server <- EmberServerBuilder
        .default[IO]
        .withHost(ipv4"127.0.0.1")
        .withPort(port"0")
        .withHttpApp(OrderDeskRoutes[IO](book).routes.orNotFound)
        .build
      client <- EmberClientBuilder.default[IO].build
    } yield (client, Uri.unsafeFromString(s"http://127.0.0.1:${server.address.getPort}"))
    running.use((client, base) => scenario(client, base)).unsafeRunSync()
  }

  /** Sends `request` and reads the status, media type, and JSON body of the response. */
  def exchange(client: Client[IO], request: Request[IO]): IO[Exchange] =
    client.run(request).use { response =>
      response.as[Json].map(body => Exchange(response.status, response.contentType.map(_.mediaType), body))
    }
}
