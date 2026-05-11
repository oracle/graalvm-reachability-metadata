/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_server_3

import java.io.File
import java.util.Properties
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine

import scala.concurrent.Future
import scala.util.Success
import scala.util.Try

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import play.api.Configuration
import play.api.Environment
import play.api.Mode
import play.api.mvc.request.RequestFactory
import play.api.Application as ScalaApplication
import play.api.http.HttpErrorHandler
import play.api.http.HttpRequestHandler
import play.core.ApplicationProvider
import play.core.server.ServerConfig
import play.core.server.ssl.ServerSSLEngine
import play.server.SSLEngineProvider as JavaSSLEngineProvider
import play.server.api.SSLEngineProvider as ScalaSSLEngineProvider

class ServerSSLEngineTest {
  @BeforeEach
  def resetConstructionEvents(): Unit = {
    ServerSSLEngineConstructionEvents.reset()
  }

  @Test
  def createsJavaProviderWithServerConfigAndApplicationProviderConstructor(): Unit = {
    val provider: JavaSSLEngineProvider = createProvider(classOf[JavaServerConfigApplicationProviderSslEngineProvider])

    assertThat(provider).isInstanceOf(classOf[JavaServerConfigApplicationProviderSslEngineProvider])
    assertThat(ServerSSLEngineConstructionEvents.lastConstructor).isEqualTo("java-server-config-application-provider")
  }

  @Test
  def createsJavaProviderWithApplicationProviderConstructor(): Unit = {
    val provider: JavaSSLEngineProvider = createProvider(classOf[JavaApplicationProviderSslEngineProvider])

    assertThat(provider).isInstanceOf(classOf[JavaApplicationProviderSslEngineProvider])
    assertThat(ServerSSLEngineConstructionEvents.lastConstructor).isEqualTo("java-application-provider")
  }

  @Test
  def createsJavaProviderWithNoArgsConstructor(): Unit = {
    val provider: JavaSSLEngineProvider = createProvider(classOf[JavaNoArgsSslEngineProvider])

    assertThat(provider).isInstanceOf(classOf[JavaNoArgsSslEngineProvider])
    assertThat(ServerSSLEngineConstructionEvents.lastConstructor).isEqualTo("java-no-args")
  }

  @Test
  def createsScalaProviderWithServerConfigAndApplicationProviderConstructor(): Unit = {
    val provider: JavaSSLEngineProvider = createProvider(classOf[ScalaServerConfigApplicationProviderSslEngineProvider])

    assertThat(provider).isInstanceOf(classOf[ScalaServerConfigApplicationProviderSslEngineProvider])
    assertThat(ServerSSLEngineConstructionEvents.lastConstructor).isEqualTo("scala-server-config-application-provider")
  }

  @Test
  def createsScalaProviderWithApplicationProviderConstructor(): Unit = {
    val provider: JavaSSLEngineProvider = createProvider(classOf[ScalaApplicationProviderSslEngineProvider])

    assertThat(provider).isInstanceOf(classOf[ScalaApplicationProviderSslEngineProvider])
    assertThat(ServerSSLEngineConstructionEvents.lastConstructor).isEqualTo("scala-application-provider")
  }

  @Test
  def createsScalaProviderWithNoArgsConstructor(): Unit = {
    val provider: JavaSSLEngineProvider = createProvider(classOf[ScalaNoArgsSslEngineProvider])

    assertThat(provider).isInstanceOf(classOf[ScalaNoArgsSslEngineProvider])
    assertThat(ServerSSLEngineConstructionEvents.lastConstructor).isEqualTo("scala-no-args")
  }

  private def createProvider(providerClass: Class[? <: JavaSSLEngineProvider]): JavaSSLEngineProvider = {
    val serverConfig: ServerConfig = testServerConfig(providerClass.getName)
    val applicationProvider: ApplicationProvider = new TestApplicationProvider

    ServerSSLEngine.createSSLEngineProvider(serverConfig, applicationProvider)
  }

  private def testServerConfig(providerClassName: String): ServerConfig = {
    val config: Config = ConfigFactory.parseString(
      s"""
         |play.server.https.engineProvider = "$providerClassName"
         |""".stripMargin
    )
    ServerConfig(
      rootDir = new File("."),
      port = Some(0),
      sslPort = None,
      address = "127.0.0.1",
      mode = Mode.Test,
      properties = new Properties,
      configuration = Configuration(config)
    )
  }
}

