/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_profunktor.redis4cats_core_3

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.unsafe.implicits.global
import com.github.fppt.jedismock.RedisServer
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.log4cats.log4CatsInstance
import io.lettuce.core.codec.CipherCodec.CipherSupplier
import io.lettuce.core.codec.CipherCodec.KeyDescriptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.nio.charset.StandardCharsets
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts stored values through `RedisCodec.secure` with versioned cipher suppliers.
 *
 * A customer service keeps personal data in Redis encrypted with AES. Each
 * stored value is tagged with the name and version of the key that sealed it,
 * so when security rotates the key from version 1 to version 2 the service
 * writes new records with the new key while still reading records sealed with
 * the old one. An operations tool reads the same keys through the raw
 * `RedisCodec.Bytes` codec to check that Redis holds only key-tagged
 * ciphertext, never the plain values, while the Redis keys stay readable.
 */
class CustomerVaultEncryptedCodecTest {

  /** What the service and the operations tool read back from Redis. */
  private final case class Observed(
      emailBeforeRotation: Option[String],
      emailAfterRotation: Option[String],
      profileAfterRotation: Map[String, String],
      newCustomerEmail: Option[String],
      rawOldEmail: Array[Byte],
      rawNewEmail: Array[Byte],
      rawPhone: Array[Byte],
      keys: List[String]
  )

  /** The versioned AES keys the service may seal and open values with. */
  private final class KeyRing(name: String, keys: Map[Int, (SecretKeySpec, IvParameterSpec)]) {

    def cipher(mode: Int, version: Int): Cipher = {
      val (key, iv) = keys(version)
      val cipher: Cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
      cipher.init(mode, key, iv)
      cipher
    }

    /** Seals new values with `version`. */
    def sealing(version: Int): CipherSupplier = new CipherSupplier {
      override def get(descriptor: KeyDescriptor): Cipher = cipher(Cipher.ENCRYPT_MODE, descriptor.getVersion)
      override def encryptionKey(): KeyDescriptor = KeyDescriptor.create(name, version)
    }

    /** Opens values sealed with any version on the ring. */
    val opening: CipherSupplier = new CipherSupplier {
      override def get(descriptor: KeyDescriptor): Cipher = cipher(Cipher.DECRYPT_MODE, descriptor.getVersion)
    }

    def codec(version: Int): RedisCodec[String, String] = RedisCodec.secure(RedisCodec.Utf8, sealing(version), opening)
  }

  /** Stores customer contact data under `customer:<id>:email` and `customer:<id>:profile`. */
  private final class CustomerVault(redis: RedisCommands[IO, String, String]) {

    def saveEmail(id: String, email: String): IO[Unit] = redis.set(s"customer:$id:email", email)

    def email(id: String): IO[Option[String]] = redis.get(s"customer:$id:email")

    def saveProfile(id: String, profile: Map[String, String]): IO[Long] = redis.hSet(s"customer:$id:profile", profile)

    def profile(id: String): IO[Map[String, String]] = redis.hGetAll(s"customer:$id:profile")
  }

  private def bytes(text: String): Array[Byte] = text.getBytes(StandardCharsets.UTF_8)

  private def aesKey(seed: Byte): (SecretKeySpec, IvParameterSpec) =
    (new SecretKeySpec(Array.tabulate[Byte](16)(i => (seed + i).toByte), "AES"), new IvParameterSpec(Array.fill[Byte](16)(seed)))

  /** Splits a stored value into its `$name+version$` tag and the ciphertext after it. */
  private def splitTag(raw: Array[Byte]): (String, Array[Byte]) = {
    val end: Int = raw.indexOf('$'.toByte, 1)
    (new String(raw, 0, end + 1, StandardCharsets.US_ASCII), Arrays.copyOfRange(raw, end + 1, raw.length))
  }

