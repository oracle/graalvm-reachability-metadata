/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.upickle_implicits_3

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import upickle.core.Annotator
import upickle.core.ArrVisitor
import upickle.core.ObjVisitor
import upickle.core.Visitor
import upickle.implicits.allowUnknownKeys
import upickle.implicits.flatten
import upickle.implicits.key
import upickle.implicits.serializeDefaults

import java.util.UUID
import scala.collection.immutable.ListMap
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.Duration
import scala.concurrent.duration.MILLISECONDS

object MiniPickle extends upickle.core.Config with upickle.implicits.MacroImplicits {
  override def taggedExpectedMsg: String = "expected tagged object"

  override def annotate[V](rw: Reader[V], key: String, value: String, shortValue: String): TaggedReader[V] = {
    new TaggedReader.Leaf[V](
      key,
      objectTypeKeyReadMap(value).toString,
      objectTypeKeyReadMap(shortValue).toString,
      rw
    )
  }

  override def annotate[V](
      rw: ObjectWriter[V],
      key: String,
      value: String,
      shortValue: String,
      checker: Annotator.Checker
  ): TaggedWriter[V] = {
    new TaggedWriter.Leaf[V](checker, key, objectTypeKeyWriteMap(value).toString, rw)
  }

  override def taggedObjectContext[T](taggedReader: TaggedReader[T], index: Int): ObjVisitor[Any, T] = {
    new ObjVisitor[Any, T] {
      private var currentKey: String = ""
      private val values: ArrayBuffer[(String, Any)] = ArrayBuffer.empty

      override def subVisitor: Visitor[?, ?] = TreeVisitor

      override def visitKey(index: Int): Visitor[?, ?] = TreeVisitor

      override def visitKeyValue(v: Any): Unit = {
        currentKey = v.toString
      }

      override def visitValue(v: Any, index: Int): Unit = {
        values += currentKey -> v
      }

      override def visitEnd(index: Int): T = {
        val tree: ListMap[String, Any] = ListMap.from(values)
        val taggedValueAndReader: Option[(String, Reader[T])] = tree.valuesIterator.collectFirst {
          case tagValue: String if taggedReader.findReader(objectTypeKeyReadMap(tagValue).toString) != null =>
            tagValue -> taggedReader.findReader(objectTypeKeyReadMap(tagValue).toString)
        }
        val (_, concreteReader) = taggedValueAndReader.getOrElse {
          throw new upickle.core.Abort("invalid tag for tagged object")
        }
        TestTreeCodec.readTreeWith(tree, concreteReader)
      }
    }
  }

  override def taggedWrite[T, R](
      w: ObjectWriter[T],
      tagKey: String,
      tagValue: String,
      out: Visitor[?, R],
      v: T
  ): R = {
    val ctx: ObjVisitor[Any, R] = out.visitObject(w.length(v) + 1, jsonableKeys = true, -1).narrow
    val keyVisitor: Visitor[?, ?] = ctx.visitKey(-1)
    ctx.visitKeyValue(keyVisitor.visitString(tagKey, -1))
    ctx.visitValue(ctx.subVisitor.visitString(tagValue, -1), -1)
    w.writeToObject(ctx, v)
    ctx.visitEnd(-1)
  }
}

object TreeVisitor extends Visitor[Any, Any] {
  override def visitNull(index: Int): Any = null

  override def visitFalse(index: Int): Any = false

  override def visitTrue(index: Int): Any = true

  override def visitFloat64StringParts(s: CharSequence, decIndex: Int, expIndex: Int, index: Int): Any = {
    val text: String = s.toString
    if (decIndex == -1 && expIndex == -1) {
      try text.toInt
      catch {
        case _: NumberFormatException => text.toLong
      }
    } else {
      text.toDouble
    }
  }

  override def visitFloat64(d: Double, index: Int): Any = d

  override def visitFloat32(d: Float, index: Int): Any = d

  override def visitInt32(i: Int, index: Int): Any = i

  override def visitInt64(i: Long, index: Int): Any = i

