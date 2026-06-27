/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_http_core_2_13

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.ClientTransport
import org.apache.pekko.http.scaladsl.ConnectionContext
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.AttributeKey
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.SimpleRequestResponseAttribute
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
import java.nio.ByteBuffer
import java.util.function.BiFunction
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSession
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.SeqHasAsJava

class Http2AlpnSupportTest {
  @Test
  def http2ClientConnectionConfiguresJdkAlpn(): Unit = synchronized {
    val originalSpecVersion: String = System.getProperty("java.specification.version")
    System.setProperty("java.specification.version", "1.8")

    var system: ActorSystem = null
    try {
      system = ActorSystem("http2-alpn-support-test")
      implicit val materializer: Materializer = SystemMaterializer(system).materializer
      val configuredProtocols: Promise[Seq[String]] = Promise[Seq[String]]()
      val httpsContext = ConnectionContext.httpsClient { (host: String, port: Int) =>
        val delegate: SSLEngine = SSLContext.getDefault.createSSLEngine(host, port)
        new ObservedSslEngine(delegate, configuredProtocols)
      }
      val settings: ClientConnectionSettings = ClientConnectionSettings(system)
        .withIdleTimeout(10.seconds)
        .withConnectingTimeout(10.seconds)
        .withTransport(new ClosingClientTransport)

      val requestAssociation = AttributeKey[SimpleRequestResponseAttribute[String]]("request-id")
      Source
        .single(
          HttpRequest(uri = "https://example.invalid/")
            .addAttribute(requestAssociation, SimpleRequestResponseAttribute("alpn")))
        .via(
          Http(system)
            .connectionTo("example.invalid")
            .withCustomHttpsConnectionContext(httpsContext)
            .withClientConnectionSettings(settings)
            .http2())
        .runWith(Sink.ignore)

      val protocols: Seq[String] = Await.result(configuredProtocols.future, 10.seconds)
      assertThat(protocols.asJava).containsExactly("h2")
    } finally {
      if (system != null) Await.ready(system.terminate(), 10.seconds)
      if (originalSpecVersion == null) System.clearProperty("java.specification.version")
      else System.setProperty("java.specification.version", originalSpecVersion)
    }
  }

  private final class ClosingClientTransport extends ClientTransport {
    override def connectTo(host: String, port: Int, settings: ClientConnectionSettings)(
        implicit system: ActorSystem): Flow[ByteString, ByteString, Future[Http.OutgoingConnection]] = {
      val localAddress: InetSocketAddress = new InetSocketAddress("127.0.0.1", 0)
      val remoteAddress: InetSocketAddress = InetSocketAddress.createUnresolved(host, port)
      Flow[ByteString]
        .take(1)
        .map(_ => ByteString.empty)
        .mapMaterializedValue(_ => Future.successful(Http.OutgoingConnection(localAddress, remoteAddress)))
    }
  }

  private final class ObservedSslEngine(delegate: SSLEngine, configuredProtocols: Promise[Seq[String]])
      extends SSLEngine(delegate.getPeerHost, delegate.getPeerPort) {
    override def wrap(srcs: Array[ByteBuffer], offset: Int, length: Int, dst: ByteBuffer): SSLEngineResult =
      delegate.wrap(srcs, offset, length, dst)

    override def unwrap(src: ByteBuffer, dsts: Array[ByteBuffer], offset: Int, length: Int): SSLEngineResult =
      delegate.unwrap(src, dsts, offset, length)

    override def getDelegatedTask: Runnable = delegate.getDelegatedTask

    override def closeInbound(): Unit = delegate.closeInbound()

    override def isInboundDone: Boolean = delegate.isInboundDone

    override def closeOutbound(): Unit = delegate.closeOutbound()

    override def isOutboundDone: Boolean = delegate.isOutboundDone

    override def getSupportedCipherSuites: Array[String] = delegate.getSupportedCipherSuites

    override def getEnabledCipherSuites: Array[String] = delegate.getEnabledCipherSuites

    override def setEnabledCipherSuites(suites: Array[String]): Unit = delegate.setEnabledCipherSuites(suites)

    override def getSupportedProtocols: Array[String] = delegate.getSupportedProtocols

    override def getEnabledProtocols: Array[String] = delegate.getEnabledProtocols

    override def setEnabledProtocols(protocols: Array[String]): Unit = delegate.setEnabledProtocols(protocols)

    override def getSession: SSLSession = delegate.getSession

    override def getHandshakeSession: SSLSession = delegate.getHandshakeSession

    override def beginHandshake(): Unit = delegate.beginHandshake()

    override def getHandshakeStatus: SSLEngineResult.HandshakeStatus = delegate.getHandshakeStatus

    override def setUseClientMode(mode: Boolean): Unit = delegate.setUseClientMode(mode)

    override def getUseClientMode: Boolean = delegate.getUseClientMode

    override def setNeedClientAuth(need: Boolean): Unit = delegate.setNeedClientAuth(need)

    override def getNeedClientAuth: Boolean = delegate.getNeedClientAuth

    override def setWantClientAuth(want: Boolean): Unit = delegate.setWantClientAuth(want)

    override def getWantClientAuth: Boolean = delegate.getWantClientAuth

    override def setEnableSessionCreation(flag: Boolean): Unit = delegate.setEnableSessionCreation(flag)

    override def getEnableSessionCreation: Boolean = delegate.getEnableSessionCreation

    override def getSSLParameters: SSLParameters = delegate.getSSLParameters

    override def setSSLParameters(params: SSLParameters): Unit = {
      delegate.setSSLParameters(params)
      val applicationProtocols: Seq[String] = delegate.getSSLParameters.getApplicationProtocols.toSeq
      if (applicationProtocols.nonEmpty) configuredProtocols.trySuccess(applicationProtocols)
    }

    override def getApplicationProtocol: String = delegate.getApplicationProtocol

    override def getHandshakeApplicationProtocol: String = delegate.getHandshakeApplicationProtocol

    override def setHandshakeApplicationProtocolSelector(
        selector: BiFunction[SSLEngine, java.util.List[String], String]): Unit =
      delegate.setHandshakeApplicationProtocolSelector(selector)

    override def getHandshakeApplicationProtocolSelector: BiFunction[SSLEngine, java.util.List[String], String] =
      delegate.getHandshakeApplicationProtocolSelector
  }
}
