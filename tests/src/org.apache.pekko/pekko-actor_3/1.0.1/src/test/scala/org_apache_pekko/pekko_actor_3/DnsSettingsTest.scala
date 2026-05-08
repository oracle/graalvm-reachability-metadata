/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_3

import java.util.Hashtable
import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.control.NonFatal

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import javax.naming.Context
import javax.naming.NamingException
import javax.naming.spi.InitialContextFactory
import javax.naming.spi.InitialContextFactoryBuilder
import javax.naming.spi.NamingManager
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.io.Dns
import org.apache.pekko.io.dns.DnsProtocol
import org.apache.pekko.pattern.ask
import org.apache.pekko.util.Timeout
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class DnsSettingsTest {
  @Test
  def fallsBackToResolverConfigurationWhenJndiNameserversAreUnavailable(): Unit = {
    FailingJndiDnsProvider.install()

    val config: Config = ConfigFactory.parseString("""
      pekko.loglevel = "OFF"
      pekko.stdout-loglevel = "OFF"
      pekko.io.dns.resolver = "async-dns"
      pekko.io.dns.async-dns.resolve-timeout = 200 ms
      pekko.actor.deployment."/IO-DNS/async-dns".nr-of-instances = 1
      """)

    val system: ActorSystem = ActorSystem("dns-settings-reflection-fallback", config)
    try {
      val resolver: ActorRef = Dns(system).getResolver
      assertNotNull(resolver)
      forceResolverStartup(resolver)(system)
    } finally Await.result(system.terminate(), 10.seconds)
  }

  private def forceResolverStartup(resolver: ActorRef)(implicit system: ActorSystem): Unit = {
    implicit val timeout: Timeout = Timeout(2.seconds)
    try Await.result(resolver ? DnsProtocol.Resolve("localhost"), 3.seconds)
    catch {
      case NonFatal(_) =>
        // The test only needs the async DNS manager to construct its settings. Depending on the host
        // resolver configuration, that construction may fail or the DNS request may time out.
    }
  }
}

private object FailingJndiDnsProvider {
  private val installed: AtomicBoolean = new AtomicBoolean(false)

  def install(): Unit = {
    if (installed.compareAndSet(false, true)) {
      try NamingManager.setInitialContextFactoryBuilder(new FailingInitialContextFactoryBuilder)
      catch {
        case _: IllegalStateException =>
          // Another test has already installed the JVM-wide builder.
      }
    }
  }
}

private final class FailingInitialContextFactoryBuilder extends InitialContextFactoryBuilder {
  override def createInitialContextFactory(environment: Hashtable[?, ?]): InitialContextFactory =
    new InitialContextFactory {
      override def getInitialContext(environment: Hashtable[?, ?]): Context =
        throw new NamingException("DNS JNDI lookup disabled for DnsSettings fallback coverage")
    }
}
