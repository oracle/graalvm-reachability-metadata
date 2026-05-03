/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_configuration_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.Environment
import play.api.Mode

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.ArrayList

class EnvironmentTest {
  @Test
  def resourceDelegatesToConfiguredClassLoader(): Unit = {
    val classLoader: RecordingResourceClassLoader = new RecordingResourceClassLoader
    val environment: Environment = Environment(new File("."), classLoader, Mode.Test)

    val resource: Option[URL] = environment.resource("/conf/application.conf")

    assertThat(resource.isDefined).isTrue
    assertThat(resource.get.toExternalForm).isEqualTo("file:/virtual/conf/application.conf")
    assertThat(classLoader.resourceNames).containsExactly("conf/application.conf")
  }

  @Test
  def resourceAsStreamDelegatesToConfiguredClassLoader(): Unit = {
    val classLoader: RecordingResourceClassLoader = new RecordingResourceClassLoader
    val environment: Environment = Environment(new File("."), classLoader, Mode.Test)

    val stream: Option[InputStream] = environment.resourceAsStream("/conf/settings.conf")

    assertThat(stream.isDefined).isTrue
    assertThat(readText(stream.get)).isEqualTo("stream:conf/settings.conf")
    assertThat(classLoader.streamNames).containsExactly("conf/settings.conf")
  }

  private def readText(stream: InputStream): String = {
    try {
      new String(stream.readAllBytes(), StandardCharsets.UTF_8)
    } finally {
      stream.close()
    }
  }
}

private class RecordingResourceClassLoader extends ClassLoader(null) {
  val resourceNames: ArrayList[String] = new ArrayList[String]
  val streamNames: ArrayList[String] = new ArrayList[String]

  override def getResource(name: String): URL = {
    resourceNames.add(name)
    URI.create(s"file:/virtual/$name").toURL
  }

  override def getResourceAsStream(name: String): InputStream = {
    streamNames.add(name)
    new ByteArrayInputStream(s"stream:$name".getBytes(StandardCharsets.UTF_8))
  }
}
