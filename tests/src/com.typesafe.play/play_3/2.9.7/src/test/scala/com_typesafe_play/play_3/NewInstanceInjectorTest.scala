/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.inject.BindingKey
import play.api.inject.NewInstanceInjector

class NewInstanceInjectorTest {
  @Test
  def createsInstanceFromClassToken(): Unit = {
    val instance: NewInstanceConstructibleService = NewInstanceInjector.instanceOf(classOf[NewInstanceConstructibleService])

    assertThat(instance.message).isEqualTo("created by NewInstanceInjector")
  }

  @Test
  def createsInstanceFromBindingKey(): Unit = {
    val key: BindingKey[NewInstanceConstructibleService] = BindingKey(classOf[NewInstanceConstructibleService])
    val instance: NewInstanceConstructibleService = NewInstanceInjector.instanceOf(key)

    assertThat(instance.message).isEqualTo("created by NewInstanceInjector")
  }
}

final class NewInstanceConstructibleService {
  def message: String = "created by NewInstanceInjector"
}
