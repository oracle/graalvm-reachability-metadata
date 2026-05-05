/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_reflect

import java.io.InputStream
import java.net.URL
import java.nio.file.{Files, Path}
import java.util.jar.{Attributes, JarOutputStream, Manifest}

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.reflect.io.{AbstractFile, ZipArchive}

class ManifestResourcesAnonymous3Test {
  @Test
  def readsManifestClassEntryFromContextClassLoaderResource(): Unit = {
    val resourcePath: String = getClass.getName.replace('.', '/') + ".class"
    val manifestJar: Path = createManifestJar(resourcePath)
    val manifestUrl: URL = new URL(s"jar:${manifestJar.toUri.toURL.toExternalForm}!/META-INF/MANIFEST.MF")
    val thread: Thread = Thread.currentThread()
    val previousContextClassLoader: ClassLoader = thread.getContextClassLoader
    val testClassLoader: ClassLoader = Option(getClass.getClassLoader).getOrElse(ClassLoader.getSystemClassLoader)

    thread.setContextClassLoader(testClassLoader)
    try {
      val archive: AbstractFile = ZipArchive.fromManifestURL(manifestUrl)
      val entry: AbstractFile = findFile(archive, resourcePath).getOrElse {
        throw new AssertionError(s"Manifest resource entry $resourcePath was not exposed by the archive")
      }

      assertThat(entry.path).isEqualTo(resourcePath)

      val input: InputStream = entry.input
      try {
        assertThat(input.read()).isEqualTo(0xca)
        assertThat(input.read()).isEqualTo(0xfe)
        assertThat(input.read()).isEqualTo(0xba)
        assertThat(input.read()).isEqualTo(0xbe)
      } finally {
        input.close()
      }
    } finally {
      thread.setContextClassLoader(previousContextClassLoader)
      Files.deleteIfExists(manifestJar)
    }
  }

  private def createManifestJar(resourcePath: String): Path = {
    val manifest: Manifest = new Manifest()
    manifest.getMainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")

    val entryAttributes: Attributes = new Attributes()
    entryAttributes.putValue("Created-By", getClass.getName)
    manifest.getEntries.put(resourcePath, entryAttributes)

    val jarPath: Path = Files.createTempFile("scala-reflect-manifest-resources-", ".jar")
    val jarOutput: JarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)
    try {
      ()
    } finally {
      jarOutput.close()
    }
    jarPath
  }

  private def findFile(file: AbstractFile, resourcePath: String): Option[AbstractFile] = {
    if (!file.isDirectory) {
      if (file.path == resourcePath) Some(file) else None
    } else {
      val children: Iterator[AbstractFile] = file.iterator
      var result: Option[AbstractFile] = None
      while (children.hasNext && result.isEmpty) {
        result = findFile(children.next(), resourcePath)
      }
      result
    }
  }
}