  override def visitUInt64(i: Long, index: Int): Any = i

  override def visitFloat64String(s: String, index: Int): Any = s.toDouble

  override def visitString(s: CharSequence, index: Int): Any = s.toString

  override def visitChar(c: Char, index: Int): Any = c

  override def visitBinary(bytes: Array[Byte], offset: Int, len: Int, index: Int): Any = {
    bytes.slice(offset, offset + len)
  }

  override def visitExt(tag: Byte, bytes: Array[Byte], offset: Int, len: Int, index: Int): Any = {
    tag -> bytes.slice(offset, offset + len)
  }

  override def visitArray(length: Int, index: Int): ArrVisitor[Any, Any] = new ArrVisitor[Any, Any] {
    private val values: ArrayBuffer[Any] = ArrayBuffer.empty

    override def subVisitor: Visitor[?, ?] = TreeVisitor

    override def visitValue(v: Any, index: Int): Unit = {
      values += v
    }

    override def visitEnd(index: Int): Any = values.toVector

    override def isObj: Boolean = false
  }

  override def visitObject(length: Int, jsonableKeys: Boolean, index: Int): ObjVisitor[Any, Any] = {
    new ObjVisitor[Any, Any] {
      private var currentKey: String = ""
      private val values: ArrayBuffer[(String, Any)] = ArrayBuffer.empty

      override def subVisitor: Visitor[?, ?] = TreeVisitor

      override def visitKey(index: Int): Visitor[?, ?] = TreeVisitor

      override def visitKeyValue(v: Any): Unit = {
        currentKey = v.toString
      }

      override def visitValue(v: Any, index: Int): Unit = {
        values += currentKey -> v
      }

      override def visitEnd(index: Int): Any = ListMap.from(values)
    }
  }
}

object TestTreeCodec {
  def writeTree[T](value: T)(implicit writer: MiniPickle.Writer[T]): Any = {
    writer.write(TreeVisitor, value)
  }

  def readTree[T](tree: Any)(implicit reader: MiniPickle.Reader[T]): T = {
    readTreeWith(tree, reader)
  }

  def readTreeWith[T](tree: Any, reader: MiniPickle.Reader[T]): T = {
    feed(tree, reader).asInstanceOf[T]
  }

  def roundTrip[T](value: T)(implicit rw: MiniPickle.ReadWriter[T]): T = {
    readTreeWith(writeTree(value), rw)
  }

  private def feed(tree: Any, visitor: Visitor[?, ?]): Any = tree match {
    case null => visitor.visitNull(-1)
    case value: Boolean => if (value) visitor.visitTrue(-1) else visitor.visitFalse(-1)
    case value: Byte => visitor.visitInt32(value.toInt, -1)
    case value: Short => visitor.visitInt32(value.toInt, -1)
    case value: Int => visitor.visitInt32(value, -1)
    case value: Long => visitor.visitInt64(value, -1)
    case value: Float => visitor.visitFloat32(value, -1)
    case value: Double => visitor.visitFloat64(value, -1)
    case value: Char => visitor.visitChar(value, -1)
    case value: String => visitor.visitString(value, -1)
    case bytes: Array[Byte] => visitor.visitBinary(bytes, 0, bytes.length, -1)
    case values: collection.Map[?, ?] =>
      val ctx: ObjVisitor[Any, Any] = visitor.visitObject(values.size, jsonableKeys = true, -1).asInstanceOf[ObjVisitor[Any, Any]]
      values.foreach { case (key, value) =>
        ctx.visitKeyValue(feed(key.toString, ctx.visitKey(-1)))
        ctx.visitValue(feed(value, ctx.subVisitor), -1)
      }
      ctx.visitEnd(-1)
    case values: Iterable[?] =>
      val ctx: ArrVisitor[Any, Any] = visitor.visitArray(values.size, -1).asInstanceOf[ArrVisitor[Any, Any]]
      values.foreach { value =>
        ctx.visitValue(feed(value, ctx.subVisitor), -1)
      }
      ctx.visitEnd(-1)
    case other => throw new IllegalArgumentException("Unsupported test tree value: " + other)
  }
}

