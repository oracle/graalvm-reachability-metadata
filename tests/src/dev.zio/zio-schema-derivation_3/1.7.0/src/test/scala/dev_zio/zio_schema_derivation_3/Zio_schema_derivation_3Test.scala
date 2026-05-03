/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_schema_derivation_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import zio.Chunk
import zio.schema.*
import zio.schema.annotation.fieldName

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag

private final case class DerivationAddress(@fieldName("street_name") street: String, zip: Int)

private object DerivationAddress {
  given Schema[DerivationAddress] = Schema.derived[DerivationAddress]
}

private sealed trait DerivationContact

private object DerivationContact {
  final case class Email(value: String) extends DerivationContact
  final case class Postal(address: DerivationAddress) extends DerivationContact
  case object NoContact extends DerivationContact

  given Schema[DerivationContact] = Schema.derived[DerivationContact]
}

private final case class DerivationEnvelope(
  id: Int,
  contact: DerivationContact,
  aliases: List[DerivationContact],
  labels: Set[String],
  scores: Map[String, Option[Int]],
  history: Vector[String],
  payload: Chunk[Int]
)

private object DerivationEnvelope {
  given Schema[DerivationEnvelope] = Schema.derived[DerivationEnvelope]
}

private final case class DerivationTree(label: String, children: Chunk[DerivationTree])

private object DerivationTree {
  given Schema[DerivationTree] = Schema.derived[DerivationTree]
}

private final case class DerivationResult[A](key: String, primary: A, secondary: Either[String, A])

private object DerivationResult {
  given [A: Schema]: Schema[DerivationResult[A]] = Schema.derived[DerivationResult[A]]
}

private final case class DerivationLargeRecord(
  f1: Int,
  f2: Int,
  f3: Int,
  f4: Int,
  f5: Int,
  f6: Int,
  f7: Int,
  f8: Int,
  f9: Int,
  f10: Int,
  f11: Int,
  f12: Int,
  f13: Int,
  f14: Int,
  f15: Int,
  f16: Int,
  f17: Int,
  f18: Int,
  f19: Int,
  f20: Int,
  f21: Int,
  f22: Int,
  f23: Int
)

private object DerivationLargeRecord {
  given Schema[DerivationLargeRecord] = Schema.derived[DerivationLargeRecord]
}

private final case class TypeDescription[A](value: String)

private class DescribingDeriver extends Deriver[TypeDescription] {
  override def deriveRecord[A](
    record: Schema.Record[A],
    fields: => Chunk[Deriver.WrappedF[TypeDescription, _]],
    summoned: => Option[TypeDescription[A]]
  ): TypeDescription[A] =
    summoned.getOrElse(TypeDescription(s"record(${record.id};${describe(fields)})"))

  override def deriveEnum[A](
    `enum`: Schema.Enum[A],
    cases: => Chunk[Deriver.WrappedF[TypeDescription, _]],
    summoned: => Option[TypeDescription[A]]
  ): TypeDescription[A] =
    summoned.getOrElse(TypeDescription(s"enum(${`enum`.id};${describe(cases)})"))

  override def derivePrimitive[A](
    st: StandardType[A],
    summoned: => Option[TypeDescription[A]]
  ): TypeDescription[A] =
    summoned.getOrElse(TypeDescription(s"primitive(${st.tag})"))

  override def derivePrimitiveAlias[A: ClassTag, U](
    st: StandardType[U],
    summoned: => Option[TypeDescription[A]]
  ): TypeDescription[A] =
    summoned.getOrElse(TypeDescription(s"primitiveAlias(${st.tag})"))

  override def deriveOption[A](
    option: Schema.Optional[A],
    inner: => TypeDescription[A],
    summoned: => Option[TypeDescription[Option[A]]]
  ): TypeDescription[Option[A]] =
    summoned.getOrElse(TypeDescription(s"option(${inner.value})"))

  override def deriveSequence[C[_], A](
    sequence: Schema.Sequence[C[A], A, _],
    inner: => TypeDescription[A],
    summoned: => Option[TypeDescription[C[A]]]
  ): TypeDescription[C[A]] =
    summoned.getOrElse(TypeDescription(s"sequence(${inner.value})"))

