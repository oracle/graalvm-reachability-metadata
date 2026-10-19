/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_zio_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test
import sttp.capabilities.zio.ZioStreams
import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.model.{HeaderNames, StatusCode, Uri}
import sttp.tapir.client.sttp4.SttpClientInterpreter
import sttp.tapir.client.sttp4.stream.StreamSttpClientInterpreter
import sttp.tapir.json.zio.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir.*
import sttp.tapir.{CodecFormat, DecodeResult, Endpoint, PublicEndpoint, Schema}
import zio.http.Server
import zio.json.{DeriveJsonCodec, JsonCodec}
import zio.stream.{ZPipeline, ZStream}
import zio.{Ref, Runtime, Task, UIO, Unsafe, ZIO, ZLayer}

/**
 * Typed clients interpreted from the same endpoint definitions that `zServerLogic` and
 * `zServerSecurityLogic` serve.
 *
 * A fleet yard describes its API once as tapir endpoints. The server side completes them with
 * ZIO logic over a `YardRegistry` taken from the ZIO environment and serves them through the ZIO
 * HTTP interpreter on a real socket. The client side turns the very same values into Scala
 * functions with the sttp client interpreter over the sttp ZIO backend: a secured client that
 * takes the API key first, a public client returning `DecodeResult` of typed errors, a client
 * that throws on errors and returns a header next to the body, a streaming client reading a
 * `ZStream` download, and an outdated client description whose drifted body fails to decode.
 * The test checks that typed inputs, `oneOf` errors, headers and streams survive the round trip.
 */
class FleetYardZioTypedClientTest {
  import FleetYardZioTypedClientTest.*

  @Test
  def typedClientsCallTheEndpointsTheServerImplements(): Unit = {
    val program: ZIO[Server & YardRegistry, Throwable, Observed] = for {
      port <- Server.install(ZioHttpInterpreter().toHttp(serverEndpoints))
      observed <- ZIO.scoped(HttpClientZioBackend.scoped().flatMap(backend => exercise(port, backend)))
    } yield observed

    val observed: Observed = Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.run(program.provide(Server.defaultWithPort(0), InMemoryYardRegistry.layer))
        .getOrThrowFiberFailure()
    }

    assertEquals(Right(("/vehicles/KA-101", Vehicle("KA-101", "Volvo", 0, "dispatcher"))), observed.registered)
    assertEquals(Right(("/vehicles/KA-202", Vehicle("KA-202", "Scania", 0, "dispatcher"))), observed.registeredSecond)
    assertEquals(Left(DuplicatePlate("KA-101")), observed.duplicate)
    assertEquals(Left(Denied("unknown api key")), observed.denied)

    assertEquals(Right(Vehicle("KA-101", "Volvo", 120, "dispatcher")), observed.firstTrip)
    assertEquals(Right(Vehicle("KA-101", "Volvo", 200, "dispatcher")), observed.secondTrip)
    assertEquals(Left(UnknownVehicle("ZZ-000")), observed.tripOfUnknown)

    assertEquals((List("KA-101", "KA-202"), 2), (observed.all._1.map(_.plate), observed.all._2))
    assertEquals((List("KA-202"), 1), (observed.scanias._1.map(_.plate), observed.scanias._2))
    assertEquals((List("KA-101"), 2), (observed.firstPage._1.map(_.plate), observed.firstPage._2))

    assertEquals(DecodeResult.Value(Right(Vehicle("KA-101", "Volvo", 200, "dispatcher"))), observed.found)
    assertEquals(DecodeResult.Value(Left(UnknownVehicle("XX-999"))), observed.missing)

    observed.drifted match {
      case DecodeResult.Error(original, _) => assertTrue(original.contains("\"mileage\":200"), original)
      case other => throw new AssertionError(s"the outdated client decoded $other")
    }

