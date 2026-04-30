/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import org.apache.pekko.protobufv3.internal.DescriptorProtos
import org.apache.pekko.protobufv3.internal.Descriptors
import org.apache.pekko.protobufv3.internal.GeneratedMessage
import org.apache.pekko.protobufv3.internal.Message
import org.assertj.core.api.Assertions.assertThat
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test

import scala.annotation.static

class GeneratedMessageAnonymous4Test {
  @Test
  def resolvesFileScopedExtensionDescriptorThroughDescriptorOuterClass(): Unit = {
    val extension: GeneratedMessage.GeneratedExtension[Message, GeneratedMessageAnonymous4DescriptorOuter] =
      GeneratedMessage.newFileScopedGeneratedExtension[Message, GeneratedMessageAnonymous4DescriptorOuter](
        classOf[GeneratedMessageAnonymous4DescriptorOuter],
        null,
        classOf[GeneratedMessageAnonymous4DescriptorOuter].getName,
        GeneratedMessageAnonymous4Descriptors.ExtensionName
      )

    try {
      val descriptor: Descriptors.FieldDescriptor = extension.getDescriptor

      assertThat(descriptor.getName).isEqualTo(GeneratedMessageAnonymous4Descriptors.ExtensionName)
      assertThat(descriptor.getContainingType.getFullName).isEqualTo("google.protobuf.MessageOptions")
    } catch {
      case error: Error =>
        if (NativeImageSupport.isUnsupportedFeatureError(error)) {
          ()
        } else {
          throw error
        }
    }
  }
}

class GeneratedMessageAnonymous4DescriptorOuter

object GeneratedMessageAnonymous4DescriptorOuter {
  @static
  val descriptor: Descriptors.FileDescriptor = GeneratedMessageAnonymous4Descriptors.FileDescriptor
}

object GeneratedMessageAnonymous4Descriptors {
  val ExtensionName: String = "anonymous4_string_extension"

  val FileDescriptor: Descriptors.FileDescriptor = Descriptors.FileDescriptor.buildFrom(
    DescriptorProtos.FileDescriptorProto.newBuilder()
      .setName("generated_message_anonymous4_test.proto")
      .setPackage("generated_message_anonymous4")
      .setSyntax("proto2")
      .addDependency("google/protobuf/descriptor.proto")
      .addExtension(
        DescriptorProtos.FieldDescriptorProto.newBuilder()
          .setName(ExtensionName)
          .setNumber(51234)
          .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
          .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
          .setExtendee(".google.protobuf.MessageOptions")
          .build()
      )
      .build(),
    Array(DescriptorProtos.getDescriptor)
  )
}
