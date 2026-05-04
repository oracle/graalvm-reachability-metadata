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

class ResourceRootInnerClassTest {
  @Test
  def readsResourceRelativeToClassRoot(): Unit = {
    val root: os.ResourceRoot = os.ResourceRoot.Class(classOf[ResourceRootInnerClassTest])
    val resource: os.ResourcePath = os.resource(root) / "resource-root-class.txt"
    val stream: java.io.InputStream = resource.getInputStream

    try {
      val contents: String = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
      assertEquals("loaded through ResourceRoot.Class\n", contents)
    } finally {
      stream.close()
    }
  }
}
