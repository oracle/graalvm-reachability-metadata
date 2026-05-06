/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_free_3

import cats.Contravariant
import cats.Eval
import cats.Id
import cats.arrow.FunctionK
import cats.catsInstancesForId
import cats.data.EitherK
import cats.free.Cofree
import cats.free.ContravariantCoyoneda
import cats.free.Coyoneda
import cats.free.Free
import cats.free.FreeApplicative
import cats.free.FreeInvariantMonoidal
import cats.free.FreeT
import cats.free.Trampoline
import cats.free.Yoneda
import cats.implicits.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

final case class Field[A](name: String, value: A)

final case class Encoder[A](encode: A => String)

class Cats_free_3Test {
  private val optionToList: FunctionK[Option, List] = new FunctionK[Option, List] {
    override def apply[A](fa: Option[A]): List[A] = fa.toList
  }

  private val listIdentity: FunctionK[List, List] = new FunctionK[List, List] {
    override def apply[A](fa: List[A]): List[A] = fa
  }

  private val fieldToId: FunctionK[Field, Id] = new FunctionK[Field, Id] {
    override def apply[A](fa: Field[A]): Id[A] = fa.value
  }

  private val fieldToOption: FunctionK[Field, Option] = new FunctionK[Field, Option] {
    override def apply[A](fa: Field[A]): Option[A] = Some(fa.value)
  }

  @Test
  def freeProgramsFoldCompileInjectAndResumeStackSafely(): Unit = {
    type Algebra[A] = EitherK[Option, List, A]

    val algebraToList: FunctionK[Algebra, List] = new FunctionK[Algebra, List] {
      override def apply[A](fa: Algebra[A]): List[A] = fa.run.fold(_.toList, identity)
    }

    val optionProgram: Free[Algebra, Int] = Free.liftInject[Algebra](Option(40))
    val listProgram: Free[Algebra, Int] = Free.liftInject[Algebra](List(1, 2, 3))
    val combined: Free[Algebra, Int] = for {
      base <- optionProgram
      increment <- listProgram
    } yield base + increment

    assertThat(combined.foldMap(algebraToList)).isEqualTo(List(41, 42, 43))

    val matchedOptionLayer: Option[Option[Free[Algebra, Int]]] = Free.match_[Algebra, Option, Int](optionProgram)
    assertThat(matchedOptionLayer.map(_.map(_.foldMap(algebraToList)))).isEqualTo(Some(Some(List(40))))

    val freeOption: Free[Option, Int] = Free.defer {
      Free.roll[Option, Int](Some(Free.pure[Option, Int](20)))
        .flatMap(value => Free.liftF(Option(value + 2)))
        .map(_ * 2)
    }
    val compiled: Free[List, Int] = freeOption.compile(optionToList)

    assertThat(freeOption.runTailRec).isEqualTo(Some(44))
    assertThat(compiled.foldMap(listIdentity)).isEqualTo(List(44))
  }

  @Test
  def trampolineEvaluatesDeepDeferredComputationsWithoutGrowingTheCallStack(): Unit = {
    def countDown(remaining: Int, total: Int): Trampoline[Int] =
      if remaining == 0 then Trampoline.done(total)
      else Trampoline.defer(countDown(remaining - 1, total + 1))

    val delayed: Trampoline[Int] = Trampoline.delay(21 + 21)

    assertThat(countDown(5000, 0).run).isEqualTo(5000)
    assertThat(delayed.run).isEqualTo(42)
  }

  @Test
  def freeApplicativeComposesIndependentFieldsAndAnalyzesStructure(): Unit = {
    val host: FreeApplicative[Field, String] = FreeApplicative.lift(Field("host", "db.local"))
    val port: FreeApplicative[Field, Int] = FreeApplicative.lift(Field("port", 5432))
    val endpoint: FreeApplicative[Field, String] = host.map2(port)((h, p) => s"$h:$p").map(_ + "/primary")

    val fieldNames: List[String] = endpoint.analyze[List[String]](new FunctionK[Field, [A] =>> List[String]] {
      override def apply[A](fa: Field[A]): List[String] = List(fa.name)
    })
    val compiledToOption: FreeApplicative[Option, String] = endpoint.compile(fieldToOption)
    val asFreeMonad: Free[Field, String] = endpoint.monad

    assertThat(endpoint.foldMap(fieldToId)).isEqualTo("db.local:5432/primary")
    assertThat(fieldNames).isEqualTo(List("host", "port"))
    assertThat(compiledToOption.fold).isEqualTo(Some("db.local:5432/primary"))
    assertThat(asFreeMonad.foldMap(fieldToId)).isEqualTo("db.local:5432/primary")
  }

  @Test
  def freeInvariantMonoidalCombinesProductsImapsCompilesAndAnalyzes(): Unit = {
    val enabled: FreeInvariantMonoidal[Field, Boolean] = FreeInvariantMonoidal.lift(Field("enabled", true))
    val retries: FreeInvariantMonoidal[Field, Int] = FreeInvariantMonoidal.lift(Field("retries", 3))
    val description: FreeInvariantMonoidal[Field, String] = enabled.product(retries).imap {
      case (isEnabled, retryCount) => s"enabled=$isEnabled,retries=$retryCount"
    } { text =>
      val isEnabled: Boolean = text.contains("enabled=true")
      val retryCount: Int = text.substring(text.lastIndexOf('=') + 1).toInt
      (isEnabled, retryCount)
    }

    val fieldNames: List[String] = description.analyze[List[String]](new FunctionK[Field, [A] =>> List[String]] {
      override def apply[A](fa: Field[A]): List[String] = List(fa.name)
    })
    val compiledToOption: FreeInvariantMonoidal[Option, String] = description.compile(fieldToOption)

    assertThat(description.foldMap(fieldToId)).isEqualTo("enabled=true,retries=3")
    assertThat(fieldNames).isEqualTo(List("enabled", "retries"))
    assertThat(compiledToOption.fold).isEqualTo(Some("enabled=true,retries=3"))
  }

