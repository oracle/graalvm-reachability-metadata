/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_schema_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import zio.Chunk
import zio.schema.DynamicValue
import zio.schema.Fallback
import zio.schema.NameFormat
import zio.schema.Schema
import zio.schema.TypeId
import zio.schema.annotation.fieldName
import zio.schema.validation.Validation

import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters.*

private final case class Person(name: String, age: Int)

private object Person {
  val schema: Schema.CaseClass2[String, Int, Person] = {
    val nameField: Schema.Field[Person, String] = Schema.Field[Person, String](
      name0 = "name",
      schema0 = Schema[String],
      annotations0 = Chunk(fieldName("full_name")),
      validation0 = Validation.minLength(2),
      get0 = _.name,
      set0 = (person, name) => person.copy(name = name)
    )
    val ageField: Schema.Field[Person, Int] = Schema.Field[Person, Int](
      name0 = "age",
      schema0 = Schema[Int].default(18),
      validation0 = Validation.between(0, 130),
      get0 = _.age,
      set0 = (person, age) => person.copy(age = age)
    )

    Schema.CaseClass2[String, Int, Person](
      id0 = TypeId.parse("example.Person"),
      field01 = nameField,
      field02 = ageField,
      construct0 = (name: String, age: Int) => Person(name, age)
    )
  }
}

private sealed trait Payment

private object Payment {
  final case class Cash(amount: BigDecimal) extends Payment
  final case class Card(last4: String) extends Payment

  private val cashSchema: Schema.CaseClass1[BigDecimal, Cash] = Schema.CaseClass1[BigDecimal, Cash](
    id0 = TypeId.parse("example.Payment.Cash"),
    field0 = Schema.Field[Cash, BigDecimal](
      name0 = "amount",
      schema0 = Schema[BigDecimal],
      get0 = _.amount,
      set0 = (cash, amount) => cash.copy(amount = amount)
    ),
    defaultConstruct0 = amount => Cash(amount)
  )

  private val cardSchema: Schema.CaseClass1[String, Card] = Schema.CaseClass1[String, Card](
    id0 = TypeId.parse("example.Payment.Card"),
    field0 = Schema.Field[Card, String](
      name0 = "last4",
      schema0 = Schema[String],
      validation0 = Validation.minLength(4),
      get0 = _.last4,
      set0 = (card, last4) => card.copy(last4 = last4)
    ),
    defaultConstruct0 = last4 => Card(last4)
  )

  val schema: Schema.Enum2[Cash, Card, Payment] = Schema.Enum2[Cash, Card, Payment](
    id = TypeId.parse("example.Payment"),
    case1 = Schema.Case[Payment, Cash]("Cash", cashSchema, _.asInstanceOf[Cash], cash => cash, _.isInstanceOf[Cash]),
    case2 = Schema.Case[Payment, Card]("Card", cardSchema, _.asInstanceOf[Card], card => card, _.isInstanceOf[Card])
  )
}

private sealed trait RecursiveTree

private object RecursiveTree {
  final case class Leaf(value: Int) extends RecursiveTree
  final case class Branch(label: String, children: Chunk[RecursiveTree]) extends RecursiveTree

