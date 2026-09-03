/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalameta.trees_2_13

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.ObjectStreamConstants
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystem
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.Collections

import scala.meta.inputs.Input
import scala.meta.io.AbsolutePath

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InputInnerFileInnerSerializationProxyTest {
  @Test
  def serializesFileInputThroughSerializationProxy(): Unit = {
    val path: AbsolutePath = AbsolutePath(new SerializablePath("/scalameta-serialization-proxy.scala"))
    val input: Input.File = Input.File(path, StandardCharsets.UTF_8)
    val bytes: Array[Byte] = serialize(input)

    assertTrue(bytes.nonEmpty)
  }

  @Test
  def deserializesFileInputThroughSerializationProxy(): Unit = {
    val file: File = File.createTempFile("scalameta-input-file", ".scala")
    file.deleteOnExit()
    val bytes: Array[Byte] = serializedFileProxy(file, StandardCharsets.UTF_8.name())

    val inputStream: ObjectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))
    try {
      val deserialized: AnyRef = inputStream.readObject().asInstanceOf[AnyRef]
      val input: Input.File = deserialized.asInstanceOf[Input.File]

      assertEquals(StandardCharsets.UTF_8, input.charset)
      assertEquals(file.getAbsoluteFile, input.path.toFile.getAbsoluteFile)
    } finally {
      inputStream.close()
    }
  }

  private def serialize(value: AnyRef): Array[Byte] = {
    val output: ByteArrayOutputStream = new ByteArrayOutputStream()
    val objectOutput: ObjectOutputStream = new ObjectOutputStream(output)
    try {
      objectOutput.writeObject(value)
    } finally {
      objectOutput.close()
    }
    output.toByteArray
  }

  private def serializedFileProxy(file: File, charsetName: String): Array[Byte] = {
    val payload: Array[Byte] = serializedObjectsPayload(file, charsetName)
    val output: ByteArrayOutputStream = new ByteArrayOutputStream()
    val dataOutput: DataOutputStream = new DataOutputStream(output)
    try {
      dataOutput.writeShort(ObjectStreamConstants.STREAM_MAGIC.toInt)
      dataOutput.writeShort(ObjectStreamConstants.STREAM_VERSION.toInt)
      dataOutput.writeByte(ObjectStreamConstants.TC_OBJECT.toInt)
      dataOutput.writeByte(ObjectStreamConstants.TC_CLASSDESC.toInt)
      dataOutput.writeUTF("scala.meta.inputs.Input$File$SerializationProxy")
      dataOutput.writeLong(1L)
      dataOutput.writeByte(
        ObjectStreamConstants.SC_SERIALIZABLE.toInt | ObjectStreamConstants.SC_WRITE_METHOD.toInt
      )
      dataOutput.writeShort(0)
      dataOutput.writeByte(ObjectStreamConstants.TC_ENDBLOCKDATA.toInt)
      dataOutput.writeByte(ObjectStreamConstants.TC_NULL.toInt)
      dataOutput.write(payload)
      dataOutput.writeByte(ObjectStreamConstants.TC_ENDBLOCKDATA.toInt)
    } finally {
      dataOutput.close()
    }
    output.toByteArray
  }

  private def serializedObjectsPayload(values: AnyRef*): Array[Byte] = {
    val output: ByteArrayOutputStream = new ByteArrayOutputStream()
    val objectOutput: ObjectOutputStream = new ObjectOutputStream(output)
    try {
      values.foreach(value => objectOutput.writeObject(value))
    } finally {
      objectOutput.close()
    }
    output.toByteArray.drop(4)
  }

}

private final class SerializablePath(private val pathText: String) extends Path with Serializable {
  override def getFileSystem: FileSystem = unsupported()

  override def isAbsolute: Boolean = true

  override def getRoot: Path = this

  override def getFileName: Path = this

  override def getParent: Path = null

  override def getNameCount: Int = 1

  override def getName(index: Int): Path = {
    if (index == 0) this
    else throw new IllegalArgumentException(s"Invalid path element index: $index")
  }

  override def subpath(beginIndex: Int, endIndex: Int): Path = {
    if (beginIndex == 0 && endIndex == 1) this
    else throw new IllegalArgumentException(s"Invalid subpath range: $beginIndex to $endIndex")
  }

  override def startsWith(other: Path): Boolean = startsWith(other.toString)

  override def startsWith(other: String): Boolean = pathText.startsWith(other)

  override def endsWith(other: Path): Boolean = endsWith(other.toString)

  override def endsWith(other: String): Boolean = pathText.endsWith(other)

  override def normalize(): Path = this

  override def resolve(other: Path): Path = new SerializablePath(pathText + "/" + other.toString)

  override def resolve(other: String): Path = resolve(new SerializablePath(other))

  override def resolveSibling(other: Path): Path = other

  override def resolveSibling(other: String): Path = new SerializablePath(other)

  override def relativize(other: Path): Path = other

  override def toUri: URI = new URI("file", null, pathText, null)

  override def toAbsolutePath: Path = this

  override def toRealPath(options: LinkOption*): Path = this

  override def toFile: File = new File(pathText)

  override def register(
      watcher: WatchService,
      events: Array[WatchEvent.Kind[_]],
      modifiers: WatchEvent.Modifier*
  ): WatchKey = unsupported()

  override def register(watcher: WatchService, events: WatchEvent.Kind[_]*): WatchKey = unsupported()

  override def iterator(): java.util.Iterator[Path] = Collections.singletonList[Path](this).iterator()

  override def compareTo(other: Path): Int = pathText.compareTo(other.toString)

  override def toString: String = pathText

  private def unsupported(): Nothing = throw new UnsupportedOperationException(pathText)
}
