/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_json4s.json4s_core_2_13

import org.assertj.core.api.Assertions.assertThat
import org.json4s.DefaultFormats
import org.json4s.Formats
import org.json4s.reflect.ClassDescriptor
import org.json4s.reflect.Reflector
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters._

class ReflectorInnerClassDescriptorBuilderTest {
  @Test
  def describesInnerCaseClassUsingMappedCompanion(): Unit = {
    Reflector.clearCaches()

    val owner: ReflectorInnerClassDescriptorBuilderFixtures = new ReflectorInnerClassDescriptorBuilderFixtures
    val sample: owner.NestedRecord = owner.NestedRecord("alpha")
    val nestedClass: Class[_] = sample.getClass
    implicit val formats: Formats = DefaultFormats

    val descriptor: ClassDescriptor = Reflector
      .createDescriptorWithFormats(
        Reflector.scalaTypeOf(nestedClass),
        companionMappings = List(nestedClass -> owner.asInstanceOf[AnyRef])
      )
      .asInstanceOf[ClassDescriptor]

    assertThat(descriptor.simpleName).isEqualTo("NestedRecord")
    assertThat(descriptor.companion.isDefined).isTrue()
    assertThat(descriptor.properties.map(_.name).asJava).contains("name", "amount")
    assertThat(descriptor.constructors.size).isGreaterThan(1)
    assertThat(descriptor.constructors.exists(_.params.forall(_.name != "$outer"))).isTrue()
    assertThat(descriptor.constructors.flatMap(_.params.map(_.name)).asJava).contains("name", "amount")
  }
}

class ReflectorInnerClassDescriptorBuilderFixtures {
  case class NestedRecord(name: String, amount: Int = 9)
}
