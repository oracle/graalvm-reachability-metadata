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
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import play.api.Mode
import play.api.mvc.RangeResult
import play.core.server.Server
import play.core.server.ServerConfig

/** Exercises HTTP byte-range file serving for resumable invoice downloads. */
class InvoiceDownloadRangeTest {
  @Test
  @Timeout(60)
  def downloadsAnInvoiceInPartsAndRejectsAnOutOfBoundsResume(): Unit = {
    val invoice: String = "invoice,item,amount\nINV-42,notebook,12.50\nINV-42,pencil,3.00\n"
    val path: Path = Files.createTempFile("invoice-download-", ".csv")
    try {
      Files.writeString(path, invoice)
      val config: ServerConfig = ServerConfig(
        port = Some(0),
        address = "127.0.0.1",
        mode = Mode.Test
      )

      Server.withRouterFromComponents(config) { components =>
        {
          case request if request.method == "GET" && request.path == "/invoices/INV-42/download" =>
            components.defaultActionBuilder { incoming =>
              RangeResult.ofPath(
                path,
                incoming.headers.get("Range"),
                "invoice-INV-42.csv",
                Some("text/csv")
              )
            }
        }
      } { port =>
        val client: HttpClient = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .build()
        try {
          val endpoint: URI = URI.create(s"http://127.0.0.1:${port.value}/invoices/INV-42/download")
          def download(range: Option[String]): HttpResponse[String] = {
            val builder: HttpRequest.Builder = HttpRequest.newBuilder(endpoint)
              .timeout(Duration.ofSeconds(20))
              .GET()
            range.foreach(value => builder.header("Range", value))
            client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
          }

          val complete: HttpResponse[String] = download(None)
          assertEquals(200, complete.statusCode())
          assertEquals(invoice, complete.body())
          assertEquals("bytes", complete.headers().firstValue("Accept-Ranges").orElseThrow())
          assertEquals("text/csv", complete.headers().firstValue("Content-Type").orElseThrow())
          assertTrue(complete.headers().firstValue("Content-Disposition").orElseThrow()
            .contains("invoice-INV-42.csv"))

          val firstPart: HttpResponse[String] = download(Some("bytes=0-18"))
          assertEquals(206, firstPart.statusCode())
          assertEquals(s"bytes 0-18/${invoice.length}",
            firstPart.headers().firstValue("Content-Range").orElseThrow())
          assertEquals(invoice.substring(0, 19), firstPart.body())

          val resumed: HttpResponse[String] = download(Some("bytes=19-"))
          assertEquals(206, resumed.statusCode())
          assertEquals(s"bytes 19-${invoice.length - 1}/${invoice.length}",
            resumed.headers().firstValue("Content-Range").orElseThrow())
          assertEquals(invoice, firstPart.body() + resumed.body())

          val invalid: HttpResponse[String] = download(Some(s"bytes=${invoice.length}-"))
          assertEquals(416, invalid.statusCode())
          assertEquals(s"bytes */${invoice.length}",
            invalid.headers().firstValue("Content-Range").orElseThrow())
        } finally {
          client.close()
        }
      }
    } finally {
      Files.deleteIfExists(path)
    }
  }
}
