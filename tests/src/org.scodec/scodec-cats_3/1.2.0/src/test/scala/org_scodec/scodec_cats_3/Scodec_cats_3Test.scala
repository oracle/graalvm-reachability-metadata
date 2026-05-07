/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scodec.scodec_cats_3

import cats.Applicative
import cats.Comonad
import cats.Contravariant
import cats.Eval
import cats.Invariant
import cats.MonadError
import cats.Monoid
import cats.Order
import cats.Semigroup
import cats.Show
import cats.Traverse
import cats.data.NonEmptyList
import cats.implicits._
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import scodec.Attempt
import scodec.Codec
import scodec.DecodeResult
import scodec.Decoder
import scodec.Encoder
import scodec.Err
import scodec.bits.BitVector
import scodec.bits.ByteVector
import scodec.codecs.uint8
import scodec.interop.cats._

final case class MeterReading(value: Int)

class Scodec_cats_3Test {
  @Test
  def providesCatsInstancesForBitAndByteVectors(): Unit = {
    val highNibble: BitVector = BitVector.fromValidHex("a")
    val lowNibble: BitVector = BitVector.fromValidHex("5")
    val oneByte: ByteVector = ByteVector.fromValidHex("01")
    val twoBytes: ByteVector = ByteVector.fromValidHex("0203")

    assertThat(Monoid[BitVector].empty).isEqualTo(BitVector.empty)
    assertThat(Monoid[BitVector].combine(highNibble, lowNibble).toHex).isEqualTo("a5")
    assertThat(Order[BitVector].compare(highNibble, lowNibble)).isPositive
    assertThat(Show[BitVector].show(highNibble)).contains("a")

    assertThat(Monoid[ByteVector].empty).isEqualTo(ByteVector.empty)
    assertThat(Monoid[ByteVector].combine(oneByte, twoBytes).toHex).isEqualTo("010203")
    assertThat(Order[ByteVector].compare(oneByte, twoBytes)).isNegative
    assertThat(Show[ByteVector].show(twoBytes)).contains("0203")
  }

  @Test
  def suppliesMonadErrorTraverseEqAndShowForAttempt(): Unit = {
    val monadError: MonadError[Attempt, Err] = MonadError[Attempt, Err]
    val failure: Err = Err("missing payload")

    val composed: Attempt[Int] = monadError.flatMap(monadError.pure(21))(value => Attempt.successful(value * 2))
    val recovered: Attempt[Int] = monadError.handleErrorWith(monadError.raiseError[Int](failure)) { err =>
      Attempt.successful(err.message.length)
    }
    val tailRecursive: Attempt[Int] = monadError.tailRecM(0) { value =>
      Attempt.successful(if value < 5 then Left(value + 1) else Right(value * 10))
    }
    val traversedSuccess: Option[Attempt[String]] = Traverse[Attempt].traverse(Attempt.successful(7))(value => Option(s"v$value"))
    val traversedFailure: Option[Attempt[String]] = Traverse[Attempt].traverse(Attempt.failure[Int](failure)) { _ =>
      Option("unreachable")
    }
    val folded: String = Traverse[Attempt].foldRight(Attempt.successful("head"), Eval.now("tail")) { (value, suffix) =>
      suffix.map(rest => s"$value-$rest")
    }.value

    assertThat(composed).isEqualTo(Attempt.successful(42))
    assertThat(recovered).isEqualTo(Attempt.successful("missing payload".length))
    assertThat(tailRecursive).isEqualTo(Attempt.successful(50))
    assertThat(traversedSuccess).isEqualTo(Some(Attempt.successful("v7")))
    assertThat(traversedFailure).isEqualTo(Some(Attempt.failure[String](failure)))
    assertThat(folded).isEqualTo("head-tail")
    assertThat(Attempt.successful(42) === Attempt.successful(42)).isTrue
    assertThat(Attempt.successful(42) === Attempt.successful(43)).isFalse
    assertThat(Attempt.failure[Int](failure) === Attempt.failure[Int](Err("missing payload"))).isTrue
    assertThat(failure === Err("missing payload")).isTrue
    assertThat(Show[Err].show(failure)).isEqualTo("missing payload")
    assertThat(Show[Attempt[Int]].show(Attempt.successful(42))).isEqualTo("Successful(42)")
  }

