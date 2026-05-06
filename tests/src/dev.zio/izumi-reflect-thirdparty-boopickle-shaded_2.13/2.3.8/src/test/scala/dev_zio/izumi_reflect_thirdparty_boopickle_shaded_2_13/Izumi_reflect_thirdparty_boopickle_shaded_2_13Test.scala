/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package izumi.reflect.boopickle_shaded_tests

import izumi.reflect.thirdparty.internal.boopickle.BasicPicklers
import izumi.reflect.thirdparty.internal.boopickle.BufferPool
import izumi.reflect.thirdparty.internal.boopickle.CompositePickler
import izumi.reflect.thirdparty.internal.boopickle.DecoderSize
import izumi.reflect.thirdparty.internal.boopickle.EncoderSize
import izumi.reflect.thirdparty.internal.boopickle.NoMacro
import izumi.reflect.thirdparty.internal.boopickle.PickleImpl
import izumi.reflect.thirdparty.internal.boopickle.PickleState
import izumi.reflect.thirdparty.internal.boopickle.Pickler
import izumi.reflect.thirdparty.internal.boopickle.StringCodec
import izumi.reflect.thirdparty.internal.boopickle.UnpickleState
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.ThrowableAssert.ThrowingCallable
import org.junit.jupiter.api.Test

import java.nio.ByteBuffer
import java.nio.ByteOrder

final class Izumi_reflect_thirdparty_boopickle_shaded_2_13Test {
  import NoMacro.{PickleState => _, Pickler => _, _}

  @Test
  def primitivePicklersRoundTripBoundaryValuesAndStrings(): Unit = {
    val integers = List(
      Int.MinValue,
      -268435456,
      -268435455,
      -1048576,
      -1048575,
      -4096,
      -4095,
      -1,
      0,
      1,
      127,
      128,
      4095,
      4096,
      1048575,
      1048576,
      268435455,
      268435456,
      Int.MaxValue
    )
    integers.foreach(value => assertThat(roundTrip(value)).isEqualTo(value))

    assertThat(roundTrip(true)).isTrue()
    assertThat(roundTrip(false)).isFalse()

    val strings = List(
      "",
      "plain ascii",
      "Greek λ and Cyrillic Ж",
      "high code units \uE123\uFFFF",
      "surrogate pair \uD83D\uDE80",
      (1 to 700).map(i => ('a' + (i % 26)).toChar).mkString
    )
    strings.foreach(value => assertThat(roundTrip(value)).isEqualTo(value))

    val nullString: String = null
    assertThat(roundTrip(nullString)).isNull()
  }

  @Test
  def optionTupleIterableAndMapPicklersPreserveNestedValues(): Unit = {
    val nestedOptions = List(Some(1), None, Some(4096), Some(Int.MinValue))
    assertThat(roundTrip(nestedOptions)).isEqualTo(nestedOptions)

    val tuple: (Option[String], List[String]) = (Some("left"), List("", "ascii", "λ", "\uE123"))
    assertThat(roundTrip(tuple)).isEqualTo(tuple)

    val map = Map("one" -> 1, "large" -> 268435456, "negative" -> -4096)
    assertThat(roundTrip(map)).isEqualTo(map)

    val nullOption: Option[String] = null
    assertThat(roundTrip(nullOption)).isNull()

    val nullList: List[Int] = null
    assertThat(roundTrip(nullList)).isNull()

    val nullMap: Map[String, Int] = null
    assertThat(roundTrip(nullMap)).isNull()
  }

  @Test
  def pickleStateCanEncodeMultipleValuesIntoOneBuffer(): Unit = {
    val optionalTuple: Option[(Int, String)] = Some((7, "tuple"))
    implicit val state: PickleState = PickleState.pickleStateSpeed
    state.pickle(42)
      .pickle("shared")
      .pickle("shared")
      .pickle(optionalTuple)
      .pickle(List(1, 2, 3))

    implicit val unpickleState: UnpickleState = UnpickleState(state.toByteBuffer)
    assertThat(unpickleState.unpickle[Int]).isEqualTo(42)
    assertThat(unpickleState.unpickle[String]).isEqualTo("shared")
    assertThat(unpickleState.unpickle[String]).isEqualTo("shared")
    assertThat(unpickleState.unpickle[Option[(Int, String)]]).isEqualTo(Some((7, "tuple")))
    assertThat(unpickleState.unpickle[List[Int]]).isEqualTo(List(1, 2, 3))
  }