  override def deriveMap[K, V](
    map: Schema.Map[K, V],
    key: => TypeDescription[K],
    value: => TypeDescription[V],
    summoned: => Option[TypeDescription[Map[K, V]]]
  ): TypeDescription[Map[K, V]] =
    summoned.getOrElse(TypeDescription(s"map(${key.value},${value.value})"))

  override def deriveEither[A, B](
    either: Schema.Either[A, B],
    left: => TypeDescription[A],
    right: => TypeDescription[B],
    summoned: => Option[TypeDescription[Either[A, B]]]
  ): TypeDescription[Either[A, B]] =
    summoned.getOrElse(TypeDescription(s"either(${left.value},${right.value})"))

  override def deriveSet[A](
    set: Schema.Set[A],
    inner: => TypeDescription[A],
    summoned: => Option[TypeDescription[Set[A]]]
  ): TypeDescription[Set[A]] =
    summoned.getOrElse(TypeDescription(s"set(${inner.value})"))

  override def deriveTupleN[T](
    schemasAndInstances: => Chunk[(Schema[_], Deriver.WrappedF[TypeDescription, _])],
    summoned: => Option[TypeDescription[T]]
  ): TypeDescription[T] =
    summoned.getOrElse(TypeDescription(s"tuple(${schemasAndInstances.map(_._2.unwrap.value).mkString(",")})"))

  override def deriveTransformedRecord[A, B](
    record: Schema.Record[A],
    transform: Schema.Transform[A, B, _],
    fields: => Chunk[Deriver.WrappedF[TypeDescription, _]],
    summoned: => Option[TypeDescription[B]]
  ): TypeDescription[B] =
    summoned.getOrElse(TypeDescription(s"transformed(${record.id};${describe(fields)})"))

  protected def describe(fields: => Chunk[Deriver.WrappedF[TypeDescription, _]]): String =
    fields.map(_.unwrap.value).mkString("[", ",", "]")
}

private final class CountingDeriver extends DescribingDeriver {
  val primitiveCalls: AtomicInteger = new AtomicInteger(0)
  val sequenceCalls: AtomicInteger = new AtomicInteger(0)

  override def derivePrimitive[A](
    st: StandardType[A],
    summoned: => Option[TypeDescription[A]]
  ): TypeDescription[A] = {
    primitiveCalls.incrementAndGet()
    super.derivePrimitive(st, summoned)
  }

  override def deriveSequence[C[_], A](
    sequence: Schema.Sequence[C[A], A, _],
    inner: => TypeDescription[A],
    summoned: => Option[TypeDescription[C[A]]]
  ): TypeDescription[C[A]] = {
    sequenceCalls.incrementAndGet()
    super.deriveSequence(sequence, inner, summoned)
  }
}

class Zio_schema_derivation_3Test {
  @Test
  def derivesCaseClassEnumAndCollectionSchemasThatRoundTripDynamicValues(): Unit = {
    val schema: Schema[DerivationEnvelope] = summon[Schema[DerivationEnvelope]]
    val value: DerivationEnvelope = DerivationEnvelope(
      id = 7,
      contact = DerivationContact.Postal(DerivationAddress("Main", 12345)),
      aliases = List(DerivationContact.Email("team@example.test"), DerivationContact.NoContact),
      labels = Set("primary", "native"),
      scores = Map("first" -> Some(10), "missing" -> None),
      history = Vector("created", "verified"),
      payload = Chunk(1, 2, 3)
    )

    val dynamic: DynamicValue = schema.toDynamic(value)

    assertThat(schema.fromDynamic(dynamic)).isEqualTo(Right(value))
    assertThat(schema.fromDynamic(malformedEnvelopeDynamic).isLeft).isTrue()

    val record: Schema.Record[DerivationEnvelope] = forceRecord(schema)
    assertThat(record.fields.map(_.name).toList.asJava).containsExactly(
      "id",
      "contact",
      "aliases",
      "labels",
      "scores",
      "history",
      "payload"
    )
  }