  @Test
  def convertsEitherErrValuesToAttempt(): Unit = {
    val err: Err = Err("not found")
    val right: Either[Err, Int] = Right(99)
    val left: Either[Err, Int] = Left(err)

    assertThat(right.toAttempt).isEqualTo(Attempt.successful(99))
    assertThat(left.toAttempt).isEqualTo(Attempt.failure[Int](err))
  }

  @Test
  def suppliesComonadTraverseEqAndShowForDecodeResult(): Unit = {
    val remainder: BitVector = BitVector.fromValidHex("ff")
    val result: DecodeResult[Int] = DecodeResult(10, remainder)
    val comonad: Comonad[DecodeResult] = Comonad[DecodeResult]
    val traversed: Option[DecodeResult[String]] = Traverse[DecodeResult].traverse(result)(value => Option(s"#$value"))
    val folded: Int = Traverse[DecodeResult].foldLeft(result, 5)(_ + _)

    assertThat(comonad.extract(result)).isEqualTo(10)
    assertThat(comonad.coflatMap(result)(_.remainder.toHex)).isEqualTo(DecodeResult("ff", remainder))
    assertThat(traversed).isEqualTo(Some(DecodeResult("#10", remainder)))
    assertThat(folded).isEqualTo(15)
    assertThat(result === DecodeResult(10, remainder)).isTrue
    assertThat(result === DecodeResult(11, remainder)).isFalse
    assertThat(Show[DecodeResult[Int]].show(result)).isEqualTo("DecodeResult(10,BitVector(8 bits, 0xff))")
  }

  @Test
  def suppliesMonadErrorAndMonoidInstancesForDecoder(): Unit = {
    val decoderMonad: MonadError[Decoder, Err] = MonadError[Decoder, Err]
    val source: BitVector = BitVector.fromValidHex("6496c8")
    val raised: Err = Err("decoder failed")

    val pureResult: Attempt[DecodeResult[String]] = decoderMonad.pure("constant").decode(source)
    val recovered: Attempt[DecodeResult[Int]] = decoderMonad
      .handleErrorWith(decoderMonad.raiseError[Int](raised))(_ => uint8)
      .decode(source)
    val summedBytes: Attempt[DecodeResult[Int]] = decoderMonad.tailRecM(0) { total =>
      uint8.map { next =>
        val updated: Int = total + next
        if updated < 300 then Left(updated) else Right(updated)
      }
    }.decode(source)
    val pairDecoder: Decoder[List[Int]] = Monoid[Decoder[List[Int]]].combine(
      uint8.map(value => List(value)),
      uint8.map(value => List(value))
    )
    val combined: Attempt[DecodeResult[List[Int]]] = pairDecoder.decode(BitVector.fromValidHex("0a0b0c"))
    val emptyString: Attempt[DecodeResult[String]] = Monoid[Decoder[String]].empty.decode(BitVector.fromValidHex("ab"))

    assertThat(pureResult).isEqualTo(Attempt.successful(DecodeResult("constant", source)))
    assertThat(recovered).isEqualTo(Attempt.successful(DecodeResult(100, BitVector.fromValidHex("96c8"))))
    assertThat(summedBytes).isEqualTo(Attempt.successful(DecodeResult(450, BitVector.empty)))
    assertThat(combined).isEqualTo(Attempt.successful(DecodeResult(List(10, 11), BitVector.fromValidHex("0c"))))
    assertThat(emptyString).isEqualTo(Attempt.successful(DecodeResult("", BitVector.fromValidHex("ab"))))
    assertThat(decoderMonad.raiseError[Int](raised).decode(source)).isEqualTo(Attempt.failure[DecodeResult[Int]](raised))
    assertThat(Show[Decoder[Int]].show(uint8)).isNotBlank
  }

