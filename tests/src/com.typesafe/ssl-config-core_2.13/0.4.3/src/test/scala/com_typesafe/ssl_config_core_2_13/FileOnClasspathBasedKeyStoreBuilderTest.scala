/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe.ssl_config_core_2_13

import java.security.KeyStore
import java.security.cert.Certificate
import java.util.Enumeration

import com.typesafe.sslconfig.ssl.FileOnClasspathBasedKeyStoreBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FileOnClasspathBasedKeyStoreBuilderTest {
  @Test
  def buildsPemKeyStoreFromClasspathResource(): Unit = {
    val resourceName: String = "com_typesafe/ssl_config_core_2_13/classpath-certificate.pem"
    val builder: FileOnClasspathBasedKeyStoreBuilder =
      new FileOnClasspathBasedKeyStoreBuilder("PEM", resourceName, None)

    val keyStore: KeyStore = builder.build()

    assertThat(keyStore.size()).isEqualTo(1)

    val aliases: Enumeration[String] = keyStore.aliases()
    val alias: String = aliases.nextElement()
    val certificate: Certificate = keyStore.getCertificate(alias)

    assertThat(keyStore.isCertificateEntry(alias)).isTrue()
    assertThat(certificate).isNotNull()
  }
}
