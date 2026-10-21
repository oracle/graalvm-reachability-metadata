/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_2_13

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import slick.jdbc.H2Profile.api._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/** Exercises Slick's custom column mapping subsystem to persist a parcel dispatch workflow.
  * Domain identifiers and delivery states remain typed in inserts, predicates, updates, and results.
  */
class ParcelDomainMappingTest {
  case class TrackingCode(value: String)

  sealed trait DeliveryState
  case object Queued extends DeliveryState
  case object Dispatched extends DeliveryState
  case object Delivered extends DeliveryState

  case class Parcel(code: TrackingCode, recipient: String, state: DeliveryState)

  implicit val trackingCodeType: BaseColumnType[TrackingCode] =
    MappedColumnType.base[TrackingCode, String](_.value, TrackingCode.apply)

  implicit val deliveryStateType: BaseColumnType[DeliveryState] =
    MappedColumnType.base[DeliveryState, String](
      {
        case Queued => "queued"
        case Dispatched => "dispatched"
        case Delivered => "delivered"
      },
      {
        case "queued" => Queued
        case "dispatched" => Dispatched
        case "delivered" => Delivered
        case value => throw new IllegalArgumentException(s"Unknown delivery state: $value")
      }
    )

  class Parcels(tag: Tag) extends Table[Parcel](tag, "DOMAIN_PARCELS") {
    def code: Rep[TrackingCode] = column[TrackingCode]("CODE", O.PrimaryKey)
    def recipient: Rep[String] = column[String]("RECIPIENT")
    def state: Rep[DeliveryState] = column[DeliveryState]("STATE")
    def * = (code, recipient, state).mapTo[Parcel]
  }

  @Test
  @Timeout(60)
  def dispatchesQueuedParcelsUsingTypedDomainValues(): Unit = {
    val parcels = TableQuery(tag => new Parcels(tag))
    val database: Database = Database.forURL(
      "jdbc:h2:mem:parcel_domain_mapping;DB_CLOSE_DELAY=-1",
      driver = "org.h2.Driver"
    )
    val queued: DeliveryState = Queued
    val dispatched: DeliveryState = Dispatched
    val firstCode: TrackingCode = TrackingCode("PKG-001")
    val queuedParcels = parcels.filter(_.state === queued)
    val firstParcel = parcels.filter(_.code === firstCode)
    val scenario = for {
      _ <- parcels.schema.create
      _ <- parcels ++= Seq(
        Parcel(firstCode, "Ada", Queued),
        Parcel(TrackingCode("PKG-002"), "Grace", Queued),
        Parcel(TrackingCode("PKG-003"), "Linus", Delivered)
      )
      beforeDispatch <- firstParcel.result.head
      dispatchCount <- queuedParcels.map(_.state).update(dispatched)
      afterDispatch <- firstParcel.result.head
      manifest <- parcels.filter(_.state === dispatched).sortBy(_.recipient).result
      remainingQueue <- queuedParcels.result
      delivered <- parcels.filter(_.code === TrackingCode("PKG-003")).result.head
    } yield (beforeDispatch, dispatchCount, afterDispatch, manifest, remainingQueue, delivered)

    try {
      val (beforeDispatch, dispatchCount, afterDispatch, manifest, remainingQueue, delivered) =
        Await.result(database.run(scenario.andFinally(parcels.schema.drop)), 30.seconds)
      assertEquals(Parcel(firstCode, "Ada", Queued), beforeDispatch)
      assertEquals(2, dispatchCount)
      assertEquals(Parcel(firstCode, "Ada", Dispatched), afterDispatch)
      assertEquals(
        Seq(
          Parcel(firstCode, "Ada", Dispatched),
          Parcel(TrackingCode("PKG-002"), "Grace", Dispatched)
        ),
        manifest
      )
      assertTrue(remainingQueue.isEmpty)
      assertEquals(Parcel(TrackingCode("PKG-003"), "Linus", Delivered), delivered)
    } finally {
      database.close()
    }
  }
}
