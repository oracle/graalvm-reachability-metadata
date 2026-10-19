/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_2_13

import akka.actor.ActorSystem
import akka.stream.{Materializer, SystemMaterializer}
import akka.stream.scaladsl.Source
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import play.api.http.HeaderNames.{AUTHORIZATION, WWW_AUTHENTICATE}
import play.api.http.Status.{FORBIDDEN, OK, UNAUTHORIZED}
import play.api.mvc.{Action, ActionFilter, ActionRefiner, AnyContent, DefaultActionBuilder, PlayBodyParsers,
  Request, RequestHeader, Result, Results, WrappedRequest}
import play.api.test.FakeRequest

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

/** Exercises Play action composition for authentication and permission checks on a customer invoice endpoint. */
@Timeout(60)
class InvoiceAuthorizationTest {
  case class Principal(customerId: String, permissions: Set[String])

  class CustomerRequest[A](val principal: Principal, request: Request[A]) extends WrappedRequest[A](request)

  class Authenticate(tokens: Map[String, Principal])(implicit protected val executionContext: ExecutionContext)
      extends ActionRefiner[Request, CustomerRequest] {
    override protected def refine[A](request: Request[A]): Future[Either[Result, CustomerRequest[A]]] = {
      val principal: Option[Principal] = request.headers.get(AUTHORIZATION)
        .filter(_.startsWith("Bearer "))
        .flatMap(header => tokens.get(header.stripPrefix("Bearer ")))
      val decision: Either[Result, CustomerRequest[A]] = principal match {
        case Some(customer) => Right(new CustomerRequest(customer, request))
        case None => Left(Results.Unauthorized("A valid access token is required")
          .withHeaders(WWW_AUTHENTICATE -> "Bearer realm=\"invoices\""))
      }
      Future.successful(decision)
    }
  }

  class RequirePermission(permission: String)(implicit protected val executionContext: ExecutionContext)
      extends ActionFilter[CustomerRequest] {
    override protected def filter[A](request: CustomerRequest[A]): Future[Option[Result]] =
      Future.successful {
        if (request.principal.permissions.contains(permission)) None
        else Some(Results.Forbidden("Invoice access is not permitted"))
      }
  }

  @Test
  def authenticatesBeforeCheckingPermissionsAndKeepsCustomerInvoicesSeparate(): Unit = {
    val system: ActorSystem = ActorSystem("invoice-authorization")
    implicit val materializer: Materializer = SystemMaterializer(system).materializer
    implicit val executionContext: ExecutionContext = system.dispatcher
    try {
      val tokens: Map[String, Principal] = Map(
        "ada-token" -> Principal("customer-41", Set("invoices:read")),
        "grace-token" -> Principal("customer-42", Set("invoices:read")),
        "upload-token" -> Principal("customer-41", Set("receipts:upload"))
      )
      val invoices: Map[String, List[String]] = Map(
        "customer-41" -> List("INV-101: USD 42.50", "INV-102: USD 19.95"),
        "customer-42" -> List("INV-201: USD 85.00")
      )
      val action: DefaultActionBuilder = DefaultActionBuilder(PlayBodyParsers().defaultBodyParser)
      val authenticated: Authenticate = new Authenticate(tokens)
      val canReadInvoices: RequirePermission = new RequirePermission("invoices:read")
      val endpoint: Action[AnyContent] = (action andThen authenticated andThen canReadInvoices) { request =>
        Results.Ok(invoices(request.principal.customerId).mkString("\n"))
          .withHeaders("Cache-Control" -> "private, no-store")
      }

      def get(authorization: Option[String]): Result = {
        val base: RequestHeader = FakeRequest("GET", "/account/invoices")
        val request: RequestHeader = authorization match {
          case Some(value) => base.withHeaders(base.headers.add(AUTHORIZATION -> value))
          case None => base
        }
        Await.result(endpoint(request).run(Source.empty), 10.seconds)
      }

      def body(result: Result): String =
        Await.result(result.body.consumeData, 10.seconds).utf8String

      // Authentication rejects missing, invalid, and wrong-scheme credentials before authorization.
      List(None, Some("Bearer unknown-token"), Some("Basic ada-token")).foreach { credentials =>
        val rejected: Result = get(credentials)
        assertEquals(UNAUTHORIZED, rejected.header.status)
        assertEquals("Bearer realm=\"invoices\"", rejected.header.headers(WWW_AUTHENTICATE))
        assertEquals("A valid access token is required", body(rejected))
      }

      val forbidden: Result = get(Some("Bearer upload-token"))
      assertEquals(FORBIDDEN, forbidden.header.status)
      assertEquals("Invoice access is not permitted", body(forbidden))

      val ada: Result = get(Some("Bearer ada-token"))
      assertEquals(OK, ada.header.status)
      assertEquals("private, no-store", ada.header.headers("Cache-Control"))
      assertEquals("INV-101: USD 42.50\nINV-102: USD 19.95", body(ada))

      val grace: Result = get(Some("Bearer grace-token"))
      assertEquals(OK, grace.header.status)
      assertEquals("INV-201: USD 85.00", body(grace))
      assertEquals("INV-101: USD 42.50\nINV-102: USD 19.95", body(get(Some("Bearer ada-token"))))
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
