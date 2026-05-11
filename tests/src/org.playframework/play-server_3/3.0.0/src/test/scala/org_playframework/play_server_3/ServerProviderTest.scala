/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_server_3

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import play.api.Configuration
import play.core.server.Server
import play.core.server.ServerProvider

class ServerProviderTest {
  @BeforeEach
  def resetConstructionEvents(): Unit = {
    ServerProviderConstructionEvents.reset()
  }

  @Test
  def createsConfiguredProviderWithDefaultConstructor(): Unit = {
    val configuration: Configuration = testConfiguration(classOf[ConfiguredServerProvider].getName)

    val provider: ServerProvider = ServerProvider.fromConfiguration(getClass.getClassLoader, configuration)

    assertThat(provider).isInstanceOf(classOf[ConfiguredServerProvider])
    assertThat(ServerProviderConstructionEvents.wasConstructed).isTrue()
  }

  private def testConfiguration(providerClassName: String): Configuration = {
    val config: Config = ConfigFactory.parseString(
      s"""
         |play.server.provider = "$providerClassName"
         |""".stripMargin
    )
    Configuration(config)
  }
}

object ServerProviderConstructionEvents {
  @volatile private var constructed: Boolean = false

  def recordConstruction(): Unit = {
    constructed = true
  }

  def reset(): Unit = {
    constructed = false
  }

  def wasConstructed: Boolean = constructed
}

final class ConfiguredServerProvider extends ServerProvider {
  ServerProviderConstructionEvents.recordConstruction()

  override def createServer(context: ServerProvider.Context): Server = {
    throw new UnsupportedOperationException("This test only verifies provider construction")
  }
}
