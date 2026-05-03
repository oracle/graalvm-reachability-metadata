/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_magnolia1_3.magnolia_3

import magnolia1.*
import magnolia1.Monadic.given
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

import scala.annotation.StaticAnnotation

final class Magnolia_3Test {
  import Magnolia_3TestFixtures.*

  @Test
  def derivesProductMetadataAndConstructors(): Unit = {
    val descriptor: Inspectable[Account] = summon[Inspectable[Account]]

    assertEquals("Account", descriptor.typeName)
    assertEquals(List("product:account"), descriptor.annotations)
    assertEquals(List("id", "retries", "active"), descriptor.params.map(_.label))
    assertEquals(List("field:identifier"), descriptor.params.head.annotations)
    assertEquals(List("field:retry-count"), descriptor.params(1).annotations)
    assertEquals("String", descriptor.params.head.typeName)
    assertEquals("Int", descriptor.params(1).typeName)
    assertEquals("Boolean", descriptor.params(2).typeName)

    val account: Account = Account("acct-1", 3, active = true)
    assertEquals("Account(id=acct-1,retries=3,active=true)", descriptor.render(account))
    assertEquals(Right(Account("acct-2", 7, active = false)), descriptor.parseFields(Map("id" -> "acct-2", "retries" -> "7", "active" -> "false")))
    assertEquals(Some(Account("acct-3", 9, active = true)), descriptor.parseFieldsOption(Map("id" -> "acct-3", "retries" -> "9", "active" -> "true")))
    assertEquals(Account("raw", 11, active = false), descriptor.constructRaw(Seq("raw", 11, false)))

    assertEquals(Left(List("retries: invalid Int 'bad'")), descriptor.parseFields(Map("id" -> "acct-4", "retries" -> "bad", "active" -> "true")))
    assertEquals(Left(List("missing retries", "missing active")), descriptor.parseFields(Map("id" -> "acct-5")))
    assertEquals(None, descriptor.parseFieldsOption(Map("id" -> "acct-6", "retries" -> "not-an-int", "active" -> "true")))
  }

  @Test
  def evaluatesProductDefaultParameterValues(): Unit = {
    val descriptor: Inspectable[RetryPolicy] = summon[Inspectable[RetryPolicy]]

    assertEquals(Right(RetryPolicy("standard", 3, enabled = true)), descriptor.parseFields(Map("name" -> "standard")))
    assertEquals(Right(RetryPolicy("custom", 5, enabled = true)), descriptor.parseFields(Map("name" -> "custom", "attempts" -> "5")))
    assertEquals(Right(RetryPolicy("disabled", 3, enabled = false)), descriptor.parseFields(Map("name" -> "disabled", "enabled" -> "false")))
    assertEquals(Left(List("missing name")), descriptor.parseFields(Map.empty))
  }

  @Test
  def derivesSealedTraitSubtypesAndChoosesRuntimeSubtype(): Unit = {
    val descriptor: Inspectable[Payment] = summon[Inspectable[Payment]]

    assertEquals("Payment", descriptor.typeName)
    assertEquals(List("Card", "Cash"), descriptor.subtypes.map(_.typeName))
    assertFalse(descriptor.subtypes.find(_.typeName == "Card").get.isObject)
    assertTrue(descriptor.subtypes.find(_.typeName == "Cash").get.isObject)

    assertEquals("Payment.Card(Card(number=411111,cvv=123))", descriptor.render(Card("411111", 123)))
    assertEquals("Payment.Cash(Cash())", descriptor.render(Cash))
  }

  @Test
  def derivesRecursiveAlgebraicDataTypesLazily(): Unit = {
    val descriptor: Inspectable[Expr] = summon[Inspectable[Expr]]
    val expression: Expr = Add(Const(1), Add(Const(2), Const(3)))

    assertEquals(List("Add", "Const"), descriptor.subtypes.map(_.typeName))
    assertEquals(
      "Expr.Add(Add(left=Expr.Const(Const(value=1)),right=Expr.Add(Add(left=Expr.Const(Const(value=2)),right=Expr.Const(Const(value=3))))))",
      descriptor.render(expression)
    )
  }