  @Test
  def derivedSchemasPreserveFieldNameAnnotationsAndSumCaseSelection(): Unit = {
    val addressRecord: Schema.Record[DerivationAddress] = forceRecord(summon[Schema[DerivationAddress]])
    val streetField = addressRecord.fields.find(_.fieldName == "street_name").getOrElse {
      throw new AssertionError("Derived address schema did not contain the renamed street field")
    }
    val contactEnum: Schema.Enum[DerivationContact] = forceEnum(summon[Schema[DerivationContact]])
    val postal: DerivationContact = DerivationContact.Postal(DerivationAddress("Oak", 60606))

    assertThat(streetField.name).isEqualTo("street_name")
    assertThat(streetField.fieldName).isEqualTo("street_name")
    assertThat(contactEnum.cases.map(_.id).toList.asJava).containsExactlyInAnyOrder("Email", "Postal", "NoContact")
    assertThat(contactEnum.caseOf(postal).map(_.id)).isEqualTo(Some("Postal"))
    assertThat(contactEnum.caseOf(DerivationContact.NoContact).map(_.id)).isEqualTo(Some("NoContact"))
    assertThat(summon[Schema[DerivationContact]].fromDynamic(summon[Schema[DerivationContact]].toDynamic(postal))).isEqualTo(Right(postal))
  }

  @Test
  def derivesRecursiveSchemasUsingDeferredSelfReferences(): Unit = {
    val schema: Schema[DerivationTree] = summon[Schema[DerivationTree]]
    val tree: DerivationTree = DerivationTree(
      "root",
      Chunk(
        DerivationTree("left", Chunk.empty),
        DerivationTree("right", Chunk(DerivationTree("leaf", Chunk.empty)))
      )
    )

    val dynamic: DynamicValue = schema.toDynamic(tree)

    assertThat(schema.fromDynamic(dynamic)).isEqualTo(Right(tree))
    dynamic match {
      case DynamicValue.Record(_, values) => assertThat(values.keys.toList.asJava).containsExactly("label", "children")
      case other                          => throw new AssertionError(s"Expected recursive tree to encode as a record, got $other")
    }
  }

  @Test
  def derivesGenericCaseClassSchemasWithTypeSpecificFieldsAndEitherBranches(): Unit = {
    val intSchema: Schema[DerivationResult[Int]] = summon[Schema[DerivationResult[Int]]]
    val stringSchema: Schema[DerivationResult[String]] = summon[Schema[DerivationResult[String]]]
    val intResult: DerivationResult[Int] = DerivationResult("numeric", 42, Right(99))
    val stringResult: DerivationResult[String] = DerivationResult("text", "primary", Left("missing"))

    val intDynamic: DynamicValue = intSchema.toDynamic(intResult)
    val stringDynamic: DynamicValue = stringSchema.toDynamic(stringResult)

    assertThat(intSchema.fromDynamic(intDynamic)).isEqualTo(Right(intResult))
    assertThat(stringSchema.fromDynamic(stringDynamic)).isEqualTo(Right(stringResult))

    val intRecord: Schema.Record[DerivationResult[Int]] = forceRecord(intSchema)
    assertThat(intRecord.fields.map(_.name).toList.asJava).containsExactly("key", "primary", "secondary")
    assertThat(intSchema.fromDynamic(replacePrimary(intDynamic, DynamicValue("not an integer"))).isLeft).isTrue()
    assertThat(stringSchema.fromDynamic(replacePrimary(stringDynamic, DynamicValue(123))).isLeft).isTrue()
  }

  @Test
  def derivesLargeCaseClassSchemasBeyondTupleArityLimit(): Unit = {
    val schema: Schema[DerivationLargeRecord] = summon[Schema[DerivationLargeRecord]]
    val value: DerivationLargeRecord = DerivationLargeRecord(
      1,
      2,
      3,
      4,
      5,
      6,
      7,
      8,
      9,
      10,
      11,
      12,
      13,
      14,
      15,
      16,
      17,
      18,
      19,
      20,
      21,
      22,
      23
    )

    val dynamic: DynamicValue = schema.toDynamic(value)

    assertThat(schema.fromDynamic(dynamic)).isEqualTo(Right(value))
    assertThat(schema.fromDynamic(replaceField(dynamic, "f23", DynamicValue("not an integer"))).isLeft).isTrue()

    dynamic match {
      case DynamicValue.Record(_, values) =>
        assertThat(values.keys.toList.asJava).containsExactly(
          "f1",
          "f2",
          "f3",
          "f4",
          "f5",
          "f6",
          "f7",
          "f8",
          "f9",
          "f10",
          "f11",
          "f12",
          "f13",
          "f14",
          "f15",
          "f16",
          "f17",
          "f18",
          "f19",
          "f20",
          "f21",
          "f22",
          "f23"
        )
      case other => throw new AssertionError(s"Expected large case class to encode as a record, got $other")
    }
  }

