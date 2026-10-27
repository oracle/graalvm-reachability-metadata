/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package realuse.http4s

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import org.http4s.{AuthedRoutes, BasicCredentials, Challenge, HttpApp, Request, Status, Uri}
import org.http4s.dsl.io.*
import org.http4s.headers.{Authorization, `WWW-Authenticate`}
import org.http4s.implicits.*
import org.http4s.server.middleware.authentication.BasicAuth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises Basic authentication middleware protecting a customer account endpoint. */
class CustomerBasicAuthTest {
  private final case class Customer(accountId: String, displayName: String)

  @Test
  @Timeout(60)
  def challengesInvalidCredentialsAndPassesAuthenticatedCustomersToTheEndpoint(): Unit = {
    val customers: Map[String, (String, Customer)] = Map(
      "alice" -> ("alice-test-password", Customer("account-42", "Alice")),
      "bob" -> ("bob-test-password", Customer("account-73", "Bob"))
    )
    val authenticate: BasicAuth.BasicAuthenticator[IO, Customer] = credentials =>
      IO.pure(customers.get(credentials.username).collect {
        case (password, customer) if password == credentials.password => customer
      })

    def accountRequest(username: String, password: String): Request[IO] =
      Request[IO](uri = Uri.unsafeFromString("/account"))
        .putHeaders(Authorization(BasicCredentials(username, password)))

    val checks: IO[Unit] = for {
      servedAccounts <- Ref.of[IO, Vector[String]](Vector.empty)
      routes: AuthedRoutes[Customer, IO] = AuthedRoutes.of[Customer, IO] {
        case GET -> Root / "account" as customer =>
          servedAccounts.update(_ :+ customer.accountId) *>
            Ok(s"${customer.displayName}: ${customer.accountId}")
      }
      application: HttpApp[IO] = BasicAuth[IO, Customer]("customer-portal", authenticate).apply(routes).orNotFound
      rejected <- List(
        Request[IO](uri = Uri.unsafeFromString("/account")),
        accountRequest("alice", "incorrect-password"),
        accountRequest("unknown", "alice-test-password")
      ).traverse(application.run)
      accountsBeforeLogin <- servedAccounts.get
      alice <- application.run(accountRequest("alice", "alice-test-password"))
      aliceBody <- alice.as[String]
      bob <- application.run(accountRequest("bob", "bob-test-password"))
      bobBody <- bob.as[String]
      accountsAfterLogin <- servedAccounts.get
    } yield {
      for (response <- rejected) {
        assertEquals(Status.Unauthorized, response.status)
        val challenge: Option[Challenge] = response.headers.get[`WWW-Authenticate`].map(_.values.head)
        assertEquals(Some("Basic"), challenge.map(_.scheme))
        assertEquals(Some("customer-portal"), challenge.map(_.realm))
        assertEquals(Some("UTF-8"), challenge.flatMap(_.params.get("charset")))
      }
      assertEquals(Vector.empty[String], accountsBeforeLogin)
      assertEquals(Status.Ok, alice.status)
      assertEquals("Alice: account-42", aliceBody)
      assertEquals(None, alice.headers.get[`WWW-Authenticate`])
      assertEquals(Status.Ok, bob.status)
      assertEquals("Bob: account-73", bobBody)
      assertEquals(Vector("account-42", "account-73"), accountsAfterLogin)
    }

    checks.unsafeRunSync()
  }
}