  @Test
  def capturesGenericTypeInformation(): Unit = {
    val descriptor: Inspectable[Envelope[String]] = summon[Inspectable[Envelope[String]]]

    assertEquals("Envelope", descriptor.typeName)
    assertEquals(List("String"), descriptor.typeParameters)
    assertEquals(List("String"), descriptor.params.map(_.typeName))
    assertEquals("Envelope(value=payload)", descriptor.render(Envelope("payload")))
  }

  @Test
  def evaluatesCallByNeedValuesOnlyWhenRequested(): Unit = {
    var evaluations: Int = 0
    val value: CallByNeed[String] = CallByNeed.createLazy(() => {
      evaluations += 1
      "computed"
    })

    assertEquals(None, value.valueEvaluator)
    assertEquals(0, evaluations)
    assertEquals("computed", value.value)
    assertEquals("computed", value.value)
    assertEquals(1, evaluations)
  }

  @Test
  def exposesRepeatableValueEvaluatorWhenRequested(): Unit = {
    var evaluations: Int = 0
    val value: CallByNeed[Int] = CallByNeed.createValueEvaluator(() => {
      evaluations += 1
      evaluations
    })

    val evaluator: () => Int = value.valueEvaluator.get
    assertEquals(1, evaluator())
    assertEquals(2, evaluator())
    assertEquals(3, value.value)
    assertEquals(3, value.value)
    assertEquals(3, evaluations)
  }
}

object Magnolia_3TestFixtures {
  final class ProductNote(val value: String) extends StaticAnnotation
  final class FieldNote(val value: String) extends StaticAnnotation

  final case class ParameterSummary(label: String, index: Int, repeated: Boolean, typeName: String, annotations: List[String])
  final case class SubtypeSummary(typeName: String, index: Int, isObject: Boolean, annotations: List[String])

  trait Inspectable[A] {
    def typeName: String
    def typeParameters: List[String]
    def annotations: List[String]
    def params: List[ParameterSummary]
    def subtypes: List[SubtypeSummary]
    def render(value: A): String
    def parse(raw: String): Either[String, A]
    def parseFields(fields: Map[String, String]): Either[List[String], A]
    def parseFieldsOption(fields: Map[String, String]): Option[A]
    def constructRaw(values: Seq[Any]): A
  }

  object Inspectable extends Derivation[Inspectable] {
    given Inspectable[String] = primitive("String", value => value, raw => Right(raw))

    given Inspectable[Int] = primitive(
      "Int",
      _.toString,
      raw => raw.toIntOption.toRight(s"invalid Int '$raw'")
    )

    given Inspectable[Boolean] = primitive(
      "Boolean",
      _.toString,
      raw => raw.toBooleanOption.toRight(s"invalid Boolean '$raw'")
    )

    override def join[A](caseClass: CaseClass[Inspectable, A]): Inspectable[A] = new Inspectable[A] {
      override val typeName: String = caseClass.typeInfo.short
      override val typeParameters: List[String] = caseClass.typeInfo.typeParams.map(_.short).toList
      override val annotations: List[String] = caseClass.annotations.map(annotationSummary).toList
      override def params: List[ParameterSummary] = caseClass.params.map { param =>
        ParameterSummary(
          param.label,
          param.index,
          param.repeated,
          param.typeclass.typeName,
          param.annotations.map(annotationSummary).toList
        )
      }.toList
      override val subtypes: List[SubtypeSummary] = Nil

      override def render(value: A): String = {
        val renderedParams: String = caseClass.params
          .map(param => s"${param.label}=${param.typeclass.render(param.deref(value))}")
          .mkString(",")
        s"$typeName($renderedParams)"
      }

      override def parse(raw: String): Either[String, A] = Left(s"$typeName cannot be parsed from a single value")

      override def parseFields(fields: Map[String, String]): Either[List[String], A] = {
        caseClass.constructEither[String, Any] { param =>
          fields.get(param.label) match {
            case Some(raw) => param.typeclass.parse(raw) match {
              case Right(value) => Right(value.asInstanceOf[Any])
              case Left(error) => Left(s"${param.label}: $error")
            }
            case None => param.evaluateDefault match {
              case Some(defaultValue) => Right(defaultValue().asInstanceOf[Any])
              case None => Left(s"missing ${param.label}")
            }
          }
        }
      }

      override def parseFieldsOption(fields: Map[String, String]): Option[A] = {
        caseClass.constructMonadic[Option, Any] { param =>
          fields.get(param.label).flatMap(raw => param.typeclass.parse(raw).toOption.map(identity[Any]))
        }
      }

      override def constructRaw(values: Seq[Any]): A = caseClass.rawConstruct(values)
    }

