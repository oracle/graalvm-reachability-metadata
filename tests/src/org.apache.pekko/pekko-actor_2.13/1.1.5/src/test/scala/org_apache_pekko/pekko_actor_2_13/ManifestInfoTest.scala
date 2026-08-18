/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_2_13

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.ExtendedActorSystem
import org.apache.pekko.util.ManifestInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class ManifestInfoTest {
  @Test
  def checksVersionsThroughExtensionAndFreshManifestInfo(): Unit = {
    val system: ActorSystem = ActorSystem("manifest-info")

    try {
      val extensionManifestInfo: ManifestInfo = ManifestInfo.get(system)
      assertThat(extensionManifestInfo.checkSameVersion("Pekko", Seq("pekko-actor"), logWarning = false)).isTrue

      val extendedSystem: ExtendedActorSystem = system.asInstanceOf[ExtendedActorSystem]
      val freshManifestInfo: ManifestInfo = new ManifestInfo(extendedSystem)

      assertThat(freshManifestInfo.system).isSameAs(extendedSystem)
      assertThat(freshManifestInfo.versions).isNotNull
      assertThat(freshManifestInfo.checkSameVersion("Pekko", Seq("pekko-actor"), logWarning = false)).isTrue
    } finally {
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
    }
  }
}
