/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_query_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import zio.Chunk
import zio.Exit
import zio.Runtime
import zio.Unsafe
import zio.ZIO
import zio.query.Cache
import zio.query.CompletedRequestMap
import zio.query.DataSource
import zio.query.Request
import zio.query.ZQuery

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@Timeout(10)
class Zio_query_3Test {
  import Zio_query_3Test._

  @Test
  def collectAllParCachesIdenticalRequestsWithinOneRun(): Unit = {
    val calls: AtomicInteger = new AtomicInteger(0)
    val source: DataSource[Any, GetName] = nameDataSource(calls)
    val queries: List[ZQuery[Any, Nothing, String]] = List(
      ZQuery.fromRequest(GetName(1))(source),
      ZQuery.fromRequest(GetName(1))(source),
      ZQuery.fromRequest(GetName(2))(source)
    )

    val result: List[String] = unsafeRun(ZQuery.collectAllPar(queries).run).toList

    assertThat(result).isEqualTo(List("Ada", "Ada", "Grace"))
    assertThat(calls.get()).isEqualTo(2)
  }

  @Test
  def uncachedQueriesForceRepeatedDataSourceLookups(): Unit = {
    val calls: AtomicInteger = new AtomicInteger(0)
    val source: DataSource[Any, GetName] = nameDataSource(calls)
    val query: ZQuery[Any, Nothing, List[String]] = ZQuery
      .collectAllPar(
        List(
          ZQuery.fromRequest(GetName(1))(source).uncached,
          ZQuery.fromRequest(GetName(1))(source).uncached
        )
      )
      .map(_.toList)

    val result: List[String] = unsafeRun(query.run)

    assertThat(result).isEqualTo(List("Ada", "Ada"))
    assertThat(calls.get()).isEqualTo(2)
  }

  @Test
  def explicitCacheCanBeSharedAcrossIndependentRuns(): Unit = {
    val calls: AtomicInteger = new AtomicInteger(0)
    val source: DataSource[Any, GetName] = nameDataSource(calls)
    val query: ZQuery[Any, Nothing, String] = ZQuery.fromRequest(GetName(3))(source)

    val result: (String, String) = unsafeRun {
      for {
        cache <- Cache.empty
        first <- query.runCache(cache)
        second <- query.runCache(cache)
      } yield (first, second)
    }

    assertThat(result).isEqualTo(("Linus", "Linus"))
    assertThat(calls.get()).isEqualTo(1)
  }

  @Test
  def foreachBatchedRunsBatchedDataSourceAndPreservesOrder(): Unit = {
    val observedBatch: AtomicReference[List[Int]] = new AtomicReference[List[Int]](List.empty[Int])
    val source: DataSource[Any, GetName] = DataSource.fromFunctionBatched("batched-names") { (requests: Chunk[GetName]) =>
      val ids: List[Int] = requests.map(_.id).toList
      observedBatch.set(ids)
      requests.map(request => names(request.id))
    }

    val result: List[String] = unsafeRun {
      ZQuery
        .foreachBatched(List(1, 2, 3)) { (id: Int) =>
          ZQuery.fromRequest(GetName(id))(source)
        }
        .run
    }.toList

    assertThat(result).isEqualTo(List("Ada", "Grace", "Linus"))
    assertThat(observedBatch.get().sorted).isEqualTo(List(1, 2, 3))
  }

  @Test
  def fromRequestsWithMapsInputsToRequestsAndReturnsChunkResults(): Unit = {
    val calls: AtomicInteger = new AtomicInteger(0)
    val source: DataSource[Any, GetName] = nameDataSource(calls)

    val result: Chunk[String] = unsafeRun {
      ZQuery.fromRequestsWith(Chunk(2, 1, 2), (id: Int) => GetName(id))(source).run
    }

    assertThat(result.toList).isEqualTo(List("Grace", "Ada", "Grace"))
    assertThat(calls.get()).isEqualTo(2)
  }

