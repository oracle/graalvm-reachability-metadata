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
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters._

case class RuntimeEntityQuatInnerCaseClassPerson(id: Int, name: String, active: Boolean)

class RuntimeEntityQuatInnerCaseClassTest {
  @Test
  def derivesProductQuatForRuntimeCaseClassEntity(): Unit = {
    val product: Quat.Product = RuntimeEntityQuat[RuntimeEntityQuatInnerCaseClassPerson].probit
    val fieldNames: java.util.Set[String] = product.fields.keySet.asJava

    assertThat(product.name).isEqualTo("RuntimeEntityQuatInnerCaseClassPerson")
    assertThat(fieldNames).contains("id", "name", "active")
    assertThat(fieldNames).doesNotContain("copy", "copy$default$1", "productElement")
    assertThat(product.lookup("id", failNonExist = true)).isSameAs(Quat.Value)
    assertThat(product.lookup("name", failNonExist = true)).isSameAs(Quat.Value)
    assertThat(product.lookup("active", failNonExist = true)).isSameAs(Quat.Value)
  }
}