  @Test
  def decoderErrorHandlingPreservesSuccessfulDecodesAndRetriesFallbackFromOriginalInput(): Unit = {
    val decoderMonad: MonadError[Decoder, Err] = MonadError[Decoder, Err]
    val source: BitVector = BitVector.fromValidHex("2a63")
    val expected: Attempt[DecodeResult[Int]] = Attempt.successful(DecodeResult(42, BitVector.fromValidHex("63")))

    val successfulPassThrough: Attempt[DecodeResult[Int]] = decoderMonad
      .handleErrorWith(uint8)(_ => decoderMonad.pure(255))
      .decode(source)
    val failingAfterFirstByte: Decoder[Int] = decoderMonad.flatMap(uint8) { firstByte =>
      decoderMonad.raiseError[Int](Err(s"rejecting byte $firstByte"))
    }
    val recoveredFromOriginalInput: Attempt[DecodeResult[Int]] = decoderMonad
      .handleErrorWith(failingAfterFirstByte)(_ => uint8)
      .decode(source)

    assertThat(successfulPassThrough).isEqualTo(expected)
    assertThat(recoveredFromOriginalInput).isEqualTo(expected)
  }

  @Test
  def combinesDecodersForSemigroupOnlyResults(): Unit = {
    val nonEmptyByteDecoder: Decoder[NonEmptyList[Int]] = uint8.map(NonEmptyList.one)
    val combinedDecoder: Decoder[NonEmptyList[Int]] = Semigroup[Decoder[NonEmptyList[Int]]].combine(
      nonEmptyByteDecoder,
      nonEmptyByteDecoder
    )

    val decoded: Attempt[DecodeResult[NonEmptyList[Int]]] = combinedDecoder.decode(BitVector.fromValidHex("0a0bff"))

    assertThat(decoded).isEqualTo(
      Attempt.successful(DecodeResult(NonEmptyList.of(10, 11), BitVector.fromValidHex("ff")))
    )
  }

  @Test
  def suppliesContravariantInstanceForEncoder(): Unit = {
    val byteEncoder: Encoder[Int] = uint8
    val stringLengthEncoder: Encoder[String] = Contravariant[Encoder].contramap(byteEncoder)(_.length)

    assertThat(stringLengthEncoder.encode("cats")).isEqualTo(Attempt.successful(BitVector.fromValidHex("04")))
    assertThat(Show[Encoder[Int]].show(byteEncoder)).isNotBlank
  }

  @Test
  def suppliesInvariantInstanceForCodec(): Unit = {
    val readingCodec: Codec[MeterReading] = Invariant[Codec].imap(uint8)(MeterReading.apply)(_.value)
    val encoded: Attempt[BitVector] = readingCodec.encode(MeterReading(42))
    val decoded: Attempt[DecodeResult[MeterReading]] = readingCodec.decode(BitVector.fromValidHex("2aff"))

    assertThat(encoded).isEqualTo(Attempt.successful(BitVector.fromValidHex("2a")))
    assertThat(decoded).isEqualTo(Attempt.successful(DecodeResult(MeterReading(42), BitVector.fromValidHex("ff"))))
    assertThat(Show[Codec[Int]].show(uint8)).isNotBlank
  }

  @Test
  def usesAttemptInstancesWithGenericCatsOperations(): Unit = {
    def decodeWithApplicative[F[_]: Applicative](first: F[Int], second: F[Int]): F[Int] =
      (first, second).mapN(_ + _)

    val success: Attempt[Int] = decodeWithApplicative(Attempt.successful(12), Attempt.successful(30))
    val failure: Attempt[Int] = decodeWithApplicative(Attempt.successful(12), Attempt.failure[Int](Err("bad addend")))

    assertThat(success).isEqualTo(Attempt.successful(42))
    assertThat(failure).isEqualTo(Attempt.failure[Int](Err("bad addend")))
  }
}
