/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_core_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.{Chunk, Stream}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

import scala.concurrent.duration.*

/** Exercises FS2 stateful stream accumulation to post wallet transactions and reject overdrafts. */
class WalletLedgerTest {
  private case class Transaction(reference: String, wallet: String, amountCents: Long)
  private case class Receipt(reference: String, wallet: String, accepted: Boolean, balanceCents: Long)

  private def post(
      balances: Map[String, Long],
      transaction: Transaction
  ): (Map[String, Long], Receipt) = {
    val previous: Long = balances.getOrElse(transaction.wallet, 0L)
    val proposed: Long = previous + transaction.amountCents
    val accepted: Boolean = proposed >= 0L
    val updated: Map[String, Long] =
      if (accepted) balances.updated(transaction.wallet, proposed) else balances
    val receipt: Receipt = Receipt(
      transaction.reference,
      transaction.wallet,
      accepted,
      updated.getOrElse(transaction.wallet, 0L)
    )
    (updated, receipt)
  }

  @Test
  @Timeout(60)
  def carriesBalancesAcrossBatchesWithoutPostingDeclinedDebits(): Unit = {
    // The second batch spends funds credited in the first; a decline must not change the balance.
    val batches: Vector[Vector[Transaction]] = Vector(
      Vector(
        Transaction("topup-1", "north", 5000L),
        Transaction("topup-2", "south", 2000L),
        Transaction("purchase-1", "north", -1250L)
      ),
      Vector(
        Transaction("purchase-2", "north", -4000L),
        Transaction("purchase-3", "south", -2000L),
        Transaction("purchase-4", "north", -3750L)
      ),
      Vector(
        Transaction("purchase-5", "new-wallet", -100L),
        Transaction("refund-1", "north", 500L),
        Transaction("purchase-6", "north", -300L)
      )
    )

    val postings: Vector[(Map[String, Long], Receipt)] = Stream
      .emits(batches)
      .covary[IO]
      .flatMap(batch => Stream.chunk(Chunk.vector(batch)))
      .mapAccumulate(Map.empty[String, Long])(post)
      .compile
      .toVector
      .timeout(20.seconds)
      .unsafeRunSync()

    val expected: Vector[Receipt] = Vector(
      Receipt("topup-1", "north", true, 5000L),
      Receipt("topup-2", "south", true, 2000L),
      Receipt("purchase-1", "north", true, 3750L),
      Receipt("purchase-2", "north", false, 3750L),
      Receipt("purchase-3", "south", true, 0L),
      Receipt("purchase-4", "north", true, 0L),
      Receipt("purchase-5", "new-wallet", false, 0L),
      Receipt("refund-1", "north", true, 500L),
      Receipt("purchase-6", "north", true, 200L)
    )
    assertEquals(expected, postings.map(_._2))
    assertEquals(Map("north" -> 200L, "south" -> 0L), postings.last._1)
    assertEquals(postings(2)._1, postings(3)._1, "A declined debit must leave all balances intact")
    assertEquals(postings(5)._1, postings(6)._1, "A decline must not create an unfunded wallet")
  }
}
