/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_2_13

import java.net.URI
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.nio.file.Paths

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.utils.Resources

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._

class ResourcesTest {
  @Test
  def treatsGraalVmResourceUrlAsFileWhenOnlyNonSlashLookupResolves(): Unit = {
    val resourcePath: String = "/play-resource-entry"
    val classLoader: ResourcesTest.SingleResourceClassLoader = new ResourcesTest.SingleResourceClassLoader(resourcePath)
    val url: URL = ResourcesTest.resourceUrl(resourcePath)

    val isDirectory: Boolean = Resources.isDirectory(classLoader, url)

    assertThat(isDirectory).isFalse
    assertThat(classLoader.requestedResources).containsExactly(resourcePath, resourcePath + "/")
  }
}

object ResourcesTest {
  private val ResolvedResourceUrl: URL = Paths.get(".").toUri.toURL

  private def resourceUrl(path: String): URL = {
    URL.of(URI.create(s"resource:$path"), NoOpUrlStreamHandler)
  }

  final class SingleResourceClassLoader(resourcePath: String) extends ClassLoader(null) {
    private val requests: ListBuffer[String] = ListBuffer.empty

    override def getResource(name: String): URL = {
      requests += name
      if (name == resourcePath) ResolvedResourceUrl else null
    }

    def requestedResources: java.util.List[String] = requests.toSeq.asJava
  }

  private object NoOpUrlStreamHandler extends URLStreamHandler {
    override protected def openConnection(url: URL): URLConnection = {
      throw new UnsupportedOperationException("This test only inspects URL metadata")
    }
  }
}