  val schema: Schema[RecursiveTree] = Schema.defer {
    val leafSchema: Schema.CaseClass1[Int, Leaf] = Schema.CaseClass1[Int, Leaf](
      id0 = TypeId.parse("example.RecursiveTree.Leaf"),
      field0 = Schema.Field[Leaf, Int](
        name0 = "value",
        schema0 = Schema[Int],
        get0 = _.value,
        set0 = (leaf, value) => leaf.copy(value = value)
      ),
      defaultConstruct0 = value => Leaf(value)
    )

    val branchSchema: Schema.CaseClass2[String, Chunk[RecursiveTree], Branch] =
      Schema.CaseClass2[String, Chunk[RecursiveTree], Branch](
        id0 = TypeId.parse("example.RecursiveTree.Branch"),
        field01 = Schema.Field[Branch, String](
          name0 = "label",
          schema0 = Schema[String],
          get0 = _.label,
          set0 = (branch, label) => branch.copy(label = label)
        ),
        field02 = Schema.Field[Branch, Chunk[RecursiveTree]](
          name0 = "children",
          schema0 = Schema.chunk(schema),
          get0 = _.children,
          set0 = (branch, children) => branch.copy(children = children)
        ),
        construct0 = (label: String, children: Chunk[RecursiveTree]) => Branch(label, children)
      )

    Schema.Enum2[Leaf, Branch, RecursiveTree](
      id = TypeId.parse("example.RecursiveTree"),
      case1 = Schema.Case[RecursiveTree, Leaf](
        "Leaf",
        leafSchema,
        _.asInstanceOf[Leaf],
        leaf => leaf,
        _.isInstanceOf[Leaf]
      ),
      case2 = Schema.Case[RecursiveTree, Branch](
        "Branch",
        branchSchema,
        _.asInstanceOf[Branch],
        branch => branch,
        _.isInstanceOf[Branch]
      )
    )
  }
}

class Zio_schema_3Test {
  @Test
  def primitiveOptionalTupleEitherAndCollectionSchemasRoundTripThroughDynamicValues(): Unit = {
    val optionalSchema: Schema[Option[Int]] = Schema[Int].optional
    val tupleSchema: Schema[(String, Int)] = Schema[String] <*> Schema[Int]
    val eitherSchema: Schema[Either[String, Int]] = Schema[String] <+> Schema[Int]
    val listSchema: Schema[List[Int]] = Schema[List[Int]]

    assertThat(optionalSchema.toDynamic(Some(7))).isEqualTo(DynamicValue.SomeValue(DynamicValue(7)))
    assertThat(optionalSchema.toDynamic(None)).isEqualTo(DynamicValue.NoneValue)
    assertThat(optionalSchema.fromDynamic(DynamicValue.SomeValue(DynamicValue(9)))).isEqualTo(Right(Some(9)))
    assertThat(optionalSchema.fromDynamic(DynamicValue.NoneValue)).isEqualTo(Right(None))

    assertThat(tupleSchema.toDynamic("answer" -> 42)).isEqualTo(DynamicValue.Tuple(DynamicValue("answer"), DynamicValue(42)))
    assertThat(tupleSchema.fromDynamic(DynamicValue.Tuple(DynamicValue("left"), DynamicValue(1)))).isEqualTo(Right("left" -> 1))

    assertThat(eitherSchema.toDynamic(Left("problem"))).isEqualTo(DynamicValue.LeftValue(DynamicValue("problem")))
    assertThat(eitherSchema.toDynamic(Right(10))).isEqualTo(DynamicValue.RightValue(DynamicValue(10)))
    assertThat(eitherSchema.fromDynamic(DynamicValue.RightValue(DynamicValue(11)))).isEqualTo(Right(Right(11)))

    assertThat(listSchema.toDynamic(List(1, 2, 3))).isEqualTo(
      DynamicValue.Sequence(Chunk(DynamicValue(1), DynamicValue(2), DynamicValue(3)))
    )
    assertThat(listSchema.fromDynamic(DynamicValue.Sequence(Chunk(DynamicValue(4), DynamicValue(5))))).isEqualTo(Right(List(4, 5)))
    assertThat(listSchema.defaultValue).isEqualTo(Right(Nil))
  }

