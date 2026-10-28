/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import zio.{Ref, Runtime, Scope, Unsafe, ZIO}
import zio.http.*
import zio.schema.{DeriveSchema, Schema}

/** Exercises ZIO HTTP backend flash scope for a profile update's one-time redirect confirmation. */
class ProfileUpdateFlashTest {
  case class UpdateReceipt(previousName: String, newName: String)
  object UpdateReceipt {
    implicit val schema: Schema[UpdateReceipt] = DeriveSchema.gen[UpdateReceipt]
  }

  @Test
  @Timeout(60)
  def showsTheConfirmationOnlyOnceWithoutLosingTheSavedProfile(): Unit = {
    val program: ZIO[Flash.Backend & Scope, Throwable, Unit] = for {
      backend <- ZIO.service[Flash.Backend]
      name <- Ref.make("Ada")
      routes = profileRoutes(backend, name)
      initial <- routes.runZIO(Request.get("/profile"))
      _ <- assertPage(initial, "Profile: Ada\nNo recent changes")
      saved <- routes.runZIO(Request.post("/profile", Body.fromString("Ada & Sons")))
      _ <- ZIO.succeed {
        assertEquals(Status.SeeOther, saved.status)
        assertEquals(Some("/profile"), saved.rawHeader("Location"))
      }
      header <- ZIO.fromOption(saved.rawHeader("Set-Cookie"))
        .orElseFail(new IllegalStateException("Profile redirect must carry a flash cookie"))
      cookie <- ZIO.fromEither(Cookie.Response.decode(header, validate = true))
      // An unrelated request without this cookie must not consume the pending confirmation.
      unrelated <- routes.runZIO(Request.get("/profile"))
      _ <- assertPage(unrelated, "Profile: Ada & Sons\nNo recent changes")
      redirectedRequest = Request.get("/profile").addCookie(cookie.toRequest)
      redirected <- routes.runZIO(redirectedRequest)
      _ <- assertPage(redirected, "Profile: Ada & Sons\nChanged Ada to Ada & Sons")
      // Even if a browser sends the old cookie again, the backend has consumed its receipt.
      refreshed <- routes.runZIO(redirectedRequest)
      _ <- assertPage(refreshed, "Profile: Ada & Sons\nNo recent changes")
      later <- routes.runZIO(Request.get("/profile"))
      _ <- assertPage(later, "Profile: Ada & Sons\nNo recent changes")
    } yield ()

    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(
        ZIO.scoped(program).provide(Flash.Backend.inMemory)
      ).getOrThrowFiberFailure()
    }
  }

  private def profileRoutes(
      backend: Flash.Backend,
      name: Ref[String]
  ): Routes[Any, Response] =
    Routes(
      Method.POST / "profile" -> handler { (request: Request) =>
        for {
          updated <- request.body.asString.mapError(_ => Response.badRequest)
          previous <- name.getAndSet(updated)
          response <- backend.addFlash(
            Response.status(Status.SeeOther).addHeader("Location", "/profile"),
            Flash.setNotice(UpdateReceipt(previous, updated))
          )
        } yield response
      },
      Method.GET / "profile" -> handler { (request: Request) =>
        for {
          current <- name.get
          notice <- backend.flashOrElse(
            request,
            Flash.getNotice[UpdateReceipt].map(receipt =>
              s"Changed ${receipt.previousName} to ${receipt.newName}"
            )
          )("No recent changes")
        } yield Response.text(s"Profile: $current\n$notice")
      }
    )

  private def assertPage(response: Response, expected: String): ZIO[Any, Throwable, Unit] =
    for {
      body <- response.body.asString
      _ <- ZIO.succeed {
        assertEquals(Status.Ok, response.status)
        assertEquals(expected, body)
      }
    } yield ()
}
