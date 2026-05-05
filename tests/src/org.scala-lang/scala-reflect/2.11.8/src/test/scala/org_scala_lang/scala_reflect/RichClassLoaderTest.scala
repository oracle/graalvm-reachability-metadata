/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_reflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters._
import scala.reflect.internal.util.RichClassLoader.wrapClassLoader

class RichClassLoaderTest {
  private val classLoader: ClassLoader = getClass.getClassLoader

  @Test
  def loadsClassesAndClassResources(): Unit = {
    val loadedClass: Option[Class[RichClassLoaderFixtures.NoArgService]] =
      classLoader.tryToLoadClass[RichClassLoaderFixtures.NoArgService](
        classOf[RichClassLoaderFixtures.NoArgService].getName
      )
    val initializedClass: Option[Class[RichClassLoaderFixtures.NoArgService]] =
      classLoader.tryToInitializeClass[RichClassLoaderFixtures.NoArgService](
        classOf[RichClassLoaderFixtures.NoArgService].getName
      )

    assertThat(loadedClass.isDefined).isTrue()
    assertThat(initializedClass.isDefined).isTrue()
    assertThat(classLoader.tryToLoadClass[AnyRef]("org_scala_lang.scala_reflect.DoesNotExist").isDefined)
      .isFalse()

    val stream: java.io.InputStream = classLoader.classAsStream(getClass.getName)
    try assertThat(stream).isNotNull()
    finally if (stream != null) stream.close()
  }

  @Test
  def createsInstancesWithNoArgAndSelectedConstructors(): Unit = {
    val noArgService: RichClassLoaderFixtures.NoArgService =
      classLoader.create(classOf[RichClassLoaderFixtures.NoArgService].getName)
        .asInstanceOf[RichClassLoaderFixtures.NoArgService]

    assertThat(noArgService.label).isEqualTo("created-with-no-arg-constructor")

    val service: RichClassLoaderFixtures.TwoArgService =
      classLoader.create[RichClassLoaderFixtures.TwoArgService](
        classOf[RichClassLoaderFixtures.TwoArgService].getName,
        message => throw new IllegalArgumentException(message)
      )("left", "right")

    assertThat(service.combined).isEqualTo("left:right")
  }

  @Test
  def runsStaticMainWithContextClassLoader(): Unit = {
    RichClassLoaderRunMain.reset()

    classLoader.run("org_scala_lang.scala_reflect.RichClassLoaderRunMain", Seq("alpha", "beta"))

    assertThat(RichClassLoaderRunMain.capturedArguments).containsExactly("alpha", "beta")
    assertThat(RichClassLoaderRunMain.contextWasRichLoader).isTrue()
  }
}

object RichClassLoaderFixtures {
  class NoArgService {
    def label: String = "created-with-no-arg-constructor"
  }

  class TwoArgService(first: String, second: String) {
    def combined: String = s"$first:$second"
  }
}

object RichClassLoaderRunMain {
  private var arguments: Vector[String] = Vector.empty
  private var contextMatched: Boolean = false

  def main(args: Array[String]): Unit = {
    arguments = args.toVector
    contextMatched = Thread.currentThread().getContextClassLoader == getClass.getClassLoader
  }

  def reset(): Unit = {
    arguments = Vector.empty
    contextMatched = false
  }

  def capturedArguments: java.lang.Iterable[String] = arguments.asJava

  def contextWasRichLoader: Boolean = contextMatched
}
