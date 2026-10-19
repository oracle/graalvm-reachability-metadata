/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_json_3

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets.UTF_8
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import zio.json.codegen.Generator

/** Exercises JSON-to-Scala source generation for scaffolding an archive provider's API models. */
class ArchiveModelScaffoldingTest {
  private def scaffold(sample: String): String = {
    val bytes: ByteArrayOutputStream = new ByteArrayOutputStream()
    val output: PrintStream = new PrintStream(bytes, true, UTF_8)
    try {
      Console.withOut(output) {
        Generator.printCaseClasses(sample)
      }
      bytes.toString(UTF_8).replace(Console.CYAN, "").replace(Console.RESET, "").trim
    } finally {
      output.close()
    }
  }

  @Test
  @Timeout(30)
  def scaffoldNestedModelsFromAnArchiveSnapshotResponse(): Unit = {
    val sample: String = """
      {
        "snapshotId": "f81d4fae-7dec-11d0-a765-00a0c91e6bf6",
        "createdOn": "2025-04-12",
        "files": [
          {"path": "backups/database.dump", "bytes": 3000000000, "checksum": null},
          {"path": "backups/manifest.txt", "bytes": 512, "checksum": "sha256:abc123"}
        ],
        "storage": {"region": "eu-west", "encrypted": true}
      }
      """
    // The developer reviews this source before adding it to an SDK; no runtime compilation is needed.
    val generated: String = scaffold(sample)
    val expected: String = """
      |final case class RootObject(
      |  snapshotId: java.util.UUID,
      |  createdOn: java.time.LocalDate,
      |  files: List[Files],
      |  storage: Storage
      |)
      |
      |object RootObject {
      |  implicit val codec: JsonCodec[RootObject] = DeriveJsonCodec.gen
      |}
      |
      |final case class Files(
      |  path: String,
      |  bytes: Long,
      |  checksum: Option[String]
      |)
      |
      |object Files {
      |  implicit val codec: JsonCodec[Files] = DeriveJsonCodec.gen
      |}
      |
      |final case class Storage(
      |  region: String,
      |  encrypted: Boolean
      |)
      |
      |object Storage {
      |  implicit val codec: JsonCodec[Storage] = DeriveJsonCodec.gen
      |}
      |""".stripMargin.trim
    assertEquals(expected, generated,
      "The scaffold must preserve large file sizes, nullable checksums, and nested provider models")
    assertEquals(generated, scaffold(sample), "The same provider sample must produce a reproducible scaffold")

    val invalidSample: String = "{\"files\":["
    val diagnostic: String = scaffold(invalidSample)
    assertEquals(s"Invalid JSON: $invalidSample", diagnostic)
    assertFalse(diagnostic.contains("final case class"), "An incomplete response must not produce SDK source")
  }
}
