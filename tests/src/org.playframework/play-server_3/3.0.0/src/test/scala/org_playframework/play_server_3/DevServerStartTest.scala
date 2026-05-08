/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_server_3

import java.io.File
import java.util.Collections
import java.util.Map as JMap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import play.api.Configuration
import play.core.BuildLink
import play.core.server.DevServerStart

class DevServerStartTest {
  @TempDir
  var projectDir: File = _

  @Test
  def mainDevPrintsScalaClasspathResourceWhenDebugClasspathIsEnabled(): Unit = {
    val previousDebugClasspath: String = System.getProperty("play.debug.classpath")
    System.setProperty("play.debug.classpath", "true")

    try {
      val starter: DevServerStart = new DevServerStart(
        (_: Configuration) => throw new ActorSystemCreationReached,
        Map.empty[String, AnyRef]
      )

      val thrown: ActorSystemCreationReached = assertThrows(
        classOf[ActorSystemCreationReached],
        () => starter.mainDev(new DevServerTestBuildLink(projectDir), "127.0.0.1", 0, -1)
      )

      assertThat(thrown).hasMessage("dev server reached actor system creation")
    } finally {
      if (previousDebugClasspath == null) {
        System.clearProperty("play.debug.classpath")
      } else {
        System.setProperty("play.debug.classpath", previousDebugClasspath)
      }
    }
  }
}

final class ActorSystemCreationReached
    extends RuntimeException("dev server reached actor system creation")

final class DevServerTestBuildLink(val projectPath: File) extends BuildLink {
  override def reload(): Object = null

  override def findSource(className: String, line: Integer): Array[Object] = Array.empty[Object]

  override def forceReload(): Unit = ()

  override def settings(): JMap[String, String] = Collections.emptyMap[String, String]
}
