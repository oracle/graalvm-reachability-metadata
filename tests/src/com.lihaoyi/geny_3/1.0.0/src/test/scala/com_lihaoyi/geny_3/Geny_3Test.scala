/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.geny_3

import geny.ByteData
import geny.Bytes
import geny.Generator
import geny.Readable
import geny.Writable
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable
import scala.io.Codec

class Geny_3Test {
  @Test
  def generatorEvaluatesCommonCollectionOperationsAndConversions(): Unit = {
    val generator: Generator[Int] = Generator(3, 1, 4, 1, 5)

    assertEquals(Some(4), generator.find(_ % 2 == 0))
    assertEquals(None, generator.find(_ > 10))
    assertTrue(generator.exists(_ == 5))
    assertTrue(generator.contains(1))
    assertFalse(generator.contains(9))
    assertTrue(generator.forall(_ > 0))
    assertEquals(5, generator.count())
    assertEquals(4, generator.count(_ % 2 == 1))
    assertEquals(14, generator.foldLeft(0)(_ + _))
    assertEquals("start-3-1-4-1-5", generator.fold("start")((accumulator, value) => s"$accumulator-$value"))
    assertEquals(14, generator.reduce(_ + _))
    assertEquals(1, generator.min)
    assertEquals(5, generator.max)
    assertEquals(5, generator.maxBy(value => value * value))
    assertEquals(1, generator.minBy(identity))

    assertEquals(3, generator.head)
    assertEquals(Some(3), generator.headOption)
    assertEquals(List(3, 1, 4, 1, 5), generator.toList)
    assertEquals(Vector(3, 1, 4, 1, 5), generator.toVector)
    assertEquals(Seq(3, 1, 4, 1, 5), generator.toSeq)
    assertEquals(Set(1, 3, 4, 5), generator.toSet)
    assertEquals(mutable.Buffer(3, 1, 4, 1, 5), generator.toBuffer)
    assertArrayEquals(Array(3, 1, 4, 1, 5), generator.toArray)
    assertEquals("[3, 1, 4, 1, 5]", generator.mkString("[", ", ", "]"))
    assertEquals("3|1|4|1|5", generator.mkString("|"))
    assertEquals("31415", generator.mkString)
    assertEquals(14, generator.sum)
    assertEquals(60, generator.product)

    val empty: Generator[Int] = Generator()
    assertEquals(None, empty.headOption)
    assertThrows(classOf[NoSuchElementException], () => empty.head)
    assertThrows(classOf[UnsupportedOperationException], () => empty.reduce(_ + _))
  }

  @Test
  def generatorFromAdaptsArbitraryIterableOnceSourcesLazilyAndRepeatably(): Unit = {
    final case class Rows[A](entries: Vector[A])

    val conversions: AtomicInteger = new AtomicInteger(0)
    val source: Rows[String] = Rows(Vector("red", "blue", "green"))
    val generator: Generator[String] = Generator.from(source) { rows =>
      conversions.incrementAndGet()
      rows.entries.iterator
    }

    assertEquals(0, conversions.get())
    assertEquals(List("red", "blue", "green"), generator.toList)
    assertEquals(1, conversions.get())
    assertEquals(List("red", "blue", "green"), generator.toList)
    assertEquals(2, conversions.get())
  }

  @Test
  def generatorTransformationsAreLazyComposableAndShortCircuiting(): Unit = {
    val evaluations: AtomicInteger = new AtomicInteger(0)
    val transformed: Generator[String] = Generator(1, 2, 3, 4, 5)
      .filter(_ % 2 == 1)
      .map { value =>
        evaluations.incrementAndGet()
        s"value-${value * 2}"
      }

    assertEquals(0, evaluations.get())
    assertEquals(List("value-2", "value-6", "value-10"), transformed.toList)
    assertEquals(3, evaluations.get())

    val comprehension: Generator[String] = for {
      outer <- Generator(1, 2, 3, 4)
      if outer % 2 == 0
      inner <- Generator("a", "b")
    } yield s"$outer-$inner"
    assertEquals(List("2-a", "2-b", "4-a", "4-b"), comprehension.toList)

    val collected: Generator[String] = Generator("1", "two", "3", "four").collect {
      case value if value.forall(_.isDigit) => s"number-$value"
    }
    assertEquals(List("number-1", "number-3"), collected.toList)
    assertEquals(Some("first-two"), Generator("one", "two", "three").collectFirst {
      case value if value.length == 3 && value.startsWith("t") => s"first-$value"
    })

    val sliced: Generator[Int] = Generator(0, 1, 2, 3, 4, 5, 6)
      .drop(1)
      .slice(1, 5)
      .take(3)
    assertEquals(List(2, 3, 4), sliced.toList)
    assertEquals(List(0, 1, 2), Generator(0, 1, 2, 3, 0).takeWhile(_ < 3).toList)
    assertEquals(List(3, 0), Generator(0, 1, 2, 3, 0).dropWhile(_ < 3).toList)
    assertEquals(List(("a", 0), ("b", 1), ("c", 2)), Generator("a", "b", "c").zipWithIndex.toList)
    assertEquals(List(("a", 10), ("b", 20)), Generator("a", "b", "c").zip(List(10, 20)).toList)
    assertEquals(List(1, 2, 3, 4), (Generator(1, 2) ++ Generator(3, 4)).toList)
    assertEquals(List(1, 2, 2, 4, 3, 6), Generator(1, 2, 3).flatMap(value => Generator(value, value * 2)).toList)

    val visited: mutable.Buffer[Int] = mutable.Buffer.empty[Int]
    val action: Generator.Action = Generator(1, 2, 3, 4).generate { value =>
      visited.append(value)
      if (value == 2) Generator.End else Generator.Continue
    }
    assertSame(Generator.End, action)
    assertEquals(List(1, 2), visited.toList)
  }

