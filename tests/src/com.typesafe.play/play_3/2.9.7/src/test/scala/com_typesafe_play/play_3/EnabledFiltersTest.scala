/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_3

import java.io.File

import scala.jdk.CollectionConverters._

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.Configuration
import play.api.Environment
import play.api.Mode
import play.api.http.EnabledFilters
import play.api.inject.NewInstanceInjector
import play.api.inject.SimpleInjector
import play.api.mvc.EssentialAction
import play.api.mvc.EssentialFilter

class EnabledFiltersTest {
  @Test
  def loadsConfiguredFiltersFromConfiguration(): Unit = {
    val configuredFilter: EnabledFiltersConfiguredFilter = new EnabledFiltersConfiguredFilter()
    val injector: SimpleInjector = new SimpleInjector(NewInstanceInjector)
      .add(classOf[EnabledFiltersConfiguredFilter], configuredFilter)
    val enabledFilters: EnabledFilters = new EnabledFilters(
      Environment(new File("."), getClass.getClassLoader, Mode.Test),
      Configuration.from(
        Map[String, Any](
          "play.filters.enabled" -> Seq(classOf[EnabledFiltersConfiguredFilter].getName),
          "play.filters.disabled" -> Seq.empty[String]
        )
      ),
      injector
    )

    assertThat(enabledFilters.filters.asJava).containsExactly(configuredFilter)
  }
}

final class EnabledFiltersConfiguredFilter extends EssentialFilter {
  override def apply(next: EssentialAction): EssentialAction = next
}
