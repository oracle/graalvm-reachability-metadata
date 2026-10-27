/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_client4.circe_3

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.circe.ParsingFailure
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sttp.client4.*
import sttp.client4.circe.*
import sttp.model.Encodings
import sttp.model.StatusCode

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.InflaterInputStream
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

/**
 * Exercises circe JSON over content-encoded HTTP transport: a catalog sync client decodes gzip- and
 * deflate-compressed responses with `asJson`, uploads `asJson` bodies compressed with `compressBody`,
 * decodes a JSON reply in a non-UTF-8 charset, and sees raw bytes when auto-decompression is disabled.
 */
class CatalogSyncCompressionTest {
  private final case class Product(sku: String, name: String, priceCents: Long, tags: List[String])
  private final case class Catalog(region: String, products: List[Product])
  private final case class ImportBatch(source: String, products: List[Product])
  private final case class ImportReceipt(source: String, accepted: Int, contentEncoding: String, wireBytes: Int)

  /** What the server observed for one request: the negotiated or received encoding and body sizes. */
  private final case class Observed(path: String, acceptEncoding: String, contentEncoding: String, wireBytes: Int)

  private val catalog: Catalog = Catalog(
    "eu-west",
    (1 to 120).toList.map { index =>
      Product(f"SKU-$index%04d", s"Catalog item number $index", 199L * index, List("sync", s"batch-${index % 4}"))
    }
  )

  private val legacyCatalog: Catalog = Catalog(
    "fr-legacy",
    List(
      Product("FR-1", "Caf\u00e9 cr\u00e8me", 450L, List("boisson")),
      Product("FR-2", "P\u00e2te \u00e0 tartiner", 699L, List("\u00e9picerie"))
    )
  )

  private val observed: ConcurrentLinkedQueue[Observed] = new ConcurrentLinkedQueue[Observed]()
  private val imported: ConcurrentLinkedQueue[ImportBatch] = new ConcurrentLinkedQueue[ImportBatch]()

  private var server: HttpServer = null
  private var backend: SyncBackend = null
  private var baseUri: String = null

  @BeforeEach
  def startServer(): Unit = {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext(
      "/catalog",
      (exchange: HttpExchange) => {
        try {
          val accepted: String = Option(exchange.getRequestHeaders.getFirst("Accept-Encoding")).getOrElse("")
          val encoding: String = negotiate(accepted)
          val wire: Array[Byte] = encode(catalog.asJson.noSpaces.getBytes(StandardCharsets.UTF_8), encoding)
          observed.add(Observed("/catalog", accepted, encoding, wire.length))
          send(exchange, 200, "application/json", encoding, wire)
        } finally {
          exchange.close()
        }
      }
    )
    server.createContext(
      "/legacy-catalog",
      (exchange: HttpExchange) => {
        try {
          val latin1: Charset = StandardCharsets.ISO_8859_1
          val wire: Array[Byte] = legacyCatalog.asJson.noSpaces.getBytes(latin1)
          observed.add(Observed("/legacy-catalog", "", "identity", wire.length))
          send(exchange, 200, "application/json; charset=ISO-8859-1", "identity", wire)
        } finally {
          exchange.close()
        }
      }
    )
    server.createContext(
      "/imports",
      (exchange: HttpExchange) => {
        try {
          val received: String = Option(exchange.getRequestHeaders.getFirst("Content-Encoding")).getOrElse("identity")
          val wire: Array[Byte] = exchange.getRequestBody.readAllBytes()
          val json: String = new String(decodeBody(wire, received), StandardCharsets.UTF_8)
          observed.add(Observed("/imports", "", received, wire.length))
          decode[ImportBatch](json) match {
            case Right(batch) =>
              imported.add(batch)
              val receipt: ImportReceipt = ImportReceipt(batch.source, batch.products.size, received, wire.length)
              val reply: Array[Byte] = encode(receipt.asJson.noSpaces.getBytes(StandardCharsets.UTF_8), Encodings.Gzip)
              send(exchange, 202, "application/json", Encodings.Gzip, reply)
            case Left(error) =>
              send(exchange, 400, "text/plain", "identity", error.getMessage.getBytes(StandardCharsets.UTF_8))
          }
        } finally {
          exchange.close()
        }
      }
    )
    server.start()
    baseUri = s"http://127.0.0.1:${server.getAddress.getPort}"
    backend = DefaultSyncBackend(BackendOptions.connectionTimeout(10.seconds))
  }

