/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_2_13

import org.apache.pekko.actor.Props
import org.apache.pekko.japi.Creator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

final class AbstractPropsTest {
  @Test
  def rejectsNonStaticCreatorAfterInspectingDeclaredConstructors(): Unit = {
    val factory: AbstractPropsCreatorFactory = new AbstractPropsCreatorFactory
    val creator: Creator[AbstractPropsJavaActor] = factory.nonStaticAnonymousCreator()

    val exception: IllegalArgumentException = assertThrows(
      classOf[IllegalArgumentException],
      () => {
        Props.create[AbstractPropsJavaActor](factory.actorClass(), creator)
        ()
      })

    assertThat(exception).hasMessageContaining("cannot use non-static local Creator")
  }
}
