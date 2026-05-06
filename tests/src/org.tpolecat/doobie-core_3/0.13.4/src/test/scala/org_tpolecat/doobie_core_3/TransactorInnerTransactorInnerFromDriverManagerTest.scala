/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_tpolecat.doobie_core_3

import cats.effect.Blocker
import cats.effect.ConcurrentEffect
import cats.effect.ContextShift
import cats.effect.IO
import doobie.util.transactor.Transactor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.sql.SQLException
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class TransactorInnerTransactorInnerFromDriverManagerTest {
  private implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  private implicit val effect: ConcurrentEffect[IO] = IO.ioConcurrentEffect(contextShift)
  private val blocker: Blocker = Blocker.liftExecutionContext(ExecutionContext.global)

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  def fromDriverManagerLoadsDriverClassWhenOpeningConnection(): Unit = {
    val xa: Transactor.Aux[IO, Unit] =
      Transactor.fromDriverManager[IO](
        "doobie.enumerated.FetchDirection",
        "jdbc:doobie-core-test:missing-database",
        blocker
      )

    val result: Either[Throwable, Unit] =
      xa.connect(xa.kernel).use(_ => IO.unit).attempt.unsafeRunTimed(5.seconds).getOrElse {
        throw new AssertionError("Connection acquisition did not complete")
      }

    val failure: Throwable =
      result.swap.getOrElse(new AssertionError("Expected connection acquisition to fail"))

    assertThat(failure).isInstanceOf(classOf[SQLException])
    assertThat(failure.getMessage).contains("No suitable driver")
  }
}
