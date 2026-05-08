/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_3

import org.apache.pekko.actor.Props
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AbstractPropsTest {
  @Test
  def rejectsEnclosedCreatorWithNoPublicConstructor(): Unit = {
    val exception: IllegalArgumentException = assertThrows(
      classOf[IllegalArgumentException],
      () => {
        val factory: AbstractPropsCreatorFactory = new AbstractPropsCreatorFactory
        Props.create(classOf[AbstractPropsCreatorFactory.AbstractPropsJavaActor], factory.newEnclosedCreator())
        ()
      })

    assertTrue(
      exception.getMessage.contains("cannot use non-static local Creator"),
      s"unexpected validation failure: ${exception.getMessage}")
  }
}
