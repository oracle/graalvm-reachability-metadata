/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_library

import org.assertj.core.api.Assertions.assertThat
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Locale

final class LambdaDeserializerTest {
  @Test
  def deserializesSerializableSamLambda(): Unit = {
    try {
      val original: LambdaDeserializerTextOperation = LambdaDeserializerFixtures.uppercaseWithSuffix("!")
      val restored: LambdaDeserializerTextOperation = deserializeOperation(serialize(original))

      assertThat(original.apply("scala")).isEqualTo("SCALA!")
      assertThat(restored.apply("graalvm")).isEqualTo("GRAALVM!")
      assertThat(restored).isNotSameAs(original)
    } catch {
      case error: Error =>
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
          throw error
        }
    }
  }

  private def serialize(value: AnyRef): Array[Byte] = {
    val bytes: ByteArrayOutputStream = new ByteArrayOutputStream()
    val out: ObjectOutputStream = new ObjectOutputStream(bytes)
    try {
      out.writeObject(value)
      out.flush()
      bytes.toByteArray
    } finally {
      out.close()
    }
  }

  private def deserializeOperation(bytes: Array[Byte]): LambdaDeserializerTextOperation = {
    val in: ObjectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))
    try {
      in.readObject().asInstanceOf[LambdaDeserializerTextOperation]
    } finally {
      in.close()
    }
  }
}

trait LambdaDeserializerTextOperation extends Serializable {
  def apply(value: String): String
}

object LambdaDeserializerFixtures {
  def uppercaseWithSuffix(suffix: String): LambdaDeserializerTextOperation = {
    (value: String) => value.toUpperCase(Locale.ROOT) + suffix
  }
}