  @Test
  def readsRecordsSealedBeforeAndAfterKeyRotation(): Unit = {
    val server: RedisServer = RedisServer.newRedisServer().start()
    try {
      given Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("customer-vault")
      given Log[IO] = log4CatsInstance[IO]

      val ring: KeyRing = new KeyRing("customer-pii", Map(1 -> aesKey(11), 2 -> aesKey(73)))

      type Connections = (
          RedisCommands[IO, String, String],
          RedisCommands[IO, String, String],
          RedisCommands[IO, Array[Byte], Array[Byte]]
      )
      val connections: Resource[IO, Connections] =
        for {
          client <- RedisClient[IO].from(s"redis://${server.getHost}:${server.getBindPort}")
          beforeRotation <- Redis[IO].fromClient(client, ring.codec(1))
          afterRotation <- Redis[IO].fromClient(client, ring.codec(2))
          raw <- Redis[IO].fromClient(client, RedisCodec.Bytes)
        } yield (beforeRotation, afterRotation, raw)

      val profile: Map[String, String] = Map("phone" -> "+43 660 1234567", "address" -> "Ringstrasse 1, 1010 Wien")

      val observed: Observed = connections
        .use { (beforeRotation, afterRotation, raw) =>
          val oldVault: CustomerVault = new CustomerVault(beforeRotation)
          val newVault: CustomerVault = new CustomerVault(afterRotation)
          for {
            _ <- oldVault.saveEmail("1001", "anna.berger@example.com")
            _ <- oldVault.saveProfile("1001", profile)
            emailBefore <- oldVault.email("1001")
            emailAfter <- newVault.email("1001")
            profileAfter <- newVault.profile("1001")
            _ <- newVault.saveEmail("1002", "jonas.huber@example.com")
            newEmail <- oldVault.email("1002")
            rawOld <- raw.get(bytes("customer:1001:email"))
            rawNew <- raw.get(bytes("customer:1002:email"))
            rawPhone <- raw.hGet(bytes("customer:1001:profile"), bytes("phone"))
            keys <- raw.keys(bytes("customer:*"))
          } yield Observed(
            emailBefore,
            emailAfter,
            profileAfter,
            newEmail,
            rawOld.getOrElse(Array.emptyByteArray),
            rawNew.getOrElse(Array.emptyByteArray),
            rawPhone.getOrElse(Array.emptyByteArray),
            keys.map(key => new String(key, StandardCharsets.UTF_8)).sorted
          )
        }
        .unsafeRunSync()

      assertEquals(Some("anna.berger@example.com"), observed.emailBeforeRotation)
      assertEquals(Some("anna.berger@example.com"), observed.emailAfterRotation)
      assertEquals(profile, observed.profileAfterRotation)
      assertEquals(Some("jonas.huber@example.com"), observed.newCustomerEmail)

      val (oldTag, oldCiphertext) = splitTag(observed.rawOldEmail)
      assertEquals("$customer-pii+1$", oldTag)
      assertFalse(new String(observed.rawOldEmail, StandardCharsets.ISO_8859_1).contains("anna.berger"))
      assertEquals("anna.berger@example.com", new String(ring.cipher(Cipher.DECRYPT_MODE, 1).doFinal(oldCiphertext), StandardCharsets.UTF_8))

      val (newTag, newCiphertext) = splitTag(observed.rawNewEmail)
      assertEquals("$customer-pii+2$", newTag)
      assertEquals("jonas.huber@example.com", new String(ring.cipher(Cipher.DECRYPT_MODE, 2).doFinal(newCiphertext), StandardCharsets.UTF_8))

      val (phoneTag, phoneCiphertext) = splitTag(observed.rawPhone)
      assertEquals("$customer-pii+1$", phoneTag)
      assertEquals("+43 660 1234567", new String(ring.cipher(Cipher.DECRYPT_MODE, 1).doFinal(phoneCiphertext), StandardCharsets.UTF_8))

      assertEquals(List("customer:1001:email", "customer:1001:profile", "customer:1002:email"), observed.keys)
    } finally {
      server.stop()
    }
  }
}
