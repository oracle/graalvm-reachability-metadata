/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_server_3

import java.io.BufferedInputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import play.api.Mode
import play.api.mvc.Results.Created
import play.core.server.Server
import play.core.server.ServerConfig

/** Exercises the HTTP Expect: 100-continue handshake for deferred purchase-order uploads. */
class PurchaseOrderContinueTest {
  @Test
  @Timeout(60)
  def waitsForPermissionToUploadBeforeSendingTheOrderBody(): Unit = {
    val config: ServerConfig = ServerConfig(
      port = Some(0),
      address = "127.0.0.1",
      mode = Mode.Test
    )

    Server.withRouterFromComponents(config) { components =>
      {
        case request if request.method == "POST" && request.path == "/purchase-orders" =>
          components.defaultActionBuilder(components.playBodyParsers.text(maxLength = 4096)) { incoming =>
            val rows: List[String] = incoming.body.linesIterator.drop(1).filter(_.nonEmpty).toList
            val units: Int = rows.map(_.split(",")(1).toInt).sum
            Created(s"Purchase order received: lines=${rows.size};units=$units\n")
              .withHeaders("Location" -> "/purchase-orders/PO-42")
          }
      }
    } { port =>
      val socket: Socket = new Socket()
      try {
        socket.connect(new InetSocketAddress("127.0.0.1", port.value), 10000)
        socket.setSoTimeout(20000)
        val input: BufferedInputStream = new BufferedInputStream(socket.getInputStream)
        val order: Array[Byte] = "sku,quantity\nnotebook,12\npencil,30\n".getBytes(StandardCharsets.UTF_8)
        val headers: String = s"""POST /purchase-orders HTTP/1.1
          |Host: 127.0.0.1:${port.value}
          |Content-Type: text/plain; charset=UTF-8
          |Content-Length: ${order.length}
          |Expect: 100-continue
          |Connection: close
          |
          |""".stripMargin.replace("\n", "\r\n")
        socket.getOutputStream.write(headers.getBytes(StandardCharsets.US_ASCII))
        socket.getOutputStream.flush()

        // No body bytes have been sent: the server must authorize transmission first.
        val permission: ResponseHead = readHead(input)
        assertEquals(100, permission.status)

        socket.getOutputStream.write(order)
        socket.getOutputStream.flush()

        val receipt: ResponseHead = readHead(input)
        assertEquals(201, receipt.status)
        assertEquals("/purchase-orders/PO-42", receipt.headers("location"))
        assertEquals("text/plain; charset=UTF-8", receipt.headers("content-type"))
        val length: Int = receipt.headers("content-length").toInt
        assertEquals(
          "Purchase order received: lines=2;units=42\n",
          new String(input.readNBytes(length), StandardCharsets.UTF_8)
        )
        assertEquals(-1, input.read(), "The final response must complete the closing connection")
      } finally {
        socket.close()
      }
    }
  }

  private case class ResponseHead(status: Int, headers: Map[String, String])

  private def readHead(input: InputStream): ResponseHead = {
    val statusLine: String = readLine(input)
    assertTrue(statusLine.startsWith("HTTP/1.1 "), s"Expected HTTP response, got: $statusLine")
    val headers: Map[String, String] = Iterator.continually(readLine(input))
      .takeWhile(_.nonEmpty)
      .map { line =>
        val separator: Int = line.indexOf(':')
        assertTrue(separator > 0, s"Invalid HTTP header: $line")
        line.substring(0, separator).toLowerCase(Locale.ROOT) -> line.substring(separator + 1).trim
      }
      .toMap
    ResponseHead(statusLine.split(" ")(1).toInt, headers)
  }

  private def readLine(input: InputStream): String = {
    val line: StringBuilder = new StringBuilder()
    var next: Int = input.read()
    while (next != '\n') {
      assertTrue(next >= 0, "Connection closed before the response headers were complete")
      assertTrue(line.length < 8192, "HTTP header line exceeds the client limit")
      line.append(next.toChar)
      next = input.read()
    }
    assertTrue(line.endsWith("\r"), "Expected CRLF-terminated response headers")
    line.toString().stripSuffix("\r")
  }
}
