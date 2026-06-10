/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_3

import java.io.File

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.Configuration
import play.api.Environment
import play.api.Mode
import play.api.inject.Modules

class ModulesTest {
  @Test
  def locatesConfiguredModuleByName(): Unit = {
    val environment: Environment = Environment(new File("."), getClass.getClassLoader, Mode.Test)
    val settings: Map[String, Any] = Map(
      "play.modules.enabled" -> Seq(classOf[ScalaConfiguredApplicationLoader].getName)
    )
    val configuration: Configuration = Configuration.from(settings)

    val modules: Seq[Any] = Modules.locate(environment, configuration)

    assertThat(modules.exists(_.isInstanceOf[ScalaConfiguredApplicationLoader])).isTrue
  }

  @Test
  def locatesDefaultModuleByName(): Unit = {
    val classLoader: ClassLoader = new DefaultModuleClassLoader(getClass.getClassLoader)
    val environment: Environment = Environment(new File("."), classLoader, Mode.Test)

    val modules: Seq[Any] = Modules.locate(environment, Configuration.empty)

    assertThat(modules.exists(_.isInstanceOf[ScalaConfiguredApplicationLoader])).isTrue
  }
}

final class DefaultModuleClassLoader(parent: ClassLoader) extends ClassLoader(parent) {
  override def loadClass(name: String): Class[?] = {
    if (name == "Module") classOf[ScalaConfiguredApplicationLoader]
    else super.loadClass(name)
  }
}
