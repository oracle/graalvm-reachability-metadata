/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_2_13

import java.util.Base64
import java.util.Hashtable
import java.util.concurrent.atomic.AtomicBoolean

import javax.naming.Context
import javax.naming.NamingException
import javax.naming.spi.InitialContextFactory
import javax.naming.spi.InitialContextFactoryBuilder
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.BootstrapSetup
import org.apache.pekko.io.Dns
import org.assertj.core.api.Assertions.assertThat
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class DnsSettingsTest {
  @Test
  def defaultNameserversFallBackToResolverConfigurationReflection(): Unit = {
    try {
      DnsSettingsTest.installFailingJndiFactory()

      val config: Config = ConfigFactory.parseString("""
        pekko.io.dns.resolver = "async-dns"
        pekko.io.dns.async-dns.nameservers = default
        pekko.io.dns.async-dns.resolve-timeout = 1s
        """)
      val classLoader: ClassLoader = new DnsSettingsTest.ResolverConfigurationClassLoader(getClass.getClassLoader)
      val setup: BootstrapSetup = BootstrapSetup().withClassloader(classLoader).withConfig(config)
      val system: ActorSystem = ActorSystem("dns-settings-reflection", setup)

      try {
        val resolver: ActorRef = Dns(system).getResolver
        val resolvedActor: ActorRef = Await.result(system.actorSelection(resolver.path).resolveOne(10.seconds), 10.seconds)

        assertThat(resolvedActor).isEqualTo(resolver)
      } finally {
        Await.result(system.terminate(), 10.seconds)
      }
    } catch {
      case error: Error =>
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
          throw error
        }
    }
  }
}

object DnsSettingsTest {
  private val jndiFactoryInstalled: AtomicBoolean = new AtomicBoolean(false)

  private val resolverConfigurationClassName: String = "sun.net.dns.ResolverConfiguration"

  private val resolverConfigurationBytes: Array[Byte] = Base64.getDecoder.decode(
    "yv66vgAAADQAHgoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWBwAIAQAhc3VuL25ldC9kbnMvUmVzb2x2ZXJDb25maWd1cmF0aW9uCgAHAAMHAAsBABBqYXZhL2xhbmcvU3RyaW5nCAANAQAVZG5zOi8vMTkyLjAuMi41Mzo1MzUzCgAPABAHABEMABIAEwEAEGphdmEvdXRpbC9BcnJheXMBAAZhc0xpc3QBACUoW0xqYXZhL2xhbmcvT2JqZWN0OylMamF2YS91dGlsL0xpc3Q7AQAEQ29kZQEAD0xpbmVOdW1iZXJUYWJsZQEABG9wZW4BACUoKUxzdW4vbmV0L2Rucy9SZXNvbHZlckNvbmZpZ3VyYXRpb247AQALbmFtZXNlcnZlcnMBABIoKUxqYXZhL3V0aWwvTGlzdDsBAAlTaWduYXR1cmUBACYoKUxqYXZhL3V0aWwvTGlzdDxMamF2YS9sYW5nL1N0cmluZzs+OwEAClNvdXJjZUZpbGUBABpSZXNvbHZlckNvbmZpZ3VyYXRpb24uamF2YQAxAAcAAgAAAAAAAwABAAUABgABABQAAAAdAAEAAQAAAAUqtwABsQAAAAEAFQAAAAYAAQAAAAYACQAWABcAAQAUAAAAIAACAAAAAAAIuwAHWbcACbAAAAABABUAAAAGAAEAAAAIAAEAGAAZAAIAFAAAACUABAABAAAADQS9AApZAxIMU7gADrAAAAABABUAAAAGAAEAAAAMABoAAAACABsAAQAcAAAAAgAd")

  def installFailingJndiFactory(): Unit = {
    if (jndiFactoryInstalled.compareAndSet(false, true)) {
      javax.naming.spi.NamingManager.setInitialContextFactoryBuilder(new InitialContextFactoryBuilder {
        override def createInitialContextFactory(environment: Hashtable[_, _]): InitialContextFactory = {
          new InitialContextFactory {
            override def getInitialContext(environment: Hashtable[_, _]): Context = {
              throw new NamingException("JNDI DNS lookup disabled so DnsSettings uses its reflection fallback")
            }
          }
        }
      })
    }
  }

  final class ResolverConfigurationClassLoader(parent: ClassLoader) extends ClassLoader(parent) {
    override protected def loadClass(name: String, resolve: Boolean): Class[_] = {
      if (name == resolverConfigurationClassName) {
        findLoadedClass(name) match {
          case null =>
            val definedClass: Class[_] = defineClass(name, resolverConfigurationBytes, 0, resolverConfigurationBytes.length)
            if (resolve) {
              resolveClass(definedClass)
            }
            definedClass
          case loadedClass =>
            if (resolve) {
              resolveClass(loadedClass)
            }
            loadedClass
        }
      } else {
        super.loadClass(name, resolve)
      }
    }
  }
}
