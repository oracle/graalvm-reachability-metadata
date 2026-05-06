/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_2_13

import scala.jdk.CollectionConverters.MapHasAsJava

import org.apache.pekko.actor.ExtendedActorSystem
import org.apache.pekko.util.ManifestInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ManifestInfoTest {
  @Test
  def loadsManifestVersionsFromActorSystemClassLoader(): Unit = {
    val system: ExtendedActorSystem = new ManifestInfoStubActorSystem(getClass.getClassLoader)

    val manifestInfo: ManifestInfo = new ManifestInfo(system)
    val versions: Map[String, ManifestInfo.Version] = manifestInfo.versions

    assertThat(versions.asJava).containsKey("pekko-actor")
    assertThat(manifestInfo.checkSameVersion("Apache Pekko", Seq.empty[String], logWarning = false, throwException = false))
      .isTrue
  }
}
