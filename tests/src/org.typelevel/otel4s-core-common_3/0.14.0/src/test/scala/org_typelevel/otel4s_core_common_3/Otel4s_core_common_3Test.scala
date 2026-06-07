/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.otel4s_core_common_3

import cats.Hash
import cats.Id
import cats.Monoid
import cats.~>
import cats.data.EitherT
import cats.data.Ior
import cats.data.IorT
import cats.data.Kleisli
import cats.data.OptionT
import cats.data.WriterT
import cats.effect.SyncIO
import cats.effect.kernel.Resource
import cats.syntax.show._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.typelevel.otel4s.AnyValue
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.AttributeKey
import org.typelevel.otel4s.AttributeType
import org.typelevel.otel4s.Attributes
import org.typelevel.otel4s.KindTransformer
import org.typelevel.otel4s.context.propagation.ContextPropagators
import org.typelevel.otel4s.context.propagation.TextMapGetter
import org.typelevel.otel4s.context.propagation.TextMapPropagator
import org.typelevel.otel4s.context.propagation.TextMapUpdater

import scala.collection.immutable.SortedMap

class Otel4s_core_common_3Test {
  @Test
  def attributesKeepNamesTypesValuesAndCatsInstances(): Unit = {
    val stringKey: AttributeKey[String] = AttributeKey.string("http.method")
    val booleanKey: AttributeKey[Boolean] = AttributeKey.boolean("feature.enabled")
    val longKey: AttributeKey[Long] = AttributeKey.long("http.status_code")
    val doubleKey: AttributeKey[Double] = AttributeKey.double("request.duration")
    val stringSeqKey: AttributeKey[Seq[String]] = AttributeKey.stringSeq("http.route")
    val booleanSeqKey: AttributeKey[Seq[Boolean]] = AttributeKey.booleanSeq("flags")
    val longSeqKey: AttributeKey[Seq[Long]] = AttributeKey.longSeq("retry.counts")
    val doubleSeqKey: AttributeKey[Seq[Double]] = AttributeKey.doubleSeq("bucket.bounds")

    assertEquals("http.method", stringKey.name)
    assertEquals(AttributeType.String, stringKey.`type`)
    assertEquals(AttributeType.Boolean, booleanKey.`type`)
    assertEquals(AttributeType.Long, longKey.`type`)
    assertEquals(AttributeType.Double, doubleKey.`type`)
    assertEquals(AttributeType.StringSeq, stringSeqKey.`type`)
    assertEquals(AttributeType.BooleanSeq, booleanSeqKey.`type`)
    assertEquals(AttributeType.LongSeq, longSeqKey.`type`)
    assertEquals(AttributeType.DoubleSeq, doubleSeqKey.`type`)

    val directAttribute: Attribute[String] = stringKey("GET")
    val selectedAttribute: Attribute[String] = Attribute("http.method", "GET")
    val seqAttribute: Attribute[Seq[Long]] = Attribute("retry.counts", Seq(1L, 2L, 3L))

    assertEquals(selectedAttribute, directAttribute)
    assertEquals(Seq(1L, 2L, 3L), seqAttribute.value)
    assertEquals("String(http.method)", stringKey.show)
    assertEquals("String(http.method)=GET", selectedAttribute.asInstanceOf[Attribute[_]].show)

    val sameStringKey: AttributeKey[String] = AttributeKey.string("http.method")
    val sameNameDifferentType: AttributeKey[Long] = AttributeKey.long("http.method")
    assertEquals(stringKey, sameStringKey)
    assertEquals(Hash[AttributeKey[String]].hash(stringKey), Hash[AttributeKey[String]].hash(sameStringKey))
    assertNotEquals(stringKey, sameNameDifferentType)
    assertTrue(Hash[Attribute[String]].eqv(directAttribute, selectedAttribute))
    assertTrue(Hash[Attribute[_]].eqv(directAttribute, selectedAttribute))
  }