  @Test
  def coyonedaAndYonedaFuseMapsAndTransformContexts(): Unit = {
    val coyoneda: Coyoneda[Option, String] = Coyoneda.lift[Option, Int](Some(2))
      .map(_ + 3)
      .map(value => s"value=$value")
    val coyonedaAsList: Coyoneda[List, String] = coyoneda.mapK(optionToList)
    val yoneda: Yoneda[Option, String] = Yoneda[Option, Int](Some(10))
      .map(_ * 2)
      .map(value => s"result=$value")
    val yonedaAsList: Yoneda[List, String] = yoneda.mapK(optionToList)

    assertThat(coyoneda.run).isEqualTo(Some("value=5"))
    assertThat(coyoneda.foldMap(optionToList)).isEqualTo(List("value=5"))
    assertThat(coyoneda.toYoneda.map(_.toUpperCase).run).isEqualTo(Some("VALUE=5"))
    assertThat(coyonedaAsList.run).isEqualTo(List("value=5"))
    assertThat(yoneda.run).isEqualTo(Some("result=20"))
    assertThat(yoneda.toCoyoneda.run).isEqualTo(Some("result=20"))
    assertThat(yonedaAsList.run).isEqualTo(List("result=20"))
  }

  @Test
  def contravariantCoyonedaDefersContramapsAndCanChangeContexts(): Unit = {
    implicit val encoderContravariant: Contravariant[Encoder] = new Contravariant[Encoder] {
      override def contramap[A, B](fa: Encoder[A])(f: B => A): Encoder[B] = Encoder(value => fa.encode(f(value)))
    }

    val lengthEncoder: ContravariantCoyoneda[Encoder, String] = ContravariantCoyoneda
      .lift[Encoder, Int](Encoder(number => s"length=$number"))
      .contramap[String](_.length)
    val prefixed: ContravariantCoyoneda[Encoder, String] = lengthEncoder.mapK(new FunctionK[Encoder, Encoder] {
      override def apply[A](fa: Encoder[A]): Encoder[A] = Encoder(value => s"encoded:${fa.encode(value)}")
    })

    assertThat(lengthEncoder.run.encode("cats")).isEqualTo("length=4")
    assertThat(lengthEncoder.foldMap(FunctionK.id[Encoder]).encode("typelevel")).isEqualTo("length=9")
    assertThat(prefixed.run.encode("free")).isEqualTo("encoded:length=4")
  }

  @Test
  def cofreeBuildsLazyTreesAndFoldsMapsAndCoflatMapsThem(): Unit = {
    val tree: Cofree[Option, Int] = Cofree.unfold[Option, Int](1) { value =>
      if value < 4 then Some(value + 1) else None
    }
    val doubled: Cofree[Option, Int] = tree.map(_ * 2)
    val subtreeSums: Cofree[Option, Int] = tree.coflatMap { subtree =>
      subtree.head + subtree.tailForced.map(_.head).getOrElse(0)
    }
    val asListBranching: Cofree[List, Int] = tree.mapBranchingS(optionToList)
    val sum: Int = Cofree.cata(tree) { (value, childSum: Option[Int]) =>
      Eval.now(value + childSum.getOrElse(0))
    }.value
    val product: Id[Int] = Cofree.cataM[Option, Id, Int, Int](tree) { (value, childProduct: Option[Int]) =>
      value * childProduct.getOrElse(1)
    }(new FunctionK[Eval, Id] {
      override def apply[A](fa: Eval[A]): Id[A] = fa.value
    })

    assertThat(tree.head).isEqualTo(1)
    assertThat(tree.tailForced.map(_.head)).isEqualTo(Some(2))
    assertThat(doubled.forceAll.tailForced.map(_.head)).isEqualTo(Some(4))
    assertThat(subtreeSums.head).isEqualTo(3)
    assertThat(asListBranching.tailForced.map(_.head)).isEqualTo(List(2))
    assertThat(sum).isEqualTo(10)
    assertThat(product).isEqualTo(24)
  }

  @Test
  def freeTCombinesOuterEffectsWithFreeSuspensionsAndInterpreters(): Unit = {
    val program: FreeT[List, Option, Int] = FreeT.liftT[List, Option, Int](Some(2)).flatMap { base =>
      FreeT.liftF[List, Option, Int](List(base + 1, base + 2)).map(_ * 10)
    }
    val counted: FreeT[List, Option, Int] = FreeT.tailRecM[List, Option, Int, Int](0) { value =>
      FreeT.pure[List, Option, Either[Int, Int]] {
        if value < 5 then Left(value + 1) else Right(value)
      }
    }
    val compiled: FreeT[Vector, Option, Int] = program.compile(new FunctionK[List, Vector] {
      override def apply[A](fa: List[A]): Vector[A] = fa.toVector
    })
    val hoisted: FreeT[List, List, Int] = program.hoist(optionToList)

    assertThat(program.foldMap(new FunctionK[List, Option] {
      override def apply[A](fa: List[A]): Option[A] = fa.headOption
    })).isEqualTo(Some(30))
    assertThat(counted.foldMap(new FunctionK[List, Option] {
      override def apply[A](fa: List[A]): Option[A] = fa.headOption
    })).isEqualTo(Some(5))
    assertThat(compiled.foldMap(new FunctionK[Vector, Option] {
      override def apply[A](fa: Vector[A]): Option[A] = fa.headOption
    })).isEqualTo(Some(30))
    assertThat(hoisted.foldMap(listIdentity)).isEqualTo(List(30, 40))
  }
}