    assertEquals(Right("KA-101,120\nKA-101,80\n"), observed.tripLog)
    assertEquals(Left(UnknownVehicle("NOPE")), observed.missingLog)
  }

  private def exercise(port: Int, backend: StreamBackend[Task, ZioStreams]): Task[Observed] = {
    val base: Option[Uri] = Some(uri"http://localhost:$port")
    val client: SttpClientInterpreter = SttpClientInterpreter()
    val register: String => NewVehicle => Task[Either[YardError, (String, Vehicle)]] =
      client.toSecureClientThrowDecodeFailures(registerVehicle, base, backend)
    val logTrip: String => ((String, Int)) => Task[Either[YardError, Vehicle]] =
      client.toSecureClientThrowDecodeFailures(recordTrip, base, backend)
    val list: ((Option[String], Int)) => Task[(List[Vehicle], Int)] =
      client.toClientThrowErrors(listVehicles, base, backend)
    val find: String => Task[DecodeResult[Either[YardError, Vehicle]]] =
      client.toClient(vehicleByPlate, base, backend)
    val findLegacy: String => Task[DecodeResult[Either[YardError, LegacyVehicle]]] =
      client.toClient(legacyVehicleByPlate, base, backend)
    val tripLog: String => Task[Either[YardError, ZStream[Any, Throwable, Byte]]] =
      StreamSttpClientInterpreter().toClientThrowDecodeFailures(exportTripLog, base, backend)

    def readLog(plate: String): Task[Either[YardError, String]] = tripLog(plate).flatMap {
      case Right(bytes) => bytes.via(ZPipeline.utf8Decode).runCollect.map(chunks => Right(chunks.mkString))
      case Left(error) => ZIO.succeed(Left(error))
    }

    for {
      registered <- register(ApiKey)(NewVehicle("KA-101", "Volvo"))
      registeredSecond <- register(ApiKey)(NewVehicle("KA-202", "Scania"))
      duplicate <- register(ApiKey)(NewVehicle("KA-101", "Volvo"))
      denied <- register("stolen-key")(NewVehicle("KA-303", "MAN"))
      firstTrip <- logTrip(ApiKey)(("KA-101", 120))
      secondTrip <- logTrip(ApiKey)(("KA-101", 80))
      tripOfUnknown <- logTrip(ApiKey)(("ZZ-000", 5))
      all <- list((None, 10))
      scanias <- list((Some("Scania"), 10))
      firstPage <- list((None, 1))
      found <- find("KA-101")
      missing <- find("XX-999")
      drifted <- findLegacy("KA-101")
      log <- readLog("KA-101")
      missingLog <- readLog("NOPE")
    } yield Observed(
      registered, registeredSecond, duplicate, denied, firstTrip, secondTrip, tripOfUnknown,
      all, scanias, firstPage, found, missing, drifted, log, missingLog
    )
  }
}

object FleetYardZioTypedClientTest {
  val ApiKey: String = "yard-key"

  final case class NewVehicle(plate: String, make: String) derives Schema
  final case class Vehicle(plate: String, make: String, mileage: Int, registeredBy: String) derives Schema

  /** The shape an older client release still expects: the server renamed `odometer` since. */
  final case class LegacyVehicle(plate: String, make: String, odometer: Int) derives Schema

  sealed trait YardError
  final case class UnknownVehicle(plate: String) extends YardError derives Schema
  final case class DuplicatePlate(plate: String) extends YardError derives Schema
  final case class Denied(reason: String) extends YardError derives Schema

  given JsonCodec[NewVehicle] = DeriveJsonCodec.gen[NewVehicle]
  given JsonCodec[Vehicle] = DeriveJsonCodec.gen[Vehicle]
  given JsonCodec[LegacyVehicle] = DeriveJsonCodec.gen[LegacyVehicle]
  given JsonCodec[UnknownVehicle] = DeriveJsonCodec.gen[UnknownVehicle]
  given JsonCodec[DuplicatePlate] = DeriveJsonCodec.gen[DuplicatePlate]
  given JsonCodec[Denied] = DeriveJsonCodec.gen[Denied]

  final case class Observed(
      registered: Either[YardError, (String, Vehicle)],
      registeredSecond: Either[YardError, (String, Vehicle)],
      duplicate: Either[YardError, (String, Vehicle)],
      denied: Either[YardError, (String, Vehicle)],
      firstTrip: Either[YardError, Vehicle],
      secondTrip: Either[YardError, Vehicle],
      tripOfUnknown: Either[YardError, Vehicle],
      all: (List[Vehicle], Int),
      scanias: (List[Vehicle], Int),
      firstPage: (List[Vehicle], Int),
      found: DecodeResult[Either[YardError, Vehicle]],
      missing: DecodeResult[Either[YardError, Vehicle]],
      drifted: DecodeResult[Either[YardError, LegacyVehicle]],
      tripLog: Either[YardError, String],
      missingLog: Either[YardError, String]
  )

  /** The service the endpoint logic requires from the ZIO environment. */
  trait YardRegistry {
    def register(vehicle: NewVehicle, operator: String): UIO[Option[Vehicle]]
    def find(plate: String): UIO[Option[Vehicle]]
    def addTrip(plate: String, km: Int): UIO[Option[Vehicle]]
    def trips(plate: String): UIO[List[Int]]
    def all: UIO[List[Vehicle]]
  }

  final class InMemoryYardRegistry(vehicles: Ref[Map[String, Vehicle]], trips: Ref[Map[String, List[Int]]])
      extends YardRegistry {
    def register(vehicle: NewVehicle, operator: String): UIO[Option[Vehicle]] = vehicles.modify { current =>
      if (current.contains(vehicle.plate)) (None, current)
      else {
        val created: Vehicle = Vehicle(vehicle.plate, vehicle.make, 0, operator)
        (Some(created), current.updated(vehicle.plate, created))
      }
    }

    def find(plate: String): UIO[Option[Vehicle]] = vehicles.get.map(_.get(plate))

    def addTrip(plate: String, km: Int): UIO[Option[Vehicle]] = for {
      updated <- vehicles.modify { current =>
        current.get(plate) match {
          case Some(vehicle) =>
            val driven: Vehicle = vehicle.copy(mileage = vehicle.mileage + km)
            (Some(driven), current.updated(plate, driven))
          case None => (None, current)
        }
      }
      _ <- ZIO.when(updated.isDefined)(trips.update(log => log.updated(plate, log.getOrElse(plate, Nil) :+ km)))
    } yield updated

    def trips(plate: String): UIO[List[Int]] = trips.get.map(_.getOrElse(plate, Nil))

    def all: UIO[List[Vehicle]] = vehicles.get.map(_.values.toList.sortBy(_.plate))
  }

