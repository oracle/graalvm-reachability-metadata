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
import play.mvc.{FileMimeTypes => JavaFileMimeTypes}
import play.mvc.Results

import java.util.Optional

class StatusHeaderTest {
  @Test
  def sendsResourceFromClassLoaderWithDownloadFilename(): Unit = {
    val scalaFileMimeTypes: FileMimeTypes = (name: String) =>
      if (name.endsWith(".txt")) Some("text/plain") else None
    val fileMimeTypes: JavaFileMimeTypes = new JavaFileMimeTypes(scalaFileMimeTypes)

    val result = Results
      .status(200)
      .sendResource(
        "results-status-resource.txt",
        getClass.getClassLoader,
        false,
        Optional.of("download.txt"),
        fileMimeTypes
      )

    val contentDisposition: String = result.header("Content-Disposition").get()

    assertThat(result.status()).isEqualTo(200)
    assertThat(contentDisposition).contains("attachment")
    assertThat(contentDisposition).contains("download.txt")
    assertThat(result.contentType()).hasValue("text/plain")
    assertThat(result.body().isKnownEmpty()).isFalse
  }
}