  @Test
  def zioBackedDataSourceFailuresRemainTypedAndRecoverable(): Unit = {
    val source: DataSource[Any, GetMaybeName] = DataSource.fromFunctionZIO("maybe-names") { (request: GetMaybeName) =>
      names.get(request.id) match {
        case Some(value) => ZIO.succeed(value)
        case None        => ZIO.fail(s"missing-${request.id}")
      }
    }

    val result: List[Either[String, String]] = unsafeRun {
      ZQuery
        .foreachPar(List(1, 99, 2)) { (id: Int) =>
          ZQuery.fromRequest(GetMaybeName(id))(source).either
        }
        .run
    }.toList

    assertThat(result).isEqualTo(List(Right("Ada"), Left("missing-99"), Right("Grace")))
  }

  @Test
  def partitionQueryCollectsFailuresAndSuccessesWithoutFailingTheQuery(): Unit = {
    val result: (Iterable[String], Iterable[String]) = unsafeRun {
      ZQuery
        .partitionQuery(List(1, 0, 2, 0)) { (id: Int) =>
          if (id == 0) ZQuery.fail("zero") else ZQuery.succeed(s"value-$id")
        }
        .run
    }

    assertThat(result._1.toList).isEqualTo(List("zero", "zero"))
    assertThat(result._2.toList).isEqualTo(List("value-1", "value-2"))
  }

  @Test
  def completedRequestMapCombinesSuccessFailureAndOptionalResults(): Unit = {
    val firstRequest: GetName = GetName(1)
    val secondRequest: GetName = GetName(2)
    val missingRequest: GetName = GetName(99)
    val failedRequest: GetMaybeName = GetMaybeName(100)

    val successful: CompletedRequestMap = CompletedRequestMap.fromIterable(
      List(
        firstRequest -> Exit.succeed("Ada"),
        secondRequest -> Exit.succeed("Grace")
      )
    )
    val optional: CompletedRequestMap = CompletedRequestMap.fromIterableOption(
      List(
        missingRequest -> Exit.succeed(Option.empty[String]),
        GetName(3) -> Exit.succeed(Some("Linus"))
      )
    )
    val failedRequests: Chunk[Request[String, String]] = Chunk[Request[String, String]](failedRequest)
    val failed: CompletedRequestMap = CompletedRequestMap.fail(failedRequests, "missing-100")
    val combined: CompletedRequestMap = CompletedRequestMap.combine(List(successful, optional, failed))

    assertThat(combined.contains(firstRequest)).isTrue()
    assertThat(combined.contains(missingRequest)).isFalse()
    assertThat(completedValue(combined.lookup(firstRequest))).isEqualTo(Right("Ada"))
    assertThat(completedValue(combined.lookup(secondRequest))).isEqualTo(Right("Grace"))
    assertThat(completedValue(combined.lookup(GetName(3)))).isEqualTo(Right("Linus"))
    assertThat(completedValue(combined.lookup(failedRequest))).isEqualTo(Left("missing-100"))
  }

  private def nameDataSource(calls: AtomicInteger): DataSource[Any, GetName] = {
    DataSource.fromFunction("names") { (request: GetName) =>
      calls.incrementAndGet()
      names(request.id)
    }
  }

  private def completedValue[E, A](exit: Option[Exit[E, A]]): Either[E, A] = {
    exit match {
      case Some(Exit.Success(value)) => Right(value)
      case Some(Exit.Failure(cause)) => Left(cause.failureOption.get)
      case None                      => throw new AssertionError("Missing completed request")
    }
  }

  private def unsafeRun[E, A](effect: ZIO[Any, E, A]): A = {
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
  }
}

object Zio_query_3Test {
  private val names: Map[Int, String] = Map(
    1 -> "Ada",
    2 -> "Grace",
    3 -> "Linus"
  )

  private final case class GetName(id: Int) extends Request[Nothing, String]

  private final case class GetMaybeName(id: Int) extends Request[String, String]
}