  @Test
  def mapAndSetSchemasRoundTripThroughDynamicDictionaryAndSetValues(): Unit = {
    val mapSchema: Schema[Map[String, Int]] = Schema.map(Schema[String], Schema[Int])
    val setSchema: Schema[Set[String]] = Schema.set(Schema[String])

    val scores: Map[String, Int] = Map("Ada" -> 10, "Bob" -> 20)
    val dynamicMap: DynamicValue = mapSchema.toDynamic(scores)
    val mapEntries: Chunk[(DynamicValue, DynamicValue)] = dynamicMap match {
      case DynamicValue.Dictionary(entries) => entries
      case other => throw new AssertionError(s"Expected DynamicValue.Dictionary, got $other")
    }
    assertThat(mapEntries.toList.asJava).containsExactlyInAnyOrder(
      DynamicValue("Ada") -> DynamicValue(10),
      DynamicValue("Bob") -> DynamicValue(20)
    )
    assertThat(mapSchema.fromDynamic(dynamicMap)).isEqualTo(Right(scores))
    assertThat(
      mapSchema.fromDynamic(DynamicValue.Dictionary(Chunk(DynamicValue("Ada") -> DynamicValue("ten")))).isLeft
    ).isTrue()
    assertThat(mapSchema.defaultValue).isEqualTo(Right(Map.empty[String, Int]))

    val tags: Set[String] = Set("scala", "native")
    val dynamicSet: DynamicValue = setSchema.toDynamic(tags)
    val setValues: Set[DynamicValue] = dynamicSet match {
      case DynamicValue.SetValue(values) => values
      case other => throw new AssertionError(s"Expected DynamicValue.SetValue, got $other")
    }
    assertThat(setValues.asJava).containsExactlyInAnyOrder(
      DynamicValue("scala"),
      DynamicValue("native")
    )
    assertThat(setSchema.fromDynamic(dynamicSet)).isEqualTo(Right(tags))
    assertThat(setSchema.fromDynamic(DynamicValue.SetValue(Set(DynamicValue(1)))).isLeft).isTrue()
    assertThat(setSchema.defaultValue).isEqualTo(Right(Set.empty[String]))
  }

  @Test
  def caseClassSchemaExposesFieldsAnnotationsDefaultsAndValidation(): Unit = {
    val schema: Schema.CaseClass2[String, Int, Person] = Person.schema
    val ada: Person = Person("Ada", 37)

    assertThat(schema.id).isEqualTo(TypeId.parse("example.Person"))
    assertThat(schema.fields.map(_.name).toList.asJava).containsExactly("name", "age")
    assertThat(schema.field1.fieldName).isEqualTo("full_name")
    assertThat(schema.field1.nameAndAliases.asJava).containsExactly("full_name")
    assertThat(schema.field2.defaultValue).isEqualTo(Some(18))
    assertThat(schema.field1.get(ada)).isEqualTo("Ada")
    assertThat(schema.field2.set(ada, 38)).isEqualTo(Person("Ada", 38))

    assertThat(Schema.validate(ada)(schema).size).isEqualTo(0)
    assertThat(Schema.validate(Person("A", -1))(schema).size).isGreaterThanOrEqualTo(2)
  }

  @Test
  def caseClassSchemaConvertsRecordsToAndFromDynamicValues(): Unit = {
    val schema: Schema[Person] = Person.schema
    val ada: Person = Person("Ada", 37)
    val dynamic: DynamicValue = schema.toDynamic(ada)

    assertThat(dynamic).isEqualTo(
      DynamicValue.Record(
        TypeId.parse("example.Person"),
        ListMap(
          "name" -> DynamicValue("Ada"),
          "age" -> DynamicValue(37)
        )
      )
    )
    assertThat(schema.fromDynamic(dynamic)).isEqualTo(Right(ada))

    val malformed: DynamicValue = DynamicValue.Record(
      TypeId.parse("example.Person"),
      ListMap(
        "name" -> DynamicValue("Ada"),
        "age" -> DynamicValue("not an integer")
      )
    )
    assertThat(schema.fromDynamic(malformed).isLeft).isTrue()
  }

  @Test
  def enumSchemaSelectsCasesAndRoundTripsDynamicEnumerations(): Unit = {
    val schema: Schema.Enum2[Payment.Cash, Payment.Card, Payment] = Payment.schema
    val card: Payment = Payment.Card("1234")
    val cash: Payment = Payment.Cash(BigDecimal("12.50"))

    assertThat(schema.cases.map(_.id).toList.asJava).containsExactly("Cash", "Card")
    assertThat(schema.caseOf("Card")).isEqualTo(Some(schema.case2))
    assertThat(schema.caseOf(card)).isEqualTo(Some(schema.case2))
    assertThat(schema.caseOf(cash)).isEqualTo(Some(schema.case1))

    val dynamicCard: DynamicValue = schema.toDynamic(card)
    assertThat(dynamicCard).isEqualTo(
      DynamicValue.Enumeration(
        TypeId.parse("example.Payment"),
        "Card" -> DynamicValue.Record(TypeId.parse("example.Payment.Card"), ListMap("last4" -> DynamicValue("1234")))
      )
    )
    assertThat(schema.fromDynamic(dynamicCard)).isEqualTo(Right(card))
    assertThat(schema.fromDynamic(DynamicValue.Enumeration(TypeId.parse("example.Payment"), "Unknown" -> DynamicValue.NoneValue)).isLeft).isTrue()
  }

