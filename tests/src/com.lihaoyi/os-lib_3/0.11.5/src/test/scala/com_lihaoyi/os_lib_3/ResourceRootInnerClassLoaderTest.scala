/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.os_lib_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import java.nio.charset.StandardCharsets

class ResourceRootInnerClassLoaderTest {
  @Test
  def readsResourceFromClassLoaderRoot(): Unit = {
    val classLoader: ClassLoader = Thread.currentThread().getContextClassLoader
    val root: os.ResourceRoot = os.ResourceRoot.ClassLoader(classLoader)
    val resource: os.ResourcePath = os.resource(root) / "com_lihaoyi" / "os_lib_3" / "resource-root-classloader.txt"
    val stream: java.io.InputStream = resource.getInputStream

    try {
      val contents: String = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
      assertEquals("loaded through ResourceRoot.ClassLoader\n", contents)
    } finally {
      stream.close()
    }
  }
}
