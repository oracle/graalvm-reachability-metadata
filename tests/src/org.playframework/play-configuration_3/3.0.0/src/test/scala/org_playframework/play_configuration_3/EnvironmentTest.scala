/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_configuration_3

import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.Environment
import play.api.Mode

class EnvironmentTest {
  private val resourceName: String = "play-configuration-environment-resource.txt"

  @Test
  def findsClasspathResourceUrl(): Unit = {
    val environment: Environment = testEnvironment()

    val resource: Option[java.net.URL] = environment.resource(s"/$resourceName")

    assertThat(resource.isDefined).isTrue()
    assertThat(resource.get.toExternalForm).contains(resourceName)
  }

  @Test
  def opensClasspathResourceStream(): Unit = {
    val environment: Environment = testEnvironment()

    val resourceStream: Option[InputStream] = environment.resourceAsStream(resourceName)

    assertThat(resourceStream.isDefined).isTrue()
    val stream: InputStream = resourceStream.get
    try {
      val content: String = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
      assertThat(content).contains("loaded through play.api.Environment")
    } finally {
      stream.close()
    }
  }

  private def testEnvironment(): Environment = {
    Environment(new File("."), getClass.getClassLoader, Mode.Test)
  }
}
