/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_cats_3

import cats.Functor
import cats.InvariantMonoidal
import cats.InvariantSemigroupal
import cats.data.Chain
import cats.data.NonEmptyChain
import cats.data.NonEmptyList
import cats.data.NonEmptySet
import cats.data.NonEmptyVector
import cats.~>
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import sttp.monad.MonadError
import sttp.tapir.Codec
import sttp.tapir.CodecFormat
import sttp.tapir.DecodeResult
import sttp.tapir.EndpointIO
import sttp.tapir.EndpointInput
import sttp.tapir.EndpointOutput
import sttp.tapir.ModifyFunctor
import sttp.tapir.PublicEndpoint
import sttp.tapir.Schema
import sttp.tapir.Validator
import sttp.tapir.endpoint
import sttp.tapir.header
import sttp.tapir.path
import sttp.tapir.query
import sttp.tapir.stringBody
import sttp.tapir.integ.cats.ValidatorCats
import sttp.tapir.integ.cats.codec._
import sttp.tapir.integ.cats.instances
import sttp.tapir.integ.cats.syntax._
import sttp.tapir.server.ServerEndpoint


class Tapir_cats_3Test {
  @Test
  def catsCollectionCodecsDecodeEncodeAndRejectEmptyInputs(): Unit = {
    val nelCodec: Codec[List[String], NonEmptyList[Int], CodecFormat.TextPlain] = implicitly
    val nevCodec: Codec[List[String], NonEmptyVector[Int], CodecFormat.TextPlain] = implicitly
    val chainCodec: Codec[List[String], Chain[Int], CodecFormat.TextPlain] = implicitly
    val necCodec: Codec[List[String], NonEmptyChain[Int], CodecFormat.TextPlain] = implicitly
    val nesCodec: Codec[List[String], NonEmptySet[Int], CodecFormat.TextPlain] = implicitly

    assertDecodeValue(nelCodec.decode(List("1", "2", "3")), NonEmptyList.of(1, 2, 3))
    assertEquals(List("1", "2", "3"), nelCodec.encode(NonEmptyList.of(1, 2, 3)))
    assertNotDecoded(nelCodec.decode(Nil))

    assertDecodeValue(nevCodec.decode(List("4", "5")), NonEmptyVector.of(4, 5))
    assertEquals(List("4", "5"), nevCodec.encode(NonEmptyVector.of(4, 5)))
    assertNotDecoded(nevCodec.decode(Nil))

    assertDecodeValue(chainCodec.decode(List("6", "7")), Chain(6, 7))
    assertEquals(List("6", "7"), chainCodec.encode(Chain(6, 7)))

    assertDecodeValue(necCodec.decode(List("8", "9")), NonEmptyChain(8, 9))
    assertEquals(List("8", "9"), necCodec.encode(NonEmptyChain(8, 9)))
    assertNotDecoded(necCodec.decode(Nil))

    assertDecodeValue(nesCodec.decode(List("10", "11", "10")), NonEmptySet.of(10, 11))
    assertEquals(Set("10", "11"), nesCodec.encode(NonEmptySet.of(10, 11)).toSet)
    assertNotDecoded(nesCodec.decode(Nil))
  }

  @Test
  def catsCollectionSchemasAreAvailableForTapirEndpoints(): Unit = {
    val nelSchema: Schema[NonEmptyList[String]] = implicitly
    val nevSchema: Schema[NonEmptyVector[String]] = implicitly
    val chainSchema: Schema[Chain[String]] = implicitly
    val necSchema: Schema[NonEmptyChain[String]] = implicitly
    val nesSchema: Schema[NonEmptySet[String]] = implicitly

    assertNotNull(nelSchema)
    assertNotNull(nevSchema)
    assertNotNull(chainSchema)
    assertNotNull(necSchema)
    assertNotNull(nesSchema)

    val endpointWithCatsTypes: PublicEndpoint[
      (NonEmptyList[String], NonEmptyVector[Int], Chain[String], NonEmptyChain[Int], NonEmptySet[String]),
      Unit,
      String,
      Any
    ] = endpoint.get
      .in(query[NonEmptyList[String]]("names"))
      .in(query[NonEmptyVector[Int]]("scores"))
      .in(query[Chain[String]]("tags"))
      .in(query[NonEmptyChain[Int]]("priorities"))
      .in(query[NonEmptySet[String]]("roles"))
      .out(stringBody)

    assertNotNull(endpointWithCatsTypes)
  }

