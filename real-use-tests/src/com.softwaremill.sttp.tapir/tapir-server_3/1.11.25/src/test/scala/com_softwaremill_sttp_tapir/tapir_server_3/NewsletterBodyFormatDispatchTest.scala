/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_server_3

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration

import io.circe.generic.auto.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import sttp.model.StatusCode
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.jdkhttp.JdkHttpServer

/** Exercises request-body content-type dispatch for JSON and form newsletter subscriptions. */
class NewsletterBodyFormatDispatchTest {
  private case class Subscription(email: String, topic: String)

  private class Subscriptions {
    private var topics: Map[String, String] = Map.empty

    def save(subscription: Subscription): String = synchronized {
      topics = topics.updated(subscription.email, subscription.topic)
      s"Subscribed ${subscription.email} to ${subscription.topic}"
    }

    def list(): String = synchronized {
      topics.toList.sortBy(_._1).map { case (email, topic) => s"$email=$topic" }.mkString("\n")
    }
  }

  @Test
  @Timeout(60)
  def acceptsBothClientFormatsAndRejectsMislabeledOrMalformedRequests(): Unit = {
    val subscriptions: Subscriptions = new Subscriptions
    val subscribe: ServerEndpoint[Any, Identity] = endpoint.post
      .in("newsletter" / "subscriptions")
      .in(oneOfBody(jsonBody[Subscription], formBody[Subscription]))
      .out(statusCode(StatusCode.Created).and(stringBody))
      .serverLogicSuccess[Identity](subscriptions.save)
    val listing: ServerEndpoint[Any, Identity] = endpoint.get
      .in("newsletter" / "subscriptions")
      .out(stringBody)
      .serverLogicSuccess[Identity](_ => subscriptions.list())

    val server = JdkHttpServer().host("127.0.0.1").port(0)
      .addEndpoint(subscribe).addEndpoint(listing).start()
    try {
      val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
      try {
        val uri: URI = URI.create(s"http://127.0.0.1:${server.getAddress.getPort}/newsletter/subscriptions")

        def submit(contentType: String, body: String): HttpResponse[String] = {
          val request: HttpRequest = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
            .header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofString(body)).build()
          client.send(request, HttpResponse.BodyHandlers.ofString())
        }

        def assertSubscriptions(expected: String): Unit = {
          val request: HttpRequest = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build()
          val response: HttpResponse[String] = client.send(request, HttpResponse.BodyHandlers.ofString())
          assertEquals(200, response.statusCode())
          assertEquals(expected, response.body())
        }

        val json: HttpResponse[String] = submit("application/json; charset=UTF-8",
          """{"email":"alice@example.com","topic":"New arrivals"}""")
        assertEquals(201, json.statusCode())
        assertEquals("Subscribed alice@example.com to New arrivals", json.body())

        // A browser form encodes literal plus signs, non-ASCII text, and spaces differently from JSON.
        val form: HttpResponse[String] = submit("application/x-www-form-urlencoded; charset=UTF-8",
          "topic=Caf%C3%A9+%26+tea&email=bob%2Boffers%40example.com")
        assertEquals(201, form.statusCode())
        assertEquals("Subscribed bob+offers@example.com to Caf\u00e9 & tea", form.body())
        val saved: String = "alice@example.com=New arrivals\nbob+offers@example.com=Caf\u00e9 & tea"
        assertSubscriptions(saved)

        val unsupported: HttpResponse[String] = submit("application/xml",
          "<subscription><email>alice@example.com</email><topic>Other</topic></subscription>")
        assertEquals(415, unsupported.statusCode())
        assertSubscriptions(saved)

        // Both bodies are valid in the other format, but content-type must determine the decoder.
        val mislabeledJson: HttpResponse[String] = submit("application/x-www-form-urlencoded",
          """{"email":"alice@example.com","topic":"Other"}""")
        assertEquals(400, mislabeledJson.statusCode())
        val mislabeledForm: HttpResponse[String] = submit("application/json",
          "email=alice%40example.com&topic=Other")
        assertEquals(400, mislabeledForm.statusCode())
        val incomplete: HttpResponse[String] = submit("application/x-www-form-urlencoded",
          "email=alice%40example.com")
        assertEquals(400, incomplete.statusCode())
        assertSubscriptions(saved)

        // Failed decodes must leave the endpoint usable for a later preference change.
        val changed: HttpResponse[String] = submit("application/x-www-form-urlencoded",
          "email=alice%40example.com&topic=Weekly+offers")
        assertEquals(201, changed.statusCode())
        assertEquals("Subscribed alice@example.com to Weekly offers", changed.body())
        assertSubscriptions("alice@example.com=Weekly offers\nbob+offers@example.com=Caf\u00e9 & tea")
      } finally client.close()
    } finally server.stop(0)
  }
}
