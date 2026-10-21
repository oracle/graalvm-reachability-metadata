/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_dsl_3

import cats.effect.IO
import cats.effect.Ref
import cats.effect.Resource
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.*
import org.http4s.DecodeResult
import org.http4s.EntityDecoder
import org.http4s.HttpApp
import org.http4s.HttpRoutes
import org.http4s.InvalidMessageBodyFailure
import org.http4s.MalformedMessageBodyFailure
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.middleware.EntityLimiter
import org.http4s.server.middleware.EntityLimiter.EntityTooLarge
import org.http4s.server.middleware.ErrorAction
import org.http4s.server.middleware.ErrorHandling
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import scala.util.control.NoStackTrace

/**
 * Failure handling in DSL routes: decode failures, domain errors and unexpected exceptions turned into responses.
 *
 * A ledger service moves cents between accounts. Transfer amounts are read through a custom
 * `EntityDecoder` that reports non-numeric bodies as `MalformedMessageBodyFailure` and
 * non-positive ones as `InvalidMessageBodyFailure`; request bodies are capped by `EntityLimiter`.
 * An overdraft is recovered inside the route as `409 Conflict`, a frozen account is mapped to
 * `423 Locked` by `ErrorHandling.Custom.recoverWith`, and everything else falls through to
 * `ErrorHandling.Recover.total`, which answers message failures with their own status and any
 * other exception with `500`, keeping failure details out of the response. `ErrorAction` records
 * every failure that escapes the routes, details included, the way an application feeds its error
 * log. The service runs on an Ember server bound to an
 * ephemeral port and is driven by an Ember client.
 */
class LedgerTransferFailureHandlingTest {
  import LedgerTransferFailureHandlingTest.*

  @Test
  def ledgerFailuresBecomeTypedResponsesAndAreRecorded(): Unit = {
    val (outcome, failures): (Observed, List[String]) = serveAndCall { (client: Client[IO], base: Uri) =>
      val accounts: Uri = base / "accounts"
      def transfer(from: String, to: String, body: String): IO[(Status, String)] =
        call(client, Request[IO](Method.POST, accounts / from / "transfers" / to).withEntity(body))
      for {
        accepted <- transfer("alice", "bob", "2500")
        malformed <- transfer("bob", "alice", "twelve")
        invalid <- transfer("bob", "alice", "-5")
        overdraft <- transfer("bob", "alice", "9999")
        frozen <- transfer("carol", "alice", "100")
        unknownPayee <- transfer("alice", "dave", "100")
        tooLarge <- transfer("alice", "bob", "1" * 100)
        corrupted <- call(client, Request[IO](Method.POST, base / "admin" / "reindex"))
        bobAfter <- call(client, Request[IO](Method.GET, accounts / "bob"))
        aliceAfter <- call(client, Request[IO](Method.GET, accounts / "alice"))
        unrouted <- call(client, Request[IO](Method.GET, base / "reports"))
      } yield Observed(
        accepted,
        malformed,
        invalid,
        overdraft,
        frozen,
        unknownPayee,
        tooLarge._1,
        corrupted,
        bobAfter,
        aliceAfter,
        unrouted._1
      )
    }

    assertEquals((Status.Ok, "alice=7500 bob=3000"), outcome.accepted)
    assertEquals((Status.BadRequest, "The request body was malformed."), outcome.malformed)
    assertEquals((Status.UnprocessableEntity, "The request body was invalid."), outcome.invalid)
    assertEquals((Status.Conflict, "bob holds 3000 cents, transfer needs 9999"), outcome.overdraft)
    assertEquals((Status.Locked, "account carol is frozen"), outcome.frozen)
    assertEquals((Status.NotFound, "no account dave"), outcome.unknownPayee)
    assertEquals(Status.PayloadTooLarge, outcome.tooLarge)
    assertEquals((Status.InternalServerError, ""), outcome.corrupted)
    assertEquals((Status.Ok, "3000"), outcome.bobAfter)
    assertEquals((Status.Ok, "7500"), outcome.aliceAfter)
    assertEquals(Status.NotFound, outcome.unrouted)
    assertEquals(
      List(
        "POST /accounts/bob/transfers/alice malformed: 'twelve' is not a whole number of cents",
        "POST /accounts/bob/transfers/alice invalid: amount must be positive, got -5",
        "POST /accounts/carol/transfers/alice frozen: carol",
        s"POST /accounts/alice/transfers/bob too large: $MaxBodyBytes",
        "POST /admin/reindex unexpected: ledger index is corrupted"
      ),
      failures
    )
  }
}

object LedgerTransferFailureHandlingTest {

  /** Largest request body, in bytes, the ledger accepts. */
  val MaxBodyBytes: Long = 32L

  /** What the client saw for each request. */
  final case class Observed(
      accepted: (Status, String),
      malformed: (Status, String),
      invalid: (Status, String),
      overdraft: (Status, String),
      frozen: (Status, String),
      unknownPayee: (Status, String),
      tooLarge: Status,
      corrupted: (Status, String),
      bobAfter: (Status, String),
      aliceAfter: (Status, String),
      unrouted: Status
  )

  /** A positive transfer amount in cents. */
  final case class Amount(cents: Long)

  /** Balances in cents and the accounts that may not send money. */
  final case class Ledger(balances: Map[String, Long], frozen: Set[String])

  /** Raised when the sender cannot cover a transfer; the route answers it itself. */
  final case class InsufficientFunds(account: String, balance: Long, needed: Long)
      extends Exception(s"$account cannot cover $needed")
      with NoStackTrace

