/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package scodec_core_3

import javax.crypto.spec.SecretKeySpec

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs.{CipherFactory, bytes, encrypted}

/** Exercises encrypted command payloads before a device client sends them. */
class EncryptedCommandCodecTest {
  private val encryptionKey: SecretKeySpec =
    new SecretKeySpec(ByteVector.fromValidHex("00112233445566778899aabbccddeeff").toArray, "AES")
  private val cipherFactory: CipherFactory =
    CipherFactory("AES/ECB/PKCS5Padding", encryptionKey)
  private val commandCodec: Codec[ByteVector] = encrypted(bytes, cipherFactory)

  @Test
  @Timeout(60)
  def encryptsAndDecryptsADeviceCommandPayload(): Unit = {
    val command: ByteVector = ByteVector.fromValidHex("646f6f723d6f70656e")
    val encoded: ByteVector = commandCodec.encode(command).require.toByteVector

    assertEquals("d5c2bac98e8362cb36059eb87e4d145d", encoded.toHex)
    assertEquals(command, commandCodec.decode(encoded.toBitVector).require.value)
  }

  @Test
  @Timeout(60)
  def rejectsACiphertextWithAnIncompleteBlock(): Unit = {
    val truncated: ByteVector = ByteVector.fromValidHex("d5c2bac98e8362cb")

    assertFalse(commandCodec.decode(truncated.toBitVector).isSuccessful)
  }
}
