/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatra.scalatra_3

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.TimeUnit
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}

import org.eclipse.jetty.http.HttpTester
import org.eclipse.jetty.server.{LocalConnector, Server}
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import org.scalatra.{Ok, ScalatraServlet}

/** Exercises streamed binary response rendering for downloadable shipment document archives. */
class ShipmentDocumentDownloadTest {
  @Test
  @Timeout(60)
  def downloadsEachShipmentsDocumentsAsAnIndependentZipAttachment(): Unit = {
    val server: Server = new Server()
    val connector: LocalConnector = new LocalConnector(server)
    connector.setIdleTimeout(10000)
    server.addConnector(connector)
    server.setStopTimeout(10000)

    val context: ServletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS)
    context.setContextPath("/")
    context.addServlet(new ServletHolder(new ShipmentServlet()), "/*")
    server.setHandler(context)

    try {
      server.start()

      val first: HttpTester.Response = download(connector, "S100")
      assertAttachment(first, "S100")
      assertEquals(
        Map("packing-list.csv" -> "sku,quantity\nhammer,2\n", "destination.txt" -> "North depot\n"),
        readDocuments(first)
      )

      val second: HttpTester.Response = download(connector, "S200")
      assertAttachment(second, "S200")
      assertEquals(
        Map("packing-list.csv" -> "sku,quantity\nspade,3\n", "destination.txt" -> "South depot\n"),
        readDocuments(second)
      )

      val missing: HttpTester.Response = download(connector, "unknown")
      assertEquals(404, missing.getStatus)
      assertEquals("Shipment not found", missing.getContent)

      val repeated: HttpTester.Response = download(connector, "S100")
      assertAttachment(repeated, "S100")
      assertEquals(readDocuments(first), readDocuments(repeated))
    } finally {
      try server.stop()
      finally server.destroy()
    }
  }

  private def download(connector: LocalConnector, shipment: String): HttpTester.Response = {
    val request: String =
      s"GET /shipments/$shipment/documents HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
    HttpTester.parseResponse(connector.getResponse(request, 10, TimeUnit.SECONDS))
  }

  private def assertAttachment(response: HttpTester.Response, shipment: String): Unit = {
    assertEquals(200, response.getStatus)
    assertEquals("application/zip", response.get("Content-Type").split(";")(0))
    assertEquals(s"attachment; filename=\"$shipment-documents.zip\"", response.get("Content-Disposition"))
    assertEquals("private, no-store", response.get("Cache-Control"))
  }

  private def readDocuments(response: HttpTester.Response): Map[String, String] = {
    val zip: ZipInputStream = new ZipInputStream(new ByteArrayInputStream(response.getContentBytes), UTF_8)
    try {
      val documents: Vector[(String, String)] = Iterator.continually(zip.getNextEntry)
        .takeWhile(_ != null)
        .map { entry =>
          val text: String = new String(zip.readAllBytes(), UTF_8)
          zip.closeEntry()
          entry.getName -> text
        }.toVector
      assertEquals(2, documents.size)
      documents.toMap
    } finally zip.close()
  }

  private case class Shipment(sku: String, quantity: Int, destination: String)

  private class ShipmentServlet extends ScalatraServlet {
    private val shipments: Map[String, Shipment] = Map(
      "S100" -> Shipment("hammer", 2, "North depot"),
      "S200" -> Shipment("spade", 3, "South depot")
    )

    get("/shipments/:id/documents") {
      val id: String = params("id")
      val shipment: Shipment = shipments.getOrElse(id, halt(404, "Shipment not found"))
      val buffer: ByteArrayOutputStream = new ByteArrayOutputStream()
      val zip: ZipOutputStream = new ZipOutputStream(buffer, UTF_8)
      try {
        val documents: Vector[(String, String)] = Vector(
          "packing-list.csv" -> s"sku,quantity\n${shipment.sku},${shipment.quantity}\n",
          "destination.txt" -> s"${shipment.destination}\n"
        )
        documents.foreach { case (name, text) =>
          val entry: ZipEntry = new ZipEntry(name)
          entry.setTime(0L)
          zip.putNextEntry(entry)
          zip.write(text.getBytes(UTF_8))
          zip.closeEntry()
        }
      } finally zip.close()

      Ok(
        new ByteArrayInputStream(buffer.toByteArray),
        headers = Map(
          "Content-Type" -> "application/zip",
          "Content-Disposition" -> s"attachment; filename=\"$id-documents.zip\"",
          "Cache-Control" -> "private, no-store"
        )
      )
    }
  }
}
