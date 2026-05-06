/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_getquill.quill_core_2_13

import io.getquill.quat.Quat
import io.getquill.quat.RuntimeEntityQuat
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters._

object RuntimeEntityQuatInnerCaseClassTest {
  final case class Person(name: String, age: Int)
}

class RuntimeEntityQuatInnerCaseClassTest {
  import RuntimeEntityQuatInnerCaseClassTest.Person

  @Test
  def runtimeEntityQuatBuildsProductQuatFromCaseClassAccessors(): Unit = {
    val quat: Quat = RuntimeEntityQuat[Person]

    quat match {
      case product: Quat.Product =>
        assertThat(product.name).contains("Person")
        assertThat(product.fields.keySet.asJava).containsExactlyInAnyOrder("name", "age")
        assertThat(product.fields.values.toList.asJava).containsOnly(Quat.Value)
      case other =>
        fail(s"Expected a product quat for the case class, but got $other")
    }
  }
}
