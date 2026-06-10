/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_3

import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.util.ArrayList

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.utils.Resources

class ResourcesTest {
  @Test
  def detectsBundleResourceDirectoryWithClassLoaderResourceLookups(): Unit = {
    val requestedResources: ArrayList[String] = new ArrayList[String]()
    val classLoader: ClassLoader = new ClassLoader(getClass.getClassLoader) {
      override def getResource(name: String): URL = {
        requestedResources.add(name)
        if (name == "/bundle-directory" || name == "/bundle-directory/") {
          new URL(null, "file:/bundle-directory", new NoOpUrlStreamHandler)
        } else {
          null
        }
      }
    }
    val bundleDirectoryUrl: URL = new URL(null, "bundle:/bundle-directory", new NoOpUrlStreamHandler)

    val isDirectory: Boolean = Resources.isDirectory(classLoader, bundleDirectoryUrl)

    assertThat(isDirectory).isTrue()
    assertThat(requestedResources).containsExactly("/bundle-directory", "/bundle-directory/")
  }
}

private final class NoOpUrlStreamHandler extends URLStreamHandler {
  override protected def openConnection(url: URL): URLConnection = new URLConnection(url) {
    override def connect(): Unit = {}
  }
}
