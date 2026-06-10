/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_http_core_3

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.ClientTransport
import org.apache.pekko.http.scaladsl.ConnectionContext
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.settings.ClientConnectionSettings
import org.apache.pekko.stream.SystemMaterializer
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class Http2AlpnSupportTest {
  @Test
  def configuresHttp2AlpnWhenHttp2TlsClientFlowStarts(): Unit = {
    val previousSpecificationVersion: String = System.getProperty("java.specification.version")
    System.setProperty("java.specification.version", "1.8")

    var system: ActorSystem = null
    try {
      system = ActorSystem("http2-alpn-support-test")
      implicit val implicitSystem: ActorSystem = system
      val engineCreated: AtomicBoolean = new AtomicBoolean(false)

      val httpsContext = ConnectionContext.httpsClient { (host: String, port: Int) =>
        engineCreated.set(true)
        val engine = SSLContext.getDefault.createSSLEngine(host, port)
        engine.setUseClientMode(true)
        engine
      }
      val settings = ClientConnectionSettings(system).withTransport(ClosingTransport)
      val flow = Http()
        .connectionTo("example.invalid")
        .withCustomHttpsConnectionContext(httpsContext)
        .withClientConnectionSettings(settings)
        .http2()

      val completion = Source.single(HttpRequest()).via(flow).runWith(Sink.ignore)(
        SystemMaterializer(system).materializer)
      Await.ready(completion, 10.seconds)

      assertThat(engineCreated.get()).isTrue
    } finally {
      if (system != null) {
        Await.ready(system.terminate(), 10.seconds)
      }
      System.setProperty("java.specification.version", previousSpecificationVersion)
    }
  }

  private object ClosingTransport extends ClientTransport {
    override def connectTo(host: String, port: Int, settings: ClientConnectionSettings)(
        implicit system: ActorSystem): Flow[ByteString, ByteString, Future[Http.OutgoingConnection]] = {
      val remoteAddress = InetSocketAddress.createUnresolved(host, port)
      val localAddress = new InetSocketAddress("127.0.0.1", 0)
      Flow[ByteString]
        .take(1)
        .mapConcat(_ => List.empty[ByteString])
        .mapMaterializedValue(_ => Future.successful(Http.OutgoingConnection(localAddress, remoteAddress)))
    }
  }
}