  @Test
  def repeatedStringsAreDeduplicatedWithinASharedUnpickleState(): Unit = {
    val repeated = new String("shared immutable λ".toCharArray)
    implicit val state: PickleState = PickleState.pickleStateSpeed
    state.pickle(repeated)
      .pickle(repeated)
      .pickle("different")
      .pickle(repeated)

    implicit val unpickleState: UnpickleState = UnpickleState(state.toByteBuffer)
    val first = unpickleState.unpickle[String]
    val second = unpickleState.unpickle[String]
    val different = unpickleState.unpickle[String]
    val third = unpickleState.unpickle[String]

    assertThat(first).isEqualTo(repeated)
    assertThat(second).isEqualTo(repeated)
    assertThat(different).isEqualTo("different")
    assertThat(third).isEqualTo(repeated)
    assertThat(first.asInstanceOf[AnyRef]).isSameAs(second.asInstanceOf[AnyRef])
    assertThat(first.asInstanceOf[AnyRef]).isSameAs(third.asInstanceOf[AnyRef])
    assertThat(first.asInstanceOf[AnyRef]).isNotSameAs(different.asInstanceOf[AnyRef])
  }

  @Test
  def xmapBuildsDomainPicklersWithoutMacrosOrReflection(): Unit = {
    implicit val addressPickler: Pickler[Address] = implicitly[Pickler[(String, Int)]].xmap {
      case (street, number) => Address(street, number)
    }(address => (address.street, address.number))
    implicit val customerPickler: Pickler[Customer] = implicitly[Pickler[(String, Address)]].xmap {
      case (name, address) => Customer(name, address)
    }(customer => (customer.name, customer.address))

    val customer = Customer("Ada", Address("Main λ", 128))
    assertThat(roundTrip(customer)).isEqualTo(customer)
  }

  @Test
  def compositePicklerEncodesKnownConcreteTypesAndRejectsUnknownOnes(): Unit = {
    implicit val circlePickler: Pickler[Circle] = implicitly[Pickler[Int]].xmap(Circle.apply)(_.radius)
    implicit val labelPickler: Pickler[Label] = implicitly[Pickler[String]].xmap(Label.apply)(_.text)

    val shapePickler = new CompositePickler[Shape]
      .addConcreteType[Circle]
      .addConcreteType[Label]

    assertThat(roundTrip[Shape](Circle(5))(shapePickler)).isEqualTo(Circle(5))
    assertThat(roundTrip[Shape](Label("north"))(shapePickler)).isEqualTo(Label("north"))
    assertThat(roundTrip[Shape](null.asInstanceOf[Shape])(shapePickler)).isNull()

    assertThatThrownBy(new ThrowingCallable {
      override def call(): Unit = {
        new CompositePickler[Shape].addConcreteType[Circle].addConcreteType[Circle]
        ()
      }
    }).isInstanceOf(classOf[IllegalArgumentException])
      .hasMessageContaining("Cannot add same class")

    assertThatThrownBy(new ThrowingCallable {
      override def call(): Unit = {
        roundTrip[Shape](Label("missing"))(new CompositePickler[Shape].addConcreteType[Circle])
        ()
      }
    }).isInstanceOf(classOf[IllegalArgumentException])
      .hasMessageContaining("doesn't know class")
  }

  @Test
  def encoderAndDecoderHandleCompactIntegerEncodingsAndRawFields(): Unit = {
    val values = List(
      0,
      1,
      127,
      128,
      4095,
      4096,
      1048575,
      1048576,
      268435455,
      268435456,
      -1,
      -4095,
      -4096,
      -1048575,
      -1048576,
      -268435455,
      -268435456,
      Int.MinValue,
      Int.MaxValue
    )
    val text = "codec λ \uE123"
    val encoder = new EncoderSize()
    values.foreach(encoder.writeInt)
    encoder.writeByte(0x2a.toByte)
    encoder.writeString(text)

    val decoder = new DecoderSize(encoder.asByteBuffer)
    values.foreach(value => assertThat(decoder.readInt).isEqualTo(value))
    assertThat(decoder.readByte).isEqualTo(0x2a.toByte)
    assertThat(decoder.readString).isEqualTo(text)
  }

