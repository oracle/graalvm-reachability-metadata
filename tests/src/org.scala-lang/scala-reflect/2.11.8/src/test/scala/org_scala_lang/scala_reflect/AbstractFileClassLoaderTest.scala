/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_reflect

import java.net.URL
import java.security.ProtectionDomain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.reflect.internal.util.AbstractFileClassLoader
import scala.reflect.io.{AbstractFile, VirtualDirectory}

class AbstractFileClassLoaderTest {
  private val parentClassLoader: ClassLoader = getClass.getClassLoader

  @Test
  def createsPackageForExistingAbstractDirectory(): Unit = {
    val root: VirtualDirectory = new VirtualDirectory("abstract-file-class-loader-root", None)
    val packageDirectory: AbstractFile = root.subdirectoryNamed("dynamicaccess").subdirectoryNamed("fixture")
    val loader: TestableAbstractFileClassLoader = new TestableAbstractFileClassLoader(root, parentClassLoader)

    val packageInstance: Package = loader.packageNamed("dynamicaccess.fixture")

    assertThat(packageDirectory.isDirectory).isTrue()
    assertThat(packageInstance).isNotNull()
    assertThat(packageInstance.getName).isEqualTo("dynamicaccess.fixture")
    assertThat(loader.packageNamed("dynamicaccess.fixture")).isSameAs(packageInstance)
  }

  @Test
  def buildsProtectionDomainFromScalaRuntimeJarResource(): Unit = {
    val root: VirtualDirectory = new VirtualDirectory("protection-domain-root", None)
    val loader: TestableAbstractFileClassLoader = new TestableAbstractFileClassLoader(root, parentClassLoader)
    val resourceClassLoader: ScalaRuntimePackageResourceClassLoader =
      new ScalaRuntimePackageResourceClassLoader(parentClassLoader)
    val thread: Thread = Thread.currentThread()
    val originalContextClassLoader: ClassLoader = thread.getContextClassLoader

    thread.setContextClassLoader(resourceClassLoader)
    try {
      val protectionDomain: ProtectionDomain = loader.currentProtectionDomain

      assertThat(resourceClassLoader.requestedResourceName).isEqualTo("scala/runtime/package.class")
      assertThat(protectionDomain).isNotNull()
      assertThat(protectionDomain.getClassLoader).isSameAs(loader)
      assertThat(protectionDomain.getCodeSource.getLocation.toExternalForm).isEqualTo("file:/tmp/scala-library.jar")
    } finally {
      thread.setContextClassLoader(originalContextClassLoader)
    }
  }

  private final class TestableAbstractFileClassLoader(root: AbstractFile, parent: ClassLoader)
      extends AbstractFileClassLoader(root, parent) {
    def packageNamed(name: String): Package = getPackage(name)

    def currentProtectionDomain: ProtectionDomain = protectionDomain
  }

  private final class ScalaRuntimePackageResourceClassLoader(parent: ClassLoader) extends ClassLoader(parent) {
    var requestedResourceName: String = _

    override def getResource(name: String): URL = {
      requestedResourceName = name
      if (name == "scala/runtime/package.class") {
        new URL("jar:file:/tmp/scala-library.jar!/scala/runtime/package.class")
      } else {
        super.getResource(name)
      }
    }
  }
}
