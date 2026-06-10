/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.utils.Reflect

class ReflectTest {
  @Test
  def createsInstanceByClassNameWithSuppliedClassLoader(): Unit = {
    val instance: ReflectConstructibleService = Reflect.createInstance[ReflectConstructibleService](
      classOf[ReflectConstructibleTarget].getName,
      getClass.getClassLoader
    )

    assertThat(instance.message).isEqualTo("created by Reflect")
  }
}

trait ReflectConstructibleService {
  def message: String
}

final class ReflectConstructibleTarget extends ReflectConstructibleService {
  override def message: String = "created by Reflect"
}
