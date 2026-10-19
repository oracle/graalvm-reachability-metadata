/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_json_3

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import zio.json.*
import zio.json.ast.Json

/** Exercises JSON AST transformations to redact schema-less configuration diagnostics. */
class ConfigurationRedactionTest {
  private def redact(document: Json): Json = {
    document.transformUpSome {
      case obj: Json.Obj => obj.mapObjectEntries {
        case (key, _) if key == "password" || key == "token" =>
          key -> Json.Str("[REDACTED]")
        case entry => entry
      }
    }
  }

  @Test
  @Timeout(30)
  def publishDiagnosticsWithoutLeakingNestedCredentials(): Unit = {
    val configuration: String = """
      {
        "service": "inventory",
        "database": {"host": "db.internal", "password": "db-secret"},
        "integrations": [
          {"name": "shipping", "token": "shipping-secret", "enabled": true},
          {"name": "billing", "credentials": {"password": "billing-secret"}}
        ],
        "extensions": {"retryDelay": 1.25, "labels": ["east", "blue"], "note": null},
        "banner": "Use the \"support\" portal\nDo not share credentials"
      }
      """
    val original: Json = configuration.fromJson[Json].fold(
      error => fail[Json](error),
      identity
    )
    val sanitized: Json = redact(original)
    val published: String = sanitized.toJsonPretty
    val expected: String = """
      {
        "service": "inventory",
        "database": {"host": "db.internal", "password": "[REDACTED]"},
        "integrations": [
          {"name": "shipping", "token": "[REDACTED]", "enabled": true},
          {"name": "billing", "credentials": {"password": "[REDACTED]"}}
        ],
        "extensions": {"retryDelay": 1.25, "labels": ["east", "blue"], "note": null},
        "banner": "Use the \"support\" portal\nDo not share credentials"
      }
      """

    assertEquals(expected.fromJson[Json], published.fromJson[Json])
    assertFalse(published.contains("db-secret"), published)
    assertFalse(published.contains("shipping-secret"), published)
    assertFalse(published.contains("billing-secret"), published)
    assertEquals(sanitized, redact(sanitized), "Redaction must be safe to apply twice")

    val originalPassword: Option[String] = original.asObject
      .flatMap(_.get("database"))
      .flatMap(_.asObject)
      .flatMap(_.get("password"))
      .flatMap(_.asString)
    assertEquals(Some("db-secret"), originalPassword, "Diagnostics must not change live configuration")
  }
}
