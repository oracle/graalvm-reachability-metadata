/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_scalaland.chimney_3

import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Total `Transformer` derivation between nested case classes, tuned with `withField*` overrides.
 *
 * A warehouse service keeps shipments in a rich domain model with typed identifiers and
 * publishes a flatter event to downstream consumers. Most of the event mirrors the domain
 * structurally, so Chimney derives the nested address, parcel list and option mappings on its
 * own; the few fields whose names or meaning differ are wired with `withFieldRenamed`,
 * `withFieldComputed` and `withFieldConst`. Identifier and parcel mappings are declared once as
 * `given` transformers and picked up inside every derived event mapping, and an archive record
 * fills the fields the domain lacks from its constructor defaults.
 */
class ShipmentEventPublishingTotalTransformerTest {

  final case class ShipmentId(value: String)
  final case class CustomerId(value: String)

  final case class Address(street: String, city: String, postalCode: String, note: Option[String])
  final case class Parcel(trackingCode: String, weightGrams: Int)
  final case class Shipment(
      id: ShipmentId,
      customer: CustomerId,
      destination: Address,
      parcels: List[Parcel],
      carrier: Option[String],
      labels: Map[String, String]
  )

  final case class AddressView(street: String, city: String, postalCode: String, note: Option[String])
  final case class ParcelView(code: String, weightKg: BigDecimal)
  final case class ShipmentEvent(
      shipmentId: String,
      customer: String,
      deliverTo: AddressView,
      parcels: Vector[ParcelView],
      carrier: Option[String],
      labels: Map[String, String],
      totalWeightKg: BigDecimal,
      schemaVersion: Int
  )

  final case class ArchiveRecord(
      id: ShipmentId,
      carrier: Option[String],
      parcelCount: Int,
      source: String = "warehouse-eu",
      archived: Boolean = true
  )

  /** Identifiers leave the service as their raw string form. */
  given Transformer[ShipmentId, String] = _.value
  given Transformer[CustomerId, String] = _.value

  /** The parcel mapping every event reuses: grams become kilograms and the code is renamed. */
  given Transformer[Parcel, ParcelView] = Transformer
    .define[Parcel, ParcelView]
    .withFieldRenamed(_.trackingCode, _.code)
    .withFieldComputed(_.weightKg, parcel => BigDecimal(parcel.weightGrams) / 1000)
    .buildTransformer

  private def publish(shipment: Shipment): ShipmentEvent =
    shipment
      .into[ShipmentEvent]
      .withFieldRenamed(_.id, _.shipmentId)
      .withFieldRenamed(_.destination, _.deliverTo)
      .withFieldComputed(_.totalWeightKg, s => BigDecimal(s.parcels.map(_.weightGrams).sum) / 1000)
      .withFieldConst(_.schemaVersion, 3)
      .transform

  @Test
  def publishesShipmentEventWithDerivedNestedStructureAndOverriddenFields(): Unit = {
    val shipment: Shipment = Shipment(
      id = ShipmentId("SHP-1042"),
      customer = CustomerId("C-77"),
      destination = Address("Baker Street 221B", "London", "NW1 6XE", Some("Ring twice")),
      parcels = List(Parcel("TRK-1", 1250), Parcel("TRK-2", 480)),
      carrier = Some("ACME Freight"),
      labels = Map("priority" -> "express", "fragile" -> "yes")
    )

    val event: ShipmentEvent = publish(shipment)

    assertEquals(
      ShipmentEvent(
        shipmentId = "SHP-1042",
        customer = "C-77",
        deliverTo = AddressView("Baker Street 221B", "London", "NW1 6XE", Some("Ring twice")),
        parcels = Vector(ParcelView("TRK-1", BigDecimal("1.25")), ParcelView("TRK-2", BigDecimal("0.48"))),
        carrier = Some("ACME Freight"),
        labels = Map("priority" -> "express", "fragile" -> "yes"),
        totalWeightKg = BigDecimal("1.73"),
        schemaVersion = 3
      ),
      event
    )
  }

  @Test
  def reusesGivenParcelMappingForStandaloneConversionsAndEmptyShipments(): Unit = {
    val view: ParcelView = Parcel("TRK-9", 2000).transformInto[ParcelView]
    assertEquals(ParcelView("TRK-9", BigDecimal(2)), view)

    val empty: Shipment = Shipment(
      id = ShipmentId("SHP-0"),
      customer = CustomerId("C-1"),
      destination = Address("Main Road 1", "Springfield", "00001", None),
      parcels = Nil,
      carrier = None,
      labels = Map.empty
    )
    val event: ShipmentEvent = publish(empty)

    assertEquals("SHP-0", event.shipmentId)
    assertEquals(None, event.deliverTo.note)
    assertEquals(Vector.empty[ParcelView], event.parcels)
    assertEquals(None, event.carrier)
    assertEquals(Map.empty[String, String], event.labels)
    assertEquals(BigDecimal(0), event.totalWeightKg)
  }

  @Test
  def derivesArchiveRecordWithDefaultValuesForFieldsTheDomainLacks(): Unit = {
    val shipment: Shipment = Shipment(
      id = ShipmentId("SHP-7"),
      customer = CustomerId("C-3"),
      destination = Address("Harbour Lane 5", "Gdansk", "80-001", None),
      parcels = List(Parcel("TRK-7", 900)),
      carrier = Some("Baltic Post"),
      labels = Map.empty
    )

    val record: ArchiveRecord = shipment
      .into[ArchiveRecord]
      .enableDefaultValues
      .withFieldComputed(_.parcelCount, _.parcels.size)
      .transform

    assertEquals(ArchiveRecord(ShipmentId("SHP-7"), Some("Baltic Post"), 1, "warehouse-eu", archived = true), record)
  }
}
