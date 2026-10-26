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
import play.api.i18n.{DefaultLangs, DefaultMessagesApi, Lang, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, DefaultActionBuilder, PlayBodyParsers, Result, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers.{call, contentAsJson, status, writeableOf_AnyContentAsEmpty}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

/** Exercises Play internationalization for language-negotiated checkout confirmations. */
@TestTimeout(60)
class LocalizedCheckoutTest {
  @Test
  def rendersLocalizedConfirmationsWithSharedMessageFallbacks(): Unit = {
    val system: ActorSystem = ActorSystem("localized-checkout")
    implicit val materializer: Materializer = SystemMaterializer(system).materializer
    implicit val executionContext: ExecutionContext = system.dispatcher
    implicit val timeout: Timeout = Timeout(10.seconds)

    try {
      val messagesApi: MessagesApi = new DefaultMessagesApi(
        messages = Map(
          "en" -> Map(
            "checkout.title" -> "Order confirmed",
            "checkout.receipt" -> "Thank you {0}, your order {1} is confirmed."
          ),
          "fr" -> Map(
            "checkout.title" -> "Commande confirmee",
            "checkout.receipt" -> "Merci {0}, commande {1}."
          ),
          "default" -> Map(
            "checkout.support" -> "support@example.test"
          )
        ),
        langs = new DefaultLangs(Seq(Lang("en"), Lang("fr")))
      )
      val parsers: PlayBodyParsers = PlayBodyParsers()
      val action: DefaultActionBuilder = DefaultActionBuilder(parsers.defaultBodyParser)
      val confirmation: Action[AnyContent] = action { request =>
        val messages: Messages = messagesApi.preferred(request)
        Results.Ok(Json.obj(
          "title" -> messages("checkout.title"),
          "receipt" -> messages("checkout.receipt", "Ada", "ORD-42"),
          "support" -> messages("checkout.support")
        )).withHeaders("Content-Language" -> messages.lang.code, "Vary" -> "Accept-Language")
      }

      val french: Future[Result] = call(confirmation,
        FakeRequest("GET", "/checkout/ORD-42/confirmation")
          .withHeaders("Accept-Language" -> "de;q=1.0,fr-CA;q=0.9,en;q=0.8"))
      assertEquals(200, status(french))
      assertEquals(Some("fr"), Await.result(french, 10.seconds).header.headers.get("Content-Language"))
      assertEquals(Some("Accept-Language"), Await.result(french, 10.seconds).header.headers.get("Vary"))
      assertEquals(Json.obj(
        "title" -> "Commande confirmee",
        "receipt" -> "Merci Ada, commande ORD-42.",
        "support" -> "support@example.test"
      ), contentAsJson(french))

      val english: Future[Result] = call(confirmation,
        FakeRequest("GET", "/checkout/ORD-42/confirmation")
          .withHeaders("Accept-Language" -> "en;q=1.0,fr;q=0.5"))
      assertEquals(200, status(english))
      assertEquals(Some("en"), Await.result(english, 10.seconds).header.headers.get("Content-Language"))
      assertEquals(Json.obj(
        "title" -> "Order confirmed",
        "receipt" -> "Thank you Ada, your order ORD-42 is confirmed.",
        "support" -> "support@example.test"
      ), contentAsJson(english))

      val unsupported: Future[Result] = call(confirmation,
        FakeRequest("GET", "/checkout/ORD-42/confirmation")
          .withHeaders("Accept-Language" -> "ja"))
      assertEquals(200, status(unsupported))
      assertEquals(Some("en"), Await.result(unsupported, 10.seconds).header.headers.get("Content-Language"))
      assertEquals(contentAsJson(english), contentAsJson(unsupported))
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
