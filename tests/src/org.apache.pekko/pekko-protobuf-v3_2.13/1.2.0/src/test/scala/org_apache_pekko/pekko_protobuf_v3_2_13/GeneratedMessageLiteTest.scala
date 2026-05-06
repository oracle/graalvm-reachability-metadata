/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType.methodType
import java.lang.reflect.Method

import org.apache.pekko.protobufv3.internal.GeneratedMessageLite
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GeneratedMessageLiteTest {
  @Test
  def defaultInstanceLookupInitializesLiteClassByName(): Unit = {
    val lookup: MethodHandles.Lookup = MethodHandles.privateLookupIn(
      classOf[GeneratedMessageLite[_, _]],
      MethodHandles.lookup()
    )
    val getDefaultInstance: MethodHandle = lookup.findStatic(
      classOf[GeneratedMessageLite[_, _]],
      "getDefaultInstance",
      methodType(
        classOf[GeneratedMessageLite[_, _]],
        classOf[Class[_]]
      )
    )

    val defaultInstance: Object = getDefaultInstance.invokeWithArguments(
      classOf[GeneratedMessageLiteSchemaMessage]
    )

    assertThat(defaultInstance).isInstanceOf(classOf[GeneratedMessageLiteSchemaMessage])
  }

  @Test
  def generatedMethodHelpersResolveAndInvokeGeneratedAccessors(): Unit = {
    val lookup: MethodHandles.Lookup = MethodHandles.privateLookupIn(
      classOf[GeneratedMessageLite[_, _]],
      MethodHandles.lookup()
    )
    val getMethodOrDie: MethodHandle = lookup.findStatic(
      classOf[GeneratedMessageLite[_, _]],
      "getMethodOrDie",
      methodType(
        classOf[Method],
        classOf[Class[_]],
        classOf[String],
        classOf[Array[Class[_]]]
      )
    ).asFixedArity()
    val invokeOrDie: MethodHandle = lookup.findStatic(
      classOf[GeneratedMessageLite[_, _]],
      "invokeOrDie",
      methodType(
        classOf[Object],
        classOf[Method],
        classOf[Object],
        classOf[Array[Object]]
      )
    ).asFixedArity()

    val accessor: Method = getMethodOrDie.invokeWithArguments(
      classOf[GeneratedMessageLitePrintableMessage],
      "getLabel",
      Array.empty[Class[_]]
    ).asInstanceOf[Method]
    val value: Object = invokeOrDie.invokeWithArguments(
      accessor,
      new GeneratedMessageLitePrintableMessage(),
      Array.empty[Object]
    )

    assertThat(value).isEqualTo("visible")
  }

  @Test
  def toStringReflectivelyPrintsGeneratedLiteAccessors(): Unit = {
    val printed: String = new GeneratedMessageLitePrintableMessage().toString

    assertThat(printed.contains("label: \"visible\"")).isTrue
    assertThat(printed.contains("tags: \"red\"")).isTrue
    assertThat(printed.contains("tags: \"blue\"")).isTrue
    assertThat(printed.contains("key: \"errors\"")).isTrue
    assertThat(printed.contains("value: 7")).isTrue
    assertThat(printed.contains("key: \"warnings\"")).isTrue
    assertThat(printed.contains("value: 3")).isTrue
  }
}