  @AfterEach
  def stopServer(): Unit = {
    backend.close()
    server.stop(0)
  }

  @Test
  def decodesGzipAndDeflateCompressedCatalogs(): Unit = {
    val defaultResponse: Response[Either[ResponseException[String], Catalog]] = basicRequest
      .get(uri"$baseUri/catalog")
      .response(asJson[Catalog])
      .send(backend)
    val deflateResponse: Response[Either[ResponseException[String], Catalog]] = basicRequest
      .get(uri"$baseUri/catalog")
      .acceptEncoding(Encodings.Deflate)
      .response(asJson[Catalog])
      .send(backend)
    val identityResponse: Response[Either[ResponseException[String], Catalog]] = basicRequest
      .get(uri"$baseUri/catalog")
      .acceptEncoding(Encodings.Identity)
      .response(asJson[Catalog])
      .send(backend)

    assertThat(defaultResponse.code).isEqualTo(StatusCode.Ok)
    assertThat(defaultResponse.body).isEqualTo(Right(catalog))
    assertThat(deflateResponse.body).isEqualTo(Right(catalog))
    assertThat(identityResponse.body).isEqualTo(Right(catalog))

    val served: List[Observed] = observed.asScala.toList
    assertThat(served.map(_.contentEncoding)).isEqualTo(List(Encodings.Gzip, Encodings.Deflate, "identity"))
    assertThat(served.head.acceptEncoding).contains(Encodings.Gzip)
    val identityBytes: Int = served(2).wireBytes
    assertThat(served(0).wireBytes).isLessThan(identityBytes / 4)
    assertThat(served(1).wireBytes).isLessThan(identityBytes / 4)
  }

  @Test
  def uploadsCompressedJsonBodiesAndDecodesCompressedReceipts(): Unit = {
    val nightly: ImportBatch = ImportBatch("nightly-feed", catalog.products.take(80))
    val manual: ImportBatch = ImportBatch("manual-fix", legacyCatalog.products)

    val gzipped: Response[Either[ResponseException[String], ImportReceipt]] = basicRequest
      .post(uri"$baseUri/imports")
      .body(asJson(nightly))
      .compressBody(Encodings.Gzip)
      .response(asJson[ImportReceipt])
      .send(backend)
    val deflated: Response[Either[ResponseException[String], ImportReceipt]] = basicRequest
      .post(uri"$baseUri/imports")
      .body(asJson(manual))
      .compressBody(Encodings.Deflate)
      .response(asJson[ImportReceipt])
      .send(backend)

    assertThat(gzipped.code).isEqualTo(StatusCode.Accepted)
    val gzipReceipt: ImportReceipt = gzipped.body.fold(error => throw error, identity)
    assertThat(gzipReceipt.source).isEqualTo("nightly-feed")
    assertThat(gzipReceipt.accepted).isEqualTo(80)
    assertThat(gzipReceipt.contentEncoding).isEqualTo(Encodings.Gzip)
    assertThat(gzipReceipt.wireBytes).isLessThan(nightly.asJson.noSpaces.length / 4)

    val deflateReceipt: ImportReceipt = deflated.body.fold(error => throw error, identity)
    assertThat(deflateReceipt).isEqualTo(ImportReceipt("manual-fix", 2, Encodings.Deflate, deflateReceipt.wireBytes))
    assertThat(imported.asScala.toList).isEqualTo(List(nightly, manual))
  }

