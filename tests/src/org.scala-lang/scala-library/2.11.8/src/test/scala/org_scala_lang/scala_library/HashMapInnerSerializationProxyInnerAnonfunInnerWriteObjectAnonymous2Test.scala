/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_library

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.nio.charset.StandardCharsets
import scala.collection.immutable.{HashMap => ImmutableHashMap}

class HashMapInnerSerializationProxyInnerAnonfunInnerWriteObjectAnonymous2Test {
  @Test
  def serializationWritesEachKeyAndValueThroughHashMapSerializationProxy(): Unit = {
    val original: ImmutableHashMap[String, String] = new ImmutableHashMap[String, String]()
      .updated("first-key", "first-value")
      .updated("second-key", "second-value")

    val serialized: Array[Byte] = serialize(original)

    assertThat(serialized.length).isGreaterThan(0)
    assertThat(containsAscii(serialized, "first-key")).isTrue()
    assertThat(containsAscii(serialized, "first-value")).isTrue()
    assertThat(containsAscii(serialized, "second-key")).isTrue()
    assertThat(containsAscii(serialized, "second-value")).isTrue()
  }

  private def serialize(value: AnyRef): Array[Byte] = {
    val bytes: ByteArrayOutputStream = new ByteArrayOutputStream()
    val output: ObjectOutputStream = new ObjectOutputStream(bytes)
    try {
      output.writeObject(value)
    } finally {
      output.close()
    }
    bytes.toByteArray
  }

  private def containsAscii(bytes: Array[Byte], text: String): Boolean = {
    val expected: Array[Byte] = text.getBytes(StandardCharsets.US_ASCII)
    var start: Int = 0
    while (start <= bytes.length - expected.length) {
      var index: Int = 0
      while (index < expected.length && bytes(start + index) == expected(index)) {
        index += 1
      }
      if (index == expected.length) {
        return true
      }
      start += 1
    }
    false
  }
}
