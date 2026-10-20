/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_tpolecat.doobie_free_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import doobie.free.connection
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.duration._

/** Exercises ConnectionIO error propagation and whole-transaction rollback for wallet transfers. */
@Timeout(60)
class WalletTransferRollbackTest {
  private final class UnknownWallet(val wallet: String)
      extends RuntimeException(s"Unknown destination wallet: $wallet")

  private case class Ledger(balances: List[(String, Int)], transfers: List[(String, String, String, Int)])

  private def transfer(id: String, destination: String, cents: Int): connection.ConnectionIO[Unit] =
    for {
      _ <- sql"""INSERT INTO wallet_transfers (transfer_id, sender, recipient, cents)
                  VALUES ($id, 'alice', $destination, $cents)""".update.run
      debited <- sql"""UPDATE wallets SET balance_cents = balance_cents - $cents
                        WHERE wallet_id = 'alice' AND balance_cents >= $cents""".update.run
      _ <- if (debited == 1) connection.unit
           else connection.raiseError[Unit](new IllegalStateException("Insufficient funds"))
      credited <- sql"""UPDATE wallets SET balance_cents = balance_cents + $cents
                         WHERE wallet_id = $destination""".update.run
      _ <- if (credited == 1) connection.unit
           else connection.raiseError[Unit](new UnknownWallet(destination))
    } yield ()

  private val readLedger: connection.ConnectionIO[Ledger] = for {
    balances <- sql"SELECT wallet_id, balance_cents FROM wallets ORDER BY wallet_id"
      .query[(String, Int)].to[List]
    transfers <- sql"""SELECT transfer_id, sender, recipient, cents
                        FROM wallet_transfers ORDER BY transfer_id"""
      .query[(String, String, String, Int)].to[List]
  } yield Ledger(balances, transfers)

  @Test
  def rejectedTransferRollsBackDebitAndAuditBeforeTheNextTransferCommits(): Unit = {
    val transactor: Transactor[IO] = Transactor.fromDriverManager[IO](
      "org.h2.Driver",
      "jdbc:h2:mem:wallet_transfer_rollback;DB_CLOSE_DELAY=-1",
      "sa",
      ""
    )
    val initialize: connection.ConnectionIO[Unit] = for {
      _ <- sql"DROP TABLE IF EXISTS wallet_transfers".update.run
      _ <- sql"DROP TABLE IF EXISTS wallets".update.run
      _ <- sql"""CREATE TABLE wallets (
                  wallet_id VARCHAR(40) PRIMARY KEY,
                  balance_cents INTEGER NOT NULL CHECK (balance_cents >= 0)
                )""".update.run
      _ <- sql"""CREATE TABLE wallet_transfers (
                  transfer_id VARCHAR(40) PRIMARY KEY,
                  sender VARCHAR(40) NOT NULL,
                  recipient VARCHAR(40) NOT NULL,
                  cents INTEGER NOT NULL CHECK (cents > 0)
                )""".update.run
      _ <- sql"INSERT INTO wallets VALUES ('alice', 5000), ('bob', 1200)".update.run
    } yield ()

    val rejectedTransfer: IO[String] = transfer("rejected-1", "closed-wallet", 700)
      .transact(transactor).as("accepted").handleErrorWith {
        case error: UnknownWallet => IO.pure(s"rejected: ${error.wallet}")
        case error => IO.raiseError[String](error)
      }

    val (status, afterRejection, afterSuccess): (String, Ledger, Ledger) = (for {
      _ <- initialize.transact(transactor)
      result <- rejectedTransfer
      unchanged <- readLedger.transact(transactor)
      _ <- transfer("accepted-2", "bob", 700).transact(transactor)
      committed <- readLedger.transact(transactor)
      _ <- sql"DROP TABLE wallet_transfers".update.run.transact(transactor)
      _ <- sql"DROP TABLE wallets".update.run.transact(transactor)
    } yield (result, unchanged, committed)).timeout(45.seconds).unsafeRunSync()

    assertEquals("rejected: closed-wallet", status)
    assertEquals(Ledger(List(("alice", 5000), ("bob", 1200)), List.empty), afterRejection)
    assertEquals(
      Ledger(List(("alice", 4300), ("bob", 1900)), List(("accepted-2", "alice", "bob", 700))),
      afterSuccess
    )
    assertEquals(6200, afterSuccess.balances.map(_._2).sum)
  }
}
