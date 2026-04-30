/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import org.apache.pekko.protobufv3.internal.CodedInputStream
import org.apache.pekko.protobufv3.internal.DescriptorProtos.DescriptorProto
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FieldDescriptorProto
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FieldDescriptorProto.Label
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FieldDescriptorProto.Type
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FileDescriptorProto
import org.apache.pekko.protobufv3.internal.DescriptorProtos.OneofDescriptorProto
import org.apache.pekko.protobufv3.internal.Descriptors.Descriptor
import org.apache.pekko.protobufv3.internal.Descriptors.FileDescriptor
import org.apache.pekko.protobufv3.internal.ExtensionRegistryLite
import org.apache.pekko.protobufv3.internal.GeneratedMessageV3
import org.apache.pekko.protobufv3.internal.Message
import org.apache.pekko.protobufv3.internal.Parser
import org.apache.pekko.protobufv3.internal.UnknownFieldSet
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable

import java.util.Collections
import java.util.Objects
import scala.annotation.static

class DescriptorMessageInfoFactoryTest {
  @Test
  def buildsSchemaForRepeatedMessageField(): Unit = {
    assertDoesNotThrow(new Executable {
      override def execute(): Unit = RepeatedMessageHost.newMutable().parseEmptyInputWithGeneratedMessageSchema()
    })

    assertSame(RepeatedMessageChild.getDefaultInstance(), RepeatedMessageHost.getDefaultInstance().getChildren(0))
  }

  @Test
  def buildsSchemaForMessageOneofField(): Unit = {
    assertDoesNotThrow(new Executable {
      override def execute(): Unit = OneofMessageHost.newMutable().parseEmptyInputWithGeneratedMessageSchema()
    })

    assertSame(OneofMessageChild.getDefaultInstance(), OneofMessageHost.getDefaultInstance().getChild())
  }
}

abstract class SchemaBackedGeneratedMessage extends GeneratedMessageV3 {
  final def parseEmptyInputWithGeneratedMessageSchema(): Unit = {
    val input: CodedInputStream = CodedInputStream.newInstance(Array.emptyByteArray)
    mergeFromAndMakeImmutableInternal(input, ExtensionRegistryLite.getEmptyRegistry)
  }

  override def getParserForType(): Parser[_ <: GeneratedMessageV3] = null

  override def newBuilderForType(): Message.Builder = null

  override def toBuilder(): Message.Builder = null

  override protected def newBuilderForType(parent: GeneratedMessageV3.BuilderParent): Message.Builder = null

  override def getUnknownFields(): UnknownFieldSet = UnknownFieldSet.getDefaultInstance

  override def getDefaultInstanceForType(): Message = this

  override def equals(other: Any): Boolean = other.asInstanceOf[AnyRef] eq this

  override def hashCode(): Int = Objects.hash(getDescriptorForType.getFullName)
}

class RepeatedMessageChild private () extends SchemaBackedGeneratedMessage {
  override protected def internalGetFieldAccessorTable(): GeneratedMessageV3.FieldAccessorTable =
    RepeatedMessageChild.FieldAccessorTable
}

object RepeatedMessageChild {
  val FieldAccessorTable: GeneratedMessageV3.FieldAccessorTable =
    new GeneratedMessageV3.FieldAccessorTable(RepeatedMessageHost.ChildDescriptorForTests, Array.empty)

  private val DefaultInstance: RepeatedMessageChild = new RepeatedMessageChild()

  @static def getDefaultInstance(): RepeatedMessageChild = DefaultInstance
}

class RepeatedMessageHost private () extends SchemaBackedGeneratedMessage {
  var children_ : java.util.List[RepeatedMessageChild] = Collections.emptyList()

  def getChildren(index: Int): RepeatedMessageChild = RepeatedMessageChild.getDefaultInstance()

  override protected def internalGetFieldAccessorTable(): GeneratedMessageV3.FieldAccessorTable =
    RepeatedMessageHost.FieldAccessorTable
}

