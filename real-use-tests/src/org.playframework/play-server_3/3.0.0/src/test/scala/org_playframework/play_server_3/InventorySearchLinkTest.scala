/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_server_3

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import play.api.Mode
import play.api.mvc.Results.Ok
import play.core.server.Server
import play.core.server.ServerConfig

/** Exercises HTTP request-target decoding for shared inventory-search links. */
class InventorySearchLinkTest {
  @Test
  @Timeout(60)
  def restoresUtf8SearchTextAndRepeatedFiltersFromABookmarkedLink(): Unit = {
    val title: String = "caf\u00e9 + tea & 100% organic"
    val products: List[Product] = List(
      Product("SKU-42", title, Set("in stock", "retail/priority")),
      Product("SKU-43", title, Set("in stock")),
      Product("SKU-44", title, Set("retail/priority")),
      Product("SKU-45", "black tea", Set("in stock", "retail/priority"))
    )
    val config: ServerConfig = ServerConfig(
      port = Some(0),
      address = "127.0.0.1",
      mode = Mode.Test
    )

    Server.withRouterFromComponents(config) { components =>
      {
        case request if request.method == "GET" && request.path == "/inventory/search" =>
          components.defaultActionBuilder { incoming =>
            val text: String = incoming.getQueryString("q").getOrElse("")
            val tags: Seq[String] = incoming.queryString.getOrElse("tag", Seq.empty)
            val matches: List[Product] = products.filter { product =>
              product.title.contains(text) && tags.forall(product.tags.contains)
            }
            Ok(matches.map(product => s"${product.sku}: ${product.title}\n").mkString)
          }
      }
    } { port =>
      val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
      try {
        def search(query: String): HttpResponse[String] = {
          val request: HttpRequest = HttpRequest.newBuilder()
            .uri(URI.create(s"http://127.0.0.1:${port.value}/inventory/search$query"))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()
          client.send(request, HttpResponse.BodyHandlers.ofString())
        }

        val all: HttpResponse[String] = search("")
        assertEquals(200, all.statusCode())
        assertEquals(s"SKU-42: $title\nSKU-43: $title\nSKU-44: $title\nSKU-45: black tea\n", all.body())

        // Spaces use '+', but a literal plus, ampersand and percent must remain search text.
        // Both tag values must survive decoding: either tag alone also selects a different SKU.
        val bookmarked: HttpResponse[String] = search(
          "?q=caf%C3%A9+%2B+tea+%26+100%25+organic&tag=in+stock&tag=retail%2Fpriority"
        )
        assertEquals(200, bookmarked.statusCode())
        assertEquals(s"SKU-42: $title\n", bookmarked.body())
        assertEquals("text/plain; charset=UTF-8", bookmarked.headers().firstValue("Content-Type").orElseThrow())

        // An encoded ampersand in a value cannot introduce another query parameter.
        val literalSeparator: HttpResponse[String] = search("?q=black+tea%26tag%3Dmissing")
        assertEquals(200, literalSeparator.statusCode())
        assertEquals("", literalSeparator.body())
      } finally {
        client.close()
      }
    }
  }

  private case class Product(sku: String, title: String, tags: Set[String])
}
