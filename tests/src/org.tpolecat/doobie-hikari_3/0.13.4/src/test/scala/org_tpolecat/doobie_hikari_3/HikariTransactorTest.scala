/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_tpolecat.doobie_hikari_3

import cats.effect.Blocker
import cats.effect.ContextShift
import cats.effect.IO
import doobie.hikari.HikariTransactor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.concurrent.ExecutionContext

class HikariTransactorTest {
  private implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  @Test
  def newHikariTransactorLoadsTheConfiguredDriverClass(): Unit = {
    val driverClassName: String = classOf[org.h2.Driver].getName
    val connectionExecutionContext: ExecutionContext = ExecutionContext.global
    val blocker: Blocker = Blocker.liftExecutionContext(connectionExecutionContext)

    HikariTransactor
      .newHikariTransactor[IO](
        driverClassName,
        "jdbc:h2:mem:doobie_hikari_transactor;DB_CLOSE_DELAY=-1",
        "sa",
        "",
        connectionExecutionContext,
        blocker
      )
      .use(transactor => IO {
        assertThat(transactor).isNotNull
        ()
      })
      .unsafeRunSync()
  }
}