  @Test
  def textMapGetterAndUpdaterSupportStandardAndAdaptedCarriers(): Unit = {
    val mapCarrier: Map[String, String] = Map("traceparent" -> "00-abc", "baggage" -> "user=alice")
    val mapGetter: TextMapGetter[Map[String, String]] = TextMapGetter[Map[String, String]]
    val mapUpdater: TextMapUpdater[Map[String, String]] = TextMapUpdater[Map[String, String]]

    assertEquals(Some("00-abc"), mapGetter.get(mapCarrier, "traceparent"))
    assertEquals(None, mapGetter.get(mapCarrier, "missing"))
    assertEquals(Set("traceparent", "baggage"), mapGetter.keys(mapCarrier).toSet)
    assertEquals(Map("traceparent" -> "00-def"), mapUpdater.updated(Map.empty, "traceparent", "00-def"))

    val sortedCarrier: SortedMap[String, String] = SortedMap("a" -> "1")
    val sortedUpdater: TextMapUpdater[SortedMap[String, String]] = TextMapUpdater[SortedMap[String, String]]
    assertEquals(SortedMap("a" -> "1", "b" -> "2"), sortedUpdater.updated(sortedCarrier, "b", "2"))

    val seqCarrier: Vector[(String, String)] = Vector("a" -> "first", "a" -> "second", "b" -> "third")
    val seqGetter: TextMapGetter[Vector[(String, String)]] = TextMapGetter[Vector[(String, String)]]
    val seqUpdater: TextMapUpdater[Vector[(String, String)]] = TextMapUpdater[Vector[(String, String)]]
    assertEquals(Some("first"), seqGetter.get(seqCarrier, "a"))
    assertEquals(List("a", "b"), seqGetter.keys(seqCarrier).toList)
    assertEquals(Vector("a" -> "first", "a" -> "second", "b" -> "third", "c" -> "fourth"), seqUpdater.updated(seqCarrier, "c", "fourth"))

    val boxedCarrier: HeaderCarrier = HeaderCarrier(Map("x-request-id" -> "request-1"))
    val boxedGetter: TextMapGetter[HeaderCarrier] =
      TextMapGetter.contravariant.contramap(mapGetter)(_.entries)
    val boxedUpdater: TextMapUpdater[HeaderCarrier] =
      TextMapUpdater.invariant.imap(mapUpdater)(HeaderCarrier.apply)(_.entries)

    assertEquals(Some("request-1"), boxedGetter.get(boxedCarrier, "x-request-id"))
    assertEquals(HeaderCarrier(Map("x-request-id" -> "request-2")), boxedUpdater.updated(boxedCarrier, "x-request-id", "request-2"))
  }

  @Test
  def attributesCollectionBuildsDeduplicatesAndUsesTypeAwareLookup(): Unit = {
    final case class User(id: Int, group: String)

    implicit val userAttributes: Attributes.Make[User] = user =>
      Attributes(Attribute.from[Int, Long]("user.id", user.id), Attribute("user.group", user.group))

    val methodKey: AttributeKey[String] = AttributeKey.string("http.method")
    val statusKey: AttributeKey[Long] = AttributeKey.long("http.status_code")
    val initial: Attributes = Attributes(methodKey("GET"), statusKey(200))
    val replaced: Attributes = initial.added(methodKey, "POST")
    val combined: Attributes = replaced ++ Attributes.from(User(7, "admin"))

    assertEquals(4, combined.size)
    assertEquals(Some("POST"), combined.get(methodKey).map(_.value))
    assertEquals(Some(200L), combined.get[Long]("http.status_code").map(_.value))
    assertEquals(None, combined.get[String]("http.status_code"))
    assertEquals(Some(7L), combined.get[Long]("user.id").map(_.value))
    assertEquals(Some("admin"), combined.get[String]("user.group").map(_.value))
    assertTrue(combined.show.contains("String(user.group)=admin"))
    assertEquals(combined, Attributes.newBuilder.addAll(combined).result())
    assertEquals(combined, Monoid[Attributes].combine(Attributes.empty, combined))
  }

  @Test
  def anyValuesPreserveNestedValuesEqualityAndRendering(): Unit = {
    val bytes: Array[Byte] = Array[Byte](1, 2, 3)
    val byteValue: AnyValue.ByteArrayValue = AnyValue.bytes(bytes)
    bytes(0) = 9

    val nested: AnyValue = AnyValue.map(
      Map(
        "name" -> AnyValue.string("alice"),
        "active" -> AnyValue.boolean(true),
        "score" -> AnyValue.double(4.5d),
        "ids" -> AnyValue.seq(List(AnyValue.long(1L), byteValue, AnyValue.empty))
      )
    )
    val sameNested: AnyValue = AnyValue.map(
      Map(
        "name" -> AnyValue.string("alice"),
        "active" -> AnyValue.boolean(true),
        "score" -> AnyValue.double(4.5d),
        "ids" -> AnyValue.seq(List(AnyValue.long(1L), AnyValue.bytes(Array[Byte](1, 2, 3)), AnyValue.empty))
      )
    )

    assertEquals(nested, sameNested)
    assertEquals(Hash[AnyValue].hash(nested), Hash[AnyValue].hash(sameNested))
    assertTrue(byteValue.value.isReadOnly)
    assertEquals(1.toByte, byteValue.value.get(0))
    assertTrue(nested.show.contains("MapValue"))
    assertTrue(nested.show.contains("ByteArrayValue(AQID)"))
  }

