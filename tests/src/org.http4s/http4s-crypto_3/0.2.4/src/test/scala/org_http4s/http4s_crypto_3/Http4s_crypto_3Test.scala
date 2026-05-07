/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.http4s.crypto_test

import cats.effect.IO
import cats.implicits._
import cats.effect.unsafe.implicits.global
import org.http4s.crypto.Crypto
import org.http4s.crypto.Hash
import org.http4s.crypto.HashAlgorithm
import org.http4s.crypto.Hmac
import org.http4s.crypto.HmacAlgorithm
import org.http4s.crypto.HmacKeyGen
import org.http4s.crypto.SecretKey
import org.http4s.crypto.SecretKeySpec
import org.http4s.crypto.SecureEq
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import scodec.bits.ByteVector

import scala.concurrent.duration._

final class Http4s_crypto_3Test {
  private type Result[A] = Either[Throwable, A]

  private val data: ByteVector = ascii("The quick brown fox jumps over the lazy dog")
  private val hmacKey: ByteVector = ascii("key")

  @Test
  def hashAlgorithmsProduceKnownDigestVectors(): Unit = {
    val vectors: List[(HashAlgorithm, String)] = List(
      HashAlgorithm.MD5 -> "9e107d9d372bb6826bd81d3542a419d6",
      HashAlgorithm.SHA1 -> "2fd4e1c67a2d28fced849ee1bb76e7391b93eb12",
      HashAlgorithm.SHA256 -> "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592",
      HashAlgorithm.SHA512 ->
        "07e547d9586f6a73f73fbac0435ed76951218fb7d0c8d788a309d785436bbb642e93a252a954f23912547d1e8a3b5ed6e1bfd7097821233fa0538f3db854fee6"
    )

    vectors.foreach { case (algorithm: HashAlgorithm, expectedHex: String) =>
      val digest: ByteVector = rightValue(Hash[Result].digest(algorithm, data))

      assertEquals(hex(expectedHex), digest)
    }
  }

  @Test
  def hmacImportKeyAndDigestProduceKnownVectors(): Unit = {
    val vectors: List[(HmacAlgorithm, String)] = List(
      HmacAlgorithm.SHA1 -> "de7c9b85b8b78aa6bc8a7a36f70a90701c9db4d9",
      HmacAlgorithm.SHA256 -> "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
      HmacAlgorithm.SHA512 ->
        "b42af09057bac1e2d41708e48a902e09b5ff7f12ab428a4fe86653c73dd248fb82f948a549f7b791a5b41915ee4d1ec3935357e4e2317250d0372afa2ebeeb3a"
    )

    vectors.foreach { case (algorithm: HmacAlgorithm, expectedHex: String) =>
      val key: SecretKey[HmacAlgorithm] = rightValue(Hmac[Result].importKey(hmacKey, algorithm))
      val digest: ByteVector = rightValue(Hmac[Result].digest(key, data))

      assertEquals(algorithm, key.algorithm)
      assertEquals(hex(expectedHex), digest)
    }
  }

  @Test
  def hmacJavaSecretKeyInteropPreservesAlgorithmAndKeyBytes(): Unit = {
    val algorithm: HmacAlgorithm = HmacAlgorithm.SHA256
    val originalKey: SecretKey[HmacAlgorithm] = SecretKeySpec(hmacKey, algorithm)
    val exportedKey: javax.crypto.SecretKey = originalKey.toJava
    val importedKey: SecretKey[HmacAlgorithm] = rightValue(Hmac[Result].importJavaKey(exportedKey))
    val importedDigest: ByteVector = rightValue(Hmac[Result].digest(importedKey, data))
    val directDigest: ByteVector = rightValue(Hmac[Result].digest(originalKey, data))

    assertEquals(algorithm, importedKey.algorithm)
    assertEquals(directDigest, importedDigest)
    assertEquals(hmacKey, ByteVector.view(exportedKey.getEncoded))
  }

  @Test
  def hmacKeyGeneratorCreatesUsableKeysForEachAlgorithm(): Unit = {
    val vectors: List[(HmacAlgorithm, Int)] = List(
      HmacAlgorithm.SHA1 -> 20,
      HmacAlgorithm.SHA256 -> 32,
      HmacAlgorithm.SHA512 -> 64
    )

    vectors.foreach { case (algorithm: HmacAlgorithm, digestLength: Int) =>
      val generatedKey: SecretKey[HmacAlgorithm] = run(HmacKeyGen[IO].generateKey(algorithm))
      val digest: ByteVector = run(Hmac[IO].digest(generatedKey, data))

      generatedKey match {
        case SecretKeySpec(keyBytes, keyAlgorithm) =>
          assertEquals(algorithm, keyAlgorithm)
          assertFalse(keyBytes.isEmpty)
      }
      assertEquals(digestLength.toLong, digest.length)
    }
  }

  @Test
  def cryptoAggregatesHashHmacAndKeyGenerationInstances(): Unit = {
    val crypto: Crypto[IO] = Crypto[IO]
    val sha256: ByteVector = run(crypto.hash.digest(HashAlgorithm.SHA256, data))
    val key: SecretKey[HmacAlgorithm] = run(crypto.hmacKeyGen.generateKey(HmacAlgorithm.SHA256))
    val mac: ByteVector = run(crypto.hmac.digest(key, data))

    assertEquals(
      hex("d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592"),
      sha256
    )
    assertEquals(HmacAlgorithm.SHA256, key.algorithm)
    assertEquals(32L, mac.length)
  }

  @Test
  def secureEqComparesByteVectorsByContentAndLength(): Unit = {
    val secureEq: SecureEq[ByteVector] = SecureEq[ByteVector]
    val expected: ByteVector = ByteVector(1, 2, 3, 4)

    assertTrue(secureEq.eqv(expected, expected))
    assertTrue(secureEq.eqv(expected, ByteVector(1, 2, 3, 4)))
    assertFalse(secureEq.eqv(expected, ByteVector(1, 2, 3, 5)))
    assertFalse(secureEq.eqv(expected, ByteVector(1, 2, 3)))
    assertFalse(secureEq.eqv(expected, ByteVector(1, 2, 3, 4, 5)))
  }

  private def ascii(value: String): ByteVector =
    ByteVector.encodeAscii(value).fold(throw _, identity)

  private def hex(value: String): ByteVector =
    ByteVector.fromHex(value).getOrElse(throw new IllegalArgumentException(s"invalid hex: $value"))

  private def rightValue[A](value: Either[Throwable, A]): A =
    value.fold(throw _, identity)

  private def run[A](io: IO[A]): A =
    io.timeout(5.seconds).unsafeRunSync()
}