  /** Raised when a frozen account tries to send money; the error middleware answers it. */
  final case class AccountFrozen(account: String) extends Exception(s"$account is frozen") with NoStackTrace

  /** Reads a plain-text body as cents, separating unreadable bodies from unacceptable ones. */
  given EntityDecoder[IO, Amount] = EntityDecoder.text[IO].flatMapR { raw =>
    raw.trim.toLongOption match {
      case None =>
        DecodeResult.failureT[IO, Amount](MalformedMessageBodyFailure(s"'$raw' is not a whole number of cents"))
      case Some(cents) if cents <= 0 =>
        DecodeResult.failureT[IO, Amount](InvalidMessageBodyFailure(s"amount must be positive, got $cents"))
      case Some(cents) => DecodeResult.successT[IO, Amount](Amount(cents))
    }
  }

  /** The ledger API; failures it does not answer itself propagate as raised errors. */
  def ledgerRoutes(ledger: Ref[IO, Ledger]): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "accounts" / name =>
      ledger.get.flatMap(_.balances.get(name).fold(NotFound(s"no account $name"))(cents => Ok(cents.toString)))

    case request @ POST -> Root / "accounts" / from / "transfers" / to =>
      val moved: IO[Either[String, Ledger]] = for {
        amount <- request.as[Amount]
        result <- ledger.modify(state => settle(state, from, to, amount.cents))
        updated <- IO.fromEither(result)
      } yield updated
      moved
        .flatMap {
          case Right(state) => Ok(s"$from=${state.balances(from)} $to=${state.balances(to)}")
          case Left(unknown) => NotFound(s"no account $unknown")
        }
        .recoverWith { case InsufficientFunds(account, balance, needed) =>
          Conflict(s"$account holds $balance cents, transfer needs $needed")
        }

    case POST -> Root / "admin" / "reindex" =>
      IO.raiseError(new IllegalStateException("ledger index is corrupted"))
  }

  /** Applies one transfer, or explains why it cannot happen; an inner `Left` names an unknown account. */
  def settle(
      state: Ledger,
      from: String,
      to: String,
      cents: Long
  ): (Ledger, Either[Throwable, Either[String, Ledger]]) =
    (state.balances.get(from), state.balances.get(to)) match {
      case (Some(_), Some(_)) if state.frozen.contains(from) => (state, Left(AccountFrozen(from)))
      case (Some(balance), Some(_)) if balance < cents => (state, Left(InsufficientFunds(from, balance, cents)))
      case (Some(balance), Some(target)) =>
        val next: Ledger =
          state.copy(balances = state.balances ++ Map(from -> (balance - cents), to -> (target + cents)))
        (next, Right(Right(next)))
      case (None, _) => (state, Right(Left(from)))
      case _ => (state, Right(Left(to)))
    }

  /** Describes a failure that escaped the routes, as the error log stores it. */
  def describe(failure: Throwable): String = failure match {
    case malformed: MalformedMessageBodyFailure => s"malformed: ${malformed.details}"
    case invalid: InvalidMessageBodyFailure => s"invalid: ${invalid.details}"
    case EntityTooLarge(limit) => s"too large: $limit"
    case AccountFrozen(account) => s"frozen: $account"
    case other => s"unexpected: ${other.getMessage}"
  }

  /** Wraps the routes in the error handling an application deploys around them. */
  def ledgerApp(ledger: Ref[IO, Ledger], failures: Ref[IO, List[String]]): HttpApp[IO] = {
    val limited: HttpApp[IO] = EntityLimiter.httpApp(ledgerRoutes(ledger).orNotFound, MaxBodyBytes)
    val recorded: HttpApp[IO] = ErrorAction.httpApp(
      limited,
      (request: Request[IO], failure: Throwable) =>
        failures.update(_ :+ s"${request.method} ${request.uri.path} ${describe(failure)}")
    )
    val domain: HttpApp[IO] = ErrorHandling.Custom.recoverWith(recorded) { case AccountFrozen(account) =>
      Locked(s"account $account is frozen")
    }
    ErrorHandling.Recover.total(domain)
  }

  /** Starts the service on an ephemeral loopback port, runs `scenario`, and returns the error log. */
  def serveAndCall[A](scenario: (Client[IO], Uri) => IO[A]): (A, List[String]) = {
    val opening: Ledger = Ledger(Map("alice" -> 10000L, "bob" -> 500L, "carol" -> 2000L), Set("carol"))
    val program: IO[(A, List[String])] = for {
      ledger <- Ref.of[IO, Ledger](opening)
      failures <- Ref.of[IO, List[String]](List.empty)
      running: Resource[IO, (Client[IO], Uri)] = for {
        server <- EmberServerBuilder
          .default[IO]
          .withHost(ipv4"127.0.0.1")
          .withPort(port"0")
          .withHttpApp(ledgerApp(ledger, failures))
          .build
        client <- EmberClientBuilder.default[IO].build
      } yield (client, Uri.unsafeFromString(s"http://127.0.0.1:${server.address.getPort}"))
      result <- running.use((client, base) => scenario(client, base))
      log <- failures.get
    } yield (result, log)
    program.unsafeRunSync()
  }

  /** Sends `request` and reads the whole response body as text. */
  def call(client: Client[IO], request: Request[IO]): IO[(Status, String)] =
    client.run(request).use(response => response.as[String].map(body => (response.status, body)))
}