  @Test
  def deriveAndFactoryBuildCustomTypeClassInstancesFromNestedSchemas(): Unit = {
    given TypeDescription[DerivationAddress] = TypeDescription("custom-address")
    val deriver: Deriver[TypeDescription] = new DescribingDeriver().autoAcceptSummoned
    val direct: TypeDescription[DerivationEnvelope] = Derive.derive[TypeDescription, DerivationEnvelope](deriver)
    val factory: Factory[DerivationEnvelope] = Factory.factory[DerivationEnvelope]
    val fromFactory: TypeDescription[DerivationEnvelope] = factory.derive[TypeDescription](deriver)
    given Schema[(String, Int)] = Schema[String] <*> Schema[Int]
    val tuple: TypeDescription[(String, Int)] = Derive.derive[TypeDescription, (String, Int)](deriver)

    assertThat(direct.value).startsWith("record(")
    assertThat(direct.value).contains("primitive(int)")
    assertThat(direct.value).contains("enum(")
    assertThat(direct.value).contains("custom-address")
    assertThat(direct.value).contains("map(primitive(string),option(primitive(int)))")
    assertThat(direct.value).contains("set(primitive(string))")
    assertThat(tuple.value).isEqualTo("tuple(primitive(string),primitive(int))")
    assertThat(fromFactory.value).startsWith("record(")
    assertThat(fromFactory.value).contains("primitive(int)")
  }

  @Test
  def cachedDeriverReusesInstancesAcrossMacroInvocationsForIdenticalSchemas(): Unit = {
    given Schema[List[String]] = Schema.list(Schema[String])
    val countingDeriver = new CountingDeriver()
    val cachedDeriver: Deriver[TypeDescription] = countingDeriver.cached

    val first: TypeDescription[List[String]] = Derive.derive[TypeDescription, List[String]](cachedDeriver)
    val second: TypeDescription[List[String]] = Derive.derive[TypeDescription, List[String]](cachedDeriver)

    assertThat(first).isEqualTo(second)
    assertThat(first.value).isEqualTo("sequence(primitive(string))")
    assertThat(countingDeriver.sequenceCalls.get()).isEqualTo(1)
    assertThat(countingDeriver.primitiveCalls.get()).isEqualTo(1)
  }

  private def replacePrimary(dynamic: DynamicValue, replacement: DynamicValue): DynamicValue =
    dynamic match {
      case DynamicValue.Record(id, values) => DynamicValue.Record(id, values.updated("primary", replacement))
      case other                           => throw new AssertionError(s"Expected generic result to encode as a record, got $other")
    }

  private def replaceField(dynamic: DynamicValue, fieldName: String, replacement: DynamicValue): DynamicValue =
    dynamic match {
      case DynamicValue.Record(id, values) => DynamicValue.Record(id, values.updated(fieldName, replacement))
      case other                           => throw new AssertionError(s"Expected a record dynamic value, got $other")
    }

  private def malformedEnvelopeDynamic: DynamicValue =
    DynamicValue.Record(
      forceRecord(summon[Schema[DerivationEnvelope]]).id,
      ListMap(
        "id" -> DynamicValue("not an integer"),
        "contact" -> summon[Schema[DerivationContact]].toDynamic(DerivationContact.NoContact),
        "aliases" -> DynamicValue.Sequence(Chunk.empty[DynamicValue]),
        "labels" -> DynamicValue.SetValue(Set.empty[DynamicValue]),
        "scores" -> DynamicValue.Dictionary(Chunk.empty[(DynamicValue, DynamicValue)]),
        "history" -> DynamicValue.Sequence(Chunk.empty[DynamicValue]),
        "payload" -> DynamicValue.Sequence(Chunk.empty[DynamicValue])
      )
    )

  private def forceRecord[A](schema: Schema[A]): Schema.Record[A] =
    Schema.force(schema) match {
      case record: Schema.Record[A] => record
      case other                    => throw new AssertionError(s"Expected derived record schema, got $other")
    }

  private def forceEnum[A](schema: Schema[A]): Schema.Enum[A] =
    Schema.force(schema) match {
      case enumSchema: Schema.Enum[A] => enumSchema
      case other                      => throw new AssertionError(s"Expected derived enum schema, got $other")
    }
}
