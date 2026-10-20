/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thesamet_scalapb.scalapb_runtime_2_13

import com.google.protobuf.struct.ListValue
import com.google.protobuf.struct.Struct
import com.google.protobuf.struct.Value
import com.google.protobuf.timestamp.Timestamp
import com.google.protobuf.wrappers.Int32Value
import com.google.protobuf.wrappers.StringValue
import io.grpc.CallOptions
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Server
import io.grpc.ServerServiceDefinition
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.ServerCalls
import io.grpc.stub.StreamObserver
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import scalapb.grpc.ClientCalls
import scalapb.grpc.Grpc
import scalapb.grpc.Marshaller
import scalapb.grpc.ProtoUtils

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import scala.collection.concurrent.TrieMap
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success
import scala.util.Try

/**
 * Exercises gRPC transport of generated messages through `scalapb.grpc`: `Marshaller.forMessage`
 * serializes requests and responses, `ClientCalls` drives unary, server-streaming and
 * client-streaming calls, and `ProtoUtils.keyForProto` carries a message in error trailers.
 *
 * A feature-flag service stores each flag as a `Struct` keyed by name. Clients look flags up,
 * stream the change history of a flag as `Timestamp`s, and publish flags in a batch. A missing
 * flag fails with `NOT_FOUND`, and the trailers carry a `Struct` with suggested flag names.
 */
class FeatureFlagServiceGrpcMarshallingTest {
  private var server: Server = _
  private var channel: ManagedChannel = _
  private val store: FlagStore = new FlagStore

  @BeforeEach
  def startServer(): Unit = {
    val name: String = InProcessServerBuilder.generateName()
    server = InProcessServerBuilder
      .forName(name)
      .directExecutor()
      .addService(FeatureFlagService.bind(store))
      .build()
      .start()
    channel = InProcessChannelBuilder.forName(name).directExecutor().build()
  }

  @AfterEach
  def stopServer(): Unit = {
    channel.shutdownNow()
    server.shutdownNow()
    assertTrue(channel.awaitTermination(10, TimeUnit.SECONDS))
    assertTrue(server.awaitTermination(10, TimeUnit.SECONDS))
  }

  @Test
  def unaryLookupsReturnTheStoredFlagDocument(): Unit = {
    store.put("checkout.v2", flag(enabled = true, rollout = 25.0, owner = "payments"))
    store.put("search.typo-tolerance", flag(enabled = false, rollout = 0.0, owner = "search"))

    val checkout: Struct = ClientCalls.blockingUnaryCall(
      channel, FeatureFlagService.Lookup, CallOptions.DEFAULT, StringValue("checkout.v2")
    )
    assertEquals(true, checkout.fields("enabled").getBoolValue)
    assertEquals(25.0, checkout.fields("rollout").getNumberValue)
    assertEquals("payments", checkout.fields("owner").getStringValue)

    val pending: Future[Struct] = ClientCalls.asyncUnaryCall(
      channel, FeatureFlagService.Lookup, CallOptions.DEFAULT, StringValue("search.typo-tolerance")
    )
    val search: Struct = Await.result(pending, 10.seconds)
    assertEquals(false, search.fields("enabled").getBoolValue)
    assertEquals("search", search.fields("owner").getStringValue)
  }

