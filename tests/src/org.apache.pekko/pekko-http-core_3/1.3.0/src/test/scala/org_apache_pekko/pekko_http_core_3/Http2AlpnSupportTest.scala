/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_http_core_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import javax.net.ssl.SSLContext
import scala.util.Try

class Http2AlpnSupportTest {
  @Test
  def clientApplicationProtocolSetupChecksForOldJettyAlpnAgent(): Unit = {
    val previousSpecificationVersion: String = System.getProperty("java.specification.version")
    System.setProperty("java.specification.version", "1.8")

    try {
      val engine = SSLContext.getDefault.createSSLEngine()
      val result = Try(Http2AlpnSupportInvoker.setClientApplicationProtocols(engine))

      result.failed.foreach { failure =>
        assertThat(containsMessage(failure, "jetty-alpn-agent")).isTrue()
      }
      result.foreach { _ =>
        assertThat(engine.getSSLParameters.getApplicationProtocols).containsExactly("h2")
      }
    } finally {
      if (previousSpecificationVersion == null) System.clearProperty("java.specification.version")
      else System.setProperty("java.specification.version", previousSpecificationVersion)
    }
  }

  private def containsMessage(throwable: Throwable, text: String): Boolean = {
    if (throwable == null) false
    else if (Option(throwable.getMessage).exists(_.contains(text))) true
    else containsMessage(throwable.getCause, text)
  }
}
