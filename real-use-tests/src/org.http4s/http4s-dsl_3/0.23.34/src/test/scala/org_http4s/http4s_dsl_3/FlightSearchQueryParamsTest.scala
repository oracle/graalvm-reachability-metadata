/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_dsl_3

import cats.data.ValidatedNel
import cats.effect.IO
import cats.effect.Resource
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import com.comcast.ip4s.*
import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.http4s.HttpRoutes
import org.http4s.Method
import org.http4s.ParseFailure
import org.http4s.QueryParamDecoder
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.dsl.impl.FlagQueryParamMatcher
import org.http4s.dsl.impl.OptionalMultiQueryParamDecoderMatcher
import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher
import org.http4s.dsl.impl.OptionalValidatingQueryParamDecoderMatcher
import org.http4s.dsl.impl.QueryParamDecoderMatcher
import org.http4s.dsl.impl.QueryParamDecoderMatcherWithDefault
import org.http4s.dsl.impl.ValidatingQueryParamDecoderMatcher
import org.http4s.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Query-string extraction with the http4s DSL query parameter matchers and custom decoders.
 *
 * A flight search endpoint reads its criteria from the query string: required airports with
 * `QueryParamDecoderMatcher`, a departure date validated by `ValidatingQueryParamDecoderMatcher`,
 * an optional stop limit, repeated airline codes through `OptionalMultiQueryParamDecoderMatcher`,
 * an optional validated sort order, a page size that falls back to a default, and a `direct`
 * flag. Parameters are combined with `:?` and `+&`, decoding failures are accumulated and
 * reported as `400 Bad Request`, and a request without a required parameter does not match the
 * route. The service runs on an Ember server and is called by an Ember client.
 */
class FlightSearchQueryParamsTest {
  import FlightSearchQueryParamsTest.*

  @Test
  def flightSearchDecodesQueryStringCriteria(): Unit = {
    val outcome: Observed = serveAndCall { (client: Client[IO], base: String) =>
      def search(query: String): IO[(Status, String)] =
        call(client, Request[IO](Method.GET, Uri.unsafeFromString(s"$base/flights?$query")))

      for {
        everything <- search("from=LHR&to=JFK&date=2026-10-19")
        byPrice <- search("from=LHR&to=JFK&date=2026-10-19&sort=price&limit=2")
        twoAirlines <- search("from=LHR&to=JFK&date=2026-10-19&airline=BA&airline=VS&sort=duration")
        nonStop <- search("from=LHR&to=JFK&date=2026-10-19&direct")
        oneStop <- search("from=LHR&to=JFK&date=2026-10-19&maxStops=1&sort=price")
        otherDay <- search("from=LHR&to=JFK&date=2026-10-20")
        invalid <- search("from=LHR&to=JFK&date=19.10.2026&airline=british&sort=cheapest")
        missingOrigin <- search("to=JFK&date=2026-10-19")
      } yield Observed(everything, byPrice, twoAirlines, nonStop, oneStop, otherDay, invalid, missingOrigin)
    }

    assertEquals(
      (Status.Ok, "BA117 0 stops 455min 612\nVS3 0 stops 470min 540\nAA101 1 stops 610min 398\nIB6251 2 stops 780min 315"),
      outcome.everything
    )
    assertEquals((Status.Ok, "IB6251 2 stops 780min 315\nAA101 1 stops 610min 398"), outcome.byPrice)
    assertEquals((Status.Ok, "BA117 0 stops 455min 612\nVS3 0 stops 470min 540"), outcome.twoAirlines)
    assertEquals((Status.Ok, "BA117 0 stops 455min 612\nVS3 0 stops 470min 540"), outcome.nonStop)
    assertEquals(
      (Status.Ok, "AA101 1 stops 610min 398\nVS3 0 stops 470min 540\nBA117 0 stops 455min 612"),
      outcome.oneStop
    )
    assertEquals((Status.Ok, "AA100 0 stops 465min 701"), outcome.otherDay)
    assertEquals(
      (Status.BadRequest, "invalid date '19.10.2026'\ninvalid airline 'british'\nunknown sort 'cheapest'"),
      outcome.invalid
    )
    assertEquals(Status.NotFound, outcome.missingOrigin._1)
  }
}

object FlightSearchQueryParamsTest {

  /** What the client saw for each search. */
  final case class Observed(
      everything: (Status, String),
      byPrice: (Status, String),
      twoAirlines: (Status, String),
      nonStop: (Status, String),
      oneStop: (Status, String),
      otherDay: (Status, String),
      invalid: (Status, String),
      missingOrigin: (Status, String)
  )

  /** A two-letter IATA airline designator. */
  final case class Airline(code: String)

  /** How results are ordered when the caller asks for it. */
  enum SortOrder(val key: String) {
    case Price extends SortOrder("price")
    case Duration extends SortOrder("duration")
  }