  @Test
  def selfClosingGeneratorRunsCleanupAfterFullAndEarlyEvaluation(): Unit = {
    val closedAfterFullRead: AtomicInteger = new AtomicInteger(0)
    val fullRead: Generator[Int] = Generator.selfClosing {
      (Iterator(1, 2, 3), () => {
        closedAfterFullRead.incrementAndGet()
        ()
      })
    }

    assertEquals(List(1, 2, 3), fullRead.toList)
    assertEquals(1, closedAfterFullRead.get())

    val closedAfterShortCircuit: AtomicInteger = new AtomicInteger(0)
    val pulledValues: AtomicInteger = new AtomicInteger(0)
    val earlyRead: Generator[Int] = Generator.selfClosing {
      val iterator: Iterator[Int] = Iterator.from(1).map { value =>
        pulledValues.incrementAndGet()
        value
      }
      (iterator, () => {
        closedAfterShortCircuit.incrementAndGet()
        ()
      })
    }

    assertEquals(Some(3), earlyRead.find(_ == 3))
    assertEquals(3, pulledValues.get())
    assertEquals(1, closedAfterShortCircuit.get())
  }

  @Test
  def byteDataChunksExposeCombinedBytesTextTrimLinesAndValueSemantics(): Unit = {
    val first: Bytes = new Bytes(" hello\n".getBytes(StandardCharsets.UTF_8))
    val second: Bytes = new Bytes("world\n\u2603 ".getBytes(StandardCharsets.UTF_8))
    val chunks: ByteData.Chunks = ByteData.Chunks(Seq(first, second))

    assertArrayEquals(" hello\nworld\n\u2603 ".getBytes(StandardCharsets.UTF_8), chunks.bytes)
    assertEquals(" hello\nworld\n\u2603 ", chunks.text())
    assertEquals("hello\nworld\n\u2603", chunks.trim())
    assertEquals(Vector(" hello", "world", "\u2603 "), chunks.lines())
    assertEquals(Seq(first, second), chunks.chunks)
    assertEquals(chunks, chunks.copy())
    assertEquals("Chunks", chunks.productPrefix)
    assertEquals(1, chunks.productArity)
    assertEquals(Seq(first, second), chunks.productElement(0))

    val sameBytes: Bytes = new Bytes("abc".getBytes(StandardCharsets.UTF_8))
    assertEquals(sameBytes, new Bytes("abc".getBytes(StandardCharsets.UTF_8)))
    assertNotEquals(sameBytes, new Bytes("abcd".getBytes(StandardCharsets.UTF_8)))
    assertNotEquals(sameBytes, "abc")
    assertEquals("abc", sameBytes.toString)
  }

  @Test
  def byteDataDecodesTextWithExplicitCodec(): Unit = {
    val latin1Text: String = " café \nniño\n"
    val chunks: ByteData.Chunks = ByteData.Chunks(Seq(
      new Bytes(" café \n".getBytes(StandardCharsets.ISO_8859_1)),
      new Bytes("niño\n".getBytes(StandardCharsets.ISO_8859_1))
    ))
    val latin1: Codec = Codec(StandardCharsets.ISO_8859_1)

    assertEquals(latin1Text, chunks.text(latin1))
    assertEquals("café \nniño", chunks.trim(latin1))
    assertEquals(Vector(" café ", "niño"), chunks.lines(latin1))
  }

