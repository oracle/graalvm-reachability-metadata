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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.http.DefaultFileMimeTypes
import play.api.http.FileMimeTypes
import play.api.http.FileMimeTypesConfiguration
import play.api.http.HeaderNames
import play.api.http.Status
import play.api.mvc.Result
import play.api.mvc.Results

import scala.concurrent.ExecutionContext

class ResultsInnerStatusTest {
  @Test
  def sendsResourceFromProvidedClassLoader(): Unit = {
    implicit val executionContext: ExecutionContext = ExecutionContext.global
    implicit val fileMimeTypes: FileMimeTypes = new DefaultFileMimeTypes(
      FileMimeTypesConfiguration(Map("txt" -> "text/plain"))
    )
    val resourceName: String = "assets/status-resource.txt"
    val resourceContent: Array[Byte] = "Play resource body".getBytes(StandardCharsets.UTF_8)
    val classLoader: ClassLoader = new InMemoryResourceClassLoader(resourceName, resourceContent)

    val result: Result = Results.Ok.sendResource(
      resource = resourceName,
      classLoader = classLoader,
      inline = false,
      fileName = _ => Some("status-resource.txt")
    )

    val contentDisposition: String = result.header.headers(HeaderNames.CONTENT_DISPOSITION)
    assertThat(result.header.status).isEqualTo(Status.OK)
    assertThat(contentDisposition).contains("attachment")
    assertThat(contentDisposition).contains("status-resource.txt")
    assertThat(result.body.contentType).isEqualTo(Some("text/plain"))
    assertThat(result.body.contentLength).isEqualTo(Some(resourceContent.length.toLong))
  }

  private final class InMemoryResourceClassLoader(resourceName: String, resourceContent: Array[Byte])
      extends ClassLoader(null) {
    override def findResource(name: String): URL = {
      if (name == resourceName) {
        new URL(
          null,
          "memory:///" + resourceName,
          new URLStreamHandler {
            override def openConnection(url: URL): URLConnection = new URLConnection(url) {
              override def connect(): Unit = ()

              override def getInputStream: InputStream = new ByteArrayInputStream(resourceContent)
            }
          }
        )
      } else {
        null
      }
    }
  }
}
