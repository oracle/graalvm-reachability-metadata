/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.upickle_3

import java.time.Instant

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import upickle.core.Abort
import upickle.core.AbortException
import upickle.core.Visitor
import upickle.default.*
import upickle.implicits.key

/**
 * Hand-written codecs composed from upickle's Reader and Writer combinators (map, mapNulls, comap,
 * comapNulls, ReadWriter.join, ReadWriter.merge, stringKeyRW and SimpleReader and Writer
 * implementations), as a fleet tracking gateway maps its domain types onto a vendor's JSON feed.
 */
class FleetTrackingCodecCombinatorsTest {

  import FleetTrackingCodecCombinatorsTest.*

  @Test
  def derivedRecordsDelegateToCombinatorCodecs(): Unit = {
    val position: Position = Position(
      vehicle = VehicleId("TRK-7"),
      recordedAt = Instant.ofEpochMilli(1700000000123L),
      speed = Speed(88.5),
      driver = Driver.Assigned("Mira")
    )

    val json: String = write(position)

    assertEquals("""{"vehicle":"TRK-7","recordedAt":1700000000123,"speed":88.5,"driver":"Mira"}""", json)
    assertEquals(position, read[Position](json))
    assertEquals(position, read[Position](writeJs(position)))
  }

  @Test
  def nullDriverIsReadAsUnassignedAndWrittenBackAsNull(): Unit = {
    val json: String = """{"vehicle":"VAN-2","recordedAt":0,"speed":12,"driver":null}"""

    val position: Position = read[Position](json)

    assertEquals(Driver.Unassigned, position.driver)
    assertEquals(Instant.EPOCH, position.recordedAt)
    val tree: ujson.Value = writeJs(position)
    assertEquals(ujson.Null, tree("driver"))
    assertEquals(position, read[Position](write(position)))
  }

