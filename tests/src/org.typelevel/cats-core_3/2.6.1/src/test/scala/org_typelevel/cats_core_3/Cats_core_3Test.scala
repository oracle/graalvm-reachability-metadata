/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_core_3

import cats.*
import cats.data.*
import cats.implicits.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Cats_core_3Test {
  private final case class Registration(name: String, age: Int, email: String)
  private final case class ServiceConfig(host: String, port: Int)
  private final case class User(name: String, age: Int)

  @Test
  def evalSupportsMemoizationRepeatedEvaluationAndStackSafeDeferral(): Unit = {
    var memoizedEvaluations: Int = 0
    val memoized: Eval[String] = Eval.later {
      memoizedEvaluations += 1
      s"computed-$memoizedEvaluations"
    }

    assertThat(memoized.value).isEqualTo("computed-1")
    assertThat(memoized.value).isEqualTo("computed-1")
    assertThat(memoizedEvaluations).isEqualTo(1)

    var repeatedEvaluations: Int = 0
    val repeated: Eval[Int] = Eval.always {
      repeatedEvaluations += 1
      repeatedEvaluations
    }

    assertThat(repeated.value).isEqualTo(1)
    assertThat(repeated.value).isEqualTo(2)

    def sumFrom(current: Int, limit: Int, accumulated: Long): Eval[Long] = {
      if current > limit then Eval.now(accumulated)
      else Eval.defer(sumFrom(current + 1, limit, accumulated + current.toLong))
    }

    assertThat(sumFrom(1, 10000, 0L).value).isEqualTo(50005000L)
  }

  @Test
  def validatedNelAccumulatesIndependentValidationFailures(): Unit = {
    val validRegistration: ValidatedNel[String, Registration] = validateRegistration(
      name = "Ada",
      age = 36,
      email = "ada@example.test"
    )

    assertThat(validRegistration.toEither).isEqualTo(Right(Registration("Ada", 36, "ada@example.test")))

    val invalidRegistration: ValidatedNel[String, Registration] = validateRegistration(
      name = "  ",
      age = -1,
      email = "not-an-email"
    )

    invalidRegistration match {
      case Validated.Invalid(errors) =>
        assertThat(errors.toList).isEqualTo(
          List("name is required", "age must be non-negative", "email must contain @")
        )
      case Validated.Valid(value) =>
        throw new AssertionError(s"Expected validation failures, got $value")
    }
  }

  @Test
  def parallelEitherSyntaxAccumulatesErrorsWhileSequentialEitherShortCircuits(): Unit = {
    val missingName: Either[List[String], String] = Left(List("name missing"))
    val missingAge: Either[List[String], Int] = Left(List("age missing"))

    val sequential: Either[List[String], (String, Int)] = for {
      name <- missingName
      age <- missingAge
    } yield (name, age)
    val parallel: Either[List[String], (String, Int)] = (missingName, missingAge).parMapN((name, age) => (name, age))

    assertThat(sequential).isEqualTo(Left(List("name missing")))
    assertThat(parallel).isEqualTo(Left(List("name missing", "age missing")))

    val validName: Either[List[String], String] = Right("Grace")
    val validAge: Either[List[String], Int] = Right(85)
    val user: Either[List[String], User] = (validName, validAge).parMapN((name, age) => User(name, age))

    assertThat(user).isEqualTo(Right(User("Grace", 85)))
  }

  @Test
  def optionTAndEitherTComposeNestedEffectfulComputations(): Unit = {
    val options: OptionT[List, Int] = OptionT(List(Some(1), None, Some(3)))
    val incrementedEvenValues: OptionT[List, Int] = options.map(_ + 1).filter(_ % 2 == 0)

    assertThat(incrementedEvenValues.value).isEqualTo(List(Some(2), None, Some(4)))

    val either: EitherT[Option, String, Int] = EitherT(Option(Right(21)))
    val successful: EitherT[Option, String, Int] = either.map(_ * 2).semiflatMap(value => Option(value + 1))
    val guarded: EitherT[Option, String, Int] = either.ensure("value was too small")(_ > 25)

    assertThat(successful.value).isEqualTo(Some(Right(43)))
    assertThat(guarded.value).isEqualTo(Some(Left("value was too small")))
  }

  @Test
  def stateWriterReaderAndKleisliModelCommonFunctionalWorkflows(): Unit = {
    val counter: State[Int, String] = for {
      initial <- State.get[Int]
      _ <- State.modify[Int](_ + 3)
      updated <- State.get[Int]
    } yield s"$initial->$updated"

    assertThat(counter.run(4).value).isEqualTo((7, "4->7"))
    assertThat(counter.runA(4).value).isEqualTo("4->7")

    val writer: Writer[Vector[String], Int] = Writer(Vector("read base"), 5)
      .flatMap(value => Writer(Vector("doubled"), value * 2))

    assertThat(writer.run).isEqualTo((Vector("read base", "doubled"), 10))

    val endpoint: Reader[ServiceConfig, String] = Reader(config => s"${config.host}:${config.port}")
    val renderedEndpoint: String = endpoint.map(_.toUpperCase).run(ServiceConfig("localhost", 8080))

    assertThat(renderedEndpoint).isEqualTo("LOCALHOST:8080")

    val parseInt: Kleisli[Option, String, Int] = Kleisli(text => text.toIntOption)
    val reciprocal: Kleisli[Option, Int, Double] = Kleisli(value => if value == 0 then None else Some(1.0 / value))
    val pipeline: Kleisli[Option, String, Double] = parseInt.andThen(reciprocal)

    assertThat(pipeline.run("4")).isEqualTo(Some(0.25))
    assertThat(pipeline.run("0")).isEqualTo(None)
    assertThat(pipeline.run("not-a-number")).isEqualTo(None)
  }

  @Test
  def traverseFoldableAndNonEmptyCollectionsProvideGenericCollectionOperations(): Unit = {
    val traversed: Option[List[Int]] = Traverse[List].traverse(List(1, 2, 3))(value => Option.when(value < 4)(value * 2))
    val failedTraverse: Option[List[Int]] = Traverse[List].traverse(List(1, 2, 5))(value => Option.when(value < 4)(value * 2))
    val totalLength: Int = Foldable[List].foldMap(List("a", "bb", "ccc"))(_.length)
    val nonEmpty: NonEmptyList[Int] = NonEmptyList.of(1, 2, 3, 4)

    assertThat(traversed).isEqualTo(Some(List(2, 4, 6)))
    assertThat(failedTraverse).isEqualTo(None)
    assertThat(totalLength).isEqualTo(6)
    assertThat(Reducible[NonEmptyList].reduce(nonEmpty)).isEqualTo(10)
    assertThat(nonEmpty.map(_ * 3).toList).isEqualTo(List(3, 6, 9, 12))
  }

  @Test
  def iorCombinesWarningsWithSuccessfulValues(): Unit = {
    val first: Ior[Chain[String], Int] = Ior.Both(Chain.one("rounded input"), 2)
    val combined: Ior[Chain[String], Int] = first.flatMap { value =>
      Ior.Both(Chain.one("used fallback multiplier"), value * 3)
    }
    val transformed: Ior[String, Int] = Ior.Both("warning", 10).leftMap(_.toUpperCase).map(_ + 5)

    combined match {
      case Ior.Both(warnings, value) =>
        assertThat(warnings.toList).isEqualTo(List("rounded input", "used fallback multiplier"))
        assertThat(value).isEqualTo(6)
      case other =>
        throw new AssertionError(s"Expected warnings and a value, got $other")
    }
    assertThat(transformed).isEqualTo(Ior.Both("WARNING", 15))
  }

  @Test
  def coreTypeClassesAndSyntaxComposeDomainValues(): Unit = {
    val mergedCounts: Map[String, Int] = Monoid[Map[String, Int]].combine(
      Map("apples" -> 2, "pears" -> 1),
      Map("apples" -> 3, "oranges" -> 4)
    )
    val optionalTotal: Option[Int] = Apply[Option].map2(Some(2), Some(40))(_ + _)
    val identityResult: Id[Int] = Monad[Id].flatMap(40)(_ + 2)
    val tupleShow: Show[(String, Int)] = Show.show { case (name, age) => s"$name is $age" }
    val userShow: Show[User] = tupleShow.contramap((user: User) => (user.name, user.age))
    val userOrder: Order[User] = Order.by[User, Int](_.age)

    assertThat(mergedCounts).isEqualTo(Map("apples" -> 5, "pears" -> 1, "oranges" -> 4))
    assertThat(optionalTotal).isEqualTo(Some(42))
    assertThat(identityResult).isEqualTo(42)
    assertThat(userShow.show(User("Ada", 36))).isEqualTo("Ada is 36")
    assertThat(userOrder.compare(User("Grace", 85), User("Ada", 36))).isGreaterThan(0)
    assertThat(Eq[List[Int]].eqv(List(1, 2, 3), List(1, 2, 3))).isTrue
  }

  @Test
  def nestedMapsThroughStackedFunctorContexts(): Unit = {
    val nested: Nested[Option, List, Int] = Nested(Some(List(1, 2, 3)))
    val mapped: Nested[Option, List, String] = nested.map(value => s"value-${value + 1}")

    assertThat(mapped.value).isEqualTo(Some(List("value-2", "value-3", "value-4")))
  }

  @Test
  def andThenComposesLargeFunctionPipelinesStackSafely(): Unit = {
    val identityPipeline: AndThen[Int, Int] = AndThen((value: Int) => value)
    val increment: Int => Int = value => value + 1
    val pipeline: AndThen[Int, Int] = (1 to 10000).foldLeft(identityPipeline) { (current, _) =>
      current.andThen(increment)
    }
    val formatted: AndThen[Int, String] = pipeline.andThen(value => s"total=$value")
    val parsed: AndThen[String, Int] = AndThen((value: String) => value.stripPrefix("total=").toInt)

    assertThat(pipeline(0)).isEqualTo(10000)
    assertThat(formatted(5)).isEqualTo("total=10005")
    assertThat(formatted.andThen(parsed)(7)).isEqualTo(10007)
    assertThat(parsed.compose(formatted)(9)).isEqualTo(10009)
  }

  private def validateRegistration(name: String, age: Int, email: String): ValidatedNel[String, Registration] = {
    (nonBlank("name", name), nonNegativeAge(age), validEmail(email)).mapN { (validName, validAge, validEmail) =>
      Registration(validName, validAge, validEmail)
    }
  }

  private def nonBlank(field: String, value: String): ValidatedNel[String, String] = {
    val trimmed: String = value.trim
    if trimmed.nonEmpty then trimmed.validNel[String]
    else s"$field is required".invalidNel[String]
  }

  private def nonNegativeAge(age: Int): ValidatedNel[String, Int] = {
    if age >= 0 then age.validNel[String]
    else "age must be non-negative".invalidNel[Int]
  }

  private def validEmail(email: String): ValidatedNel[String, String] = {
    if email.contains("@") then email.validNel[String]
    else "email must contain @".invalidNel[String]
  }
}