import MiniPickle.*

case class Meta(source: String, priority: Int)
object Meta {
  implicit val rw: MiniPickle.ReadWriter[Meta] = MiniPickle.macroRWAll[Meta]
}

case class Envelope(id: String, @flatten meta: Meta)
object Envelope {
  implicit val rw: MiniPickle.ReadWriter[Envelope] = MiniPickle.macroRWAll[Envelope]
}

case class Address(@key("street_name") street: String, zip: Int = 0)
object Address {
  implicit val rw: MiniPickle.ReadWriter[Address] = MiniPickle.macroRWAll[Address]
}

case class UserProfile(
    id: Long,
    name: String,
    address: Address,
    aliases: Vector[String],
    scores: Map[String, Int],
    preference: Option[String],
    result: Either[String, Int],
    active: Boolean = true
)
object UserProfile {
  implicit val rw: MiniPickle.ReadWriter[UserProfile] = MiniPickle.macroRWAll[UserProfile]
}

case class DefaultsExample(name: String, enabled: Boolean = true, count: Int = 7)
object DefaultsExample {
  implicit val rw: MiniPickle.ReadWriter[DefaultsExample] = MiniPickle.macroRWAll[DefaultsExample]
}

@serializeDefaults(true)
case class VerboseDefaultsExample(name: String = "default", enabled: Boolean = true, count: Int = 7)
object VerboseDefaultsExample {
  implicit val rw: MiniPickle.ReadWriter[VerboseDefaultsExample] = MiniPickle.macroRWAll[VerboseDefaultsExample]
}

@allowUnknownKeys(false)
case class StrictExample(name: String)
object StrictExample {
  implicit val rw: MiniPickle.ReadWriter[StrictExample] = MiniPickle.macroRWAll[StrictExample]
}

@key("kind")
sealed trait Command
@key("create")
case class CreateCommand(name: String, retries: Int = 1) extends Command
case class DeleteCommand(id: Long) extends Command
object CreateCommand {
  implicit val rw: MiniPickle.ReadWriter[CreateCommand] = MiniPickle.macroRWAll[CreateCommand]
}
object DeleteCommand {
  implicit val rw: MiniPickle.ReadWriter[DeleteCommand] = MiniPickle.macroRWAll[DeleteCommand]
}
object Command {
  implicit val rw: MiniPickle.ReadWriter[Command] = MiniPickle.macroRWAll[Command]
}

case object Heartbeat
implicit val heartbeatRw: MiniPickle.ReadWriter[Heartbeat.type] = MiniPickle.macroRWAll[Heartbeat.type]

class Upickle_implicits_3Test {
  import MiniPickle.*
  import TestTreeCodec.*

  @Test
  def primitiveCollectionsAndStandardTypesRoundTrip(): Unit = {
    val uuid: UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

    assertThat(roundTrip(42)).isEqualTo(42)
    assertThat(roundTrip("hello")).isEqualTo("hello")
    assertThat(roundTrip(true)).isEqualTo(true)
    assertThat(roundTrip(BigInt("12345678901234567890"))).isEqualTo(BigInt("12345678901234567890"))
    assertThat(roundTrip(BigDecimal("12345.6789"))).isEqualTo(BigDecimal("12345.6789"))
    assertThat(roundTrip(uuid)).isEqualTo(uuid)
    assertThat(roundTrip(Duration(250, MILLISECONDS))).isEqualTo(Duration(250, MILLISECONDS))
    assertThat(roundTrip(Option("present"))).isEqualTo(Some("present"))
    assertThat(roundTrip(Option.empty[String])).isEqualTo(None)
    assertThat(roundTrip[Either[String, Int]](Right(9))).isEqualTo(Right(9))
    assertThat(roundTrip[Either[String, Int]](Left("bad"))).isEqualTo(Left("bad"))
    assertThat(roundTrip(Vector(1, 2, 3))).isEqualTo(Vector(1, 2, 3))
    assertThat(roundTrip(Map("a" -> 1, "b" -> 2))).isEqualTo(Map("a" -> 1, "b" -> 2))
    assertThat(roundTrip(("tuple", 3, true))).isEqualTo(("tuple", 3, true))
    assertThat(roundTrip(Array[Byte](1, 2, 3)).toIndexedSeq).isEqualTo(IndexedSeq(1.toByte, 2.toByte, 3.toByte))
  }