    override def split[A](sealedTrait: SealedTrait[Inspectable, A]): Inspectable[A] = new Inspectable[A] {
      override val typeName: String = sealedTrait.typeInfo.short
      override val typeParameters: List[String] = sealedTrait.typeInfo.typeParams.map(_.short).toList
      override val annotations: List[String] = sealedTrait.annotations.map(annotationSummary).toList
      override val params: List[ParameterSummary] = Nil
      override val subtypes: List[SubtypeSummary] = sealedTrait.subtypes.map { subtype =>
        SubtypeSummary(subtype.typeInfo.short, subtype.index, subtype.isObject, subtype.annotations.map(annotationSummary).toList)
      }.toList

      override def render(value: A): String = sealedTrait.choose(value) { subtype =>
        s"$typeName.${subtype.typeInfo.short}(${subtype.typeclass.render(subtype.value)})"
      }

      override def parse(raw: String): Either[String, A] = Left(s"$typeName cannot be parsed from a single value")
      override def parseFields(fields: Map[String, String]): Either[List[String], A] = Left(List(s"$typeName cannot be parsed from fields"))
      override def parseFieldsOption(fields: Map[String, String]): Option[A] = None
      override def constructRaw(values: Seq[Any]): A = throw new UnsupportedOperationException(s"$typeName is not a product type")
    }

    private def primitive[A](name: String, renderValue: A => String, parseValue: String => Either[String, A]): Inspectable[A] = new Inspectable[A] {
      override val typeName: String = name
      override val typeParameters: List[String] = Nil
      override val annotations: List[String] = Nil
      override val params: List[ParameterSummary] = Nil
      override val subtypes: List[SubtypeSummary] = Nil
      override def render(value: A): String = renderValue(value)
      override def parse(raw: String): Either[String, A] = parseValue(raw)
      override def parseFields(fields: Map[String, String]): Either[List[String], A] = Left(List(s"$name cannot be parsed from fields"))
      override def parseFieldsOption(fields: Map[String, String]): Option[A] = None
      override def constructRaw(values: Seq[Any]): A = throw new UnsupportedOperationException(s"$name is not a product type")
    }

    private def annotationSummary(annotation: Any): String = annotation match {
      case note: ProductNote => s"product:${note.value}"
      case note: FieldNote => s"field:${note.value}"
      case other => other.toString
    }
  }

  @ProductNote("account")
  final case class Account(
      @FieldNote("identifier") id: String,
      @FieldNote("retry-count") retries: Int,
      active: Boolean
  )

  given Inspectable[Account] = Inspectable.derived[Account]

  final case class RetryPolicy(name: String, attempts: Int = 3, enabled: Boolean = true)

  given Inspectable[RetryPolicy] = Inspectable.derived[RetryPolicy]

  sealed trait Payment
  final case class Card(number: String, cvv: Int) extends Payment
  case object Cash extends Payment

  given Inspectable[Payment] = Inspectable.derived[Payment]

  sealed trait Expr
  final case class Const(value: Int) extends Expr
  final case class Add(left: Expr, right: Expr) extends Expr

  given Inspectable[Expr] = Inspectable.derived[Expr]

  final case class Envelope[A](value: A)

  given Inspectable[Envelope[String]] = Inspectable.derived[Envelope[String]]
}
