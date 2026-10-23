/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_3

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import org.junit.jupiter.api.io.TempDir
import zio.{Duration, IO, Runtime, Unsafe, ZIO}

/** Exercises ZIO effect caching and invalidation by refreshing file-backed shipping rates used by concurrent quotes. */
class CachedShippingRatesTest {
  private case class ShippingRates(baseCents: Long, perKilogramCents: Long) {
    def quote(weightKilograms: Int): Long = baseCents + perKilogramCents * weightKilograms
  }

  private def loadRates(path: Path): IO[Throwable, ShippingRates] =
    ZIO.attemptBlocking {
      val fields: Array[String] = Files.readString(path, UTF_8).trim.split(",")
      require(fields.length == 2, "Shipping rates require base and per-kilogram charges")
      val rates: ShippingRates = ShippingRates(fields(0).toLong, fields(1).toLong)
      require(rates.baseCents >= 0 && rates.perKilogramCents >= 0, "Shipping charges cannot be negative")
      rates
    }

  @Test
  @Timeout(60)
  def concurrentQuotesReuseRatesUntilAnExplicitRefresh(@TempDir directory: Path): Unit = {
    val ratesFile: Path = directory.resolve("shipping-rates.csv")
    Files.writeString(ratesFile, "400,150\n", UTF_8)
    val weights: List[Int] = List(1, 2, 5, 10)
    val program = for {
      cached <- loadRates(ratesFile).cachedInvalidate(Duration.Infinity)
      (currentRates, refresh) = cached
      initial <- ZIO.foreachPar(weights)(weight => currentRates.map(_.quote(weight)))
      // Publishing a rate file does not change the snapshot already serving requests.
      _ <- ZIO.attemptBlocking(Files.writeString(ratesFile, "500,200\n", UTF_8))
      beforeRefresh <- ZIO.foreachPar(weights)(weight => currentRates.map(_.quote(weight)))
      _ <- refresh
      afterRefresh <- ZIO.foreachPar(weights)(weight => currentRates.map(_.quote(weight)))
      nextQuote <- currentRates.map(_.quote(3))
    } yield (initial, beforeRefresh, afterRefresh, nextQuote)

    val (initial, beforeRefresh, afterRefresh, nextQuote) = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(
        program.timeoutFail(new IllegalStateException("Shipping rate refresh timed out"))(Duration.fromSeconds(30))
      ).getOrThrowFiberFailure()
    }

    assertEquals(List(550L, 700L, 1150L, 1900L), initial)
    assertEquals(initial, beforeRefresh)
    assertEquals(List(700L, 900L, 1500L, 2500L), afterRefresh)
    assertEquals(1100L, nextQuote)
  }
}
