/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_core_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.Response
import org.http4s.StaticFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.{Files => JFiles}
import java.util.concurrent.atomic.AtomicReference

class StaticFileTest {
  @Test
  def fromResourceLoadsResourceThroughProvidedClassLoader(): Unit = {
    val resourceName: String = "static/http4s-covered.txt"
    val content: String = "covered by StaticFile.fromResource"
    val tempDirectory: java.nio.file.Path = JFiles.createTempDirectory("http4s-static-file-test")
    val tempFile: java.nio.file.Path = tempDirectory.resolve("http4s-covered.txt")
    JFiles.writeString(tempFile, content, StandardCharsets.UTF_8)
    val loader: SingleResourceClassLoader =
      new SingleResourceClassLoader(resourceName, tempFile.toUri.toURL)

    try {
      val response: Response[IO] = StaticFile
        .fromResource[IO]("/" + resourceName, classloader = Some(loader))
        .value
        .unsafeRunSync()
        .getOrElse(fail("StaticFile.fromResource should load the test resource"))

      val body: String = response.body.compile.toVector
        .map(bytes => new String(bytes.toArray, StandardCharsets.UTF_8))
        .unsafeRunSync()

      assertEquals(resourceName, loader.requestedName.get())
      assertEquals(content, body)
    } finally {
      JFiles.deleteIfExists(tempFile)
      JFiles.deleteIfExists(tempDirectory)
    }
  }

  private final class SingleResourceClassLoader(resourceName: String, resourceUrl: URL)
      extends ClassLoader(null) {
    val requestedName: AtomicReference[String] = new AtomicReference[String]()

    override def getResource(name: String): URL = {
      requestedName.set(name)
      if (name == resourceName) resourceUrl else null
    }
  }
}
