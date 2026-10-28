/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package realuse.tapircirce

import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import io.circe.{Codec as CirceCodec}
import io.circe.parser.parse
import org.http4s.Header
import org.http4s.HttpApp
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.typelevel.ci.CIString
import sttp.tapir.*
import sttp.tapir.json.circe.TapirJsonCirce
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.duration.*

/** Exercises Circe JSON codecs in request and response headers for configurable CSV exports. */
class ExportJsonHeadersTest extends TapirJsonCirce {
  case class ExportOptions(columns: List[String], includeHeader: Boolean)
      derives CirceCodec.AsObject, Schema
  case class ExportSummary(rows: Int, columns: Int) derives CirceCodec.AsObject, Schema
  case class Stock(sku: String, available: Int)

  @Test
  @Timeout(60)
  def selectsExportColumnsFromJsonHeaderAndReturnsJsonSummary(): Unit = {
    val exports: Ref[IO, Int] = Ref.of[IO, Int](0).unsafeRunSync()
    val stock: List[Stock] = List(Stock("tea-17", 12), Stock("cup-3", 0))
    val optionsCodec: Codec[String, ExportOptions, CodecFormat.Json] = circeCodec[ExportOptions]
    val summaryCodec: Codec[String, ExportSummary, CodecFormat.Json] = circeCodec[ExportSummary]
    val download = endpoint.get
      .in("stock-export")
      .in(header[String]("X-Export-Options").mapDecode(optionsCodec.decode)(optionsCodec.encode))
      .out(header[String]("X-Export-Summary").mapDecode(summaryCodec.decode)(summaryCodec.encode))
      .out(stringBody)
      .serverLogicSuccess[IO] { options =>
        val rows: List[String] = stock.map { item =>
          options.columns.map {
            case "sku" => item.sku
            case "available" => item.available.toString
            case _ => ""
          }.mkString(",")
        }
        val lines: List[String] =
          if (options.includeHeader) options.columns.mkString(",") :: rows else rows
        exports.update(_ + 1).as((ExportSummary(stock.size, options.columns.size), lines.mkString("\n")))
      }
    val app: HttpApp[IO] = Http4sServerInterpreter[IO]().toRoutes(download).orNotFound
    val summaryName: CIString = CIString("X-Export-Summary")

    def fetch(options: Option[String]): (Status, Option[String], String) = {
      val request: Request[IO] = Request[IO](Method.GET, Uri.unsafeFromString("/stock-export"))
      val configured: Request[IO] = options.fold(request) { value =>
        request.putHeaders(Header.Raw(CIString("X-Export-Options"), value))
      }
      (for {
        response <- app.run(configured)
        text <- response.bodyText.compile.string
      } yield (response.status, response.headers.headers.find(_.name == summaryName).map(_.value), text))
        .timeout(15.seconds).unsafeRunSync()
    }

    val full: (Status, Option[String], String) = fetch(Some(
      """{"columns":["sku","available"],"includeHeader":true}"""
    ))
    assertEquals(Status.Ok, full._1)
    assertEquals("sku,available\ntea-17,12\ncup-3,0", full._3)
    assertEquals(
      parse("""{"rows":2,"columns":2}""").fold(throw _, identity),
      parse(full._2.getOrElse(throw new AssertionError("Missing JSON summary header")))
        .fold(throw _, identity)
    )

    val countsOnly: (Status, Option[String], String) = fetch(Some(
      """{"columns":["available"],"includeHeader":false}"""
    ))
    assertEquals(Status.Ok, countsOnly._1)
    assertEquals("12\n0", countsOnly._3)
    assertEquals(
      parse("""{"rows":2,"columns":1}""").fold(throw _, identity),
      parse(countsOnly._2.getOrElse(throw new AssertionError("Missing JSON summary header")))
        .fold(throw _, identity)
    )

    val invalid: List[Option[String]] = List(
      None,
      Some("{not-json}"),
      Some("""{"columns":"sku","includeHeader":true}"""),
      Some("""{"columns":["sku"],"includeHeader":"yes"}"""),
      Some("""{"columns":["sku"]}""")
    )
    invalid.foreach { options =>
      val rejected: (Status, Option[String], String) = fetch(options)
      assertEquals(Status.BadRequest, rejected._1)
      assertEquals(None, rejected._2)
    }
    assertEquals(2, exports.get.unsafeRunSync())
  }
}