  /** One scheduled departure the search can return. */
  final case class Flight(
      number: String,
      airline: Airline,
      from: String,
      to: String,
      date: LocalDate,
      stops: Int,
      minutes: Int,
      fare: Int
  ) {
    def render: String = s"$number $stops stops ${minutes}min $fare"
  }

  given QueryParamDecoder[LocalDate] = QueryParamDecoder[String].emap { raw =>
    Either
      .catchOnly[DateTimeParseException](LocalDate.parse(raw))
      .leftMap(error => ParseFailure(s"invalid date '$raw'", error.getMessage))
  }

  given QueryParamDecoder[Airline] = QueryParamDecoder[String].emap { raw =>
    if (raw.matches("[A-Z0-9]{2}")) Right(Airline(raw))
    else Left(ParseFailure(s"invalid airline '$raw'", "expected a two-character designator"))
  }

  given QueryParamDecoder[SortOrder] = QueryParamDecoder[String].emap { raw =>
    SortOrder.values.find(_.key == raw).toRight(ParseFailure(s"unknown sort '$raw'", raw))
  }

  object Origin extends QueryParamDecoderMatcher[String]("from")
  object Destination extends QueryParamDecoderMatcher[String]("to")
  object Departing extends ValidatingQueryParamDecoderMatcher[LocalDate]("date")
  object MaxStops extends OptionalQueryParamDecoderMatcher[Int]("maxStops")
  object Airlines extends OptionalMultiQueryParamDecoderMatcher[Airline]("airline")
  object Sort extends OptionalValidatingQueryParamDecoderMatcher[SortOrder]("sort")
  object Limit extends QueryParamDecoderMatcherWithDefault[Int]("limit", 10)
  object DirectOnly extends FlagQueryParamMatcher("direct")

  /** The timetable the search filters; listed in schedule order. */
  val timetable: List[Flight] = List(
    Flight("BA117", Airline("BA"), "LHR", "JFK", LocalDate.of(2026, 10, 19), 0, 455, 612),
    Flight("VS3", Airline("VS"), "LHR", "JFK", LocalDate.of(2026, 10, 19), 0, 470, 540),
    Flight("AA101", Airline("AA"), "LHR", "JFK", LocalDate.of(2026, 10, 19), 1, 610, 398),
    Flight("IB6251", Airline("IB"), "LHR", "JFK", LocalDate.of(2026, 10, 19), 2, 780, 315),
    Flight("AA100", Airline("AA"), "LHR", "JFK", LocalDate.of(2026, 10, 20), 0, 465, 701),
    Flight("BA178", Airline("BA"), "JFK", "LHR", LocalDate.of(2026, 10, 19), 0, 415, 590)
  )

  /** Criteria decoded from a search request. */
  final case class Criteria(
      from: String,
      to: String,
      date: LocalDate,
      maxStops: Option[Int],
      airlines: List[Airline],
      sort: Option[SortOrder],
      limit: Int
  )

  def find(criteria: Criteria): List[Flight] = {
    val matching: List[Flight] = timetable.filter { flight =>
      flight.from == criteria.from && flight.to == criteria.to && flight.date == criteria.date &&
      criteria.maxStops.forall(flight.stops <= _) &&
      (criteria.airlines.isEmpty || criteria.airlines.contains(flight.airline))
    }
    val ordered: List[Flight] = criteria.sort match {
      case Some(SortOrder.Price) => matching.sortBy(_.fare)
      case Some(SortOrder.Duration) => matching.sortBy(_.minutes)
      case None => matching
    }
    ordered.take(criteria.limit)
  }

  /** The search API an application would expose. */
  val flightRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "flights" :? Origin(from) +& Destination(to) +& Departing(date) +&
        MaxStops(maxStops) +& Airlines(airlines) +& Sort(sort) +& Limit(limit) +& DirectOnly(direct) =>
      val decoded: ValidatedNel[ParseFailure, Criteria] =
        (date, airlines, sort.sequence).mapN { (departure, carriers, order) =>
          Criteria(from, to, departure, if (direct) Some(0) else maxStops, carriers, order, limit)
        }
      decoded.fold(
        failures => BadRequest(failures.toList.map(_.sanitized).mkString("\n")),
        criteria => Ok(find(criteria).map(_.render).mkString("\n"))
      )
  }

  /** Starts the service on an ephemeral loopback port and runs `scenario` against it. */
  def serveAndCall[A](scenario: (Client[IO], String) => IO[A]): A = {
    val running: Resource[IO, (Client[IO], String)] = for {
      server <- EmberServerBuilder
        .default[IO]
        .withHost(ipv4"127.0.0.1")
        .withPort(port"0")
        .withHttpApp(flightRoutes.orNotFound)
        .build
      client <- EmberClientBuilder.default[IO].build
    } yield (client, s"http://127.0.0.1:${server.address.getPort}")
    running.use((client, base) => scenario(client, base)).unsafeRunSync()
  }

  /** Sends `request` and reads the whole response body as text. */
  def call(client: Client[IO], request: Request[IO]): IO[(Status, String)] =
    client.run(request).use(response => response.as[String].map(body => (response.status, body)))
}
