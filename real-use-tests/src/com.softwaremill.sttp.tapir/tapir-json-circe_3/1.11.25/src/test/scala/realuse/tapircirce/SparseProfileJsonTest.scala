/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package realuse.tapircirce

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Codec
import io.circe.Json
import io.circe.Printer
import io.circe.parser.parse
import org.http4s.HttpApp
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import sttp.tapir.*
import sttp.tapir.json.circe.TapirJsonCirce
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.duration.*

/** Exercises custom Circe JSON printer configuration for sparse public profile responses. */
class SparseProfileJsonTest extends TapirJsonCirce {
  override def jsonPrinter: Printer = Printer.noSpaces.copy(dropNullValues = true)

  case class Contact(email: Option[String], phone: Option[String]) derives Codec.AsObject, Schema
  case class Profile(id: String, displayName: String, bio: Option[String], contact: Contact,
                     tags: List[String], verified: Boolean) derives Codec.AsObject, Schema

  @Test
  @Timeout(60)
  def omitsAbsentFieldsButPreservesPresentAndEmptyValues(): Unit = {
    val sparse: Profile = Profile(
      "user-17", "Ada \"A\" Lovelace", None, Contact(Some("ada@example.test"), None), Nil, false
    )
    val complete: Profile = Profile(
      "user-18", "Grace Hopper", Some(""), Contact(None, Some("555-0180")), List("compiler"), true
    )
    val profiles = endpoint.get
      .in("profiles" / path[String]("id"))
      .out(jsonBody[Profile])
      .serverLogicSuccess[IO] { id => IO.pure(if (id == sparse.id) sparse else complete) }
    val app: HttpApp[IO] = Http4sServerInterpreter[IO]().toRoutes(profiles).orNotFound

    def fetch(id: String): (Status, Option[MediaType], Json) = {
      val request: Request[IO] = Request[IO](Method.GET, Uri.unsafeFromString(s"/profiles/$id"))
      (for {
        response <- app.run(request)
        body <- response.bodyText.compile.string
      } yield (response.status, response.contentType.map(_.mediaType),
        parse(body).fold(throw _, identity))).timeout(30.seconds).unsafeRunSync()
    }

    val first: (Status, Option[MediaType], Json) = fetch(sparse.id)
    val expectedSparse: Json = parse("""{
      "id": "user-17",
      "displayName": "Ada \"A\" Lovelace",
      "contact": {"email": "ada@example.test"},
      "tags": [],
      "verified": false
    }""").fold(throw _, identity)
    assertEquals(Status.Ok, first._1)
    assertEquals(Some(MediaType.application.json), first._2)
    assertEquals(expectedSparse, first._3)

    val second: (Status, Option[MediaType], Json) = fetch(complete.id)
    val expectedComplete: Json = parse("""{
      "id": "user-18",
      "displayName": "Grace Hopper",
      "bio": "",
      "contact": {"phone": "555-0180"},
      "tags": ["compiler"],
      "verified": true
    }""").fold(throw _, identity)
    assertEquals(Status.Ok, second._1)
    assertEquals(Some(MediaType.application.json), second._2)
    assertEquals(expectedComplete, second._3)
  }
}