  object InMemoryYardRegistry {
    val layer: ZLayer[Any, Nothing, YardRegistry] = ZLayer.fromZIO(for {
      vehicles <- Ref.make(Map.empty[String, Vehicle])
      trips <- Ref.make(Map.empty[String, List[Int]])
    } yield new InMemoryYardRegistry(vehicles, trips))
  }

  private val yardErrors = oneOf[YardError](
    oneOfVariant(StatusCode.NotFound, jsonBody[UnknownVehicle]),
    oneOfVariant(StatusCode.Conflict, jsonBody[DuplicatePlate]),
    oneOfVariant(StatusCode.Unauthorized, jsonBody[Denied])
  )

  private val secured: Endpoint[String, Unit, YardError, Unit, Any] =
    endpoint.securityIn(auth.apiKey(header[String]("X-Api-Key"))).errorOut(yardErrors)

  val registerVehicle: Endpoint[String, NewVehicle, YardError, (String, Vehicle), Any] =
    secured.post.in("vehicles").in(jsonBody[NewVehicle])
      .out(statusCode(StatusCode.Created)).out(header[String](HeaderNames.Location)).out(jsonBody[Vehicle])

  val recordTrip: Endpoint[String, (String, Int), YardError, Vehicle, Any] =
    secured.post.in("vehicles" / path[String]("plate") / "trips").in(query[Int]("km")).out(jsonBody[Vehicle])

  val listVehicles: PublicEndpoint[(Option[String], Int), Unit, (List[Vehicle], Int), Any] =
    endpoint.get.in("vehicles").in(query[Option[String]]("make")).in(query[Int]("limit").default(10))
      .out(jsonBody[List[Vehicle]]).out(header[Int]("X-Total-Count"))

  val vehicleByPlate: PublicEndpoint[String, YardError, Vehicle, Any] =
    endpoint.get.in("vehicles" / path[String]("plate")).errorOut(yardErrors).out(jsonBody[Vehicle])

  /** The description an outdated client was built from; the server never implements it. */
  val legacyVehicleByPlate: PublicEndpoint[String, YardError, LegacyVehicle, Any] =
    endpoint.get.in("vehicles" / path[String]("plate")).errorOut(yardErrors).out(jsonBody[LegacyVehicle])

  val exportTripLog: PublicEndpoint[String, YardError, ZStream[Any, Throwable, Byte], ZioStreams] =
    endpoint.get.in("vehicles" / path[String]("plate") / "log").errorOut(yardErrors)
      .out(streamBinaryBody(ZioStreams)(CodecFormat.TextPlain()))

  private def existing(plate: String): ZIO[YardRegistry, YardError, Vehicle] =
    ZIO.serviceWithZIO[YardRegistry](_.find(plate)).someOrFail(UnknownVehicle(plate))

  private val operators: Map[String, String] = Map(ApiKey -> "dispatcher")

  private val authenticated: ZPartialServerEndpoint[YardRegistry, String, String, Unit, YardError, Unit, Any] =
    secured.zServerSecurityLogic[YardRegistry, String] { key =>
      ZIO.fromOption(operators.get(key)).orElseFail(Denied("unknown api key"))
    }

  val serverEndpoints: List[ZServerEndpoint[YardRegistry, ZioStreams]] = List(
    authenticated.post.in("vehicles").in(jsonBody[NewVehicle])
      .out(statusCode(StatusCode.Created)).out(header[String](HeaderNames.Location)).out(jsonBody[Vehicle])
      .serverLogic { operator => vehicle =>
        ZIO.serviceWithZIO[YardRegistry](_.register(vehicle, operator))
          .someOrFail(DuplicatePlate(vehicle.plate))
          .map(created => (s"/vehicles/${created.plate}", created))
      },
    authenticated.post.in("vehicles" / path[String]("plate") / "trips").in(query[Int]("km")).out(jsonBody[Vehicle])
      .serverLogic { _ => (plate, km) =>
        ZIO.serviceWithZIO[YardRegistry](_.addTrip(plate, km)).someOrFail(UnknownVehicle(plate))
      },
    listVehicles.zServerLogic[YardRegistry] { (make, limit) =>
      ZIO.serviceWithZIO[YardRegistry](_.all).map { vehicles =>
        val matching: List[Vehicle] = vehicles.filter(v => make.forall(_ == v.make))
        (matching.take(limit), matching.size)
      }
    },
    vehicleByPlate.zServerLogic[YardRegistry](existing),
    exportTripLog.zServerLogic[YardRegistry] { plate =>
      existing(plate) *> ZIO.serviceWithZIO[YardRegistry](_.trips(plate)).map { trips =>
        ZStream.fromIterable(trips).map(km => s"$plate,$km\n").via(ZPipeline.utf8Encode)
      }
    }
  )
}
