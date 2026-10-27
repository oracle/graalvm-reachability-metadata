/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatra.scalatra_3

import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.TimeUnit
import javax.servlet.MultipartConfigElement

import scala.collection.concurrent.TrieMap

import org.eclipse.jetty.http.HttpTester
import org.eclipse.jetty.server.{LocalConnector, Server}
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import org.scalatra.ScalatraServlet
import org.scalatra.servlet.{FileItem, FileUploadSupport}

/** Exercises multipart file uploads for a warehouse CSV inventory import. */
class InventoryFileImportTest {
  @Test
  @Timeout(60)
  def importsAnUploadedStockFileAlongsideItsWarehouseFormField(): Unit = {
    val server: Server = new Server()
    val connector: LocalConnector = new LocalConnector(server)
    connector.setIdleTimeout(10000)
    server.addConnector(connector)
    server.setStopTimeout(10000)

    val context: ServletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS)
    context.setContextPath("/")
    val holder: ServletHolder = new ServletHolder(new InventoryServlet())
    context.addServlet(holder, "/*")
    holder.getRegistration.setMultipartConfig(new MultipartConfigElement("", 1048576, 2097152, 1048576))
    server.setHandler(context)

    try {
      server.start()

      val imported: HttpTester.Response = upload(connector, includeFile = true)
      assertEquals(201, imported.getStatus)
      assertEquals("central:stock.csv:text/csv:2", imported.getContent)
      assertStock(connector, "hammer", "central:hammer:12")
      assertStock(connector, "saw", "central:saw:7")

      val missingFile: HttpTester.Response = upload(connector, includeFile = false)
      assertEquals(400, missingFile.getStatus)
      assertEquals("Choose a CSV stock file", missingFile.getContent)
      assertStock(connector, "hammer", "central:hammer:12")
    } finally {
      try server.stop()
      finally server.destroy()
    }
  }

  private def upload(connector: LocalConnector, includeFile: Boolean): HttpTester.Response = {
    val boundary: String = "warehouse-import-boundary"
    val filePart: String = if (includeFile) {
      s"--$boundary\r\n" +
        "Content-Disposition: form-data; name=\"stock\"; filename=\"stock.csv\"\r\n" +
        "Content-Type: text/csv\r\n\r\n" +
        "sku,quantity\nhammer,12\nsaw,7\n\r\n"
    } else ""
    val body: String =
      s"--$boundary\r\n" +
        "Content-Disposition: form-data; name=\"warehouse\"\r\n\r\n" +
        s"central\r\n$filePart--$boundary--\r\n"
    val request: String =
      "POST /inventory/import HTTP/1.1\r\nHost: localhost\r\n" +
        s"Content-Type: multipart/form-data; boundary=$boundary\r\n" +
        s"Content-Length: ${body.getBytes(UTF_8).length}\r\nConnection: close\r\n\r\n$body"
    HttpTester.parseResponse(connector.getResponse(request, 10, TimeUnit.SECONDS))
  }

  private def assertStock(connector: LocalConnector, sku: String, expected: String): Unit = {
    val request: String =
      s"GET /inventory/central/$sku HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
    val response: HttpTester.Response =
      HttpTester.parseResponse(connector.getResponse(request, 10, TimeUnit.SECONDS))
    assertEquals(200, response.getStatus)
    assertEquals(expected, response.getContent)
  }

  private class InventoryServlet extends ScalatraServlet with FileUploadSupport {
    private val inventory: TrieMap[(String, String), Int] = TrieMap.empty

    post("/inventory/import") {
      val warehouse: String = params.getOrElse("warehouse", halt(400, "Choose a warehouse"))
      val file: FileItem = fileParams.get("stock").getOrElse(halt(400, "Choose a CSV stock file"))
      val csv: String = new String(file.get(), UTF_8)
      val lines: Vector[String] = csv.linesIterator.toVector
      if (lines.headOption != Some("sku,quantity")) halt(400, "Expected sku,quantity header")
      val rows: Vector[(String, Int)] = lines.tail.map { line =>
        val columns: Array[String] = line.split(",")
        if (columns.length != 2) halt(400, "Expected a SKU and quantity")
        val quantity: Int = columns(1).toIntOption.getOrElse(halt(400, "Expected an integer quantity"))
        columns(0) -> quantity
      }
      rows.foreach { case (sku, quantity) => inventory.put((warehouse, sku), quantity) }
      status = 201
      contentType = "text/plain"
      s"$warehouse:${file.name}:${file.contentType.getOrElse("application/octet-stream")}:${rows.size}"
    }

    get("/inventory/:warehouse/:sku") {
      val warehouse: String = params("warehouse")
      val sku: String = params("sku")
      val quantity: Int = inventory.getOrElse((warehouse, sku), halt(404, "Unknown stock item"))
      contentType = "text/plain"
      s"$warehouse:$sku:$quantity"
    }
  }
}
