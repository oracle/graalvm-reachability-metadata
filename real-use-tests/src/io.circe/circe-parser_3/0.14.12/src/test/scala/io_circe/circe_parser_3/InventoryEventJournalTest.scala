/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_parser_3

import io.circe.{Decoder, DecodingFailure}
import io.circe.generic.semiauto.deriveDecoder
import io.circe.parser.decode
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue, fail}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises generic sealed-trait decoding of tagged JSON events for an inventory journal. */
@Timeout(60)
class InventoryEventJournalTest {
  private sealed trait InventoryEvent
  private final case class Received(sku: String, quantity: Int) extends InventoryEvent
  private final case class Shipped(sku: String, quantity: Int) extends InventoryEvent
  private final case class StockCounted(sku: String, onHand: Int) extends InventoryEvent

  private given Decoder[Received] = deriveDecoder[Received]
  private given Decoder[Shipped] = deriveDecoder[Shipped]
  private given Decoder[StockCounted] = deriveDecoder[StockCounted]
  private given Decoder[InventoryEvent] = deriveDecoder[InventoryEvent]

  private def replay(events: Vector[InventoryEvent]): Map[String, Int] =
    events.foldLeft(Map.empty[String, Int]) { (stock, event) =>
      event match {
        case Received(sku, quantity) =>
          stock.updated(sku, stock.getOrElse(sku, 0) + quantity)
        case Shipped(sku, quantity) =>
          stock.updated(sku, stock.getOrElse(sku, 0) - quantity)
        case StockCounted(sku, onHand) =>
          stock.updated(sku, onHand)
      }
    }

  @Test
  def rebuildsStockFromAnOrderedJournalOfDifferentEventTypes(): Unit = {
    val journal: String =
      """[
        |  {"Received": {"sku": "book-42", "quantity": 12}},
        |  {"Received": {"sku": "pen-7", "quantity": 20}},
        |  {"Shipped": {"sku": "book-42", "quantity": 3}},
        |  {"StockCounted": {"sku": "book-42", "onHand": 8}},
        |  {"Received": {"sku": "book-42", "quantity": 2}},
        |  {"Shipped": {"sku": "pen-7", "quantity": 5}}
        |]""".stripMargin

    val events: Vector[InventoryEvent] =
      decode[Vector[InventoryEvent]](journal).fold(error => throw error, identity)

    assertEquals(
      Vector[InventoryEvent](
        Received("book-42", 12),
        Received("pen-7", 20),
        Shipped("book-42", 3),
        StockCounted("book-42", 8),
        Received("book-42", 2),
        Shipped("pen-7", 5)
      ),
      events
    )
    assertEquals(Map("book-42" -> 10, "pen-7" -> 15), replay(events))
  }

  @Test
  def rejectsAnUnknownEventRatherThanSilentlyLosingAStockChange(): Unit = {
    val incompatibleJournal: String =
      """[
        |  {"Received": {"sku": "book-42", "quantity": 12}},
        |  {"Transferred": {"sku": "book-42", "quantity": 4}}
        |]""".stripMargin

    decode[Vector[InventoryEvent]](incompatibleJournal) match {
      case Left(error) => assertTrue(error.isInstanceOf[DecodingFailure])
      case Right(_) => fail("An unknown inventory event must reject the journal before replay")
    }
  }
}
