/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe.ssl_config_core_2_13

import com.typesafe.config.{ Config, ConfigFactory }
import com.typesafe.sslconfig.ssl.{ NoopHostnameVerifier, SSLConfigFactory, SSLConfigSettings }
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SSLConfigParserTest {
  @Test
  def parsesConfiguredHostnameVerifierClass(): Unit = {
    val config: Config = ConfigFactory.parseString(
      """
        |default = false
        |protocol = "TLSv1.2"
        |checkRevocation = null
        |revocationLists = []
        |enabledCipherSuites = []
        |enabledProtocols = []
        |hostnameVerifierClass = "com.typesafe.sslconfig.ssl.NoopHostnameVerifier"
        |disabledSignatureAlgorithms = []
        |disabledKeyAlgorithms = []
        |sslParameters {
        |  clientAuth = "default"
        |  protocols = []
        |}
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
        |  allowWeakProtocols = false
        |  allowWeakCiphers = false
        |  allowLegacyHelloMessages = null
        |  allowUnsafeRenegotiation = null
        |  disableHostnameVerification = false
        |  disableSNI = false
        |  acceptAnyCertificate = false
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

    assertThat(settings.hostnameVerifierClass).isEqualTo(classOf[NoopHostnameVerifier])
  }
}
