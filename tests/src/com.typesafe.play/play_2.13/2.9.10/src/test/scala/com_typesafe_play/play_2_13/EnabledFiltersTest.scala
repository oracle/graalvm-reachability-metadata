/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_2_13

import java.io.File

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
  def loadsConfiguredFilterClassAndRetrievesItFromInjector(): Unit = {
    val filter: EnabledFiltersTestFilter = new EnabledFiltersTestFilter
    val injector: SimpleInjector = new SimpleInjector(NewInstanceInjector)
      .add(classOf[EnabledFiltersTestFilter], filter)
    val environment: Environment = Environment(new File("."), getClass.getClassLoader, Mode.Test)
    val configuration: Configuration = Configuration.from(
      Map(
        "play.filters.enabled" -> Seq(classOf[EnabledFiltersTestFilter].getName),
        "play.filters.disabled" -> Seq.empty[String]
      )
    )

    val enabledFilters: EnabledFilters = new EnabledFilters(environment, configuration, injector)

    assertThat(enabledFilters.filters.size).isEqualTo(1)
    assertThat(enabledFilters.filters.head).isSameAs(filter)
  }
}

final class EnabledFiltersTestFilter extends EssentialFilter {
  override def apply(next: EssentialAction): EssentialAction = next
}
