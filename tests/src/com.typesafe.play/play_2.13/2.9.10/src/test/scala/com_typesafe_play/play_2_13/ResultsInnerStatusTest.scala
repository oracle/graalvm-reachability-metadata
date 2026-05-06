/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_2_13

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.http.FileMimeTypes
import play.api.mvc.Results

import scala.concurrent.ExecutionContext

class ResultsInnerStatusTest {
  @Test
  def sendsClasspathResourceFromStatusResult(): Unit = {
    implicit val executionContext: ExecutionContext = ExecutionContext.global
    implicit val fileMimeTypes: FileMimeTypes = (name: String) =>
      if (name.endsWith(".txt")) Some("text/plain") else None

    val result = Results.Ok.sendResource(
      resource = "results-status-resource.txt",
      classLoader = getClass.getClassLoader,
      inline = false
    )

    val contentDisposition: String = result.header.headers("Content-Disposition")

    assertThat(result.header.status).isEqualTo(200)
    assertThat(contentDisposition.contains("attachment")).isTrue
    assertThat(contentDisposition.contains("results-status-resource.txt")).isTrue
    assertThat(result.body.contentLength).isEqualTo(Some(20L))
    assertThat(result.body.contentType).isEqualTo(Some("text/plain"))
  }
}
