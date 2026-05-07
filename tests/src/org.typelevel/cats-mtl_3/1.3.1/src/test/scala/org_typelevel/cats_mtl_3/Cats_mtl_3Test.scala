/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_mtl_3

import cats.Id
import cats.implicits.*
import cats.data.EitherT
import cats.data.Ior
import cats.data.IorT
import cats.data.Kleisli
import cats.data.OptionT
import cats.data.ReaderWriterStateT
import cats.data.StateT
import cats.data.Validated
import cats.data.WriterT
import cats.mtl.Ask
import cats.mtl.Chronicle
import cats.mtl.Censor
import cats.mtl.Handle
import cats.mtl.Listen
import cats.mtl.Local
import cats.mtl.MonadPartialOrder
import cats.mtl.Raise
import cats.mtl.Stateful
import cats.mtl.Tell
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Cats_mtl_3Test {
  @Test
  def askAndLocalUseKleisliEnvironment(): Unit = {
    type Env[A] = Kleisli[Id, Map[String, Int], A]

    val program: Env[Int] = for {
      environment <- Ask.ask[Env, Map[String, Int]]
      bonus <- Ask.reader[Env, Map[String, Int], Int](_("bonus"))
      base <- Ask.reader[Env, Map[String, Int], Int](_("base"))
    } yield environment("base") + bonus + base

    assertEquals(11, program.run(Map("base" -> 3, "bonus" -> 5)))

    val scoped: Env[Int] = Local.scope(program)(Map("base" -> 10, "bonus" -> 1))
    assertEquals(21, scoped.run(Map("base" -> 0, "bonus" -> 0)))

    val localized: Env[Int] = Local[Env, Map[String, Int]].local(program)(_ + ("bonus" -> 7))
    assertEquals(13, localized.run(Map("base" -> 3, "bonus" -> 1)))
  }

  @Test
  def tellListenAndCensorUseWriterTLogs(): Unit = {
    type Log[A] = WriterT[Id, Vector[String], A]

    val tell: Tell[Log, Vector[String]] = Tell[Log, Vector[String]]
    val program: Log[(Int, Int, (String, Vector[String]), (String, Int))] = for {
      _ <- tell.tell(Vector("start"))
      written <- tell.writer(7, Vector("computed"))
      tupled <- tell.tuple((Vector("tupled"), 9))
      listened <- Listen[Log, Vector[String]].listen(tell.tell(Vector("inside")).as("value"))
      summarized <- Listen[Log, Vector[String]].listens(tell.writer("result", Vector("summary")))(_.size)
    } yield (written, tupled, listened, summarized)

    val (log, value) = program.run
    assertEquals(Vector("start", "computed", "tupled", "inside", "summary"), log)
    assertEquals((7, 9, ("value", Vector("inside")), ("result", 1)), value)

    val censored: Log[(Int, Int, (String, Vector[String]), (String, Int))] =
      Censor[Log, Vector[String]].censor(program)(_.map(_.toUpperCase))
    assertEquals(Vector("START", "COMPUTED", "TUPLED", "INSIDE", "SUMMARY"), censored.run._1)

    val cleared: Log[(Int, Int, (String, Vector[String]), (String, Int))] =
      Censor[Log, Vector[String]].clear(program)
    assertEquals(Vector.empty[String], cleared.run._1)
    assertEquals(value, cleared.run._2)
  }

  @Test
  def raiseAndHandleCoverEitherOptionAndValidated(): Unit = {
    type ErrorOr[A] = Either[String, A]
    type ValidatedString[A] = Validated[String, A]

    val handle: Handle[ErrorOr, String] = Handle[ErrorOr, String]
    val raised: ErrorOr[Int] = Raise[ErrorOr, String].raise[String, Int]("bad")

    assertEquals(Left("bad"), raised)
    assertEquals(Right(3), handle.handle(raised)(_.length))
    assertEquals(Right(Left("bad")), handle.attempt(raised))
    assertEquals(Right(5), Raise[ErrorOr, String].ensure(Right(5))("too small")(_ > 3))
    assertEquals(Left("too small"), Raise[ErrorOr, String].ensure(Right(2))("too small")(_ > 3))
    assertEquals(
      Left("boom"),
      Raise[ErrorOr, String].catchNonFatal(throw new IllegalArgumentException("boom"))(_.getMessage)
    )
    assertEquals(Left("missing"), Raise[ErrorOr, String].raise[String, Int]("missing"))

    assertEquals(None, Raise[Option, Unit].raise[Unit, Int](()))
    assertEquals(Some(10), Handle[Option, Unit].handle(None: Option[Int])(_ => 10))

    val invalid: ValidatedString[Int] = Validated.Invalid("invalid")
    assertEquals(Validated.Valid(12), Handle[ValidatedString, String].handle(invalid)(_ => 12))
  }

  @Test
  def statefulAndMonadPartialOrderLiftEffectsIntoStatefulStacks(): Unit = {
    type Counter[A] = StateT[Id, Int, A]

    val program: Counter[(Int, String)] = for {
      before <- Stateful.get[Counter, Int]
      _ <- Stateful.modify[Counter, Int](_ + 5)
      after <- Stateful.inspect[Counter, Int, String](state => s"value=$state")
      _ <- Stateful.set[Counter, Int](99)
    } yield (before, after)

    assertEquals((99, (10, "value=15")), program.run(10))

    val lift: MonadPartialOrder[Id, Counter] = MonadPartialOrder[Id, Counter]
    assertEquals((7, 42), lift(42).run(7))

    type Rw[A] = ReaderWriterStateT[Id, String, Vector[String], Int, A]
    val rwProgram: Rw[Int] = for {
      environment <- Ask.ask[Rw, String]
      _ <- Tell.tell[Rw, Vector[String]](Vector(s"env=$environment"))
      current <- Stateful.get[Rw, Int]
      _ <- Stateful.set[Rw, Int](current + environment.length)
    } yield current

    assertEquals((Vector("env=abc"), 7, 4), rwProgram.run("abc", 4))
  }

  @Test
  def chronicleHandlesWarningsAndFatalErrorsWithIor(): Unit = {
    type WarningOr[A] = Ior[String, A]

    val chronicle: Chronicle[WarningOr, String] = Chronicle[WarningOr, String]

    assertEquals(Ior.Both("note", ()), chronicle.dictate("note"))
    assertEquals(Ior.Left("fatal"), chronicle.confess[Int]("fatal"))
    assertEquals(Ior.Right(Ior.Both("warn", 3)), chronicle.materialize(Ior.Both("warn", 3)))
    assertEquals(Ior.Both("warn", Right(3)), chronicle.memento(Ior.Both("warn", 3)))
    assertEquals(Ior.Right(0), chronicle.absolve(Ior.Left("fatal"))(0))
    assertEquals(Ior.Left("warn"), chronicle.condemn(Ior.Both("warn", 3)))
    assertEquals(Ior.Both("WARN", 3), chronicle.retcon(Ior.Both("warn", 3))(_.toUpperCase))
    assertEquals(Ior.Both("logged", ()), chronicle.dictate("logged"))
    assertEquals(Ior.Both("w", 6), chronicle.chronicle(Ior.Both("w", 6)))
  }

  @Test
  def transformerInstancesComposeCapabilities(): Unit = {
    type ErrorOr[A] = Either[String, A]
    type LoggedEither[A] = WriterT[ErrorOr, Vector[String], A]

    val recovered: LoggedEither[Int] = Handle[LoggedEither, String].handleWith(
      Handle[LoggedEither, String].raise[String, Int]("bad")
    )(error => WriterT[ErrorOr, Vector[String], Int](Right((Vector("recovered"), error.length))))

    assertEquals(Right((Vector("recovered"), 3)), recovered.run)

    type Env[A] = Kleisli[Id, Int, A]
    type OptionalEnv[A] = OptionT[Env, A]

    val optionalProgram: OptionalEnv[Int] = for {
      value <- Ask.ask[OptionalEnv, Int]
      doubled <- OptionT.some[Env](value * 2)
    } yield doubled

    val scoped: OptionalEnv[Int] = Local.scope(optionalProgram)(21)
    assertEquals(Some(42), scoped.value.run(0))

    type Warnings[A] = IorT[Id, String, A]
    val warnings: Warnings[Int] = for {
      _ <- Chronicle.dictate[Warnings, String]("w1")
      value <- IorT[Id, String, Int](Ior.Right(2))
      _ <- Chronicle.dictate[Warnings, String]("w2")
    } yield value + 1

    assertEquals(Ior.Both("w1w2", 3), warnings.value)

    val eitherWarnings: EitherT[Warnings, String, Int] =
      EitherT[Warnings, String, Int](IorT[Id, String, Either[String, Int]](Ior.Right(Right(5))))
    assertEquals(Ior.Right(Right(5)), eitherWarnings.value.value)
  }
}
