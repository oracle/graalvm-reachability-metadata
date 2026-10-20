/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_parser_3

import io.circe.{Json, ParsingFailure, Printer}
import io.circe.parser.parse
import org.junit.jupiter.api.Assertions.{assertEquals, assertNotEquals, assertTrue}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises configurable JSON printing for stable, compact deployment snapshot exports. */
@Timeout(60)
class DeploymentSnapshotExportTest {
  private val snapshotPrinter: Printer = Printer.noSpaces.copy(
    sortKeys = true,
    dropNullValues = true
  )

  private def exportSnapshot(document: String): Either[ParsingFailure, String] =
    parse(document).map(snapshotPrinter.print)

  @Test
  def exportsTheSameSnapshotRegardlessOfObjectFieldOrder(): Unit = {
    val controlPlaneResponse: String =
      """{
        |  "service": "order-gateway",
        |  "rollback": null,
        |  "routes": [
        |    {"weight": 90, "target": "primary", "comment": null},
        |    {"weight": 10, "target": "canary"}
        |  ],
        |  "settings": {"tls": true, "owner": null, "banner": "Say \"ready\"\nNext"},
        |  "maintenanceWindows": [null, "Sunday"]
        |}""".stripMargin
    val reorderedResponse: String =
      """{
        |  "maintenanceWindows": [null, "Sunday"],
        |  "settings": {"banner": "Say \"ready\"\nNext", "tls": true},
        |  "routes": [
        |    {"target": "primary", "weight": 90},
        |    {"target": "canary", "weight": 10}
        |  ],
        |  "service": "order-gateway"
        |}""".stripMargin

    val snapshot: String = exportSnapshot(controlPlaneResponse).fold(error => throw error, identity)
    val expected: String =
      """{"maintenanceWindows":[null,"Sunday"],"routes":[{"target":"primary","weight":90},{"target":"canary","weight":10}],"service":"order-gateway","settings":{"banner":"Say \"ready\"\nNext","tls":true}}"""

    assertEquals(expected, snapshot)
    assertEquals(Right(snapshot), exportSnapshot(reorderedResponse))
    assertEquals(Right(snapshot), exportSnapshot(snapshot))

    val restored: Json = parse(snapshot).fold(error => throw error, identity)
    assertEquals(Right("Say \"ready\"\nNext"), restored.hcursor.downField("settings").get[String]("banner"))
    assertEquals(None, restored.hcursor.downField("rollback").focus)
    assertEquals(None, restored.hcursor.downField("settings").downField("owner").focus)
    assertEquals(Some(Json.Null), restored.hcursor.downField("maintenanceWindows").downArray.focus)

    // Routing priority is application data: sorting object keys must not reorder arrays.
    val changedPriority: String = reorderedResponse.replace(
      """{"target": "primary", "weight": 90},
        |    {"target": "canary", "weight": 10}""".stripMargin,
      """{"target": "canary", "weight": 10},
        |    {"target": "primary", "weight": 90}""".stripMargin
    )
    val changedSnapshot: String = exportSnapshot(changedPriority).fold(error => throw error, identity)
    assertNotEquals(snapshot, changedSnapshot)
    assertEquals(
      Right("canary"),
      parse(changedSnapshot).fold(error => throw error, identity)
        .hcursor.downField("routes").downArray.get[String]("target")
    )
  }

  @Test
  def rejectsAnIncompleteControlPlaneResponseInsteadOfExportingIt(): Unit = {
    val incompleteResponse: String = """{"service":"order-gateway","routes":["""

    assertTrue(exportSnapshot(incompleteResponse).isLeft)
  }
}
