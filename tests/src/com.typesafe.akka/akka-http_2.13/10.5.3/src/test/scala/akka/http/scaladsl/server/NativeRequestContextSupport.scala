/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package akka.http.scaladsl.server

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutor

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Cancellable
import akka.actor.Props
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.settings.ParserSettings
import akka.http.scaladsl.settings.RoutingSettings
import akka.stream.ActorMaterializerSettings
import akka.stream.Attributes
import akka.stream.ClosedShape
import akka.stream.Graph
import akka.stream.MaterializationContext
import akka.stream.Materializer
import com.typesafe.config.Config

object NativeRequestContextSupport {
  def newRequestContext(request: HttpRequest, config: Config): RequestContext = {
    val executionContext: ExecutionContextExecutor = ExecutionContext.global
    val materializer: Materializer = new StubMaterializer(executionContext)
    new RequestContextImpl(
      request,
      request.uri.path,
      executionContext,
      materializer,
      akka.event.NoLogging,
      RoutingSettings(config),
      ParserSettings(config))
  }
}

final class NoOpCancellable extends Cancellable {
  override def cancel(): Boolean = false

  override def isCancelled: Boolean = false
}

final class StubMaterializer(private val executionContextExecutor: ExecutionContextExecutor) extends Materializer {
  private val cancellable: Cancellable = new NoOpCancellable

  override def withNamePrefix(name: String): Materializer = this

  override def materialize[Mat](runnable: Graph[ClosedShape, Mat]): Mat = unsupported("materialize")

  override def materialize[Mat](runnable: Graph[ClosedShape, Mat], attributes: Attributes): Mat =
    unsupported("materialize")

  override def executionContext(): ExecutionContextExecutor = executionContextExecutor

  override def scheduleOnce(delay: scala.concurrent.duration.FiniteDuration, task: Runnable): Cancellable =
    cancellable

  override def scheduleWithFixedDelay(
      initialDelay: scala.concurrent.duration.FiniteDuration,
      delay: scala.concurrent.duration.FiniteDuration,
      task: Runnable): Cancellable = cancellable

  override def scheduleAtFixedRate(
      initialDelay: scala.concurrent.duration.FiniteDuration,
      interval: scala.concurrent.duration.FiniteDuration,
      task: Runnable): Cancellable = cancellable

  override def schedulePeriodically(
      initialDelay: scala.concurrent.duration.FiniteDuration,
      interval: scala.concurrent.duration.FiniteDuration,
      task: Runnable): Cancellable = cancellable

  override def shutdown(): Unit = ()

  override def isShutdown(): Boolean = false

  override def system(): ActorSystem = unsupported("system")

  override def logger(): akka.event.LoggingAdapter = akka.event.NoLogging

  override def supervisor(): ActorRef = unsupported("supervisor")

  override def actorOf(context: MaterializationContext, props: Props): ActorRef = unsupported("actorOf")

  override def settings(): ActorMaterializerSettings = unsupported("settings")

  private def unsupported[T](operation: String): T = {
    throw new UnsupportedOperationException(operation)
  }
}
