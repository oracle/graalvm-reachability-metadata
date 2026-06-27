/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.io.Dns
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.util.Hashtable
import javax.naming.Context
import javax.naming.NamingException
import javax.naming.spi.InitialContextFactory
import javax.naming.spi.InitialContextFactoryBuilder
import javax.naming.spi.NamingManager
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class DnsSettingsTest {
  @Test
  def startsAsyncDnsResolverWithExplicitNameserverWhenJndiDnsLookupFails(): Unit = {
    FailingJndiDnsContext.install()
    val system: ActorSystem = ActorSystem("dns-settings-reflection-fallback", asyncDnsConfig)

    try {
      val resolver: ActorRef = Dns(system).getResolver

      assertThat(resolver.path.name).isEqualTo("IO-DNS")
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }

  private def asyncDnsConfig: Config = ConfigFactory.parseString("""
      akka.loglevel = "ERROR"
      akka.stdout-loglevel = "ERROR"
      akka.actor.default-dispatcher.shutdown-timeout = 10s
      akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
      akka.io.dns.resolver = "async-dns"
      akka.io.dns.async-dns.nameservers = ["127.0.0.1"]
      akka.io.dns.async-dns.resolve-timeout = 10s
      akka.io.dns.async-dns.cache-cleanup-interval = 10s
      """).withFallback(ConfigFactory.load())
}

private object FailingJndiDnsContext {
  private lazy val installed: Boolean = {
    NamingManager.setInitialContextFactoryBuilder(new FailingInitialContextFactoryBuilder)
    true
  }

  def install(): Unit = {
    val _: Boolean = installed
  }
}

private final class FailingInitialContextFactoryBuilder extends InitialContextFactoryBuilder {
  override def createInitialContextFactory(environment: Hashtable[_, _]): InitialContextFactory =
    new InitialContextFactory {
      override def getInitialContext(environment: Hashtable[_, _]): Context =
        throw new NamingException("JNDI DNS is unavailable in this test")
    }
}
