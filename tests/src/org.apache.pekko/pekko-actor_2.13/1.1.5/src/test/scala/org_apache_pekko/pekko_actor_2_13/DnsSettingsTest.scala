/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_2_13

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.Actor
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.Props
import org.apache.pekko.event.Logging
import org.apache.pekko.io.Dns
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.util.Hashtable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.naming.Context
import javax.naming.NamingException
import javax.naming.spi.InitialContextFactory
import javax.naming.spi.InitialContextFactoryBuilder
import javax.naming.spi.NamingManager
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.control.NonFatal

class DnsSettingsTest {
  @Test
  def reportsDefaultNameserversUnavailableWhenAutomaticDiscoveryCannotProvideThem(): Unit = {
    DnsSettingsTest.installFailingJndiFactory()

    val config: Config = ConfigFactory.parseString("""
        pekko.io.dns.resolver = async-dns
        pekko.io.dns.async-dns.resolve-timeout = 10s
        pekko.loglevel = "ERROR"
        """)
    val system: ActorSystem = ActorSystem("dns-settings-reflection-fallback", config)
    val failure: CompletableFuture[Throwable] = new CompletableFuture[Throwable]

    try {
      val observer = system.actorOf(Props(new DnsSettingsFailureObserver(failure)), "dns-settings-failure-observer")
      system.eventStream.subscribe(observer, classOf[Logging.Error])

      val immediateFailure: Option[Throwable] = try {
        Dns(system).getResolver
        None
      } catch {
        case NonFatal(t) => Some(t)
      }
      val discoveredFailure: Throwable = immediateFailure.getOrElse(failure.get(10, TimeUnit.SECONDS))

      assertThat(DnsSettingsTest.containsDefaultNameserverFailure(discoveredFailure)).isTrue
    } finally {
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
    }
  }
}

object DnsSettingsTest {
  private val failingJndiFactoryInstalled: AtomicBoolean = new AtomicBoolean(false)
  private val DefaultNameserverFailureMessage: String = "Unable to obtain default nameservers"

  private def installFailingJndiFactory(): Unit = {
    if (failingJndiFactoryInstalled.compareAndSet(false, true)) {
      NamingManager.setInitialContextFactoryBuilder(new InitialContextFactoryBuilder {
        override def createInitialContextFactory(environment: Hashtable[_, _]): InitialContextFactory = {
          new InitialContextFactory {
            override def getInitialContext(environment: Hashtable[_, _]): Context = {
              throw new NamingException("JNDI DNS lookup disabled for DnsSettings fallback coverage")
            }
          }
        }
      })
    }
  }

  def containsDefaultNameserverFailure(throwable: Throwable): Boolean = {
    if (throwable == null) {
      false
    } else if (Option(throwable.getMessage).exists(_.contains(DefaultNameserverFailureMessage))) {
      true
    } else {
      containsDefaultNameserverFailure(throwable.getCause)
    }
  }
}

final class DnsSettingsFailureObserver(result: CompletableFuture[Throwable]) extends Actor {
  override def receive: Receive = {
    case error: Logging.Error if DnsSettingsTest.containsDefaultNameserverFailure(error.cause) =>
      result.complete(error.cause)
  }
}
