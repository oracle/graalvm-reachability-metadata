/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.simulacrum_scalafix_annotations_3

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import simulacrum.noop
import simulacrum.op
import simulacrum.typeclass

import scala.annotation.Annotation

class Simulacrum_scalafix_annotations_3Test {
  @Test
  def constructsEveryAnnotationWithDefaultAndExplicitArguments(): Unit = {
    val annotations: List[Annotation] = List(
      new typeclass(),
      new typeclass(excludeParents = List("scala.AnyRef", "java.io.Serializable"), generateAllOps = false),
      new op("combine"),
      new op(name = "combineAlias", alias = true),
      new noop()
    )

    assertEquals(5, annotations.size)
    assertTrue(annotations.forall(_.isInstanceOf[Annotation]))
  }

  @Test
  def annotatedTypeclassAndOperationsCanBeUsedInOrdinaryScalaCode(): Unit = {
    import SimulacrumAnnotationFixtures.Semigroup
    import SimulacrumAnnotationFixtures.Semigroup.given
    import SimulacrumAnnotationFixtures.Semigroup.syntax.*

    assertEquals(3, Semigroup[Int].combine(1, 2))
    assertEquals("left:right", Semigroup[String].combine("left", "right"))
    assertEquals(42, 42.combineWith(0))
    assertEquals("a:b:c", "a".combineWith("b").combineWith("c"))
    assertEquals("combines two values of the same type", Semigroup[Int].documentation)
  }

  @Test
  def explicitTypeclassOptionsAndNoopMembersDoNotChangeRuntimeSemantics(): Unit = {
    import SimulacrumAnnotationFixtures.Renderer
    import SimulacrumAnnotationFixtures.Renderer.given

    val booleanRenderer: Renderer[Boolean] = Renderer[Boolean]
    val optionRenderer: Renderer[Option[Int]] = Renderer.optionRenderer[Int]

    assertEquals("true", booleanRenderer.render(true))
    assertEquals("false", Renderer[Boolean].render(false))
    assertEquals("Some(7)", optionRenderer.render(Some(7)))
    assertEquals("None", optionRenderer.render(None))
    assertEquals("renderer", booleanRenderer.annotationOnlyDescription)
  }

  @Test
  def annotatedHigherKindedTypeclassSupportsPolymorphicCurriedOperations(): Unit = {
    import SimulacrumAnnotationFixtures.Functor
    import SimulacrumAnnotationFixtures.Functor.given
    import SimulacrumAnnotationFixtures.Functor.syntax.*

    val numbers: List[Int] = List(1, 2, 3)

    assertEquals(
      List("n=1", "n=2", "n=3"),
      Functor[List].map(numbers)(number => s"n=$number")
    )
    assertEquals(Some(4), Option(2).fmap(number => number * 2))
    assertEquals(None, (None: Option[Int]).fmap(number => number + 1))
  }

  @Test
  def typeclassCanDeclareMultipleAnnotatedOperationsWithIndependentSyntax(): Unit = {
    import SimulacrumAnnotationFixtures.Codec
    import SimulacrumAnnotationFixtures.Codec.given
    import SimulacrumAnnotationFixtures.Codec.syntax.*

    assertEquals("123", Codec[Int].encode(123))
    assertEquals(Right(123), Codec[Int].decode("123"))
    assertEquals(Left("not an integer: abc"), Codec[Int].decode("abc"))
    assertEquals("scala", "scala".encodeAs)
    assertEquals(Right("native"), "native".decodeAs[String])
    assertEquals(Right(42), "42".decodeAs[Int])
  }
}

private object SimulacrumAnnotationFixtures {
  @typeclass
  trait Semigroup[A] {
    @op("combineWith")
    def combine(left: A, right: A): A

    @noop
    def documentation: String = "combines two values of the same type"
  }

  object Semigroup {
    def apply[A](using instance: Semigroup[A]): Semigroup[A] = instance

    given Semigroup[Int] with {
      override def combine(left: Int, right: Int): Int = left + right
    }

    given Semigroup[String] with {
      override def combine(left: String, right: String): String = s"$left:$right"
    }

    object syntax {
      extension [A](left: A)(using instance: Semigroup[A]) {
        def combineWith(right: A): A = instance.combine(left, right)
      }
    }
  }

  @typeclass(excludeParents = List("scala.Product"), generateAllOps = false)
  trait Renderer[A] extends Serializable {
    @op(name = "renderAsString", alias = true)
    def render(value: A): String

    @noop
    def annotationOnlyDescription: String = "renderer"
  }

  object Renderer {
    def apply[A](using instance: Renderer[A]): Renderer[A] = instance

    given Renderer[Boolean] with {
      override def render(value: Boolean): String = value.toString
    }

    def optionRenderer[A]: Renderer[Option[A]] = new Renderer[Option[A]] {
      override def render(value: Option[A]): String = value.fold("None")(element => s"Some($element)")
    }
  }

  @typeclass
  trait Functor[F[_]] {
    @op("fmap")
    def map[A, B](fa: F[A])(function: A => B): F[B]
  }

  object Functor {
    def apply[F[_]](using instance: Functor[F]): Functor[F] = instance

    given Functor[List] with {
      override def map[A, B](fa: List[A])(function: A => B): List[B] = fa.map(function)
    }

    given Functor[Option] with {
      override def map[A, B](fa: Option[A])(function: A => B): Option[B] = fa.map(function)
    }

    object syntax {
      extension [F[_], A](fa: F[A])(using instance: Functor[F]) {
        def fmap[B](function: A => B): F[B] = instance.map(fa)(function)
      }
    }
  }

  @typeclass
  trait Codec[A] {
    @op("encodeAs")
    def encode(value: A): String

    @op("decodeAs")
    def decode(value: String): Either[String, A]
  }

  object Codec {
    def apply[A](using instance: Codec[A]): Codec[A] = instance

    given Codec[Int] with {
      override def encode(value: Int): String = value.toString

      override def decode(value: String): Either[String, Int] =
        value.toIntOption.toRight(s"not an integer: $value")
    }

    given Codec[String] with {
      override def encode(value: String): String = value

      override def decode(value: String): Either[String, String] = Right(value)
    }

    object syntax {
      extension [A](value: A)(using instance: Codec[A]) {
        def encodeAs: String = instance.encode(value)
      }

      extension (value: String) {
        def decodeAs[A](using instance: Codec[A]): Either[String, A] = instance.decode(value)
      }
    }
  }
}
