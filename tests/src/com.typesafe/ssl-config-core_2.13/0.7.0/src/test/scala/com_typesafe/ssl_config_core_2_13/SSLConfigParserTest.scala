/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe.ssl_config_core_2_13

import com.typesafe.config.{ Config, ConfigFactory }
import com.typesafe.sslconfig.ssl.KeyStoreConfig
import com.typesafe.sslconfig.ssl.SSLConfigFactory
import com.typesafe.sslconfig.ssl.SSLConfigSettings
import com.typesafe.sslconfig.ssl.TrustStoreConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SSLConfigParserTest {
  @Test
  def parsesConfiguredSettings(): Unit = {
    val config: Config = ConfigFactory.parseString(
      """
        |default = false
        |protocol = "TLSv1.2"
        |checkRevocation = true
        |revocationLists = ["https://example.com/root.crl"]
        |enabledCipherSuites = ["TLS_AES_128_GCM_SHA256"]
        |enabledProtocols = ["TLSv1.3"]
        |keyManager {
        |  algorithm = "SunX509"
        |  stores = [{
        |    type = "PKCS12"
        |    path = "client-keystore.p12"
        |    data = null
        |    password = "changeit"
        |    classpath = true
        |  }]
        |  prototype.stores {
        |    type = null
        |    path = null
        |    data = null
        |    password = null
        |    classpath = false
        |  }
        |}
        |trustManager {
        |  algorithm = "PKIX"
        |  stores = [{
        |    type = "JKS"
        |    path = "truststore.jks"
        |    data = null
        |    password = "changeit"
        |    classpath = false
        |  }]
        |  prototype.stores {
        |    type = null
        |    path = null
        |    data = null
        |    password = null
        |    classpath = false
        |  }
        |}
        |loose {
        |  allowLegacyHelloMessages = true
        |  allowUnsafeRenegotiation = false
        |  disableHostnameVerification = true
        |  acceptAnyCertificate = true
        |}
        |debug {
        |  all = false
        |  ssl = true
        |  sslctx = true
        |  keymanager = false
        |  trustmanager = true
        |}
        |""".stripMargin
    ).resolve()

    val settings: SSLConfigSettings = SSLConfigFactory.parse(config)

    assertThat(settings.default).isFalse()
    assertThat(settings.protocol).isEqualTo("TLSv1.2")
    assertThat(settings.checkRevocation).isEqualTo(Some(true))
    assertThat(settings.revocationLists.map(_.map(_.toString))).isEqualTo(Some(Seq("https://example.com/root.crl")))
    assertThat(settings.enabledCipherSuites).isEqualTo(Some(Seq("TLS_AES_128_GCM_SHA256")))
    assertThat(settings.enabledProtocols).isEqualTo(Some(Seq("TLSv1.3")))

    assertThat(settings.keyManagerConfig.algorithm).isEqualTo("SunX509")
    assertThat(settings.keyManagerConfig.keyStoreConfigs.size).isEqualTo(1)
    val keyStoreConfig: KeyStoreConfig = settings.keyManagerConfig.keyStoreConfigs.head
    assertThat(keyStoreConfig.storeType).isEqualTo("PKCS12")
    assertThat(keyStoreConfig.filePath).isEqualTo(Some("client-keystore.p12"))
    assertThat(keyStoreConfig.password).isEqualTo(Some("changeit"))
    assertThat(keyStoreConfig.isFileOnClasspath).isTrue()

    assertThat(settings.trustManagerConfig.algorithm).isEqualTo("PKIX")
    assertThat(settings.trustManagerConfig.trustStoreConfigs.size).isEqualTo(1)
    val trustStoreConfig: TrustStoreConfig = settings.trustManagerConfig.trustStoreConfigs.head
    assertThat(trustStoreConfig.storeType).isEqualTo("JKS")
    assertThat(trustStoreConfig.filePath).isEqualTo(Some("truststore.jks"))
    assertThat(trustStoreConfig.password).isEqualTo(Some("changeit"))
    assertThat(trustStoreConfig.isFileOnClasspath).isFalse()

    assertThat(settings.loose.allowLegacyHelloMessages).isEqualTo(Some(true))
    assertThat(settings.loose.allowUnsafeRenegotiation).isEqualTo(Some(false))
    assertThat(settings.loose.disableHostnameVerification).isTrue()
    assertThat(settings.loose.acceptAnyCertificate).isTrue()

    assertThat(settings.debug.ssl).isTrue()
    assertThat(settings.debug.sslctx).isTrue()
    assertThat(settings.debug.keymanager).isFalse()
    assertThat(settings.debug.trustmanager).isTrue()
  }
}
