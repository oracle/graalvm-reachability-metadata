/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import java.io.File

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.Configuration
import play.api.Environment
import play.api.Mode
import play.api.inject.Binding
import play.api.inject.Modules

class ModulesTest {
  @Test
  def locatesConfiguredAndDefaultModules(): Unit = {
    ModulesTestConfiguredModule.constructorArgumentsSeen = false

    val environment: Environment = Environment(new File("."), getClass.getClassLoader, Mode.Test)
    val settings: Map[String, Any] = Map(
      "play.modules.enabled" -> Seq(classOf[ModulesTestConfiguredModule].getName)
    )
    val configuration: Configuration = Configuration.from(settings)

    val modules: Seq[Any] = Modules.locate(environment, configuration)

    assertThat(modules.exists(_.isInstanceOf[ModulesTestConfiguredModule])).isTrue
    assertThat(modules.exists(_.isInstanceOf[Module])).isTrue
    assertThat(ModulesTestConfiguredModule.constructorArgumentsSeen).isTrue
  }
}

object ModulesTestConfiguredModule {
  var constructorArgumentsSeen: Boolean = false
}

final class ModulesTestConfiguredModule(environment: Environment, configuration: Configuration)
    extends play.api.inject.Module {
  ModulesTestConfiguredModule.constructorArgumentsSeen = environment != null && configuration != null

  override def bindings(
      environment: Environment,
      configuration: Configuration
  ): scala.collection.Seq[Binding[_]] = Seq.empty
}

final class Module extends play.api.inject.Module {
  override def bindings(
      environment: Environment,
      configuration: Configuration
  ): scala.collection.Seq[Binding[_]] = Seq.empty
}
