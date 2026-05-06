/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13

import java.util.Hashtable
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

import javax.naming.Context
import javax.naming.NamingException
import javax.naming.spi.InitialContextFactory
import javax.naming.spi.InitialContextFactoryBuilder
import javax.naming.spi.NamingManager

import akka.actor.ActorIdentity
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Identify
import akka.io.Dns
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class DnsSettingsTest {
  @Test
  def startsAsyncDnsResolverUsingReflectiveDefaultNameServerFallback(): Unit = {
    DnsSettingsTest.installFailingJndiFactory()

    val config: Config = ConfigFactory.parseString("""
      akka.io.dns.resolver = async-dns
      akka.io.dns.async-dns.resolve-timeout = 1s
      akka.io.dns.async-dns.cache-cleanup-interval = 1h
      """)

    withActorSystem("dns-settings-reflection-fallback", config) { system: ActorSystem =>
      implicit val timeout: Timeout = Timeout(3.seconds)
      val manager: ActorRef = Dns(system).manager

      Try(Await.result(manager ? Identify("dns-manager"), 3.seconds).asInstanceOf[ActorIdentity]) match {
        case Success(identity) =>
          assertThat(identity.correlationId).isEqualTo("dns-manager")
          assertThat(manager.path.name).isEqualTo("IO-DNS")
        case Failure(_: TimeoutException) =>
          assertThat(manager.path.name).isEqualTo("IO-DNS")
        case Failure(exception) => throw exception
      }
    }
  }

  private def withActorSystem(name: String, config: Config)(body: ActorSystem => Unit): Unit = {
    val system: ActorSystem = ActorSystem(name, config)
    try {
      body(system)
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}

object DnsSettingsTest {
  private val jndiFactoryInstalled: AtomicBoolean = new AtomicBoolean(false)

  def installFailingJndiFactory(): Unit = {
    if (jndiFactoryInstalled.compareAndSet(false, true)) {
      NamingManager.setInitialContextFactoryBuilder(new FailingInitialContextFactoryBuilder)
    }
  }

  private class FailingInitialContextFactoryBuilder extends InitialContextFactoryBuilder {
    override def createInitialContextFactory(environment: Hashtable[_, _]): InitialContextFactory = {
      new InitialContextFactory {
        override def getInitialContext(environment: Hashtable[_, _]): Context = {
          throw new NamingException("JNDI name server discovery should fall back to reflective discovery")
        }
      }
    }
  }
}
