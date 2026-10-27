/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_parser_3

import io.circe.{Json, ParsingFailure}
import io.circe.parser.parse
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises recursive JSON object merging for tenant-specific dashboard definitions. */
@Timeout(60)
class TenantDashboardMergeTest {
  private def renderDashboard(base: String, overrides: String): Either[ParsingFailure, Json] =
    for {
      template <- parse(base)
      tenant <- parse(overrides)
    } yield template.deepMerge(tenant)

  @Test
  def inheritsNestedDefaultsWhileReplacingTenantWidgetsAndOptionalBranding(): Unit = {
    val template: String =
      """{
        |  "title": "Operations",
        |  "theme": {
        |    "palette": {"background": "white", "accent": "blue"},
        |    "fontSize": 14
        |  },
        |  "widgets": [
        |    {"id": "orders", "visible": true},
        |    {"id": "revenue", "visible": true}
        |  ],
        |  "branding": {"logo": "default.svg", "helpUrl": "/help"},
        |  "refreshSeconds": 30
        |}""".stripMargin
    val overrides: String =
      """{
        |  "title": "Warehouse",
        |  "theme": {"palette": {"accent": "green"}},
        |  "widgets": [{"id": "stock", "visible": true}],
        |  "branding": {"logo": null},
        |  "tenant": {"id": "north-depot"}
        |}""".stripMargin

    val dashboard: Json = renderDashboard(template, overrides).fold(error => throw error, identity)
    val expected: String =
      """{
        |  "title": "Warehouse",
        |  "theme": {
        |    "palette": {"background": "white", "accent": "green"},
        |    "fontSize": 14
        |  },
        |  "widgets": [{"id": "stock", "visible": true}],
        |  "branding": {"logo": null, "helpUrl": "/help"},
        |  "refreshSeconds": 30,
        |  "tenant": {"id": "north-depot"}
        |}""".stripMargin

    // Persist the assembled document as an application would before serving it.
    val storedDashboard: String = dashboard.noSpaces
    assertEquals(parse(expected), parse(storedDashboard))
    assertEquals(
      Right("stock"),
      dashboard.hcursor.downField("widgets").downArray.get[String]("id")
    )
    assertEquals(Some(Json.Null), dashboard.hcursor.downField("branding").downField("logo").focus)

    // A second tenant with no overrides must still receive the unmodified template.
    assertEquals(parse(template), renderDashboard(template, "{}"))
  }

  @Test
  def refusesToPublishADashboardFromATruncatedOverrideDocument(): Unit = {
    val template: String = """{"title":"Operations","refreshSeconds":30}"""
    val truncatedOverride: String = """{"title":"Warehouse","theme":{"fontSize":18}"""

    assertTrue(renderDashboard(template, truncatedOverride).isLeft)
  }
}
