/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_3

import akka.protobufv3.internal.DescriptorProtos.DescriptorProto
import akka.protobufv3.internal.DescriptorProtos.FieldDescriptorProto
import akka.protobufv3.internal.DescriptorProtos.FileDescriptorProto
import akka.protobufv3.internal.Descriptors.FieldDescriptor
import akka.protobufv3.internal.Descriptors.FileDescriptor
import akka.protobufv3.internal.GeneratedMessage
import akka.protobufv3.internal.Message
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

import scala.annotation.static

class GeneratedMessageAnonymous4Test {
  @Test
  def fileScopedExtensionLoadsDescriptorThroughGeneratedDescriptorClassName(): Unit = {
    val extension: GeneratedMessage.GeneratedExtension[Message, GeneratedMessageAnonymous4DescriptorOuter] =
      GeneratedMessage.newFileScopedGeneratedExtension(
        classOf[GeneratedMessageAnonymous4DescriptorOuter],
        null,
        classOf[GeneratedMessageAnonymous4DescriptorOuter].getName,
        "covered_extension"
      )

    val descriptor: FieldDescriptor = extension.getDescriptor

    assertEquals("covered_extension", descriptor.getName)
    assertEquals(100, descriptor.getNumber)
    assertEquals(FieldDescriptor.JavaType.STRING, descriptor.getJavaType)
    assertSame(GeneratedMessageAnonymous4DescriptorOuter.descriptor, descriptor.getFile)
  }
}

final class GeneratedMessageAnonymous4DescriptorOuter private ()

object GeneratedMessageAnonymous4DescriptorOuter {
  @static val descriptor: FileDescriptor = FileDescriptor.buildFrom(
    FileDescriptorProto
      .newBuilder()
      .setName("coverage/generated_message_anonymous4.proto")
      .setPackage("coverage.generatedmessageanonymous4")
      .addMessageType(
        DescriptorProto
          .newBuilder()
          .setName("ExtensibleMessage")
          .addExtensionRange(
            DescriptorProto.ExtensionRange
              .newBuilder()
              .setStart(100)
              .setEnd(536870912)
          )
      )
      .addExtension(
        FieldDescriptorProto
          .newBuilder()
          .setName("covered_extension")
          .setNumber(100)
          .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
          .setType(FieldDescriptorProto.Type.TYPE_STRING)
          .setExtendee(".coverage.generatedmessageanonymous4.ExtensibleMessage")
      )
      .build(),
    Array.empty[FileDescriptor]
  )
}
