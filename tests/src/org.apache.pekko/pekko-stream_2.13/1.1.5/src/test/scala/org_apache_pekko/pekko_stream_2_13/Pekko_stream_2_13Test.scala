/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_stream_2_13

import org.apache.pekko.{Done, NotUsed}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.{FlowShape, KillSwitches, Materializer, OverflowStrategy}
import org.apache.pekko.stream.{QueueOfferResult, UniqueKillSwitch}
import org.apache.pekko.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}
import org.apache.pekko.stream.scaladsl.{SourceQueueWithComplete, ZipWith}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{AfterEach, BeforeEach, Test}

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}

class Pekko_stream_2_13Test {
  private implicit var system: ActorSystem = _
  private implicit var materializer: Materializer = _
  private implicit var executionContext: ExecutionContext = _

  private val awaitTimeout: FiniteDuration = 10.seconds

  @BeforeEach
  def setUp(): Unit = {
    val actorSystem: ActorSystem = ActorSystem("pekko-stream-test")
    system = actorSystem
    materializer = Materializer(actorSystem)
    executionContext = actorSystem.dispatcher
  }

  @AfterEach
  def tearDown(): Unit = {
    Await.result(system.terminate(), awaitTimeout)
  }

  @Test
  def transformsFiltersAndFoldsFiniteSource(): Unit = {
    val result: Future[Int] = Source(1 to 10)
      .via(Flow[Int].map(_ * 2).filter(_ % 4 == 0))
      .runWith(Sink.fold(0)(_ + _))

    assertEquals(60, Await.result(result, awaitTimeout))
  }

  @Test
  def materializesSourceQueueAndBackpressuresOffers(): Unit = {
    val (queue: SourceQueueWithComplete[Int], result: Future[Int]) = Source
      .queue[Int](4, OverflowStrategy.backpressure)
      .toMat(Sink.fold(0)(_ + _))(Keep.both)
      .run()

    assertEquals(QueueOfferResult.Enqueued, Await.result(queue.offer(1), awaitTimeout))
    assertEquals(QueueOfferResult.Enqueued, Await.result(queue.offer(2), awaitTimeout))
    assertEquals(QueueOfferResult.Enqueued, Await.result(queue.offer(3), awaitTimeout))
    queue.complete()

    assertEquals(6, Await.result(result, awaitTimeout))
  }

  @Test
  def buildsReusableGraphDslFlow(): Unit = {
    val graphFlow: Flow[Int, String, NotUsed] = Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Int](2))
      val zip = builder.add(ZipWith[Int, Int, String]((incremented: Int, squared: Int) => s"$incremented:$squared"))

      broadcast.out(0) ~> Flow[Int].map(_ + 1) ~> zip.in0
      broadcast.out(1) ~> Flow[Int].map(number => number * number) ~> zip.in1

      FlowShape(broadcast.in, zip.out)
    })

    val result: Future[Seq[String]] = Source(1 to 4).via(graphFlow).runWith(Sink.seq)

    assertEquals(List("2:1", "3:4", "4:9", "5:16"), Await.result(result, awaitTimeout).toList)
  }

  @Test
  def recoversFromFailedStreamStage(): Unit = {
    val result: Future[Seq[Int]] = Source(List("1", "2", "not-a-number", "4"))
      .map(_.toInt)
      .recover { case _: NumberFormatException => -1 }
      .runWith(Sink.seq)

    assertEquals(List(1, 2, -1), Await.result(result, awaitTimeout).toList)
  }

  @Test
  def splitsPrefixFromTailSource(): Unit = {
    val result: Future[(List[Int], List[Int])] = Source(1 to 5)
      .prefixAndTail(2)
      .runWith(Sink.head)
      .flatMap { case (prefix: Seq[Int], tail: Source[Int, NotUsed]) =>
        tail.runWith(Sink.seq).map(rest => (prefix.toList, rest.toList))
      }

    assertEquals((List(1, 2), List(3, 4, 5)), Await.result(result, awaitTimeout))
  }

  @Test
  def groupsAndFlattensBatchesInOrder(): Unit = {
    val result: Future[Seq[Int]] = Source(1 to 7)
      .grouped(3)
      .mapConcat(group => group.reverse.toList)
      .runWith(Sink.seq)

    assertEquals(List(3, 2, 1, 6, 5, 4, 7), Await.result(result, awaitTimeout).toList)
  }

  @Test
  def partitionsElementsIntoSubstreamsAndMergesResults(): Unit = {
    val result: Future[Seq[(String, Vector[String])]] = Source(
      List("apple", "avocado", "banana", "blueberry", "apricot", "blackberry")
    )
      .groupBy(2, _.head)
      .fold(("", Vector.empty[String])) { case ((_, words), word) =>
        (word.head.toString, words :+ word.toUpperCase)
      }
      .mergeSubstreams
      .runWith(Sink.seq)

    assertEquals(
      List(
        ("a", Vector("APPLE", "AVOCADO", "APRICOT")),
        ("b", Vector("BANANA", "BLUEBERRY", "BLACKBERRY"))
      ),
      Await.result(result, awaitTimeout).toList.sortBy(_._1)
    )
  }

  @Test
  def shutsDownRunningStreamWithKillSwitch(): Unit = {
    val (killSwitch: UniqueKillSwitch, completion: Future[Done]) = Source
      .maybe[Int]
      .viaMat(KillSwitches.single)(Keep.right)
      .toMat(Sink.ignore)(Keep.both)
      .run()

    killSwitch.shutdown()

    assertEquals(Done, Await.result(completion, awaitTimeout))
  }
}
