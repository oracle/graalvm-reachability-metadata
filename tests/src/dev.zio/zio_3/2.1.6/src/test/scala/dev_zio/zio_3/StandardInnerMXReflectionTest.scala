/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import zio.Duration
import zio.Runtime
import zio.Schedule
import zio.ULayer
import zio.Unsafe
import zio.ZIO
import zio.ZLayer
import zio.metrics.jvm.JvmMetricsSchedule
import zio.metrics.jvm.Standard

@Timeout(10)
class StandardInnerMXReflectionTest {
  @Test
  def pollsStandardJvmMetricsThroughMxBeanReflection(): Unit = {
    val values: (Long, Either[Throwable, Long], Either[Throwable, Long]) = unsafeRun(
      ZIO
        .scoped {
          ZIO.serviceWithZIO[Standard] { standard =>
            for {
              cpuNanos <- standard.cpuSecondsTotal.poll.map(_.asInstanceOf[Long])
              _        <- standard.cpuSecondsTotal.pollAndUpdate
              openFds  <- standard.openFdCount.poll.map(_.asInstanceOf[Long]).either
              maxFds   <- standard.maxFdCount.poll.map(_.asInstanceOf[Long]).either
            } yield (cpuNanos, openFds, maxFds)
          }
        }
        .provideLayer(testScheduleLayer >>> Standard.live)
    )

    assertThat(values._1).isGreaterThanOrEqualTo(-1L)
    values._2.foreach(count => assertThat(count).isGreaterThanOrEqualTo(0L))
    values._3.foreach(count => assertThat(count).isGreaterThanOrEqualTo(0L))
  }

  private val testScheduleLayer: ULayer[JvmMetricsSchedule] =
    ZLayer.succeed(
      JvmMetricsSchedule(
        Schedule.fixed(Duration.fromSeconds(3600)),
        Schedule.fixed(Duration.fromSeconds(3600))
      )
    )

  private def unsafeRun[A](effect: ZIO[Any, Throwable, A]): A = {
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
  }
}
