/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.ObjectStreamClass

import org.apache.pekko.protobufv3.internal.BoolValue
import org.apache.pekko.protobufv3.internal.MessageLite
import org.apache.pekko.protobufv3.internal.UnknownFieldSet
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GeneratedMessageLiteInnerSerializedFormTest {
  @Test
  def deserializesGeneratedMessageThroughSerializedForm(): Unit = {
    val message: BoolValue = BoolValue.newBuilder().setValue(true).build()

    val deserialized: BoolValue = roundTripMessage(message).asInstanceOf[BoolValue]

    assertThat(deserialized.getValue).isTrue()
  }

  @Test
  def deserializesLegacySerializedFormByResolvingMessageClassName(): Unit = {
    val message: BoolValue = BoolValue.newBuilder().setValue(true).build()
    val legacyForm: LegacySerializedForm = new LegacySerializedForm(message.toByteArray, classOf[BoolValue].getName)

    val deserialized: BoolValue = roundTripLegacySerializedForm(legacyForm).asInstanceOf[BoolValue]

    assertThat(deserialized.getValue).isTrue()
  }

  @Test
  def deserializesLegacySerializedFormUsingLegacyDefaultInstanceField(): Unit = {
    val fieldNumber: Int = 123
    val fieldValue: Long = 42L
    val message: UnknownFieldSet = UnknownFieldSet.newBuilder()
      .mergeVarintField(fieldNumber, fieldValue.toInt)
      .build()
    val legacyForm: LegacySerializedForm = new LegacySerializedForm(message.toByteArray, classOf[UnknownFieldSet].getName)

    val deserialized: UnknownFieldSet = roundTripLegacySerializedForm(legacyForm).asInstanceOf[UnknownFieldSet]

    assertThat(deserialized.asMap()).containsKey(fieldNumber)
    assertThat(deserialized.asMap().get(fieldNumber).getVarintList).containsExactly(fieldValue)
  }

  private def roundTripMessage(message: MessageLite): MessageLite = {
    deserialize(serialize(message)).asInstanceOf[MessageLite]
  }

  private def roundTripLegacySerializedForm(form: LegacySerializedForm): MessageLite = {
    deserialize(serializeAsGeneratedMessageLiteSerializedForm(form)).asInstanceOf[MessageLite]
  }

  private def serialize(value: AnyRef): Array[Byte] = {
    val output: ByteArrayOutputStream = new ByteArrayOutputStream()
    val objectOutput: ObjectOutputStream = new ObjectOutputStream(output)
    try {
      objectOutput.writeObject(value)
    } finally {
      objectOutput.close()
    }
    output.toByteArray
  }

  private def serializeAsGeneratedMessageLiteSerializedForm(value: LegacySerializedForm): Array[Byte] = {
    val output: ByteArrayOutputStream = new ByteArrayOutputStream()
    val objectOutput: ObjectOutputStream = new GeneratedMessageLiteSerializedFormObjectOutputStream(
      output,
      GeneratedMessageLiteInnerSerializedFormTest.SerializedFormDescriptor
    )
    try {
      objectOutput.writeObject(value)
    } finally {
      objectOutput.close()
    }
    output.toByteArray
  }

  private def deserialize(bytes: Array[Byte]): AnyRef = {
    val input: ObjectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))
    try {
      input.readObject()
    } finally {
      input.close()
    }
  }
}

object GeneratedMessageLiteInnerSerializedFormTest {
  private val SerializedFormClassName: String = "org.apache.pekko.protobufv3.internal.GeneratedMessageLite$SerializedForm"

  val SerializedFormDescriptor: ObjectStreamClass = {
    val message: BoolValue = BoolValue.getDefaultInstance
    val output: ByteArrayOutputStream = new ByteArrayOutputStream()
    val objectOutput: CapturingSerializedFormObjectOutputStream = new CapturingSerializedFormObjectOutputStream(output)
    try {
      objectOutput.writeObject(message)
    } finally {
      objectOutput.close()
    }
    val descriptor: ObjectStreamClass = objectOutput.serializedFormDescriptor
    require(descriptor != null, "Expected generated message serialization to use GeneratedMessageLite.SerializedForm")
    descriptor
  }

  private final class CapturingSerializedFormObjectOutputStream(output: ByteArrayOutputStream)
    extends ObjectOutputStream(output) {
    private var descriptor: ObjectStreamClass = null

    def serializedFormDescriptor: ObjectStreamClass = descriptor

    override protected def writeClassDescriptor(candidate: ObjectStreamClass): Unit = {
      if (candidate.getName == SerializedFormClassName) {
        descriptor = candidate
      }
      super.writeClassDescriptor(candidate)
    }
  }
}

@SerialVersionUID(0L)
private final class LegacySerializedForm(
  private val asBytes: Array[Byte],
  private val messageClassName: String
) extends Serializable {
  var messageClass: Class[_] = null
}

private final class GeneratedMessageLiteSerializedFormObjectOutputStream(
  output: ByteArrayOutputStream,
  serializedFormDescriptor: ObjectStreamClass
) extends ObjectOutputStream(output) {
  override protected def writeClassDescriptor(descriptor: ObjectStreamClass): Unit = {
    val descriptorToWrite: ObjectStreamClass = {
      if (descriptor.getName == classOf[LegacySerializedForm].getName) {
        serializedFormDescriptor
      } else {
        descriptor
      }
    }
    super.writeClassDescriptor(descriptorToWrite)
  }
}
