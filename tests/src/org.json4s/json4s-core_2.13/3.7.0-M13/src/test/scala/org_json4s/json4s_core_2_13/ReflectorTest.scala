/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_json4s.json4s_core_2_13

import org.assertj.core.api.Assertions.assertThat
import org.json4s.reflect.ClassDescriptor
import org.json4s.reflect.ConstructorParamDescriptor
import org.json4s.reflect.Reflector
import org.junit.jupiter.api.Test

class ReflectorTest {
  @Test
  def invokesDefaultValueAccessorFromDescribedConstructor(): Unit = {
    Reflector.clearCaches()

    val descriptor: ClassDescriptor = Reflector.describe[ReflectorDefaultValueFixture].asInstanceOf[ClassDescriptor]
    val defaultedParam: ConstructorParamDescriptor = descriptor.mostComprehensive.find(_.name == "label").get

    assertThat(defaultedParam.hasDefault).isTrue()
    assertThat(defaultedParam.defaultValue.isDefined).isTrue()
    assertThat(defaultedParam.defaultValue.get.apply()).isEqualTo("from-default")
  }
}

case class ReflectorDefaultValueFixture(label: String = "from-default", count: Int = 7)
