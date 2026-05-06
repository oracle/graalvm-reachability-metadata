/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_http_core_2_13 {

import akka.actor.ActorSystem
import akka.http.scaladsl.ClientTransport
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.OutgoingConnection
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test

import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.InetSocketAddress
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success
import scala.jdk.CollectionConverters._
import scala.util.Try

class Http2AlpnSupportTest {
  @Test
  def rejectsLegacyJettyAlpnAgentWhenJdkAlpnPathIsSelected(): Unit = {
    var loader: URLClassLoader = null
    var alpnClassDirectory: Path = null
    try {
      alpnClassDirectory = Files.createTempDirectory("akka-http-alpn-extension")
      writeAlpnExtensionClass(alpnClassDirectory)
      loader = new ChildFirstUrlClassLoader(testClasspathUrls :+ alpnClassDirectory.toUri.toURL, getClass.getClassLoader)
      val runnerClass: Class[_] = Class.forName(Http2AlpnSupportTest.ChildRunnerClassName, true, loader)
      val runner: AnyRef = runnerClass.getField("MODULE$").get(null)
      runnerClass.getMethod("run").invoke(runner)
    } catch {
      case exception: InvocationTargetException =>
        exception.getCause match {
          case error: Error if NativeImageSupport.isUnsupportedFeatureError(error) => ()
          case cause => throw cause
        }
      case error: Error if NativeImageSupport.isUnsupportedFeatureError(error) => ()
    } finally {
      if (loader != null) loader.close()
      deleteRecursively(alpnClassDirectory)
    }
  }

  private def writeAlpnExtensionClass(directory: Path): Unit = {
    val classFile: Path = directory.resolve("sun/security/ssl/ALPNExtension.class")
    Files.createDirectories(classFile.getParent)
    Files.write(classFile, Base64.getDecoder.decode(Http2AlpnSupportTest.AlpnExtensionClassBase64))
  }

  private def deleteRecursively(path: Path): Unit = {
    if (path != null) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.sortBy(_.toString).reverse.foreach(Files.deleteIfExists(_))
      finally stream.close()
    }
  }

  private def testClasspathUrls: Array[URL] =
    System.getProperty("java.class.path")
      .split(File.pathSeparator)
      .filter(_.nonEmpty)
      .map(entry => new File(entry).toURI.toURL)
}

object Http2AlpnSupportTest {
  private val ChildRunnerClassName = "com_typesafe_akka.akka_http_core_2_13.Http2AlpnSupportChildRunner$"
  private val AlpnExtensionClassBase64 =
    "yv66vgAAADQADgoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWBwAIAQAec3VuL3NlY3VyaXR5L3NzbC9BTFBORXh0ZW5zaW9uAQAEQ29kZQEAD0xpbmVOdW1iZXJUYWJsZQEABGluaXQBAApTb3VyY2VGaWxlAQASQUxQTkV4dGVuc2lvbi5qYXZhACEABwACAAAAAAACAAEABQAGAAEACQAAAB0AAQABAAAABSq3AAGxAAAAAQAKAAAABgABAAAAAgABAAsABgABAAkAAAAZAAAAAQAAAAGxAAAAAQAKAAAABgABAAAABAABAAwAAAACAA0="
}

private final class ChildFirstUrlClassLoader(urls: Array[URL], parent: ClassLoader) extends URLClassLoader(urls, parent) {
  override def loadClass(name: String, resolve: Boolean): Class[_] = synchronized {
    val loadedClass: Class[_] = findLoadedClass(name)
    if (loadedClass != null) {
      if (resolve) resolveClass(loadedClass)
      loadedClass
    } else if (shouldLoadChildFirst(name)) {
      val childClass: Class[_] = Try(findClass(name)).getOrElse(super.loadClass(name, false))
      if (resolve) resolveClass(childClass)
      childClass
    } else {
      super.loadClass(name, resolve)
    }
  }

  private def shouldLoadChildFirst(name: String): Boolean =
    name.startsWith("akka.http.") ||
      name.startsWith("com_typesafe_akka.akka_http_core_2_13.Http2AlpnSupportChildRunner") ||
      name == "sun.security.ssl.ALPNExtension"
}

object Http2AlpnSupportChildRunner {
  def run(): Unit = {
    val originalJavaSpecificationVersion: String = System.getProperty("java.specification.version")
    System.setProperty("java.specification.version", "1.8")

    var system: ActorSystem = null
    try {
      system = ActorSystem("http2-alpn-support-test")
      implicit val actorSystem: ActorSystem = system
      val clientSettings: ClientConnectionSettings = ClientConnectionSettings(system).withTransport(loopbackTransport)
      val requestFailure: Throwable = materializeHttp2ClientFlow(clientSettings)

      val failureMessages: String = causalMessages(requestFailure)
      assertThat(failureMessages).contains("remove jetty-alpn-agent")
      assertThat(failureMessages).contains("use version 2.0.10")
    } finally {
      if (originalJavaSpecificationVersion == null) System.clearProperty("java.specification.version")
      else System.setProperty("java.specification.version", originalJavaSpecificationVersion)

      if (system != null) Await.result(system.terminate(), 10.seconds)
    }
  }

  private def materializeHttp2ClientFlow(clientSettings: ClientConnectionSettings)(implicit system: ActorSystem): Throwable = {
    val flow = Http()
      .connectionTo("example.com")
      .toPort(443)
      .withClientConnectionSettings(clientSettings)
      .http2()

    Try(Await.result(Source.single(HttpRequest()).via(flow).runWith(Sink.head), 10.seconds)) match {
      case Failure(exception) => exception
      case Success(response) => throw new AssertionError(s"Expected HTTP/2 client flow to fail before receiving $response")
    }
  }

  private def loopbackTransport: ClientTransport = new ClientTransport {
    override def connectTo(
      host: String,
      port: Int,
      settings: ClientConnectionSettings)(implicit system: ActorSystem): Flow[ByteString, ByteString, Future[OutgoingConnection]] = {
      val localAddress: InetSocketAddress = InetSocketAddress.createUnresolved("localhost", 0)
      val remoteAddress: InetSocketAddress = InetSocketAddress.createUnresolved(host, port)
      Flow.fromSinkAndSourceMat(
        Sink.ignore,
        Source.repeat(ByteString(0.toByte)).take(16)
      )(Keep.left).mapMaterializedValue(_ => Future.successful(OutgoingConnection(localAddress, remoteAddress)))
    }
  }

  private def causalMessages(throwable: Throwable): String = {
    Iterator.iterate(throwable)(_.getCause)
      .takeWhile(_ != null)
      .flatMap(error => Option(error.getMessage))
      .mkString("\n")
  }
}
}
