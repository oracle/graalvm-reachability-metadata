/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_server_3

import java.lang.ref.Reference
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import play.api.Mode
import play.api.mvc.Results.BadRequest
import play.api.mvc.Results.Created
import play.core.server.Server
import play.core.server.ServerConfig

/** Exercises multipart request-body handling for an inventory CSV upload over HTTP. */
class MultipartInventoryUploadTest {
  @Test
  @Timeout(60)
  def importsUploadedInventoryAndRejectsMissingFile(): Unit = {
    val config: ServerConfig = ServerConfig(
      port = Some(0),
      address = "127.0.0.1",
      mode = Mode.Test
    )

    Server.withRouterFromComponents(config) { components =>
      {
        case request if request.method == "POST" && request.path == "/inventory/import" =>
          components.defaultActionBuilder(components.playBodyParsers.multipartFormData) { incoming =>
            incoming.body.file("inventory") match {
              case Some(upload) =>
                val csv: String = try {
                  Files.readString(upload.ref.path)
                } finally {
                  // Keep Play's temporary-file owner alive until the file has been read.
                  Reference.reachabilityFence(upload.ref)
                }
                val rows: List[String] = csv.linesIterator.drop(1).filter(_.nonEmpty).toList
                val units: Int = rows.map(_.split(",")(1).toInt).sum
                val warehouse: String = incoming.body.dataParts("warehouse").head
                Created(s"warehouse=$warehouse;file=${upload.filename};items=${rows.size};units=$units")
              case None => BadRequest("An inventory CSV file is required")
            }
          }
      }
    } { port =>
      val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
      try {
        val boundary: String = "inventory-upload-boundary"
        val fields: String = s"""--$boundary
          |Content-Disposition: form-data; name="warehouse"
          |
          |north-depot
          |""".stripMargin.replace("\n", "\r\n")
        val file: String = s"""--$boundary
          |Content-Disposition: form-data; name="inventory"; filename="stock.csv"
          |Content-Type: text/csv
          |
          |sku,quantity
          |notebook,12
          |pencil,30
          |""".stripMargin.replace("\n", "\r\n")
        val endpoint: URI = URI.create(s"http://127.0.0.1:${port.value}/inventory/import")

        def submit(body: String): HttpResponse[String] = {
          val request: HttpRequest = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", s"multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
          client.send(request, HttpResponse.BodyHandlers.ofString())
        }

        val accepted: HttpResponse[String] = submit(s"$fields$file--$boundary--\r\n")
        assertEquals(201, accepted.statusCode())
        assertEquals("warehouse=north-depot;file=stock.csv;items=2;units=42", accepted.body())

        val rejected: HttpResponse[String] = submit(s"$fields--$boundary--\r\n")
        assertEquals(400, rejected.statusCode())
        assertEquals("An inventory CSV file is required", rejected.body())
      } finally {
        client.close()
      }
    }
  }
}
