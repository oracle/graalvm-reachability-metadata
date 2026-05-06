/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_http_core_2_13

import org.apache.pekko.Done
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.ClientTransport
import org.apache.pekko.http.scaladsl.ConnectionContext
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.Http.OutgoingConnection
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.settings.ClientConnectionSettings
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.SystemMaterializer
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLContext
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success

class Http2AlpnSupportTest {
  @Test
  def http2ClientConnectionChecksJdkAlpnCompatibility(): Unit = {
    withJavaSpecificationVersion("1.8") {
      implicit val system: ActorSystem = ActorSystem("http2-alpn-support-test")
      implicit val materializer: Materializer = SystemMaterializer(system).materializer

      try {
        val engineRef: AtomicReference[SSLEngine] = new AtomicReference[SSLEngine]()
        val context = ConnectionContext.httpsClient { (host: String, port: Int) =>
          val engine = SSLContext.getDefault.createSSLEngine(host, port)
          engine.setUseClientMode(true)
          engineRef.set(engine)
          engine
        }
        val settings = ClientConnectionSettings(system).withTransport(ClosingTransport)
        val http2Connection = Http(system)
          .connectionTo("example.com")
          .toPort(443)
          .withCustomHttpsConnectionContext(context)
          .withClientConnectionSettings(settings)
          .http2()

        val completion: Future[Done] = Source.single(HttpRequest()).via(http2Connection).runWith(Sink.ignore)

        eventually(5.seconds) {
          engineRef.get() != null
        }

        Await.ready(completion, 10.seconds)
        completion.value match {
          case Some(Failure(exception)) if containsMessage(exception, "jetty-alpn-agent") =>
            assertThat(engineRef.get()).isNotNull
          case Some(Failure(_)) | Some(Success(_)) =>
            assertThat(engineRef.get().getSSLParameters.getApplicationProtocols).contains("h2")
          case None =>
            throw new AssertionError("HTTP/2 connection stream did not finish within the bounded wait")
        }
      } finally {
        Await.result(system.terminate(), 10.seconds)
      }
    }
  }

  private def withJavaSpecificationVersion[T](version: String)(body: => T): T = {
    val propertyName = "java.specification.version"
    val originalVersion = Option(System.getProperty(propertyName))
    System.setProperty(propertyName, version)
    try body
    finally {
      originalVersion match {
        case Some(value) => System.setProperty(propertyName, value)
        case None        => System.clearProperty(propertyName)
      }
    }
  }

  private def containsMessage(throwable: Throwable, message: String): Boolean = {
    Iterator.iterate[Throwable](throwable)(_.getCause)
      .takeWhile(_ != null)
      .exists(current => Option(current.getMessage).exists(_.contains(message)))
  }

  private def eventually(timeout: FiniteDuration)(condition: => Boolean): Unit = {
    val deadline = timeout.fromNow
    while (!condition && deadline.hasTimeLeft()) {
      Thread.sleep(25L)
    }
    assertThat(condition).isTrue
  }

  private object ClosingTransport extends ClientTransport {
    override def connectTo(host: String, port: Int, settings: ClientConnectionSettings)(
        implicit system: ActorSystem): Flow[ByteString, ByteString, Future[OutgoingConnection]] = {
      val outgoingConnection = OutgoingConnection(
        new InetSocketAddress("127.0.0.1", 0),
        InetSocketAddress.createUnresolved(host, port))

      Flow.fromSinkAndSourceCoupled(Sink.ignore, Source.empty[ByteString])
        .mapMaterializedValue(_ => Future.successful(outgoingConnection))
    }
  }
}
