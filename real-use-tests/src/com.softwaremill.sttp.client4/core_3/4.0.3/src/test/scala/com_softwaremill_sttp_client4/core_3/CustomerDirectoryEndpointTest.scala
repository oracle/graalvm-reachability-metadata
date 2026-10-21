/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_client4.core_3

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.ConcurrentLinkedQueue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.client4.wrappers.ResolveRelativeUrisBackend
import sttp.model.Uri

/** Exercises base-URI resolution for a customer-directory client following relative pagination links. */
class CustomerDirectoryEndpointTest {
  @Test
  @Timeout(60)
  def retrievesAllCustomerPagesUnderTheConfiguredApiPrefix(): Unit = {
    val requests: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String]()
    val server: HttpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    try {
      server.createContext("/", (exchange: HttpExchange) => {
        val query: String = Option(exchange.getRequestURI.getRawQuery)
          .map(value => s"?${URLDecoder.decode(value, UTF_8)}").getOrElse("")
        val target: String = exchange.getRequestURI.getPath + query
        requests.add(s"${exchange.getRequestMethod} $target")
        target match {
          case "/tenant/west/api/customers?region=west coast" =>
            exchange.getResponseHeaders.set("X-Next-Page", "customers?region=west%20coast&page=2")
            respond(exchange, 200, "C17,Alice\nC21,Bob\n")
          case "/tenant/west/api/customers?region=west coast&page=2" =>
            respond(exchange, 200, "C42,Carol\n")
          case _ => respond(exchange, 404, "Unknown directory endpoint")
        }
      })
      server.start()

      val port: Int = server.getAddress.getPort
      val backend: SyncBackend = ResolveRelativeUrisBackend(
        HttpClientSyncBackend(BackendOptions.connectionTimeout(10.seconds)),
        uri"http://127.0.0.1:$port/tenant/west/api/"
      )
      try {
        val directory: DirectoryClient = new DirectoryClient(backend)
        val customers: Vector[Customer] = directory.customers("west coast")

        assertEquals(
          Vector(Customer("C17", "Alice"), Customer("C21", "Bob"), Customer("C42", "Carol")),
          customers
        )
        assertEquals(
          List(
            "GET /tenant/west/api/customers?region=west coast",
            "GET /tenant/west/api/customers?region=west coast&page=2"
          ),
          requests.iterator().asScala.toList
        )
      } finally backend.close()
    } finally server.stop(0)
  }

  private case class Customer(id: String, name: String)

  private class DirectoryClient(backend: SyncBackend) {
    def customers(region: String): Vector[Customer] = {
      def readPage(location: Uri, pagesRemaining: Int): Vector[Customer] = {
        require(pagesRemaining > 0, "Directory pagination exceeded its page limit")
        val response: Response[Either[String, String]] = basicRequest
          .get(location)
          .readTimeout(10.seconds)
          .send(backend)
        val text: String = response.body.fold(
          error => throw new IllegalStateException(s"Directory returned ${response.code}: $error"),
          identity
        )
        val page: Vector[Customer] = text.linesIterator.map { line =>
          val fields: Array[String] = line.split(",", 2)
          Customer(fields(0), fields(1))
        }.toVector
        response.header("X-Next-Page") match {
          case Some(next) => page ++ readPage(Uri.unsafeParse(next), pagesRemaining - 1)
          case None => page
        }
      }
      readPage(uri"customers?region=$region", 5)
    }
  }

  private def respond(exchange: HttpExchange, status: Int, text: String): Unit = {
    try {
      val bytes: Array[Byte] = text.getBytes(UTF_8)
      exchange.getResponseHeaders.set("Content-Type", "text/csv; charset=UTF-8")
      exchange.sendResponseHeaders(status, bytes.length.toLong)
      exchange.getResponseBody.write(bytes)
    } finally exchange.close()
  }
}
