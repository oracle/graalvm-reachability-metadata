/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalactic.scalactic_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.scalactic.source.ObjectMeta

class ObjectMetaAnonymous1Test {
  @Test
  def fieldNamesAndPublicValuesUseJavaReflection(): Unit = {
    val fixture: ObjectMetaVisibleFixture = ObjectMetaVisibleFixture("Ada", 37)
    val meta: ObjectMeta = ObjectMeta(fixture)

    assertThat(meta.fieldNames.contains("name")).isTrue()
    assertThat(meta.fieldNames.contains("count")).isTrue()
    assertThat(meta.hasField("name")).isTrue()
    assertThat(meta.value("name")).isEqualTo("Ada")
    assertThat(meta.value("count")).isEqualTo(37)
    assertThat(meta.typeName("name")).isEqualTo(classOf[String].getName)
    assertThat(meta.shortTypeName("count")).isEqualTo("Integer")
  }

  @Test
  def privateValueFallsBackToAccessibleInvocation(): Unit = {
    val fixture: ObjectMetaPrivateFixture = new ObjectMetaPrivateFixture("classified")
    val meta: ObjectMeta = ObjectMeta(fixture)

    assertThat(meta.fieldNames.contains("secret")).isTrue()
    assertThat(meta.value("secret")).isEqualTo("classified")
  }
}

final case class ObjectMetaVisibleFixture(name: String, count: Int)

final class ObjectMetaPrivateFixture(private val secret: String)
