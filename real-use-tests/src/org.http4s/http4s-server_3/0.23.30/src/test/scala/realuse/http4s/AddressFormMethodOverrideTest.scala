/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package realuse.http4s

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import org.http4s.{HttpApp, HttpRoutes, Method, Request, Status, Uri, UrlForm}
import org.http4s.dsl.io.*
import org.http4s.headers.Location
import org.http4s.implicits.*
import org.http4s.server.middleware.HttpMethodOverrider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises HTTP method override middleware for browser forms editing and deleting a saved address. */
class AddressFormMethodOverrideTest {
  @Test
  @Timeout(60)
  def dispatchesPostFormsWithoutAllowingGetRequestsToDeleteAddresses(): Unit = {
    val addressUri: Uri = Uri.unsafeFromString("/account/address")
    val config = HttpMethodOverrider.defaultConfig[IO, IO]
      .withOverrideStrategy(HttpMethodOverrider.QueryOverrideStrategy[IO, IO]("_method"))
      .withOverridableMethods(Set(Method.POST))

    // HTML forms use POST, with the intended operation encoded in their action URL.
    val editForm: Request[IO] = Request[IO](
      method = Method.POST,
      uri = addressUri.withQueryParam("_method", "put")
    ).withEntity(UrlForm("street" -> "42 Maple Lane", "city" -> "New Town"))
    val deleteForm: Request[IO] = Request[IO](
      method = Method.POST,
      uri = addressUri.withQueryParam("_method", "DELETE")
    ).withEntity(UrlForm.empty)

    val checks: IO[Unit] = for {
      savedAddress <- Ref.of[IO, Option[String]](Some("7 Old Road, Old Town"))
      routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
        case GET -> Root / "account" / "address" =>
          savedAddress.get.flatMap {
            case Some(address) => Ok(address)
            case None => NotFound("No saved address")
          }
        case request @ PUT -> Root / "account" / "address" =>
          request.as[UrlForm].flatMap { form =>
            (form.values.get("street").flatMap(_.headOption),
              form.values.get("city").flatMap(_.headOption)) match {
              case (Some(street), Some(city)) =>
                savedAddress.set(Some(s"$street, $city")) *> SeeOther(Location(addressUri))
              case _ => BadRequest("Street and city are required")
            }
          }
        case DELETE -> Root / "account" / "address" =>
          savedAddress.set(None) *> NoContent()
      }
      application: HttpApp[IO] = HttpMethodOverrider(routes.orNotFound, config)
      // A bare POST must not silently become an update.
      missingOverride <- application.run(Request[IO](method = Method.POST, uri = addressUri))
      original <- application.run(Request[IO](uri = addressUri))
      originalBody <- original.as[String]
      edited <- application.run(editForm)
      editedBody <- edited.as[String]
      refreshed <- application.run(Request[IO](uri = addressUri))
      refreshedBody <- refreshed.as[String]
      // Following a link must remain a safe GET, even if its query requests DELETE.
      linked <- application.run(Request[IO](uri = addressUri.withQueryParam("_method", "DELETE")))
      linkedBody <- linked.as[String]
      stillSaved <- application.run(Request[IO](uri = addressUri))
      stillSavedBody <- stillSaved.as[String]
      deleted <- application.run(deleteForm)
      deletedBody <- deleted.as[String]
      missing <- application.run(Request[IO](uri = addressUri))
      missingBody <- missing.as[String]
    } yield {
      assertEquals(Status.NotFound, missingOverride.status)
      assertEquals(Status.Ok, original.status)
      assertEquals("7 Old Road, Old Town", originalBody)
      assertEquals(Status.SeeOther, edited.status)
      assertEquals(Some(Location(addressUri)), edited.headers.get[Location])
      assertEquals("", editedBody)
      assertEquals(Status.Ok, refreshed.status)
      assertEquals("42 Maple Lane, New Town", refreshedBody)
      assertEquals(Status.Ok, linked.status)
      assertEquals(refreshedBody, linkedBody)
      assertEquals(Status.Ok, stillSaved.status)
      assertEquals(refreshedBody, stillSavedBody)
      assertEquals(Status.NoContent, deleted.status)
      assertEquals("", deletedBody)
      assertEquals(Status.NotFound, missing.status)
      assertEquals("No saved address", missingBody)
    }

    checks.unsafeRunSync()
  }
}
