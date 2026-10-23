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
import io.circe.Codec
import io.circe.Json
import io.circe.parser.parse
import org.http4s.HttpApp
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.headers.`Content-Type`
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import sttp.tapir.*
import sttp.tapir.json.circe.TapirJsonCirce
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.duration.*

/** Exercises recursive Circe JSON body codecs for hierarchical folder templates. */
class RecursiveFolderJsonTest extends TapirJsonCirce {
  case class Folder(name: String, children: List[Folder]) derives Codec.AsObject

  // A def lets Tapir resolve recursive references through its schema derivation cache.
  implicit def folderSchema: Schema[Folder] = Schema.derived[Folder]

  private def normalize(folder: Folder): Folder =
    Folder(folder.name.trim, folder.children.map(normalize).sortBy(_.name))

  @Test
  @Timeout(60)
  def importsRecursiveTemplateAndRejectsInvalidDescendantsWithoutReplacingIt(): Unit = {
    val stored: Ref[IO, Folder] = Ref.of[IO, Folder](Folder("empty", Nil)).unsafeRunSync()
    val replace = endpoint.put
      .in("folder-template")
      .in(jsonBody[Folder])
      .out(statusCode(sttp.model.StatusCode.NoContent))
      .serverLogicSuccess[IO] { folder => stored.set(normalize(folder)) }
    val read = endpoint.get
      .in("folder-template")
      .out(jsonBody[Folder])
      .serverLogicSuccess[IO] { _ => stored.get }
    val app: HttpApp[IO] = Http4sServerInterpreter[IO]()
      .toRoutes(List(replace, read)).orNotFound

    def put(body: String): Status = {
      val request: Request[IO] = Request[IO](Method.PUT, Uri.unsafeFromString("/folder-template"))
        .withEntity(body)
        .putHeaders(`Content-Type`(MediaType.application.json))
      app.run(request).flatMap { response =>
        response.body.compile.drain.as(response.status)
      }.timeout(15.seconds).unsafeRunSync()
    }

    def assertTemplate(expected: String): Unit = {
      val request: Request[IO] = Request[IO](Method.GET, Uri.unsafeFromString("/folder-template"))
      val (status, contentType, body) = (for {
        response <- app.run(request)
        text <- response.bodyText.compile.string
      } yield (response.status, response.contentType.map(_.mediaType), text))
        .timeout(15.seconds).unsafeRunSync()
      val expectedJson: Json = parse(expected).fold(throw _, identity)
      assertEquals(Status.Ok, status)
      assertEquals(Some(MediaType.application.json), contentType)
      assertEquals(expectedJson, parse(body).fold(throw _, identity))
    }

    val imported: String = """{
      "name":" Workspace ",
      "children":[
        {"name":" Projects ","children":[
          {"name":" Launch ","children":[{"name":" Notes ","children":[]}]}
        ]},
        {"name":" Archive ","children":[]}
      ]
    }"""
    val normalized: String = """{
      "name":"Workspace",
      "children":[
        {"name":"Archive","children":[]},
        {"name":"Projects","children":[
          {"name":"Launch","children":[{"name":"Notes","children":[]}]}
        ]}
      ]
    }"""
    assertEquals(Status.NoContent, put(imported))
    assertTemplate(normalized)

    val invalidDescendant: String = imported.replace("\" Notes \"", "42")
    assertEquals(Status.BadRequest, put(invalidDescendant))
    assertTemplate(normalized)

    // A leaf at the root is a valid replacement, not a missing hierarchy.
    assertEquals(Status.NoContent, put("""{"name":" Personal ","children":[]}"""))
    assertTemplate("""{"name":"Personal","children":[]}""")
  }
}
