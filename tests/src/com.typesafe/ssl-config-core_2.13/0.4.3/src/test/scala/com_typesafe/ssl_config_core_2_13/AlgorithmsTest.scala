/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe.ssl_config_core_2_13

import java.lang.IllegalAccessException
import java.security.Key
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

import com.typesafe.sslconfig.ssl.Algorithms
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AlgorithmsTest {
  @Test
  def translatesEcPublicKeyThroughJdkEcKeyFactory(): Unit = {
    val keyPairGenerator: KeyPairGenerator = KeyPairGenerator.getInstance("EC")
    keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"))
    val publicKey: ECPublicKey = keyPairGenerator.generateKeyPair().getPublic.asInstanceOf[ECPublicKey]

    val originalClassLoader: ClassLoader = Thread.currentThread().getContextClassLoader
    Thread.currentThread().setContextClassLoader(getClass.getClassLoader)
    try {
      try {
        val translatedKey: Key = Algorithms.translateECKey(publicKey)
        val keySize: Option[Int] = Algorithms.keySize(translatedKey)

        assertThat(translatedKey).isInstanceOf(classOf[ECPublicKey])
        assertThat(keySize.isDefined).isTrue()
        assertThat(keySize.get).isEqualTo(publicKey.getParams.getOrder.bitLength())
      } catch {
        case e: IllegalAccessException =>
          assertThat(e.getMessage).contains("sun.security.ec.ECKeyFactory")
      }
    } finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader)
    }
  }
}