  @Test
  def nonEmptyFoldableValidatorUsesCatsFoldableForDifferentCollections(): Unit = {
    val listValidator: Validator[List[String]] = ValidatorCats.nonEmptyFoldable[List, String]
    val vectorValidator: Validator[Vector[Int]] = ValidatorCats.nonEmptyFoldable[Vector, Int]
    val chainValidator: Validator[Chain[String]] = ValidatorCats.nonEmptyFoldable[Chain, String]

    assertTrue(listValidator(Nil).nonEmpty)
    assertTrue(vectorValidator(Vector.empty).nonEmpty)
    assertTrue(chainValidator(Chain.empty).nonEmpty)
    assertTrue(listValidator(List("tapir")).isEmpty)
    assertTrue(vectorValidator(Vector(1)).isEmpty)
    assertTrue(chainValidator(Chain.one("cats")).isEmpty)
  }

  @Test
  def invariantInstancesComposeEndpointInputsOutputsAndEndpointIO(): Unit = {
    val inputInstance: InvariantSemigroupal[EndpointInput] = instances.endpointInputInvariantSemigroupal
    val outputInstance: InvariantMonoidal[EndpointOutput] = instances.endpointOutputInvariantMonoidal
    val ioInstance: InvariantSemigroupal[EndpointIO] = instances.endpointIOInvariantSemigroupal

    val tupledInput: EndpointInput[(String, Int)] = inputInstance.product(path[String]("name"), query[Int]("age"))
    val personInput: EndpointInput[Person] = inputInstance.imap(tupledInput)(
      { case (name: String, age: Int) => Person(name, age) }
    )(
      (person: Person) => (person.name, person.age)
    )

    val tupledOutput: EndpointOutput[(String, String)] = outputInstance.product(stringBody, header[String]("X-Trace-Id"))
    val responseOutput: EndpointOutput[GreetingResponse] = outputInstance.imap(tupledOutput)(
      { case (body: String, traceId: String) => GreetingResponse(body, traceId) }
    )(
      (response: GreetingResponse) => (response.body, response.traceId)
    )

    val unitOutput: EndpointOutput[Unit] = outputInstance.unit
    val tupledIO: EndpointIO[(String, String)] = ioInstance.product(header[String]("X-Request-Id"), stringBody)
    val requestContextIO: EndpointIO[RequestContext] = ioInstance.imap(tupledIO)(
      { case (requestId: String, payload: String) => RequestContext(payload, requestId) }
    )(
      (context: RequestContext) => (context.requestId, context.session)
    )

    val composedEndpoint: PublicEndpoint[(Person, RequestContext), Unit, GreetingResponse, Any] = endpoint.post
      .in(personInput)
      .in(requestContextIO)
      .out(responseOutput)

    assertNotNull(unitOutput)
    assertNotNull(composedEndpoint)
  }

  @Test
  def exampleFunctorMapsEndpointExamplesAndPreservesMetadata(): Unit = {
    val functor: Functor[EndpointIO.Example] = instances.exampleFunctor
    val example: EndpointIO.Example[String] = EndpointIO.Example
      .of("41")
      .name("status")
      .summary("successful response")
      .description("An example response body")

    val mapped: EndpointIO.Example[Int] = functor.map(example)(_.toInt + 1)

    assertEquals(42, mapped.value)
    assertEquals(example.name, mapped.name)
    assertEquals(example.summary, mapped.summary)
    assertEquals(example.description, mapped.description)
  }

  @Test
  def modifyFunctorInstancesAreAvailableForCatsCollections(): Unit = {
    val nelFunctor: ModifyFunctor[NonEmptyList, String] = instances.nonEmptyListModifyFuntor[String]
    val nesFunctor: ModifyFunctor[NonEmptySet, Int] = instances.nonEmptySetModifyFunctor[Int]
    val chainFunctor: ModifyFunctor[Chain, String] = instances.chainModifyFunctor[String]
    val necFunctor: ModifyFunctor[NonEmptyChain, Int] = instances.nonEmptyChainModifyFunctor[Int]

    assertNotNull(nelFunctor)
    assertNotNull(nesFunctor)
    assertNotNull(chainFunctor)
    assertNotNull(necFunctor)
  }

