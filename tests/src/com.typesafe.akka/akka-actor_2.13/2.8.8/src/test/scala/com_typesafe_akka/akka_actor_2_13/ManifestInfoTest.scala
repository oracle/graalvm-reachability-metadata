/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13

import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.util.ManifestInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class ManifestInfoTest {
  @Test
  def discoversKnownVendorArtifactVersionsFromClasspathManifests(): Unit = {
    withActorSystem("manifest-info") { system: ActorSystem =>
      val manifestInfo: ManifestInfo = new ManifestInfo(system.asInstanceOf[ExtendedActorSystem])

      assertThat(manifestInfo.versions.contains("akka-manifest-info-test")).isTrue()
      assertThat(manifestInfo.checkSameVersion("Akka", Seq("akka-manifest-info-test"), logWarning = false)).isTrue()
    }
  }

  private def withActorSystem(name: String)(body: ActorSystem => Unit): Unit = {
    val system: ActorSystem = ActorSystem(name)
    try {
      body(system)
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
