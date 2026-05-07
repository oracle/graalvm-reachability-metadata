/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import com.google.common.reflect.TypeToken
import org.assertj.core.api.Assertions.assertThat
import org.joda.convert.StringConvert
import org.junit.jupiter.api.Test

class TypeUtilsTest {
  @Test
  def parsesCanonicalArrayClassArgumentsInTypeTokenStrings(): Unit = {
    val convert: StringConvert = new StringConvert()
    val token: TypeToken[?] = convert.convertFromString(
      classOf[TypeToken[?]],
      "java.util.List<java.lang.String[]>"
    )

    assertThat(convert.convertToString(classOf[TypeToken[?]], token))
      .isEqualTo("java.util.List<java.lang.String[]>")
  }

  @Test
  def parsesJvmDescriptorArrayClassArgumentsInTypeTokenStrings(): Unit = {
    val convert: StringConvert = new StringConvert()
    val token: TypeToken[?] = convert.convertFromString(
      classOf[TypeToken[?]],
      "java.util.List<[Ljava.lang.String;>"
    )

    assertThat(convert.convertToString(classOf[TypeToken[?]], token))
      .isEqualTo("java.util.List<java.lang.String[]>")
  }
}