  @Test
  def missingFlagFailsWithSuggestionsInTheTrailers(): Unit = {
    store.put("checkout.v2", flag(enabled = true, rollout = 100.0, owner = "payments"))
    store.put("checkout.express", flag(enabled = false, rollout = 0.0, owner = "payments"))
    store.put("search.ranking", flag(enabled = true, rollout = 50.0, owner = "search"))

    val failure: StatusRuntimeException = assertThrows(
      classOf[StatusRuntimeException],
      () => ClientCalls.blockingUnaryCall(
        channel, FeatureFlagService.Lookup, CallOptions.DEFAULT, StringValue("checkout.v3")
      )
    )
    assertEquals(Status.Code.NOT_FOUND, failure.getStatus.getCode)
    assertEquals("Unknown flag checkout.v3", failure.getStatus.getDescription)

    val details: Struct = failure.getTrailers.get(FeatureFlagService.SuggestionsKey)
    assertEquals("google.protobuf.struct-bin", FeatureFlagService.SuggestionsKey.name())
    assertEquals(
      List("checkout.express", "checkout.v2"),
      details.fields("suggestions").getListValue.values.map(_.getStringValue).toList
    )

    val pending: Future[Struct] = ClientCalls.asyncUnaryCall(
      channel, FeatureFlagService.Lookup, CallOptions.DEFAULT, StringValue("billing.dunning")
    )
    val asyncFailure: Try[Struct] = Await.ready(pending, 10.seconds).value.get
    asyncFailure match {
      case Failure(e: StatusRuntimeException) =>
        assertEquals(Status.Code.NOT_FOUND, e.getStatus.getCode)
        assertEquals(Seq.empty, e.getTrailers.get(FeatureFlagService.SuggestionsKey)
          .fields("suggestions").getListValue.values)
      case other => throw new AssertionError(s"Expected NOT_FOUND, got $other")
    }
  }

  @Test
  def historyStreamsChangeTimestampsInOrder(): Unit = {
    store.put("checkout.v2", flag(enabled = true, rollout = 10.0, owner = "payments"))
    store.put("checkout.v2", flag(enabled = true, rollout = 50.0, owner = "payments"))
    store.put("checkout.v2", flag(enabled = true, rollout = 100.0, owner = "payments"))

    val history: List[Timestamp] = ClientCalls
      .blockingServerStreamingCall(
        channel, FeatureFlagService.History, CallOptions.DEFAULT, StringValue("checkout.v2")
      )
      .toList

    assertEquals(3, history.size)
    assertEquals(history.sortBy(t => (t.seconds, t.nanos)), history)
    assertEquals(List(1760000000L, 1760000060L, 1760000120L), history.map(_.seconds))
    assertEquals(List(0, 1, 2), history.map(_.nanos))
  }

  @Test
  def clientStreamingPublishCountsTheAcceptedFlags(): Unit = {
    val done: CountDownLatch = new CountDownLatch(1)
    val result: AtomicReference[Try[Int32Value]] = new AtomicReference[Try[Int32Value]]()
    val responses: StreamObserver[Int32Value] = new StreamObserver[Int32Value] {
      override def onNext(value: Int32Value): Unit = result.set(Success(value))
      override def onError(t: Throwable): Unit = {
        result.set(Failure(t))
        done.countDown()
      }
      override def onCompleted(): Unit = done.countDown()
    }

    val requests: StreamObserver[Struct] = ClientCalls.asyncClientStreamingCall(
      channel, FeatureFlagService.Publish, CallOptions.DEFAULT, responses
    )
    requests.onNext(named("billing.retry", flag(enabled = true, rollout = 5.0, owner = "billing")))
    requests.onNext(named("billing.invoice-pdf", flag(enabled = false, rollout = 0.0, owner = "billing")))
    requests.onNext(Struct(fields = Map("enabled" -> Value(Value.Kind.BoolValue(true)))))
    requests.onCompleted()

    assertTrue(done.await(10, TimeUnit.SECONDS))
    assertEquals(Success(Int32Value(2)), result.get())
    assertEquals(Set("billing.invoice-pdf", "billing.retry"), store.names)
    assertEquals(
      "billing",
      ClientCalls.blockingUnaryCall(
        channel, FeatureFlagService.Lookup, CallOptions.DEFAULT, StringValue("billing.retry")
      ).fields("owner").getStringValue
    )
  }

  private def flag(enabled: Boolean, rollout: Double, owner: String): Struct =
    Struct(fields = Map(
      "enabled" -> Value(Value.Kind.BoolValue(enabled)),
      "rollout" -> Value(Value.Kind.NumberValue(rollout)),
      "owner" -> Value(Value.Kind.StringValue(owner))
    ))

  private def named(name: String, document: Struct): Struct =
    document.update(_.fields :+= ("name" -> Value(Value.Kind.StringValue(name))))

  /** Flags by name, each with the timestamps of its successive versions. */
  final class FlagStore {
    private val flags: TrieMap[String, List[(Timestamp, Struct)]] = TrieMap.empty

