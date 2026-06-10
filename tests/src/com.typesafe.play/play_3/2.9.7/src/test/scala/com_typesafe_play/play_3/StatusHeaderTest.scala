/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_3

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.nio.charset.StandardCharsets
import java.util.Optional
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.stream.SystemMaterializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.mvc.Http.HeaderNames
import play.mvc.Http.MimeTypes
import play.mvc.Http.Status
import play.mvc.Result
import play.mvc.Results

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class StatusHeaderTest {
  @Test
  def sendsResourceFromProvidedClassLoader(): Unit = {
    val resourceName: String = "play-status-header-resource.txt"
    val resourceText: String = "Play StatusHeader resource body"
    val classLoader: InMemoryResourceClassLoader = new InMemoryResourceClassLoader(
      resourceName,
      resourceText
    )

    val result: Result = Results.ok().sendResource(
      resourceName,
      classLoader,
      false,
      Optional.of("status-header-resource.txt")
    )

    assertThat(result.status()).isEqualTo(Status.OK)
    assertThat(result.header(HeaderNames.CONTENT_DISPOSITION).orElse(""))
      .contains("attachment")
      .contains("status-header-resource.txt")
    assertThat(result.contentType()).contains(MimeTypes.TEXT)
    assertThat(result.body().contentLength()).isEmpty
    assertThat(consumeBody(result)).isEqualTo(resourceText)
    assertThat(classLoader.openCount.get()).isEqualTo(1)
  }

  private def consumeBody(result: Result): String = {
    val actorSystem: ActorSystem = ActorSystem.create("StatusHeaderTest")
    try {
      val bytes = result
        .body()
        .consumeData(SystemMaterializer.get(actorSystem).materializer)
        .toCompletableFuture
        .get(10, TimeUnit.SECONDS)
      bytes.utf8String
    } finally {
      actorSystem.terminate()
      Await.result(actorSystem.whenTerminated, Duration(10, TimeUnit.SECONDS))
    }
  }

  private final class InMemoryResourceClassLoader(resourceName: String, resourceText: String)
      extends ClassLoader(null) {
    val openCount: AtomicInteger = new AtomicInteger(0)
    private val resourceBytes: Array[Byte] = resourceText.getBytes(StandardCharsets.UTF_8)

    override def findResource(name: String): URL = {
      if (name == resourceName) {
        new URL(
          null,
          "memory:///" + resourceName,
          new URLStreamHandler {
            override def openConnection(url: URL): URLConnection = new URLConnection(url) {
              override def connect(): Unit = ()

              override def getInputStream: InputStream = {
                openCount.incrementAndGet()
                new ByteArrayInputStream(resourceBytes)
              }
            }
          }
        )
      } else {
        null
      }
    }
  }
}