object ServerSSLEngineConstructionEvents {
  @volatile private var constructorName: String = ""

  def record(name: String): Unit = {
    constructorName = name
  }

  def reset(): Unit = {
    constructorName = ""
  }

  def lastConstructor: String = constructorName
}

final class TestApplicationProvider extends ApplicationProvider {
  override val get: Try[ScalaApplication] = Success(new TestApplication)
}

final class TestApplication extends ScalaApplication {
  override def path: File = new File(".")

  override def classloader: ClassLoader = classOf[ServerSSLEngineTest].getClassLoader

  override def environment: Environment = unused

  override def configuration: Configuration = Configuration(ConfigFactory.empty())

  override def actorSystem: org.apache.pekko.actor.ActorSystem = unused

  override implicit def materializer: org.apache.pekko.stream.Materializer = unused

  override def coordinatedShutdown: org.apache.pekko.actor.CoordinatedShutdown = unused

  override def requestFactory: RequestFactory = unused

  override def requestHandler: HttpRequestHandler = unused

  override def errorHandler: HttpErrorHandler = unused

  override def stop(): Future[?] = Future.successful(())

  override def asJava: play.Application = new TestJavaApplication(this)

  private def unused[A]: A = throw new UnsupportedOperationException("This test application only exposes class loading")
}

final class TestJavaApplication(scalaApplication: ScalaApplication) extends play.Application {
  override def getWrappedApplication: ScalaApplication = scalaApplication

  override def asScala: ScalaApplication = scalaApplication

  override def environment(): play.Environment = unused

  override def config(): Config = ConfigFactory.empty()

  override def injector(): play.inject.Injector = unused

  private def unused[A]: A = throw new UnsupportedOperationException("This test Java application only exposes asScala")
}

final class JavaServerConfigApplicationProviderSslEngineProvider(
    serverConfig: ServerConfig,
    applicationProvider: play.server.ApplicationProvider
) extends JavaSSLEngineProvider {
  require(serverConfig != null)
  require(applicationProvider != null)
  ServerSSLEngineConstructionEvents.record("java-server-config-application-provider")

  override def sslContext(): SSLContext = SSLContext.getDefault

  override def createSSLEngine(): SSLEngine = sslContext().createSSLEngine()
}

final class JavaApplicationProviderSslEngineProvider(applicationProvider: play.server.ApplicationProvider)
    extends JavaSSLEngineProvider {
  require(applicationProvider != null)
  ServerSSLEngineConstructionEvents.record("java-application-provider")

  override def sslContext(): SSLContext = SSLContext.getDefault

  override def createSSLEngine(): SSLEngine = sslContext().createSSLEngine()
}

final class JavaNoArgsSslEngineProvider extends JavaSSLEngineProvider {
  ServerSSLEngineConstructionEvents.record("java-no-args")

  override def sslContext(): SSLContext = SSLContext.getDefault

  override def createSSLEngine(): SSLEngine = sslContext().createSSLEngine()
}

final class ScalaServerConfigApplicationProviderSslEngineProvider(
    serverConfig: ServerConfig,
    applicationProvider: ApplicationProvider
) extends ScalaSSLEngineProvider {
  require(serverConfig != null)
  require(applicationProvider != null)
  ServerSSLEngineConstructionEvents.record("scala-server-config-application-provider")

  override def sslContext(): SSLContext = SSLContext.getDefault

  override def createSSLEngine: SSLEngine = sslContext().createSSLEngine()
}

final class ScalaApplicationProviderSslEngineProvider(applicationProvider: ApplicationProvider)
    extends ScalaSSLEngineProvider {
  require(applicationProvider != null)
  ServerSSLEngineConstructionEvents.record("scala-application-provider")

  override def sslContext(): SSLContext = SSLContext.getDefault

  override def createSSLEngine: SSLEngine = sslContext().createSSLEngine()
}

final class ScalaNoArgsSslEngineProvider extends ScalaSSLEngineProvider {
  ServerSSLEngineConstructionEvents.record("scala-no-args")

  override def sslContext(): SSLContext = SSLContext.getDefault

  override def createSSLEngine: SSLEngine = sslContext().createSSLEngine()
}
