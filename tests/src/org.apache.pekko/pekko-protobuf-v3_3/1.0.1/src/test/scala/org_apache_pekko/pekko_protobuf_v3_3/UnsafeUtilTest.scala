/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import org.apache.pekko.protobufv3.internal.CodedOutputStream
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
  def generatedMessageLiteSchemaLookupAllocatesDefaultInstanceWithUnsafe(): Unit = {
    val message: UnsafeUtilLiteMessageProbe = new UnsafeUtilLiteMessageProbe

    assertThat(message.getSerializedSize()).isZero()
  }
}