    def put(name: String, document: Struct): Unit = synchronized {
      val versions: List[(Timestamp, Struct)] = flags.getOrElse(name, Nil)
      val changedAt: Timestamp =
        Timestamp(seconds = 1760000000L + 60L * versions.size, nanos = versions.size)
      flags.put(name, versions :+ (changedAt -> document))
    }

    def current(name: String): Option[Struct] = flags.get(name).map(_.last._2)

    def history(name: String): List[Timestamp] = flags.getOrElse(name, Nil).map(_._1)

    def names: Set[String] = flags.keySet.toSet
  }

  /** The service contract: method descriptors whose payloads are marshalled by ScalaPB. */
  object FeatureFlagService {
    private val ServiceName: String = "flags.FeatureFlagService"

    val Lookup: MethodDescriptor[StringValue, Struct] = MethodDescriptor
      .newBuilder(Marshaller.forMessage(StringValue), Marshaller.forMessage(Struct))
      .setType(MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(MethodDescriptor.generateFullMethodName(ServiceName, "Lookup"))
      .build()

    val History: MethodDescriptor[StringValue, Timestamp] = MethodDescriptor
      .newBuilder(Marshaller.forMessage(StringValue), Marshaller.forMessage(Timestamp))
      .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
      .setFullMethodName(MethodDescriptor.generateFullMethodName(ServiceName, "History"))
      .build()

    val Publish: MethodDescriptor[Struct, Int32Value] = MethodDescriptor
      .newBuilder(Marshaller.forMessage(Struct), Marshaller.forMessage(Int32Value))
      .setType(MethodDescriptor.MethodType.CLIENT_STREAMING)
      .setFullMethodName(MethodDescriptor.generateFullMethodName(ServiceName, "Publish"))
      .build()

    val SuggestionsKey: Metadata.Key[Struct] = ProtoUtils.keyForProto(Struct)

    def bind(store: FlagStore): ServerServiceDefinition =
      ServerServiceDefinition
        .builder(ServiceName)
        .addMethod(Lookup, ServerCalls.asyncUnaryCall(
          (request: StringValue, observer: StreamObserver[Struct]) =>
            lookup(store, request.value, observer)
        ))
        .addMethod(History, ServerCalls.asyncServerStreamingCall(
          (request: StringValue, observer: StreamObserver[Timestamp]) => {
            store.history(request.value).foreach(observer.onNext)
            observer.onCompleted()
          }
        ))
        .addMethod(Publish, ServerCalls.asyncClientStreamingCall(
          (observer: StreamObserver[Int32Value]) => new PublishCollector(store, observer)
        ))
        .build()

    private def lookup(store: FlagStore, name: String, observer: StreamObserver[Struct]): Unit =
      store.current(name) match {
        case Some(document) => Grpc.completeObserver(observer)(Success(document))
        case None =>
          val prefix: String = name.takeWhile(_ != '.')
          val suggestions: Seq[Value] = store.names.toSeq.sorted
            .filter(_.startsWith(prefix + "."))
            .map(n => Value(Value.Kind.StringValue(n)))
          val trailers: Metadata = new Metadata()
          trailers.put(SuggestionsKey, Struct(fields = Map(
            "suggestions" -> Value(Value.Kind.ListValue(ListValue(suggestions)))
          )))
          observer.onError(
            Status.NOT_FOUND.withDescription(s"Unknown flag $name").asRuntimeException(trailers)
          )
      }
  }

  /** Stores every published flag that carries a name and replies with how many it accepted. */
  final class PublishCollector(store: FlagStore, observer: StreamObserver[Int32Value])
      extends StreamObserver[Struct] {
    private var accepted: Int = 0

    override def onNext(document: Struct): Unit =
      document.fields.get("name").map(_.getStringValue).foreach { name =>
        store.put(name, document.update(_.fields.modify(_ - "name")))
        accepted += 1
      }

    override def onError(t: Throwable): Unit = observer.onError(t)

    override def onCompleted(): Unit = Grpc.completeObserver(observer)(Success(Int32Value(accepted)))
  }
}
