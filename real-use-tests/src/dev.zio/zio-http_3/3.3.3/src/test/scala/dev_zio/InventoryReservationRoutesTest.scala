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
import zio.http.codec.PathCodec.int

/** Exercises ZIO HTTP routing and effectful handlers for an inventory reservation API. */
class InventoryReservationRoutesTest {
  @Test
  @Timeout(60)
  def reservesStockAndReportsRejectedRequests(): Unit = {
    val program: ZIO[Scope, Throwable, Unit] = for {
      stock <- Ref.make(Map(42 -> 7, 81 -> 4))
      routes = inventoryRoutes(stock)
      initial <- routes.runZIO(Request.get("/inventory/42"))
      _ <- assertResponse(initial, Status.Ok, "7")
      reserved <- routes.runZIO(Request.post("/inventory/42/reserve", Body.fromString("3")))
      _ <- assertResponse(reserved, Status.Ok, "reserved 3; remaining 4")
      insufficient <- routes.runZIO(Request.post("/inventory/42/reserve", Body.fromString("5")))
      _ <- assertResponse(insufficient, Status.Conflict, "insufficient stock")
      invalid <- routes.runZIO(Request.post("/inventory/42/reserve", Body.fromString("-1")))
      _ <- assertResponse(invalid, Status.BadRequest, "quantity must be a positive integer")
      remaining <- routes.runZIO(Request.get("/inventory/42"))
      _ <- assertResponse(remaining, Status.Ok, "4")
      untouched <- routes.runZIO(Request.get("/inventory/81"))
      _ <- assertResponse(untouched, Status.Ok, "4")
      missing <- routes.runZIO(Request.post("/inventory/999/reserve", Body.fromString("1")))
      _ <- assertResponse(missing, Status.NotFound, "unknown product")
      unmatched <- routes.runZIO(Request.get("/inventory/not-a-product-id"))
      _ <- ZIO.succeed(assertEquals(Status.NotFound, unmatched.status))
    } yield ()

    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(ZIO.scoped(program)).getOrThrowFiberFailure()
    }
  }

  private def inventoryRoutes(stock: Ref[Map[Int, Int]]): Routes[Any, Response] =
    Routes(
      Method.GET / "inventory" / int("productId") -> handler { (productId: Int, _: Request) =>
        stock.get.map { inventory =>
          inventory.get(productId) match {
            case Some(quantity) => Response.text(quantity.toString)
            case None => Response.text("unknown product").status(Status.NotFound)
          }
        }
      },
      Method.POST / "inventory" / int("productId") / "reserve" -> handler {
        (productId: Int, request: Request) =>
          request.body.asString
            .mapError(_ => Response.text("unreadable quantity").status(Status.BadRequest))
            .flatMap { body =>
              body.toIntOption.filter(_ > 0) match {
                case None =>
                  ZIO.succeed(
                    Response.text("quantity must be a positive integer").status(Status.BadRequest)
                  )
                case Some(quantity) =>
                  stock.modify { inventory =>
                    inventory.get(productId) match {
                      case None =>
                        (Response.text("unknown product").status(Status.NotFound), inventory)
                      case Some(available) if available < quantity =>
                        (Response.text("insufficient stock").status(Status.Conflict), inventory)
                      case Some(available) =>
                        val remaining: Int = available - quantity
                        (
                          Response.text(s"reserved $quantity; remaining $remaining"),
                          inventory.updated(productId, remaining)
                        )
                    }
                  }
              }
            }
      }
    )

  private def assertResponse(
      response: Response,
      expectedStatus: Status,
      expectedBody: String
  ): ZIO[Any, Throwable, Unit] =
    for {
      _ <- ZIO.succeed(assertEquals(expectedStatus, response.status))
      body <- response.body.asString
      _ <- ZIO.succeed(assertEquals(expectedBody, body))
    } yield ()
}
