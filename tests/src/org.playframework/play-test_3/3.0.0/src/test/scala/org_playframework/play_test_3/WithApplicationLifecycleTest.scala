/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_test_3

import java.util.Map as JMap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.Application
import play.api.Configuration

class WithApplicationLifecycleTest {
  @Test
  def withApplicationStartsConfiguredApplicationAndExposesInjectedComponents(): Unit = {
    val probe = new ConfiguredWithApplication(
      JMap.of(
        "play.http.secret.key",
        "with-application-test-secret-32-bytes",
        "probe.message",
        "configured by WithApplication"
      )
    )

    try {
      probe.startPlay()

      assertThat(probe.currentApplication).isNotNull()
      assertThat(probe.currentApplication.isTest).isTrue()
      assertThat(probe.currentMaterializerIsAvailable).isTrue()
      assertThat(probe.currentApplication.config().getString("probe.message"))
        .isEqualTo("configured by WithApplication")
      assertThat(probe.injectedConfiguration.get[String]("probe.message"))
        .isEqualTo("configured by WithApplication")
    } finally {
      probe.stopPlay()
    }

    assertThat(probe.currentApplication).isNull()
  }
}

final class ConfiguredWithApplication(configuration: JMap[String, String]) extends _root_.play.test.WithApplication {
  override protected def provideApplication(): Application = _root_.play.test.Helpers.fakeApplication(configuration)

  def currentApplication: Application = app

  def currentMaterializerIsAvailable: Boolean = mat != null

  def injectedConfiguration: Configuration = instanceOf(classOf[Configuration])
}
