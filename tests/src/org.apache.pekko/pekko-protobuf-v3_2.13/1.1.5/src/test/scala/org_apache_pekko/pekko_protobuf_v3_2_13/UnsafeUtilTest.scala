/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13

import org.apache.pekko.protobufv3.internal.CodedOutputStream
import org.apache.pekko.protobufv3.internal.GeneratedMessageLite
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UnsafeUtilTest {
  @Test
  def codedOutputStreamInitializesUnsafeUtilAndroidSupportChecks(): Unit = {
    val buffer: Array[Byte] = new Array[Byte](16)
    val output: CodedOutputStream = CodedOutputStream.newInstance(buffer)

    output.writeUInt32NoTag(150)
    output.writeBoolNoTag(true)
    output.flush()

    assertThat(buffer.take(3)).containsExactly(0x96.toByte, 0x01.toByte, 0x01.toByte)
  }

  @Test
  def generatedMessageLiteFallbackAllocatesDefaultInstanceWithUnsafe(): Unit = {
    val factoryClass: Class[_] = Class.forName("org.apache.pekko.protobufv3.internal.GeneratedMessageInfoFactory")
    val getInstance = factoryClass.getDeclaredMethod("getInstance")
    getInstance.setAccessible(true)
    val factory: AnyRef = getInstance.invoke(null).asInstanceOf[AnyRef]
    val messageInfoFor = factoryClass.getDeclaredMethod("messageInfoFor", classOf[Class[_]])
    messageInfoFor.setAccessible(true)

    val messageInfo: AnyRef = messageInfoFor.invoke(factory, classOf[LiteMessageProbe]).asInstanceOf[AnyRef]

    assertThat(messageInfo).isNull()
  }
}

final class LiteMessageProbe extends GeneratedMessageLite[LiteMessageProbe, LiteMessageProbeBuilder] {
  override protected def dynamicMethod(
      method: GeneratedMessageLite.MethodToInvoke,
      arg0: AnyRef,
      arg1: AnyRef): AnyRef = {
    method match {
      case GeneratedMessageLite.MethodToInvoke.BUILD_MESSAGE_INFO =>
        null
      case GeneratedMessageLite.MethodToInvoke.NEW_MUTABLE_INSTANCE =>
        new LiteMessageProbe
      case GeneratedMessageLite.MethodToInvoke.NEW_BUILDER =>
        new LiteMessageProbeBuilder(this)
      case GeneratedMessageLite.MethodToInvoke.GET_DEFAULT_INSTANCE =>
        this
      case GeneratedMessageLite.MethodToInvoke.GET_PARSER =>
        null
      case GeneratedMessageLite.MethodToInvoke.GET_MEMOIZED_IS_INITIALIZED =>
        Byte.box(1.toByte)
      case GeneratedMessageLite.MethodToInvoke.SET_MEMOIZED_IS_INITIALIZED =>
        null
    }
  }
}

final class LiteMessageProbeBuilder(defaultInstance: LiteMessageProbe)
    extends GeneratedMessageLite.Builder[LiteMessageProbe, LiteMessageProbeBuilder](defaultInstance)
