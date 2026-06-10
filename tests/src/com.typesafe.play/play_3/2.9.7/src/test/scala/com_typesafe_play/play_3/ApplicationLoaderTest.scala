/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_3

import java.io.File

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import play.api.Application
import play.api.ApplicationLoader
import play.api.Environment
import play.api.Mode

class ApplicationLoaderTest {
  private val LoaderKey: String = "play.application.loader"

  @Test
  def instantiatesConfiguredScalaApplicationLoader(): Unit = {
    val loader: ApplicationLoader = ApplicationLoader(
      contextFor(classOf[ScalaConfiguredApplicationLoader].getName)
    )

    assertThat(loader).isInstanceOf(classOf[ScalaConfiguredApplicationLoader])
  }

  @Test
  def instantiatesConfiguredJavaApplicationLoader(): Unit = {
    val context: ApplicationLoader.Context = contextFor(
      classOf[JavaConfiguredApplicationLoader].getName
    )
    val loader: ApplicationLoader = ApplicationLoader(context)

    val exception: UnsupportedOperationException = assertThrows(
      classOf[UnsupportedOperationException],
      new Executable {
        override def execute(): Unit = {
          loader.load(context)
        }
      }
    )
    assertThat(exception.getMessage).isEqualTo("The Java ApplicationLoader was invoked through the Scala adapter")
  }

  private def contextFor(loaderClassName: String): ApplicationLoader.Context = {
    val environment: Environment = Environment(new File("."), getClass.getClassLoader, Mode.Test)
    val settings: Map[String, AnyRef] = Map(LoaderKey -> loaderClassName)
    ApplicationLoader.Context.create(environment, settings)
  }
}

final class ScalaConfiguredApplicationLoader extends ApplicationLoader {
  override def load(context: ApplicationLoader.Context): Application = {
    throw new UnsupportedOperationException("The ApplicationLoader.apply tests only instantiate the loader")
  }
}

final class JavaConfiguredApplicationLoader extends play.ApplicationLoader {
  override def load(context: play.ApplicationLoader.Context): play.Application = {
    throw new UnsupportedOperationException("The Java ApplicationLoader was invoked through the Scala adapter")
  }
}
