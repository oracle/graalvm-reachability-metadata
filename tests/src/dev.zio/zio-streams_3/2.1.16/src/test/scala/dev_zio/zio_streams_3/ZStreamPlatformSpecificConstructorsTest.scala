/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_streams_3

import java.io.FileNotFoundException
import java.io.IOException

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import zio.Chunk
import zio.Runtime
import zio.Unsafe
import zio.stream.ZStream

@Timeout(10)
final class ZStreamPlatformSpecificConstructorsTest {
  @Test
  def readsClasspathResourcesThroughZStream(): Unit = {
    val resourcePaths: List[String] = List(
      "zio/stream/ZStreamPlatformSpecificConstructors.class",
      "zio/stream/ZStream.class",
      "zio/stream/ZStream$.class",
      "scala/Option.class",
      "org/junit/jupiter/api/Test.class"
    )

    val results: List[Either[IOException, Chunk[Byte]]] = resourcePaths.map { resourcePath =>
      val result: Either[IOException, Chunk[Byte]] = runStreamEither(ZStream.fromResource(resourcePath, chunkSize = 64))

      result match {
        case Right(bytes) =>
          assertThat(bytes.length).isGreaterThan(0)
        case Left(error) =>
          assertThat(error).isInstanceOf(classOf[FileNotFoundException])
          assertThat(error.getMessage).contains(resourcePath)
      }

      result
    }

    assertThat(results.exists(_.isRight)).isTrue()
  }

  @Test
  def reportsMissingClasspathResource(): Unit = {
    val resourcePath: String = "zio-streams-test-resource-that-does-not-exist.txt"

    val result: Either[IOException, Chunk[Byte]] = runStreamEither(ZStream.fromResource(resourcePath))

    assertThat(result.isLeft).isTrue()
    assertThat(result.left.toOption.get).isInstanceOf(classOf[FileNotFoundException])
    assertThat(result.left.toOption.get.getMessage).contains(resourcePath)
  }

  private def runStreamEither(stream: ZStream[Any, IOException, Byte]): Either[IOException, Chunk[Byte]] =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(stream.runCollect.either)
        .getOrThrowFiberFailure()
    }
}
