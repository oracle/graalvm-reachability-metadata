/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13

import akka.actor.ActorSystem
import akka.util.ManifestInfo
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._

class ManifestInfoTest {
  @Test
  def discoversAkkaManifestVersionsFromActorSystemClassLoader(): Unit = {
    val system: ActorSystem = ActorSystem("manifest-info", quietConfig)

    try {
      val info: ManifestInfo = ManifestInfo.get(system)
      val versions: Map[String, ManifestInfo.Version] = info.versions

      assertThat(versions.keySet.asJava).contains("akka-actor")
      assertThat(versions("akka-actor").toString).isNotBlank()
      val sameVersion: Boolean = info.checkSameVersion(
        "Akka",
        Seq("akka-actor"),
        logWarning = false,
        throwException = false)
      assertThat(sameVersion).isTrue()
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }

  private def quietConfig: Config = ConfigFactory.parseString("""
      akka.loglevel = "ERROR"
      akka.stdout-loglevel = "ERROR"
      akka.actor.default-dispatcher.shutdown-timeout = 10s
      akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
      """).withFallback(ConfigFactory.load())
}
