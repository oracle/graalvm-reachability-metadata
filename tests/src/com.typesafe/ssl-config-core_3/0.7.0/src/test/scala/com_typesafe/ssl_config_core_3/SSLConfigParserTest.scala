/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe.ssl_config_core_3

import scala.jdk.CollectionConverters.*

import com.typesafe.config.{ Config, ConfigFactory }
import com.typesafe.sslconfig.ssl.SSLConfigFactory
import com.typesafe.sslconfig.ssl.SSLConfigSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SSLConfigParserTest {
  @Test
  def parsesConfiguredSslSettings(): Unit = {
    val config: Config = ConfigFactory.parseString(
      """
        |default = false
        |protocol = "TLSv1.2"
        |checkRevocation = true
        |revocationLists = ["https://example.com/revoked.crl"]
        |enabledCipherSuites = ["TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"]
        |enabledProtocols = ["TLSv1.2"]
        |keyManager {
        |  algorithm = null
        |  stores = []
        |  prototype.stores {
        |    type = null
        |    path = null
        |    data = null
        |    password = null
        |    classpath = false
        |  }
        |}
        |trustManager {
        |  algorithm = null
        |  stores = []
        |  prototype.stores {
        |    type = null
        |    path = null
        |    data = null
        |    password = null
        |    classpath = false
        |  }
        |}
        |loose {
        |  allowLegacyHelloMessages = null
        |  allowUnsafeRenegotiation = null
        |  disableHostnameVerification = true
        |  acceptAnyCertificate = true
        |}
        |debug {
        |  all = false
        |  ssl = false
        |  sslctx = false
        |  keymanager = false
        |  trustmanager = false
        |}
        |""".stripMargin
    ).resolve()

    val settings: SSLConfigSettings = SSLConfigFactory.parse(config)

    val revocationLists: Seq[String] = settings.revocationLists.get.map(_.toString)

    assertThat(settings.default).isFalse()
    assertThat(settings.protocol).isEqualTo("TLSv1.2")
    assertThat(settings.checkRevocation.get).isTrue()
    assertThat(revocationLists.asJava).containsExactly("https://example.com/revoked.crl")
    assertThat(settings.enabledCipherSuites.get.asJava).containsExactly("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")
    assertThat(settings.enabledProtocols.get.asJava).containsExactly("TLSv1.2")
    assertThat(settings.loose.disableHostnameVerification).isTrue()
    assertThat(settings.loose.acceptAnyCertificate).isTrue()
  }
}