  @Test
  def textMapPropagatorsComposeExtractionInjectionAndFieldsInOrder(): Unit = {
    val first: TextMapPropagator[List[String]] = RecordingPropagator("traceparent", "trace", "out-trace")
    val second: TextMapPropagator[List[String]] = RecordingPropagator("baggage", "bag", "out-bag")
    val duplicateField: TextMapPropagator[List[String]] = RecordingPropagator("traceparent", "trace-again", "out-trace-again")

    val single: TextMapPropagator[List[String]] = TextMapPropagator.of(first)
    assertSame(first, single)

    val combined: TextMapPropagator[List[String]] = TextMapPropagator.of(first, second, duplicateField)
    val extracted: List[String] = combined.extract(Nil, Map("traceparent" -> "in", "baggage" -> "items"))
    val injected: Map[String, String] = combined.inject(extracted, Map.empty[String, String])

    assertEquals(List("trace", "bag", "trace-again"), extracted)
    assertEquals(List("traceparent", "baggage"), combined.fields.toList)
    assertEquals(Map("out-trace" -> "trace,bag,trace-again", "out-bag" -> "trace,bag,trace-again", "out-trace-again" -> "trace,bag,trace-again"), injected)
    assertTrue(combined.toString.contains("TextMapPropagator.Multi"))

    val noop: TextMapPropagator[List[String]] = TextMapPropagator.noop
    assertEquals(Nil, noop.fields.toList)
    assertEquals(List("existing"), noop.extract(List("existing"), Map("traceparent" -> "in")))
    assertEquals(Map("kept" -> "value"), noop.inject(List("existing"), Map("kept" -> "value")))
  }

  @Test
  def textMapPropagatorMonoidCombinesDelegatesAndPreservesIdentityBehavior(): Unit = {
    val monoid: Monoid[TextMapPropagator[List[String]]] =
      Monoid[TextMapPropagator[List[String]]]
    val first: TextMapPropagator[List[String]] =
      RecordingPropagator("traceparent", "trace", "out-trace")
    val second: TextMapPropagator[List[String]] =
      RecordingPropagator("baggage", "bag", "out-bag")
    val duplicateField: TextMapPropagator[List[String]] =
      RecordingPropagator("traceparent", "trace-again", "out-trace-again")

    val leftIdentity: TextMapPropagator[List[String]] = monoid.combine(monoid.empty, first)
    val rightIdentity: TextMapPropagator[List[String]] = monoid.combine(first, monoid.empty)
    assertEquals(List("traceparent"), leftIdentity.fields.toList)
    assertEquals(List("trace"), leftIdentity.extract(Nil, Map("traceparent" -> "in")))
    assertEquals(
      Map("out-trace" -> "existing"),
      rightIdentity.inject(List("existing"), Map.empty[String, String])
    )

    val combined: TextMapPropagator[List[String]] = monoid.combine(
      monoid.combine(first, second),
      duplicateField
    )
    val extracted: List[String] = combined.extract(Nil, Map("traceparent" -> "in", "baggage" -> "items"))
    val injected: Map[String, String] = combined.inject(extracted, Map.empty[String, String])

    assertEquals(List("traceparent", "baggage"), combined.fields.toList)
    assertEquals(List("trace", "bag", "trace-again"), extracted)
    assertEquals(
      Map(
        "out-trace" -> "trace,bag,trace-again",
        "out-bag" -> "trace,bag,trace-again",
        "out-trace-again" -> "trace,bag,trace-again"
      ),
      injected
    )
  }

  @Test
  def contextPropagatorsExposeNoopAndCompositeTextMapPropagators(): Unit = {
    val first: TextMapPropagator[List[String]] = RecordingPropagator("traceparent", "trace", "out-trace")
    val second: TextMapPropagator[List[String]] = RecordingPropagator("baggage", "bag", "out-bag")

    val noop: ContextPropagators[List[String]] = ContextPropagators.noop
    assertEquals(Nil, noop.textMapPropagator.fields.toList)
    assertEquals(List("ctx"), noop.textMapPropagator.extract(List("ctx"), Map("traceparent" -> "in")))
    assertTrue(noop.toString.contains("ContextPropagators.Noop"))

    val propagators: ContextPropagators[List[String]] = ContextPropagators.of(first, second)
    assertEquals(List("traceparent", "baggage"), propagators.textMapPropagator.fields.toList)
    assertEquals(List("trace", "bag"), propagators.textMapPropagator.extract(Nil, Map("traceparent" -> "in", "baggage" -> "items")))
    assertTrue(propagators.toString.contains("ContextPropagators.Default"))
  }

