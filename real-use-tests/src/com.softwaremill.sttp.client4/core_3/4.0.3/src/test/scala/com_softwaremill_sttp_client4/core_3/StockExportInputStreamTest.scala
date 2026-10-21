/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_client4.core_3

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import scala.concurrent.duration.*
import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.model.StatusCode

/** Exercises managed input-stream responses by incrementally aggregating a chunked stock-movement CSV export. */
class StockExportInputStreamTest {
  private case class StockTotals(rows: Int, quantities: Map[String, Long])

  @Test
  @Timeout(60)
  def aggregatesStockMovementsWithoutBufferingTheExport(): Unit = {
    val server: HttpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    try {
      server.createContext("/stock/movements.csv", (exchange: HttpExchange) => {
        try {
          exchange.getResponseHeaders.set("Content-Type", "text/csv; charset=UTF-8")
          exchange.getResponseHeaders.set("X-Export-Id", "movements-west-17")
          exchange.sendResponseHeaders(200, 0)
          val writer: BufferedWriter = new BufferedWriter(new OutputStreamWriter(exchange.getResponseBody, UTF_8))
          try {
            writer.write("item,quantity\n")
            for (index <- 0 until 1500) {
              writer.write("tea,2\ncoffee,5\n")
            }
          } finally writer.close()
        } finally exchange.close()
      })
      server.start()

      val backend: WebSocketSyncBackend = HttpClientSyncBackend(BackendOptions.connectionTimeout(10.seconds))
      try {
        val port: Int = server.getAddress.getPort
        val response: Response[Either[String, StockTotals]] = basicRequest
          .get(uri"http://127.0.0.1:$port/stock/movements.csv")
          .readTimeout(20.seconds)
          .response(asInputStream(summarize))
          .send(backend)

        assertEquals(StatusCode.Ok, response.code)
        assertEquals(Some("movements-west-17"), response.header("X-Export-Id"))
        assertEquals(Right(StockTotals(3000, Map("tea" -> 3000L, "coffee" -> 7500L))), response.body)
        val totals: StockTotals = response.body.fold(error => throw new IllegalStateException(error), identity)
        assertEquals(10500L, totals.quantities.values.sum)
      } finally backend.close()
    } finally server.stop(0)
  }

  private def summarize(stream: InputStream): StockTotals = {
    val reader: BufferedReader = new BufferedReader(new InputStreamReader(stream, UTF_8))
    try {
      require(reader.readLine() == "item,quantity", "Unexpected stock export columns")
      Iterator.continually(reader.readLine()).takeWhile(_ != null)
        .foldLeft(StockTotals(0, Map.empty[String, Long])) { (totals, line) =>
          val fields: Array[String] = line.split(",", 2)
          val item: String = fields(0)
          val quantity: Long = fields(1).toLong
          StockTotals(totals.rows + 1, totals.quantities.updated(item, totals.quantities.getOrElse(item, 0L) + quantity))
        }
    } finally reader.close()
  }
}
