/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_profunktor.redis4cats_core_3

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.effect.Log.NoOp._
import dev.profunktor.redis4cats.effects.{Distance, GeoLocation, GeoRadius, Latitude, Longitude}
import io.lettuce.core.GeoArgs
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import redis.embedded.RedisServer

import java.net.ServerSocket
import scala.concurrent.duration._

/** Exercises geospatial indexing to find nearby parcel pickup locations and reflect a relocated branch. */
class NearbyPickupLocationsTest {
  @Test
  @Timeout(60)
  def nearbyBranchesAreDistanceOrderedAndRelocationSurvivesReconnection(): Unit = {
    val locationsKey: String = "parcel:pickup-locations"
    val searchArea: GeoRadius = GeoRadius(Longitude(-0.1), Latitude(51.5), Distance(5.0))
    val nearestFirst: GeoArgs = new GeoArgs().withDistance().withCoordinates().asc()
    val program: IO[Unit] = serverResource.use { server =>
      val uri: String = s"redis://127.0.0.1:${server.ports().get(0)}?timeout=15s"
      for {
        _ <- Redis[IO].utf8(uri).use { directory =>
          for {
            _ <- directory.geoAdd(
              locationsKey,
              GeoLocation(Longitude(-0.1), Latitude(51.5), "central-locker"),
              GeoLocation(Longitude(-0.105), Latitude(51.505), "market-shop"),
              GeoLocation(Longitude(-0.12), Latitude(51.52), "north-depot"),
              GeoLocation(Longitude(-0.3), Latitude(51.6), "airport-depot")
            )
            nearby <- directory.geoRadius(locationsKey, searchArea, GeoArgs.Unit.km, nearestFirst)
            walkingDistance <- directory.geoDist(
              locationsKey, "central-locker", "market-shop", GeoArgs.Unit.km
            )
            _ <- IO {
              assertEquals(List("central-locker", "market-shop", "north-depot"), nearby.map(_.value))
              assertEquals(0.0, nearby.head.dist.value, 0.01)
              assertTrue(walkingDistance > 0.5 && walkingDistance < 0.8)
              assertEquals(walkingDistance, nearby(1).dist.value, 0.01)
              assertTrue(nearby(2).dist.value > 2.0 && nearby(2).dist.value < 3.0)
              assertEquals(-0.105, nearby(1).coordinate.x, 0.00001)
              assertEquals(51.505, nearby(1).coordinate.y, 0.00001)
            }
            // The shop moves beyond the customer's search radius, retaining its business identifier.
            _ <- directory.geoAdd(
              locationsKey,
              GeoLocation(Longitude(-0.4), Latitude(51.7), "market-shop")
            )
          } yield ()
        }
        _ <- Redis[IO].utf8(uri).use { reader =>
          for {
            nearby <- reader.geoRadius(locationsKey, searchArea, GeoArgs.Unit.km, nearestFirst)
            depotNeighbors <- reader.geoRadiusByMember(
              locationsKey, "north-depot", Distance(5.0), GeoArgs.Unit.km
            )
            relocated <- reader.geoPos(locationsKey, "market-shop")
            relocatedDistance <- reader.geoDist(
              locationsKey, "central-locker", "market-shop", GeoArgs.Unit.km
            )
            locationCount <- reader.zCard(locationsKey)
            _ <- IO {
              assertEquals(List("central-locker", "north-depot"), nearby.map(_.value))
              assertEquals(Set("central-locker", "north-depot"), depotNeighbors)
              assertEquals(1, relocated.size)
              assertEquals(-0.4, relocated.head.x, 0.00001)
              assertEquals(51.7, relocated.head.y, 0.00001)
              assertTrue(relocatedDistance > 20.0)
              // Updating a location must not create a second entry for the same branch.
              assertEquals(4L, locationCount)
            }
          } yield ()
        }
      } yield ()
    }
    program.timeout(45.seconds).unsafeRunSync()
  }

  private def serverResource: Resource[IO, RedisServer] = {
    val create: IO[RedisServer] = IO.blocking {
      val socket: ServerSocket = new ServerSocket(0)
      val port: Int = try socket.getLocalPort finally socket.close()
      RedisServer.newRedisServer()
        .bind("127.0.0.1")
        .port(port)
        .setting("save \"\"")
        .setting("appendonly no")
        .build()
    }
    Resource.make(create)(server => IO.blocking(server.stop()).void)
      .evalTap(server => IO.blocking(server.start()).void)
  }
}