  @Test
  def kindTransformersLiftAndMapCommonCatsDataTypes(): Unit = {
    val idTransformer: KindTransformer[Id, Id] = summon[KindTransformer[Id, Id]]
    val idTransformation: Id ~> Id = new (Id ~> Id) {
      def apply[A](fa: A): A = fa
    }
    assertEquals("value", idTransformer.liftK("value"))
    assertEquals("mapped", idTransformer.limitedMapK("mapped")(idTransformation))
    assertSame(idTransformation, idTransformer.liftFunctionK(idTransformation))

    val reverseList: List ~> List = new (List ~> List) {
      def apply[A](fa: List[A]): List[A] = fa.reverse
    }

    val optionTransformer: KindTransformer[List, [A] =>> OptionT[List, A]] = summon[KindTransformer[List, [A] =>> OptionT[List, A]]]
    assertEquals(List(Some(1), Some(2)), optionTransformer.liftK(List(1, 2)).value)
    assertEquals(List(None, Some(1)), optionTransformer.limitedMapK(OptionT(List(Some(1), None)))(reverseList).value)

    val eitherTransformer: KindTransformer[List, [A] =>> EitherT[List, String, A]] = summon[KindTransformer[List, [A] =>> EitherT[List, String, A]]]
    val eitherValues: List[Either[String, Int]] = List(Right(1), Left("bad"))
    assertEquals(List(Right(7)), eitherTransformer.liftK(List(7)).value)
    assertEquals(List(Left("bad"), Right(1)), eitherTransformer.limitedMapK(EitherT(eitherValues))(reverseList).value)

    val iorTransformer: KindTransformer[List, [A] =>> IorT[List, String, A]] = summon[KindTransformer[List, [A] =>> IorT[List, String, A]]]
    val iorValues: List[Ior[String, Int]] = List(Ior.Right(1), Ior.Both("warn", 2))
    assertEquals(List(Ior.Both("warn", 2), Ior.Right(1)), iorTransformer.limitedMapK(IorT(iorValues))(reverseList).value)

    val kleisliTransformer: KindTransformer[List, [A] =>> Kleisli[List, String, A]] = summon[KindTransformer[List, [A] =>> Kleisli[List, String, A]]]
    val kleisli: Kleisli[List, String, Int] = Kleisli((input: String) => List(input.length, input.length + 1))
    assertEquals(List(4, 3), kleisliTransformer.limitedMapK(kleisli)(reverseList).run("abc"))

    val writerTransformer: KindTransformer[List, [A] =>> WriterT[List, Vector[String], A]] = summon[KindTransformer[List, [A] =>> WriterT[List, Vector[String], A]]]
    val writer: WriterT[List, Vector[String], Int] = WriterT(List((Vector("first"), 1), (Vector("second"), 2)))
    assertEquals(List((Vector("second"), 2), (Vector("first"), 1)), writerTransformer.limitedMapK(writer)(reverseList).run)
    assertEquals(List((Vector.empty[String], 7)), writerTransformer.liftK(List(7)).run)

    val syncIOIdentity: SyncIO ~> SyncIO = new (SyncIO ~> SyncIO) {
      def apply[A](fa: SyncIO[A]): SyncIO[A] = fa.map(identity)
    }
    val resourceTransformer: KindTransformer[SyncIO, [A] =>> Resource[SyncIO, A]] = summon[KindTransformer[SyncIO, [A] =>> Resource[SyncIO, A]]]
    val resource: Resource[SyncIO, String] = Resource.eval(SyncIO("resource"))
    assertEquals("resource!", resourceTransformer.limitedMapK(resource)(syncIOIdentity).use(value => SyncIO(value + "!")).unsafeRunSync())
    assertEquals(5, resourceTransformer.liftK(SyncIO(5)).use(value => SyncIO(value)).unsafeRunSync())
  }

  private final case class HeaderCarrier(entries: Map[String, String])

  private final case class RecordingPropagator(
      field: String,
      extractedValue: String,
      injectedKey: String
  ) extends TextMapPropagator[List[String]] {
    val fields: Iterable[String] = List(field)

    def extract[A: TextMapGetter](ctx: List[String], carrier: A): List[String] =
      TextMapGetter[A].get(carrier, field).fold(ctx)(_ => ctx :+ extractedValue)

    def inject[A: TextMapUpdater](ctx: List[String], carrier: A): A =
      TextMapUpdater[A].updated(carrier, injectedKey, ctx.mkString(","))
  }
}