object RepeatedMessageHost {
  private val File: FileDescriptor = {
    val childType: DescriptorProto = DescriptorProto.newBuilder()
      .setName("RepeatedMessageChild")
      .build()
    val childrenField: FieldDescriptorProto = FieldDescriptorProto.newBuilder()
      .setName("children")
      .setNumber(1)
      .setLabel(Label.LABEL_REPEATED)
      .setType(Type.TYPE_MESSAGE)
      .setTypeName(".dynamicaccess.RepeatedMessageChild")
      .build()
    val hostType: DescriptorProto = DescriptorProto.newBuilder()
      .setName("RepeatedMessageHost")
      .addField(childrenField)
      .build()
    val fileProto: FileDescriptorProto = FileDescriptorProto.newBuilder()
      .setName("repeated_message_host.proto")
      .setPackage("dynamicaccess")
      .setSyntax("proto3")
      .addMessageType(childType)
      .addMessageType(hostType)
      .build()

    FileDescriptor.buildFrom(fileProto, Array.empty[FileDescriptor])
  }

  private val HostDescriptor: Descriptor = File.findMessageTypeByName("RepeatedMessageHost")
  val ChildDescriptorForTests: Descriptor = File.findMessageTypeByName("RepeatedMessageChild")
  val FieldAccessorTable: GeneratedMessageV3.FieldAccessorTable =
    new GeneratedMessageV3.FieldAccessorTable(HostDescriptor, Array("Children"))

  private val DefaultInstance: RepeatedMessageHost = new RepeatedMessageHost()

  @static def getDefaultInstance(): RepeatedMessageHost = DefaultInstance

  def newMutable(): RepeatedMessageHost = new RepeatedMessageHost()
}

class OneofMessageChild private () extends SchemaBackedGeneratedMessage {
  override protected def internalGetFieldAccessorTable(): GeneratedMessageV3.FieldAccessorTable =
    OneofMessageChild.FieldAccessorTable
}

object OneofMessageChild {
  val FieldAccessorTable: GeneratedMessageV3.FieldAccessorTable =
    new GeneratedMessageV3.FieldAccessorTable(OneofMessageHost.ChildDescriptorForTests, Array.empty)

  private val DefaultInstance: OneofMessageChild = new OneofMessageChild()

  @static def getDefaultInstance(): OneofMessageChild = DefaultInstance
}

class OneofMessageHost private () extends SchemaBackedGeneratedMessage {
  var kindCase_ : Int = 0
  var kind_ : AnyRef = OneofMessageChild.getDefaultInstance()

  def getChild(): OneofMessageChild = OneofMessageChild.getDefaultInstance()

  override protected def internalGetFieldAccessorTable(): GeneratedMessageV3.FieldAccessorTable =
    OneofMessageHost.FieldAccessorTable
}

object OneofMessageHost {
  private val File: FileDescriptor = {
    val childType: DescriptorProto = DescriptorProto.newBuilder()
      .setName("OneofMessageChild")
      .build()
    val childField: FieldDescriptorProto = FieldDescriptorProto.newBuilder()
      .setName("child")
      .setNumber(1)
      .setLabel(Label.LABEL_OPTIONAL)
      .setType(Type.TYPE_MESSAGE)
      .setTypeName(".dynamicaccess.OneofMessageChild")
      .setOneofIndex(0)
      .build()
    val nameField: FieldDescriptorProto = FieldDescriptorProto.newBuilder()
      .setName("name")
      .setNumber(2)
      .setLabel(Label.LABEL_OPTIONAL)
      .setType(Type.TYPE_STRING)
      .setOneofIndex(0)
      .build()
    val hostType: DescriptorProto = DescriptorProto.newBuilder()
      .setName("OneofMessageHost")
      .addOneofDecl(OneofDescriptorProto.newBuilder().setName("kind"))
      .addField(childField)
      .addField(nameField)
      .build()
    val fileProto: FileDescriptorProto = FileDescriptorProto.newBuilder()
      .setName("oneof_message_host.proto")
      .setPackage("dynamicaccess")
      .setSyntax("proto3")
      .addMessageType(childType)
      .addMessageType(hostType)
      .build()

    FileDescriptor.buildFrom(fileProto, Array.empty[FileDescriptor])
  }

  private val HostDescriptor: Descriptor = File.findMessageTypeByName("OneofMessageHost")
  val ChildDescriptorForTests: Descriptor = File.findMessageTypeByName("OneofMessageChild")
  val FieldAccessorTable: GeneratedMessageV3.FieldAccessorTable =
    new GeneratedMessageV3.FieldAccessorTable(HostDescriptor, Array("Child", "Name"))

  private val DefaultInstance: OneofMessageHost = new OneofMessageHost()

  @static def getDefaultInstance(): OneofMessageHost = DefaultInstance

  def newMutable(): OneofMessageHost = new OneofMessageHost()
}