  @Test
  def deferredRecursiveSchemasRoundTripNestedDynamicValues(): Unit = {
    val schema: Schema[RecursiveTree] = RecursiveTree.schema
    val tree: RecursiveTree = RecursiveTree.Branch(
      "root",
      Chunk(
        RecursiveTree.Leaf(1),
        RecursiveTree.Branch("child", Chunk(RecursiveTree.Leaf(2)))
      )
    )

    val dynamic: DynamicValue = schema.toDynamic(tree)

    assertThat(dynamic).isEqualTo(
      DynamicValue.Enumeration(
        TypeId.parse("example.RecursiveTree"),
        "Branch" -> DynamicValue.Record(
          TypeId.parse("example.RecursiveTree.Branch"),
          ListMap(
            "label" -> DynamicValue("root"),
            "children" -> DynamicValue.Sequence(
              Chunk(
                DynamicValue.Enumeration(
                  TypeId.parse("example.RecursiveTree"),
                  "Leaf" -> DynamicValue.Record(
                    TypeId.parse("example.RecursiveTree.Leaf"),
                    ListMap("value" -> DynamicValue(1))
                  )
                ),
                DynamicValue.Enumeration(
                  TypeId.parse("example.RecursiveTree"),
                  "Branch" -> DynamicValue.Record(
                    TypeId.parse("example.RecursiveTree.Branch"),
                    ListMap(
                      "label" -> DynamicValue("child"),
                      "children" -> DynamicValue.Sequence(
                        Chunk(
                          DynamicValue.Enumeration(
                            TypeId.parse("example.RecursiveTree"),
                            "Leaf" -> DynamicValue.Record(
                              TypeId.parse("example.RecursiveTree.Leaf"),
                              ListMap("value" -> DynamicValue(2))
                            )
                          )
                        )
                      )
                    )
                  )
                )
              )
            )
          )
        )
      )
    )
    assertThat(schema.fromDynamic(dynamic)).isEqualTo(Right(tree))

    val malformedNestedLeaf: DynamicValue = DynamicValue.Enumeration(
      TypeId.parse("example.RecursiveTree"),
      "Branch" -> DynamicValue.Record(
        TypeId.parse("example.RecursiveTree.Branch"),
        ListMap(
          "label" -> DynamicValue("root"),
          "children" -> DynamicValue.Sequence(
            Chunk(
              DynamicValue.Enumeration(
                TypeId.parse("example.RecursiveTree"),
                "Leaf" -> DynamicValue.Record(
                  TypeId.parse("example.RecursiveTree.Leaf"),
                  ListMap("value" -> DynamicValue("one"))
                )
              )
            )
          )
        )
      )
    )
    assertThat(schema.fromDynamic(malformedNestedLeaf).isLeft).isTrue()
  }

  @Test
  def transformationsSupportValidationFailuresAndDefaultValues(): Unit = {
    val portSchema: Schema[Int] = Schema[String].default("0").transformOrFail[Int](
      value => value.toIntOption.filter(port => port >= 0 && port <= 65535).toRight(s"invalid port: $value"),
      port => Right(port.toString)
    )

    assertThat(portSchema.fromDynamic(DynamicValue("8080"))).isEqualTo(Right(8080))
    assertThat(portSchema.toDynamic(443)).isEqualTo(DynamicValue("443"))
    assertThat(portSchema.fromDynamic(DynamicValue("70000")).isLeft).isTrue()
    assertThat(portSchema.defaultValue).isEqualTo(Right(0))
  }

