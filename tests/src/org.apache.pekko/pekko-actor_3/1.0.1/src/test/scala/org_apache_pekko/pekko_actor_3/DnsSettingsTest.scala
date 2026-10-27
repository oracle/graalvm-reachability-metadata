/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_3

import java.util.Hashtable
import java.util.concurrent.atomic.AtomicBoolean

import javax.naming.Context
import javax.naming.NamingException
import javax.naming.spi.InitialContextFactory
import javax.naming.spi.InitialContextFactoryBuilder
import javax.naming.spi.NamingManager

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorNotFound
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.io.Dns
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success

class DnsSettingsTest {
  @Test
  def discoversDefaultNameserversThroughResolverConfigurationFallback(): Unit = {
    DnsSettingsTest.forceJndiDnsLookupToFail()

    val config: Config = ConfigFactory.parseString("""
      pekko.io.dns.resolver = async-dns
      pekko.io.dns.async-dns.resolve-timeout = 1s
      """)
    val system: ActorSystem = ActorSystem(s"dns-settings-${System.nanoTime()}", config)
    try {
      val resolver: ActorRef = Dns(system).getResolver
      val managerStartedOrStopped: Future[ActorRef] = system.actorSelection(resolver.path).resolveOne(5.seconds)
      Await.ready(managerStartedOrStopped, 5.seconds).value match {
        case Some(Success(resolved)) => assertThat(resolved).isEqualTo(resolver)
        case Some(Failure(_: ActorNotFound)) => // The fallback can produce no usable nameservers on some hosts.
        case Some(Failure(exception)) => throw exception
        case None => throw new AssertionError("DNS manager actor selection did not complete")
      }

      assertThat(resolver.path.name).isEqualTo("IO-DNS")
    } finally {
      Await.result(system.terminate(), 5.seconds)
    }
  }
}

object DnsSettingsTest {
  private val builderInstallationAttempted: AtomicBoolean = new AtomicBoolean(false)

  def forceJndiDnsLookupToFail(): Unit = {
    if (builderInstallationAttempted.compareAndSet(false, true)) {
      NamingManager.setInitialContextFactoryBuilder(new FailingDnsInitialContextFactoryBuilder)
    }
  }
}

private final class FailingDnsInitialContextFactoryBuilder extends InitialContextFactoryBuilder {
  override def createInitialContextFactory(environment: Hashtable[?, ?]): InitialContextFactory = {
    new InitialContextFactory {
      override def getInitialContext(environment: Hashtable[?, ?]): Context = {
        throw new NamingException("Forcing Pekko DNS settings to use the ResolverConfiguration fallback")
      }
    }
  }
}
