/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalameta.trees_2_13

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import scala.meta.inputs.Input
import scala.meta.io.AbsolutePath

class InputInnerFileInnerSerializationProxyTest {
  @Test
  def serializesAndDeserializesFileInputThroughProxy(): Unit = {
    val file: Path = Files.createTempFile("scalameta-input-file", ".scala")
    try {
      val content: String = "object SerializationProxySample { val answer = 42 }\n"
      Files.writeString(file, content, StandardCharsets.UTF_8)

      val original: Input.File = Input.File(file, StandardCharsets.UTF_8)
      val serialized: Array[Byte] = serialize(original)
      val deserialized: Input.File = deserialize(serialized).asInstanceOf[Input.File]

      assertEquals(original.path, deserialized.path)
      assertEquals(original.charset, deserialized.charset)
      assertEquals(content, deserialized.text)
    } finally {
      Files.deleteIfExists(file)
    }
  }

  private def serialize(value: AnyRef): Array[Byte] = {
    val bytes: ByteArrayOutputStream = new ByteArrayOutputStream()
    val out: ObjectOutputStream = new PathReplacingObjectOutputStream(bytes)
    try {
      out.writeObject(value)
    } finally {
      out.close()
    }
    bytes.toByteArray
  }

  private def deserialize(bytes: Array[Byte]): AnyRef = {
    val in: ObjectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))
    try {
      in.readObject()
    } finally {
      in.close()
    }
  }

  private final class PathReplacingObjectOutputStream(out: ByteArrayOutputStream)
      extends ObjectOutputStream(out) {
    enableReplaceObject(true)

    override protected def replaceObject(obj: AnyRef): AnyRef = obj match {
      case path: AbsolutePath => path.toFile
      case _ => obj
    }
  }
}