  @Test
  def monadErrorImapKTranslatesAllCoreOperations(): Unit = {
    val toBox: EitherThrowable ~> Box = new (EitherThrowable ~> Box) {
      override def apply[A](fa: EitherThrowable[A]): Box[A] = Box(fa)
    }
    val fromBox: Box ~> EitherThrowable = new (Box ~> EitherThrowable) {
      override def apply[A](fa: Box[A]): EitherThrowable[A] = fa.value
    }
    val boxMonad: MonadError[Box] = eitherThrowableMonad.imapK(toBox)(fromBox)
    val boom: RuntimeException = new RuntimeException("boom")

    assertEquals(Box(Right(1)), boxMonad.unit(1))
    assertEquals(Box(Right(3)), boxMonad.map(Box(Right(2)))(_ + 1))
    assertEquals(Box(Right(6)), boxMonad.flatMap(Box(Right(3)))(value => Box(Right(value * 2))))
    assertSame(boom, boxMonad.error[Int](boom).value.left.toOption.get)

    val recovered: Box[Int] = boxMonad.handleError(boxMonad.error[Int](boom)) { case throwable if throwable eq boom => Box(Right(42)) }
    assertEquals(Box(Right(42)), recovered)

    val notRecovered: Box[Int] = boxMonad.handleError(boxMonad.error[Int](boom)) { case _: IllegalArgumentException => Box(Right(0)) }
    assertSame(boom, notRecovered.value.left.toOption.get)

    assertEquals(Box(Right("safe")), boxMonad.ensure2(Box(Right("safe")), Box(Right(()))))
    assertEquals(Box(Right("computed")), boxMonad.blocking("computed"))
  }

  @Test
  def serverEndpointSyntaxChangesEffectTypeWithoutChangingEndpointDescription(): Unit = {
    val toBox: EitherThrowable ~> Box = new (EitherThrowable ~> Box) {
      override def apply[A](fa: EitherThrowable[A]): Box[A] = Box(fa)
    }
    val fromBox: Box ~> EitherThrowable = new (Box ~> EitherThrowable) {
      override def apply[A](fa: Box[A]): EitherThrowable[A] = fa.value
    }
    val original: ServerEndpoint[Any, EitherThrowable] = endpoint.get
      .in(query[Int]("value"))
      .out(stringBody)
      .serverLogicSuccess((value: Int) => Right(s"value:${value + 1}"))

    val mapped: ServerEndpoint[Any, Box] = original.imapK(toBox)(fromBox)

    assertNotNull(mapped)
    assertEquals(original.endpoint, mapped.endpoint)
  }

  private def assertDecodeValue[A](actual: DecodeResult[A], expected: A): Unit =
    actual match {
      case DecodeResult.Value(value) => assertEquals(expected, value)
      case other                    => fail(s"Expected successful decode of $expected, got $other")
    }

  private def assertNotDecoded[A](actual: DecodeResult[A]): Unit =
    actual match {
      case DecodeResult.Value(value) => fail(s"Expected decoding to fail, but got $value")
      case _                         => assertFalse(false)
    }

  private type EitherThrowable[A] = Either[Throwable, A]

  private val eitherThrowableMonad: MonadError[EitherThrowable] = new MonadError[EitherThrowable] {
    override def unit[T](t: T): EitherThrowable[T] = Right(t)

    override def map[T, T2](fa: EitherThrowable[T])(f: T => T2): EitherThrowable[T2] = fa.map(f)

    override def flatMap[T, T2](fa: EitherThrowable[T])(f: T => EitherThrowable[T2]): EitherThrowable[T2] = fa.flatMap(f)

    override def error[T](t: Throwable): EitherThrowable[T] = Left(t)

    override protected def handleWrappedError[T](rt: EitherThrowable[T])(h: PartialFunction[Throwable, EitherThrowable[T]]): EitherThrowable[T] =
      rt match {
        case Left(throwable) if h.isDefinedAt(throwable) => h(throwable)
        case other                                       => other
      }

    override def ensure[T](f: EitherThrowable[T], e: => EitherThrowable[Unit]): EitherThrowable[T] = f

    override def ensure2[T](f: => EitherThrowable[T], e: => EitherThrowable[Unit]): EitherThrowable[T] = f

    override def blocking[T](t: => T): EitherThrowable[T] = Right(t)
  }

  private final case class Box[A](value: EitherThrowable[A])

  private final case class Person(name: String, age: Int)

  private final case class GreetingResponse(body: String, traceId: String)

  private final case class RequestContext(session: String, requestId: String)
}