  @Test
  def stringCodecSupportsUtfEncodingsAndArrayOrDirectFastBuffers(): Unit = {
    val value = "ascii λ Ж \uE123 \uD83D\uDE80"

    val utf8 = StringCodec.encodeUTF8(value)
    assertThat(StringCodec.decodeUTF8(utf8.remaining(), utf8)).isEqualTo(value)

    val utf16 = StringCodec.encodeUTF16(value)
    assertThat(StringCodec.decodeUTF16(utf16.remaining(), utf16)).isEqualTo(value)

    val heap = ByteBuffer.allocate(value.length * 3).order(ByteOrder.LITTLE_ENDIAN)
    StringCodec.encodeFast(value, heap)
    heap.flip()
    assertThat(StringCodec.decodeFast(value.length, heap)).isEqualTo(value)

    val direct = ByteBuffer.allocateDirect(value.length * 3).order(ByteOrder.LITTLE_ENDIAN)
    StringCodec.encodeFast(value, direct)
    direct.flip()
    assertThat(StringCodec.decodeFast(value.length, direct)).isEqualTo(value)
  }

  @Test
  def bufferPoolCanBeEnabledDisabledAndReusesReleasedHeapBuffers(): Unit = {
    val originallyDisabled = BufferPool.isDisabled
    try {
      BufferPool.enable()
      val released = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
      BufferPool.release(released)

      val allocated = BufferPool.allocate(128)
      assertThat(allocated.isDefined).isTrue()
      assertThat(allocated.get.capacity()).isGreaterThanOrEqualTo(512)
      assertThat(allocated.get.order()).isEqualTo(ByteOrder.LITTLE_ENDIAN)
      BufferPool.release(allocated.get)

      BufferPool.disable()
      assertThat(BufferPool.isDisabled).isTrue()
      assertThat(BufferPool.allocate(128).isEmpty).isTrue()
      assertThat(BufferPool.allocateDirect(128).isEmpty).isTrue()
    } finally {
      if (originallyDisabled) {
        BufferPool.disable()
      } else {
        BufferPool.enable()
      }
    }
  }

  @Test
  def invalidEncodingsSurfaceClearExceptions(): Unit = {
    assertThatThrownBy(new ThrowingCallable {
      override def call(): Unit = decodeSingle(ByteBuffer.wrap(Array[Byte](2)), BasicPicklers.BooleanPickler)
    }).isInstanceOf(classOf[IllegalArgumentException])
      .hasMessageContaining("Invalid value 2 for Boolean")

    assertThatThrownBy(new ThrowingCallable {
      override def call(): Unit = decodeSingle(ByteBuffer.wrap(Array[Byte](3)), BasicPicklers.OptionPickler[Int])
    }).isInstanceOf(classOf[IllegalArgumentException])
      .hasMessageContaining("Invalid coding for Option type")

    assertThatThrownBy(new ThrowingCallable {
      override def call(): Unit = new DecoderSize(ByteBuffer.wrap(Array(0xf0.toByte))).readInt
    }).isInstanceOf(classOf[IllegalArgumentException])
      .hasMessageContaining("Unknown integer coding")
  }

  private def roundTrip[A](value: A)(implicit pickler: Pickler[A]): A = {
    implicit val pickleState: PickleState = PickleState.pickleStateSpeed
    val bytes = PickleImpl.intoBytes(value)
    implicit val unpickleState: UnpickleState = UnpickleState(bytes)
    unpickleState.unpickle[A]
  }

  private def decodeSingle[A](bytes: ByteBuffer, pickler: Pickler[A]): A = {
    implicit val unpickleState: UnpickleState = UnpickleState(bytes)
    pickler.unpickle
  }

  private final case class Address(street: String, number: Int)

  private final case class Customer(name: String, address: Address)

  private sealed trait Shape

  private final case class Circle(radius: Int) extends Shape

  private final case class Label(text: String) extends Shape
}
