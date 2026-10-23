/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_2_13

import akka.actor.ActorSystem
import akka.stream.{Materializer, SystemMaterializer}
import akka.util.Timeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout => TestTimeout}
import play.api.libs.json.Json
import play.api.mvc.{Action, ActionFilter, ActionRefiner, AnyContent, DefaultActionBuilder, PlayBodyParsers, Request, Result, Results, WrappedRequest}
import play.api.test.FakeRequest
import play.api.test.Helpers.{call, contentAsJson, contentAsString, status, writeableOf_AnyContentAsEmpty}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

/** Exercises Play action composition for API-key authentication and role-based report authorization. */
@TestTimeout(60)
class AuthorizedReportActionTest {
  case class Account(id: String, role: String)

  class AccountRequest[A](val account: Account, request: Request[A]) extends WrappedRequest[A](request)

  class Authenticate(accounts: Map[String, Account])(
      implicit override protected val executionContext: ExecutionContext
  ) extends ActionRefiner[Request, AccountRequest] {
    override protected def refine[A](request: Request[A]): Future[Either[Result, AccountRequest[A]]] = {
      val account: Option[Account] = request.headers.get("X-Api-Key").flatMap(accounts.get)
      Future.successful(account match {
        case Some(found) => Right(new AccountRequest(found, request))
        case None => Left(Results.Unauthorized("Valid API key required"))
      })
    }
  }

  class RequireReportingRole(implicit override protected val executionContext: ExecutionContext)
      extends ActionFilter[AccountRequest] {
    override protected def filter[A](request: AccountRequest[A]): Future[Option[Result]] = {
      Future.successful {
        if (request.account.role == "reporting") None
        else Some(Results.Forbidden("Reporting role required"))
      }
    }
  }

  @Test
  def authenticatesAndAuthorizesBeforeReturningAnAccountReport(): Unit = {
    val system: ActorSystem = ActorSystem("authorized-report-action")
    implicit val materializer: Materializer = SystemMaterializer(system).materializer
    implicit val executionContext: ExecutionContext = system.dispatcher
    implicit val timeout: Timeout = Timeout(10.seconds)

    try {
      val accounts: Map[String, Account] = Map(
        "north-report-key" -> Account("north", "reporting"),
        "south-report-key" -> Account("south", "reporting"),
        "north-reader-key" -> Account("north", "reader")
      )
      val totals: Map[String, Int] = Map("north" -> 7500, "south" -> 4200)
      val parsers: PlayBodyParsers = PlayBodyParsers()
      val action: DefaultActionBuilder = DefaultActionBuilder(parsers.defaultBodyParser)
      val authenticate: Authenticate = new Authenticate(accounts)
      val reportingRole: RequireReportingRole = new RequireReportingRole()
      val report: Action[AnyContent] = (action andThen authenticate andThen reportingRole) { request =>
        Results.Ok(Json.obj(
          "accountId" -> request.account.id,
          "totalCents" -> totals(request.account.id)
        ))
      }

      val anonymous: Future[Result] = call(report, FakeRequest("GET", "/account/report"))
      assertEquals(401, status(anonymous))
      assertEquals("Valid API key required", contentAsString(anonymous))

      val invalidKey: Future[Result] = call(report,
        FakeRequest("GET", "/account/report").withHeaders("X-Api-Key" -> "unknown-key"))
      assertEquals(401, status(invalidKey))
      assertEquals("Valid API key required", contentAsString(invalidKey))

      val reader: Future[Result] = call(report,
        FakeRequest("GET", "/account/report").withHeaders("X-Api-Key" -> "north-reader-key"))
      assertEquals(403, status(reader))
      assertEquals("Reporting role required", contentAsString(reader))

      val north: Future[Result] = call(report,
        FakeRequest("GET", "/account/report").withHeaders("X-Api-Key" -> "north-report-key"))
      assertEquals(200, status(north))
      assertEquals(Json.obj("accountId" -> "north", "totalCents" -> 7500), contentAsJson(north))

      val south: Future[Result] = call(report,
        FakeRequest("GET", "/account/report").withHeaders("X-Api-Key" -> "south-report-key"))
      assertEquals(200, status(south))
      assertEquals(Json.obj("accountId" -> "south", "totalCents" -> 4200), contentAsJson(south))

      val signedOut: Future[Result] = call(report, FakeRequest("GET", "/account/report"))
      assertEquals(401, status(signedOut))
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