  @Test
  def speedReaderAcceptsVendorUnitsAndRejectsOtherShapes(): Unit = {
    assertEquals(Speed(90.0), read[Speed]("90"))
    assertEquals(Speed(42.25), read[Speed]("42.25"))
    assertEquals(Speed(120.0), read[Speed](""""120 km/h""""))
    assertEquals(Speed(80.4672), read[Speed](""""50 mph""""))
    assertEquals(Speed(15.0), read[Speed](ujson.Num(15)))

    val wrongShape: Exception = assertThrows(classOf[Exception], () => read[Speed]("true"))
    assertEquals("expected speed in km/h or mph got boolean at index 0", abortMessage(wrongShape))

    val unknownUnit: Exception =
      assertThrows(classOf[Exception], () => read[Position]("""{"vehicle":"TRK-1","recordedAt":5,"speed":"7 knots","driver":"Ola"}"""))
    assertTrue(abortMessage(unknownUnit).startsWith("unsupported speed unit: knots"), abortMessage(unknownUnit))
  }

  @Test
  def stringKeyCodecsRenderMapsAsJsonObjects(): Unit = {
    val mileage: Map[VehicleId, Int] = Map(VehicleId("TRK-7") -> 412, VehicleId("VAN-2") -> 97)
    val routeLoads: Map[RouteCode, Int] = Map(RouteCode("R-12") -> 3)

    val mileageJson: ujson.Value = writeJs(mileage)
    val routeJson: ujson.Value = writeJs(routeLoads)

    assertEquals(ujson.Obj("TRK-7" -> 412, "VAN-2" -> 97), mileageJson)
    assertEquals(mileage, read[Map[VehicleId, Int]](write(mileage)))
    assertEquals(ujson.Arr(ujson.Arr("R-12", 3)), routeJson)
    assertEquals(routeLoads, read[Map[RouteCode, Int]](write(routeLoads)))
  }

  @Test
  def mergedCodecDispatchesOpenEventFamilyByTag(): Unit = {
    val feed: String =
      """[
        |  {"$type": "heartbeat", "vehicle": "TRK-7", "battery": 81},
        |  {"$type": "geofence", "vehicle": "VAN-2", "zone": "depot-north", "entered": true},
        |  {"$type": "service-due", "vehicle": "TRK-7", "inKilometers": 350},
        |  {"$type": "heartbeat", "vehicle": "VAN-2", "battery": 12}
        |]""".stripMargin

    val events: List[VendorEvent] = read[List[VendorEvent]](feed)

    assertEquals(
      List(
        Heartbeat(VehicleId("TRK-7"), 81),
        GeofenceCrossing(VehicleId("VAN-2"), "depot-north", entered = true),
        ServiceDue(VehicleId("TRK-7"), 350),
        Heartbeat(VehicleId("VAN-2"), 12)
      ),
      events
    )
    val lowBattery: List[String] = events.collect { case Heartbeat(vehicle, battery) if battery < 20 => vehicle.value }
    assertEquals(List("VAN-2"), lowBattery)

    val rewritten: ujson.Value = writeJs[VendorEvent](GeofenceCrossing(VehicleId("TRK-7"), "yard", entered = false))
    assertEquals("geofence", rewritten("$type").str)
    assertEquals("yard", rewritten("zone").str)
    val maintenance: ujson.Value = writeJs[VendorEvent](ServiceDue(VehicleId("VAN-2"), 0))
    assertEquals("service-due", maintenance("$type").str)
    assertEquals(events, read[List[VendorEvent]](write(events)))
  }
}

object FleetTrackingCodecCombinatorsTest {

  /** The message of the AbortException a failed read carries, possibly wrapped by a trace. */
  def abortMessage(failure: Throwable): String =
    Iterator.iterate(failure)(_.getCause).takeWhile(_ != null).collectFirst { case abort: AbortException => abort.getMessage }
      .getOrElse(throw new AssertionError("no AbortException in " + failure, failure))

  opaque type VehicleId = String

  object VehicleId {
    def apply(value: String): VehicleId = value

    given ReadWriter[VehicleId] = stringKeyRW(readwriter[String].bimap[VehicleId](_.value, VehicleId(_)))
  }

  extension (id: VehicleId) {
    def value: String = id
  }

  final case class RouteCode(code: String)

  object RouteCode {
    given ReadWriter[RouteCode] = readwriter[String].bimap[RouteCode](_.code, RouteCode(_))
  }

  given ReadWriter[Instant] =
    ReadWriter.join(reader[Long].map(Instant.ofEpochMilli), writer[Long].comap[Instant](_.toEpochMilli))

  final case class Speed(kilometersPerHour: Double)

  object Speed {
    private val KilometersPerMile: Double = 1.609344

    /** Reads plain km/h numbers as well as the vendor's unit-suffixed strings. */
    private object SpeedReader extends SimpleReader[Speed] {
      override def expectedMsg: String = "expected speed in km/h or mph"

      override def visitInt32(i: Int, index: Int): Speed = Speed(i.toDouble)

      override def visitInt64(i: Long, index: Int): Speed = Speed(i.toDouble)

      override def visitFloat64(d: Double, index: Int): Speed = Speed(d)

      override def visitFloat64StringParts(s: CharSequence, decIndex: Int, expIndex: Int, index: Int): Speed =
        Speed(s.toString.toDouble)

      override def visitString(s: CharSequence, index: Int): Speed =
        s.toString.split(' ') match {
          case Array(amount, "km/h") => Speed(amount.toDouble)
          case Array(amount, "mph") => Speed(BigDecimal(amount.toDouble * KilometersPerMile).setScale(4, BigDecimal.RoundingMode.HALF_UP).toDouble)
          case Array(_, unit) => throw new Abort(s"unsupported speed unit: $unit")
          case _ => throw new Abort(expectedMsg)
        }
    }

    /** Always emits km/h as a JSON number. */
    private object SpeedWriter extends Writer[Speed] {
      override def write0[V](out: Visitor[?, V], v: Speed): V = out.visitFloat64(v.kilometersPerHour, -1)
    }

    given ReadWriter[Speed] = ReadWriter.join(SpeedReader, SpeedWriter)
  }

  enum Driver {
    case Assigned(name: String)
    case Unassigned
  }

  object Driver {
    given ReadWriter[Driver] = ReadWriter.join(
      reader[String].mapNulls(name => if (name == null) Driver.Unassigned else Driver.Assigned(name)),
      writer[String].comapNulls[Driver] {
        case Driver.Assigned(name) => name
        case Driver.Unassigned => null
      }
    )
  }

  final case class Position(vehicle: VehicleId, recordedAt: Instant, speed: Speed, driver: Driver) derives ReadWriter

  /** An open event family: each vendor module contributes a sealed group the codec merges. */
  trait VendorEvent

  sealed trait TelemetryEvent extends VendorEvent derives ReadWriter

  @key("heartbeat")
  final case class Heartbeat(vehicle: VehicleId, battery: Int) extends TelemetryEvent

  @key("geofence")
  final case class GeofenceCrossing(vehicle: VehicleId, zone: String, entered: Boolean) extends TelemetryEvent

  sealed trait MaintenanceEvent extends VendorEvent derives ReadWriter

  @key("service-due")
  final case class ServiceDue(vehicle: VehicleId, inKilometers: Int) extends MaintenanceEvent

  given ReadWriter[VendorEvent] =
    ReadWriter.merge(summon[ReadWriter[TelemetryEvent]], summon[ReadWriter[MaintenanceEvent]])
}
