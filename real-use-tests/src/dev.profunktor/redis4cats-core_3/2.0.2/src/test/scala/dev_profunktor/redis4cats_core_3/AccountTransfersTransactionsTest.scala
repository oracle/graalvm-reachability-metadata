/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_profunktor.redis4cats_core_3

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.unsafe.implicits.global
import com.github.fppt.jedismock.RedisServer
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.log4cats.log4CatsInstance
import dev.profunktor.redis4cats.tx.TransactionDiscarded
import dev.profunktor.redis4cats.tx.TxStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration.DurationInt

/**
 * Runs MULTI/EXEC transactions through the `TxRunner` and collects their results in a `TxStore`.
 *
 * A bank keeps account balances in Redis. A transfer debits one account and
 * credits another inside a single transaction, and the new balances are
 * captured in the transaction's store once EXEC has run. A second transfer
 * watches the debited account; a teller on another connection changes that
 * balance before the transfer commits, so Redis discards the transaction and
 * neither account moves.
 */
class AccountTransfersTransactionsTest {

  /** What the application reads back after both transfers. */
  private final case class Observed(
      committed: Map[String, Long],
      conflicting: Either[Throwable, Unit],
      balances: Map[String, String]
  )

  /** Moves money between `balance:<account>` keys atomically. */
  private final class Bank(redis: RedisCommands[IO, String, String]) {

    def open(account: String, balance: Long): IO[Unit] = redis.set(s"balance:$account", balance.toString)

    def transfer(from: String, to: String, amount: Long): IO[Map[String, Long]] =
      redis.transact { (store: TxStore[IO, String, Long]) =>
        List(
          redis.decrBy(s"balance:$from", amount).flatMap(store.set(from)),
          redis.incrBy(s"balance:$to", amount).flatMap(store.set(to))
        )
      }

    /** Watches the debited account, runs `meanwhile`, then commits only if nobody touched it. */
    def guardedTransfer(from: String, to: String, amount: Long)(meanwhile: IO[Unit]): IO[Unit] =
      redis.watch(s"balance:$from") *> meanwhile *>
        redis.transact_(List(redis.decrBy(s"balance:$from", amount).void, redis.incrBy(s"balance:$to", amount).void))

    def balances(accounts: Set[String]): IO[Map[String, String]] =
      redis.mGet(accounts.map(account => s"balance:$account")).map(_.map((key, v) => key.stripPrefix("balance:") -> v))
  }

  @Test
  def commitsTransfersAndDiscardsConflictingOnes(): Unit = {
    val server: RedisServer = RedisServer.newRedisServer().start()
    try {
      given Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("bank")
      given Log[IO] = log4CatsInstance[IO]

      val connections: Resource[IO, (RedisCommands[IO, String, String], RedisCommands[IO, String, String])] =
        for {
          client <- RedisClient[IO].from(s"redis://${server.getHost}:${server.getBindPort}")
          bank <- Redis[IO].fromClient(client, RedisCodec.Utf8)
          teller <- Redis[IO].fromClient(client, RedisCodec.Utf8)
        } yield (bank, teller)

      val observed: Observed = connections
        .use { (redis, teller) =>
          val bank: Bank = new Bank(redis)
          for {
            _ <- bank.open("alice", 100L)
            _ <- bank.open("bob", 100L)
            committed <- bank.transfer("alice", "bob", 30L)
            conflicting <- bank
              .guardedTransfer("alice", "bob", 50L)(teller.set("balance:alice", "500"))
              .attempt
              .timeout(30.seconds)
            balances <- bank.balances(Set("alice", "bob"))
          } yield Observed(committed, conflicting, balances)
        }
        .unsafeRunSync()

      assertEquals(Map("alice" -> 70L, "bob" -> 130L), observed.committed)
      assertEquals(Left(TransactionDiscarded), observed.conflicting)
      assertEquals(Map("alice" -> "500", "bob" -> "130"), observed.balances)
    } finally {
      server.stop()
    }
  }
}
