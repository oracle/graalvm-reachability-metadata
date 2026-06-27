/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_http_core_2_13

import akka.actor.ActorSystem
import akka.http.scaladsl.ClientTransport
import akka.http.scaladsl.ConnectionContext
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.OutgoingConnection
import akka.http.scaladsl.HttpsConnectionContext
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.stream.Materializer
import akka.stream.SystemMaterializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLContext
import scala.concurrent.Await
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.control.NonFatal

class Http2AlpnSupportTest {
  @Test
  def configuresApplicationProtocolsForHttp2ClientConnections(): Unit = {
    val originalJavaSpecificationVersion: String = System.getProperty("java.specification.version")
    System.setProperty("java.specification.version", "1.8")

    val system: ActorSystem = ActorSystem("http2-alpn-support-test")
    try {
      implicit val actorSystem: ActorSystem = system
      implicit val executionContext: ExecutionContextExecutor = system.dispatcher
      implicit val materializer: Materializer = SystemMaterializer(system).materializer

      val engineRef: AtomicReference[SSLEngine] = new AtomicReference[SSLEngine]()
      val httpsContext: HttpsConnectionContext = ConnectionContext.httpsClient {
        (host: String, port: Int) =>
          val engine: SSLEngine = SSLContext.getDefault.createSSLEngine(host, port)
          engine.setUseClientMode(true)
          engineRef.set(engine)
          engine
      }
      val settings: ClientConnectionSettings =
        ClientConnectionSettings(system).withTransport(ClosingClientTransport)
      val http2Connection: Flow[HttpRequest, HttpResponse, Future[OutgoingConnection]] = Http(system)
        .connectionTo("example.com")
        .toPort(443)
        .withCustomHttpsConnectionContext(httpsContext)
        .withClientConnectionSettings(settings)
        .http2()

      val streamCompletion: Future[Unit] = Source
        .single(HttpRequest(uri = "/"))
        .via(http2Connection)
        .runWith(Sink.ignore)
        .map(_ => ())
        .recover { case NonFatal(_) => () }

      eventually("the HTTP/2 TLS engine is configured for ALPN") {
        val engine: SSLEngine = engineRef.get()
        engine != null && engine.getSSLParameters.getApplicationProtocols.toSeq.contains("h2")
      }
      Await.result(streamCompletion, 20.seconds)
    } finally {
      System.setProperty("java.specification.version", originalJavaSpecificationVersion)
      Await.result(system.terminate(), 20.seconds)
    }
  }

  private def eventually(description: String)(condition: => Boolean): Unit = {
    val deadline = 20.seconds.fromNow
    while (!condition && deadline.hasTimeLeft()) {
      Thread.sleep(50L)
    }
    assertTrue(condition, description)
  }

  private object ClosingClientTransport extends ClientTransport {
    override def connectTo(
      host: String,
      port: Int,
      settings: ClientConnectionSettings
    )(implicit system: ActorSystem): Flow[ByteString, ByteString, Future[OutgoingConnection]] = {
      val localAddress: InetSocketAddress = new InetSocketAddress("127.0.0.1", 0)
      val remoteAddress: InetSocketAddress = InetSocketAddress.createUnresolved(host, port)
      val connection: OutgoingConnection = OutgoingConnection(localAddress, remoteAddress)
      Flow
        .fromSinkAndSourceCoupled(Sink.ignore, Source.empty[ByteString])
        .mapMaterializedValue(_ => Future.successful(connection))
    }
  }
}
