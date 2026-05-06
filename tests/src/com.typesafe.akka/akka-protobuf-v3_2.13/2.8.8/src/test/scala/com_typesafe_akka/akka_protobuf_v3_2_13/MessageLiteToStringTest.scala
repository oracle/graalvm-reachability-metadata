/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_2_13

import akka.protobufv3.internal.CodedOutputStream
import akka.protobufv3.internal.GeneratedMessageLite
import akka.protobufv3.internal.GeneratedMessageLite.MethodToInvoke
import java.util.Arrays
import java.util.Collections
import java.util.LinkedHashMap
import java.util.{Map => JavaMap}
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageLiteToStringTest {
  @Test
  def toStringReflectivelyPrintsGeneratedLiteAccessors(): Unit = {
    val printed: String = new PrintableLiteMessageForMessageLiteToStringTest().toString

    assertTrue(printed.contains("label: \"visible\""), printed)
    assertTrue(printed.contains("tags: \"red\""), printed)
    assertTrue(printed.contains("tags: \"blue\""), printed)
    assertTrue(printed.contains("key: \"errors\""), printed)
    assertTrue(printed.contains("value: 7"), printed)
    assertTrue(printed.contains("key: \"warnings\""), printed)
    assertTrue(printed.contains("value: 3"), printed)
  }
}

final class PrintableLiteMessageForMessageLiteToStringTest
    extends GeneratedMessageLite[
      PrintableLiteMessageForMessageLiteToStringTest,
      PrintableLiteMessageForMessageLiteToStringTestBuilder
    ] {
  import PrintableLiteMessageForMessageLiteToStringTest._

  def getLabel: String = Label

  def hasLabel: Boolean = true

  def setLabel(label: String): Unit = throw new UnsupportedOperationException(
    "Setter exists only to mirror generated message accessors"
  )

  def getTagsList: java.util.List[String] = Tags

  def getCountsMap: JavaMap[String, Integer] = Counts

  override def writeTo(output: CodedOutputStream): Unit = output.writeString(1, getLabel)

  override def getSerializedSize: Int = CodedOutputStream.computeStringSize(1, getLabel)

  override def hashCode: Int = 31

  override protected def dynamicMethod(
      method: MethodToInvoke,
      firstArgument: Object,
      secondArgument: Object
  ): Object =
    method match {
      case MethodToInvoke.GET_DEFAULT_INSTANCE => DefaultInstance
      case MethodToInvoke.GET_MEMOIZED_IS_INITIALIZED => Byte.box(1.toByte)
      case MethodToInvoke.NEW_MUTABLE_INSTANCE => new PrintableLiteMessageForMessageLiteToStringTest()
      case MethodToInvoke.NEW_BUILDER => new PrintableLiteMessageForMessageLiteToStringTestBuilder()
      case MethodToInvoke.BUILD_MESSAGE_INFO => null
      case MethodToInvoke.GET_PARSER => null
      case MethodToInvoke.SET_MEMOIZED_IS_INITIALIZED => null
    }
}

object PrintableLiteMessageForMessageLiteToStringTest {
  val Label: String = "visible"
  val Tags: java.util.List[String] = Collections.unmodifiableList(Arrays.asList("red", "blue"))
  val Counts: JavaMap[String, Integer] = {
    val counts: LinkedHashMap[String, Integer] = new LinkedHashMap[String, Integer]()
    counts.put("errors", Integer.valueOf(7))
    counts.put("warnings", Integer.valueOf(3))
    Collections.unmodifiableMap(counts)
  }
  val DefaultInstance: PrintableLiteMessageForMessageLiteToStringTest =
    new PrintableLiteMessageForMessageLiteToStringTest()
}

final class PrintableLiteMessageForMessageLiteToStringTestBuilder
    extends GeneratedMessageLite.Builder[
      PrintableLiteMessageForMessageLiteToStringTest,
      PrintableLiteMessageForMessageLiteToStringTestBuilder
    ](PrintableLiteMessageForMessageLiteToStringTest.DefaultInstance)