  @Test
  def decodesJsonInTheCharsetNamedByTheContentType(): Unit = {
    val response: Response[Either[ResponseException[String], Catalog]] = basicRequest
      .get(uri"$baseUri/legacy-catalog")
      .response(asJson[Catalog])
      .send(backend)

    assertThat(response.contentType).isEqualTo(Some("application/json; charset=ISO-8859-1"))
    val decoded: Catalog = response.body.fold(error => throw error, identity)
    assertThat(decoded).isEqualTo(legacyCatalog)
    assertThat(decoded.products.map(_.name)).isEqualTo(List("Caf\u00e9 cr\u00e8me", "P\u00e2te \u00e0 tartiner"))
  }

  @Test
  def disablingAutoDecompressionHandsCompressedBytesToTheApplication(): Unit = {
    val raw: Response[Array[Byte]] = basicRequest
      .get(uri"$baseUri/catalog")
      .acceptEncoding(Encodings.Gzip)
      .disableAutoDecompression
      .response(asByteArrayAlways)
      .send(backend)

    assertThat(raw.header("Content-Encoding")).isEqualTo(Some(Encodings.Gzip))
    assertThat(raw.body(0)).isEqualTo(0x1f.toByte)
    assertThat(raw.body(1)).isEqualTo(0x8b.toByte)
    val inflated: String = new String(decodeBody(raw.body, Encodings.Gzip), StandardCharsets.UTF_8)
    assertThat(decode[Catalog](inflated)).isEqualTo(Right(catalog))

    val undecoded: Response[Either[ResponseException[String], Catalog]] = basicRequest
      .get(uri"$baseUri/catalog")
      .acceptEncoding(Encodings.Gzip)
      .disableAutoDecompression
      .response(asJson[Catalog])
      .send(backend)

    undecoded.body match {
      case Left(ResponseException.DeserializationException(_, cause, metadata)) =>
        assertThat(metadata.code).isEqualTo(StatusCode.Ok)
        assertThat(cause).isInstanceOf(classOf[ParsingFailure])
      case other => throw new AssertionError(s"Expected a deserialization failure, got $other")
    }
  }

  private def negotiate(acceptEncoding: String): String = {
    val offered: List[String] = acceptEncoding.split(',').toList.map(_.trim.takeWhile(_ != ';')).filter(_.nonEmpty)
    offered.find(encoding => encoding == Encodings.Gzip || encoding == Encodings.Deflate).getOrElse("identity")
  }

  private def encode(bytes: Array[Byte], encoding: String): Array[Byte] = {
    if (encoding == "identity") {
      bytes
    } else {
      val buffer: ByteArrayOutputStream = new ByteArrayOutputStream()
      val compressing: OutputStream =
        if (encoding == Encodings.Gzip) new GZIPOutputStream(buffer) else new DeflaterOutputStream(buffer)
      compressing.write(bytes)
      compressing.close()
      buffer.toByteArray
    }
  }

  private def decodeBody(bytes: Array[Byte], encoding: String): Array[Byte] = {
    val source: InputStream = new ByteArrayInputStream(bytes)
    val decompressing: InputStream = encoding match {
      case Encodings.Gzip    => new GZIPInputStream(source)
      case Encodings.Deflate => new InflaterInputStream(source)
      case _                 => source
    }
    try decompressing.readAllBytes()
    finally decompressing.close()
  }

  private def send(exchange: HttpExchange, status: Int, contentType: String, encoding: String, body: Array[Byte]): Unit = {
    exchange.getResponseHeaders.set("Content-Type", contentType)
    if (encoding != "identity") {
      exchange.getResponseHeaders.set("Content-Encoding", encoding)
    }
    exchange.sendResponseHeaders(status, body.length.toLong)
    exchange.getResponseBody.write(body)
  }
}