  @Test
  def derivedCaseClassReadWriterHonorsKeysDefaultsAndNestedValues(): Unit = {
    val profile: UserProfile = UserProfile(
      id = 7L,
      name = "Ada",
      address = Address("Main Street"),
      aliases = Vector("a", "lovelace"),
      scores = Map("math" -> 100, "logic" -> 99),
      preference = Some("dark"),
      result = Right(10)
    )

    val tree: ListMap[String, Any] = writeTree(profile).asInstanceOf[ListMap[String, Any]]
    val addressTree: ListMap[String, Any] = tree("address").asInstanceOf[ListMap[String, Any]]

    assertThat(tree.contains("active")).isFalse
    assertThat(addressTree("street_name")).isEqualTo("Main Street")
    assertThat(addressTree.contains("zip")).isFalse
    assertThat(roundTrip(profile)).isEqualTo(profile)
  }

  @Test
  def readerUsesConstructorDefaultsAndHandlesUnknownKeysAccordingToAnnotation(): Unit = {
    val decoded: DefaultsExample = readTree[DefaultsExample](
      ListMap("name" -> "configured", "ignored" -> 123)
    )

    assertThat(decoded).isEqualTo(DefaultsExample("configured"))
    assertThatThrownBy(() => readTree[StrictExample](ListMap("name" -> "strict", "ignored" -> true)))
      .isInstanceOf(classOf[Exception])
  }

  @Test
  def serializeDefaultsAnnotationWritesDefaultValuedFields(): Unit = {
    val value: VerboseDefaultsExample = VerboseDefaultsExample()
    val tree: ListMap[String, Any] = writeTree(value).asInstanceOf[ListMap[String, Any]]

    assertThat(tree("name")).isEqualTo("default")
    assertThat(tree("enabled")).isEqualTo(true)
    assertThat(tree("count")).isEqualTo(7)
    assertThat(readTree[VerboseDefaultsExample](tree)).isEqualTo(value)
  }

  @Test
  def flattenedCaseClassFieldsAreWrittenAtParentLevelAndReadBack(): Unit = {
    val envelope: Envelope = Envelope("evt-1", Meta("sensor", 5))
    val tree: ListMap[String, Any] = writeTree(envelope).asInstanceOf[ListMap[String, Any]]

    assertThat(tree("id")).isEqualTo("evt-1")
    assertThat(tree("source")).isEqualTo("sensor")
    assertThat(tree("priority")).isEqualTo(5)
    assertThat(tree.contains("meta")).isFalse
    assertThat(readTree[Envelope](tree)).isEqualTo(envelope)
  }

  @Test
  def singletonCaseObjectDerivationWritesReadableObjectAndReadsSameInstance(): Unit = {
    val tree: ListMap[String, Any] = writeTree(Heartbeat).asInstanceOf[ListMap[String, Any]]

    assertThat(readTree[Heartbeat.type](tree)).isSameAs(Heartbeat)
    assertThat(roundTrip(Heartbeat)).isSameAs(Heartbeat)
  }

  @Test
  def sealedTraitDerivationWritesAndReadsConfiguredTypeTags(): Unit = {
    val command: Command = CreateCommand("index", retries = 3)
    val tree: ListMap[String, Any] = writeTree(command).asInstanceOf[ListMap[String, Any]]

    assertThat(tree("kind")).isEqualTo("create")
    assertThat(tree("name")).isEqualTo("index")
    assertThat(tree("retries")).isEqualTo(3)
    assertThat(readTree[Command](tree)).isEqualTo(command)

    val delete: Command = DeleteCommand(99L)
    assertThat(roundTrip(delete)).isEqualTo(delete)
  }
}