  @Test
  def diffAndPatchWorkForPrimitiveRecordAndSequenceSchemas(): Unit = {
    val intSchema: Schema[Int] = Schema[Int]
    val personSchema: Schema[Person] = Person.schema
    val listSchema: Schema[List[Int]] = Schema[List[Int]]

    val intPatch = intSchema.diff(1, 2)
    val personPatch = personSchema.diff(Person("Ada", 37), Person("Ada", 38))
    val listPatch = listSchema.diff(List(1, 2, 3), List(1, 3, 4))

    assertThat(intSchema.patch(1, intPatch)).isEqualTo(Right(2))
    assertThat(personSchema.patch(Person("Ada", 37), personPatch)).isEqualTo(Right(Person("Ada", 38)))
    assertThat(listSchema.patch(List(1, 2, 3), listPatch)).isEqualTo(Right(List(1, 3, 4)))
  }

  @Test
  def orderingUsesSchemaShapeForOptionsEitherTuplesRecordsAndSequences(): Unit = {
    val optionalOrdering: Ordering[Option[Int]] = Schema[Option[Int]].ordering
    val eitherOrdering: Ordering[Either[String, Int]] = Schema[Either[String, Int]].ordering
    val tupleOrdering: Ordering[(String, Int)] = (Schema[String] <*> Schema[Int]).ordering
    val personOrdering: Ordering[Person] = Person.schema.ordering
    val listOrdering: Ordering[List[Int]] = Schema[List[Int]].ordering

    assertThat(optionalOrdering.compare(None, Some(1))).isLessThan(0)
    assertThat(eitherOrdering.compare(Left("z"), Right(1))).isLessThan(0)
    assertThat(tupleOrdering.compare("a" -> 2, "a" -> 3)).isLessThan(0)
    assertThat(personOrdering.compare(Person("Ada", 37), Person("Bob", 20))).isLessThan(0)
    assertThat(listOrdering.compare(List(1, 2), List(1, 2, 3))).isLessThan(0)
  }

  @Test
  def fallbackAndNameFormattingHelpersPreserveTheirPublicSemantics(): Unit = {
    val both: Fallback[Int, String] = Fallback.Both(1, "one")
    val right: Fallback[Int, String] = Fallback.fromEither(Right("two"))
    val fallbackSchema: Schema[Fallback[Int, String]] = Schema.fallback[Int, String]

    assertThat(both.toEither).isEqualTo(Left(1))
    assertThat(both.simplify).isEqualTo(Fallback.Left(1))
    assertThat(both.mapLeft(_ + 1)).isEqualTo(Fallback.Both(2, "one"))
    assertThat(both.mapRight(_.toUpperCase)).isEqualTo(Fallback.Both(1, "ONE"))
    assertThat(both.swap).isEqualTo(Fallback.Both("one", 1))
    assertThat(right.fold(_.toString, identity)).isEqualTo("two")
    assertThat(fallbackSchema.toDynamic(Fallback.Left(5))).isEqualTo(DynamicValue.LeftValue(DynamicValue(5)))
    assertThat(fallbackSchema.toDynamic(Fallback.Right("five"))).isEqualTo(DynamicValue.RightValue(DynamicValue("five")))
    assertThat(fallbackSchema.toDynamic(both)).isEqualTo(DynamicValue.BothValue(DynamicValue(1), DynamicValue("one")))

    assertThat(NameFormat.CamelCase("http_response-code")).isEqualTo("httpResponseCode")
    assertThat(NameFormat.PascalCase("http_response-code")).isEqualTo("HttpResponseCode")
    assertThat(NameFormat.SnakeCase("HTTPResponseCode")).isEqualTo("http_response_code")
    assertThat(NameFormat.KebabCase("HTTPResponseCode")).isEqualTo("http-response-code")
    assertThat(NameFormat.Identity("already_Mixed")).isEqualTo("already_Mixed")
    assertThat(NameFormat.Custom(_.reverse)("abc")).isEqualTo("cba")
  }
}
