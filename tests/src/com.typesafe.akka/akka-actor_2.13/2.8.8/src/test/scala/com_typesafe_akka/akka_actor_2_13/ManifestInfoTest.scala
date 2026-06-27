/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.util.Collections
import java.util.Enumeration

import akka.actor.ActorSystem
import akka.util.ManifestInfo
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.collection.immutable
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._

class ManifestInfoTest {

  @Test
  def manifestInfoExtensionScansClasspathManifests(): Unit = {
    val classLoader: ClassLoader = new ManifestInfoResourceClassLoader(getClass.getClassLoader)
    val config: Config = ConfigFactory.parseString("akka.loglevel = \"ERROR\"")

    withActorSystem("manifest-info-extension", config, classLoader) { system: ActorSystem =>
      val manifestInfo: ManifestInfo = ManifestInfo(system)
      val dependencies: immutable.Seq[String] = immutable.Seq.empty[String]

      assertThat(manifestInfo.versions.keySet.asJava)
        .contains(ManifestInfoResourceClassLoader.ArtifactTitle)
      assertThat(manifestInfo.versions(ManifestInfoResourceClassLoader.ArtifactTitle).toString)
        .isEqualTo("1.0.0")
      assertThat(manifestInfo.checkSameVersion(
        "Akka",
        dependencies,
        logWarning = false,
        throwException = false)).isTrue
    }
  }

  private def withActorSystem(
      name: String,
      config: Config,
      classLoader: ClassLoader)(body: ActorSystem => Unit): Unit = {
    val system: ActorSystem = ActorSystem(name, config, classLoader)
    try body(system)
    finally {
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
    }
  }
}

object ManifestInfoResourceClassLoader {
  val ArtifactTitle: String = "manifest-info-test-artifact"

  private val ManifestBytes: Array[Byte] =
    s"""Manifest-Version: 1.0
       |Implementation-Title: $ArtifactTitle
       |Implementation-Version: 1.0.0
       |Implementation-Vendor-Id: Lightbend Inc.
       |
       |""".stripMargin.getBytes("UTF-8")

  val ManifestUrl: URL = new URL(null, "memory:manifest-info-test", new URLStreamHandler {
    override def openConnection(url: URL): URLConnection = new URLConnection(url) {
      override def connect(): Unit = ()

      override def getInputStream: InputStream = new ByteArrayInputStream(ManifestBytes)
    }
  })
}

final class ManifestInfoResourceClassLoader(parent: ClassLoader) extends ClassLoader(parent) {
  override def getResources(name: String): Enumeration[URL] = {
    val parentResources: Seq[URL] = super.getResources(name).asScala.toSeq
    if (name == "META-INF/MANIFEST.MF") {
      val resources: Seq[URL] = parentResources :+ ManifestInfoResourceClassLoader.ManifestUrl
      Collections.enumeration(resources.asJava)
    } else {
      Collections.enumeration(parentResources.asJava)
    }
  }
}
