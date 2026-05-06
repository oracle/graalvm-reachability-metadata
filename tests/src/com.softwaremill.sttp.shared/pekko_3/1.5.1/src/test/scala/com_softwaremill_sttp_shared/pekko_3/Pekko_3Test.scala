/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_shared.pekko_3

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Keep, Sink, Source}
import org.apache.pekko.util.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import sttp.capabilities.StreamMaxLengthExceededException
import sttp.capabilities.pekko.PekkoStreams

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._

class Pekko_3Test {
  @Test
  def limitBytesKeepsChunksWhenTotalSizeIsBelowLimit(): Unit = {
    withMaterializer { materializer =>
      val chunks: List[ByteString] = List(ByteString("ab"), ByteString("cde"), ByteString("f"))
      val source: Source[ByteString, Any] = Source(chunks)
      val limited: Source[ByteString, Any] = PekkoStreams.limitBytes(source, 6L)

      val result: Seq[ByteString] = await(limited.runWith(Sink.seq)(materializer))

      assertThat(result.map(_.utf8String).asJava).containsExactly("ab", "cde", "f")
    }
  }

  @Test
  def limitBytesAllowsAStreamWhoseSizeExactlyMatchesTheLimit(): Unit = {
    withMaterializer { materializer =>
      val source: Source[ByteString, Any] = Source(List(ByteString("hello")))
      val limited: Source[ByteString, Any] = PekkoStreams.limitBytes(source, 5L)

      val result: ByteString = await(limited.runFold(ByteString.empty)(_ ++ _)(materializer))

      assertThat(result.utf8String).isEqualTo("hello")
    }
  }

  @Test
  def limitBytesAllowsAnEmptyStreamWithAZeroByteLimit(): Unit = {
    withMaterializer { materializer =>
      val source: Source[ByteString, Any] = Source.empty[ByteString]
      val limited: Source[ByteString, Any] = PekkoStreams.limitBytes(source, 0L)

      val result: Seq[ByteString] = await(limited.runWith(Sink.seq)(materializer))

      assertThat(result.asJava).isEmpty()
    }
  }

  @Test
  def limitBytesMapsPekkoLimitFailureToSttpStreamMaxLengthExceeded(): Unit = {
    withMaterializer { materializer =>
      val source: Source[ByteString, Any] = Source(List(ByteString("ab"), ByteString("cde")))
      val limited: Source[ByteString, Any] = PekkoStreams.limitBytes(source, 4L)

      val exception: StreamMaxLengthExceededException = assertThrows(
        classOf[StreamMaxLengthExceededException],
        () => {
          await(limited.runWith(Sink.ignore)(materializer))
          ()
        }
      )

      assertThat(exception.maxBytes).isEqualTo(4L)
    }
  }

  @Test
  def limitBytesDoesNotReplaceUnrelatedStreamFailures(): Unit = {
    withMaterializer { materializer =>
      val original: IllegalStateException = new IllegalStateException("upstream failed")
      val source: Source[ByteString, Any] = Source.failed[ByteString](original)
      val limited: Source[ByteString, Any] = PekkoStreams.limitBytes(source, 10L)

      val exception: IllegalStateException = assertThrows(
        classOf[IllegalStateException],
        () => {
          await(limited.runWith(Sink.ignore)(materializer))
          ()
        }
      )

      assertThat(exception).isSameAs(original)
    }
  }

  @Test
  def limitBytesPreservesSourceMaterializedValue(): Unit = {
    withMaterializer { materializer =>
      val source: Source[ByteString, String] =
        Source.single(ByteString("payload")).mapMaterializedValue(_ => "source-materialized")
      val limited: Source[ByteString, Any] = PekkoStreams.limitBytes(source, 10L)

      val materialized: (Any, Future[Seq[ByteString]]) = limited.toMat(Sink.seq)(Keep.both).run()(materializer)
      val materializedValue: Any = materialized._1
      val result: Seq[ByteString] = await(materialized._2)

      assertThat(materializedValue).isEqualTo("source-materialized")
      assertThat(result.map(_.utf8String).asJava).containsExactly("payload")
    }
  }

  private def withMaterializer(testBody: Materializer => Unit): Unit = {
    val system: ActorSystem = ActorSystem("sttp-shared-pekko-test")
    try {
      testBody(Materializer(system))
    } finally {
      await(system.terminate())
    }
  }

  private def await[T](future: Future[T]): T = Await.result(future, 10.seconds)
}