  @Test
  def writableImplementationsReportMetadataAndWriteWithoutConsumingByteBuffers(): Unit = {
    val stringWritable: Writable = Writable.StringWritable("a\u2603")
    assertEquals(Some("text/plain; charset=utf-8"), stringWritable.httpContentType)
    assertEquals(Some("a\u2603".getBytes(StandardCharsets.UTF_8).length.toLong), stringWritable.contentLength)
    assertArrayEquals("a\u2603".getBytes(StandardCharsets.UTF_8), writeToBytes(stringWritable))

    val byteArray: Array[Byte] = Array[Byte](1, 2, 3, 4)
    val byteArrayWritable: Writable = Writable.ByteArrayWritable(byteArray)
    assertEquals(Some("application/octet-stream"), byteArrayWritable.httpContentType)
    assertEquals(Some(4L), byteArrayWritable.contentLength)
    assertArrayEquals(byteArray, writeToBytes(byteArrayWritable))

    val buffer: ByteBuffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put(Array[Byte](9, 8, 7, 6, 5))
    buffer.flip()
    buffer.get()
    val initialPosition: Int = buffer.position()
    val byteBufferWritable: Writable = Writable.ByteBufferWritable(buffer)

    assertEquals(Some("application/octet-stream"), byteBufferWritable.httpContentType)
    assertEquals(Some(4L), byteBufferWritable.contentLength)
    assertArrayEquals(Array[Byte](8, 7, 6, 5), writeToBytes(byteBufferWritable))
    assertEquals(initialPosition, buffer.position())
    assertEquals(ByteOrder.LITTLE_ENDIAN, buffer.order())
  }

  @Test
  def readableImplementationsCanReadAndWriteByteSources(): Unit = {
    val stringReadable: Readable = Readable.StringReadable("hello")
    assertEquals(Some("text/plain; charset=utf-8"), stringReadable.httpContentType)
    assertEquals(Some(5L), stringReadable.contentLength)
    assertEquals("hello", readAllText(stringReadable))
    assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), writeToBytes(stringReadable))

    val byteArrayReadable: Readable = Readable.ByteArrayReadable(Array[Byte](10, 11, 12))
    assertEquals(Some("application/octet-stream"), byteArrayReadable.httpContentType)
    assertEquals(Some(3L), byteArrayReadable.contentLength)
    assertArrayEquals(Array[Byte](10, 11, 12), readAllBytes(byteArrayReadable))

    val buffer: ByteBuffer = ByteBuffer.wrap(Array[Byte](0, 1, 2, 3, 4))
    buffer.position(2)
    val byteBufferReadable: Readable = Readable.ByteBufferReadable(buffer)
    assertEquals(Some("application/octet-stream"), byteBufferReadable.httpContentType)
    assertEquals(Some(3L), byteBufferReadable.contentLength)
    assertArrayEquals(Array[Byte](2, 3, 4), readAllBytes(byteBufferReadable))
    assertEquals(2, buffer.position())

    val inputStream: CloseAwareInputStream = new CloseAwareInputStream(Array[Byte](42, 43, 44))
    val inputStreamReadable: Readable = Readable.InputStreamReadable(inputStream)
    assertEquals(Some("application/octet-stream"), inputStreamReadable.httpContentType)
    assertEquals(None, inputStreamReadable.contentLength)
    assertArrayEquals(Array[Byte](42, 43, 44), writeToBytes(inputStreamReadable))
    assertTrue(inputStream.closed)
  }

  private def writeToBytes(writable: Writable): Array[Byte] = {
    val outputStream: ByteArrayOutputStream = new ByteArrayOutputStream()
    writable.writeBytesTo(outputStream)
    outputStream.toByteArray
  }

  private def readAllText(readable: Readable): String = {
    new String(readAllBytes(readable), StandardCharsets.UTF_8)
  }

  private def readAllBytes(readable: Readable): Array[Byte] = {
    readable.readBytesThrough { inputStream =>
      val outputStream: ByteArrayOutputStream = new ByteArrayOutputStream()
      val buffer: Array[Byte] = new Array[Byte](2)
      var read: Int = inputStream.read(buffer)
      while (read != -1) {
        outputStream.write(buffer, 0, read)
        read = inputStream.read(buffer)
      }
      outputStream.toByteArray
    }
  }

  private final class CloseAwareInputStream(bytes: Array[Byte]) extends InputStream {
    private val delegate: ByteArrayInputStream = new ByteArrayInputStream(bytes)
    var closed: Boolean = false

    override def read(): Int = delegate.read()

    override def read(buffer: Array[Byte], offset: Int, length: Int): Int = delegate.read(buffer, offset, length)

    override def available(): Int = delegate.available()

    override def close(): Unit = {
      closed = true
      delegate.close()
    }
  }
}
