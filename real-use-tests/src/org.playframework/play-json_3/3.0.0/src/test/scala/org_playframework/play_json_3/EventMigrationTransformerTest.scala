/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_json_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import play.api.libs.functional.syntax.*
import play.api.libs.json.JsArray
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsError
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsPath
import play.api.libs.json.JsResult
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Reads
import play.api.libs.json.Reads.JsObjectReducer
import play.api.libs.json.__

/**
 * JSON-to-JSON transformers built from `JsPath.json` pickers, copiers, updaters and pruners.
 *
 * An event store upgrades legacy audit events to its version-two layout without binding them to
 * case classes: it renames fields with `copyFrom`, strips the stored password with `prune`,
 * stamps a schema version with `put`, normalizes tags in place with `update`, and chains a
 * second enrichment step with `andThen`. A reporting job projects a single branch out of the
 * upgraded event, and a legacy event that lost its kind is rejected at the path it is missing.
 */
class EventMigrationTransformerTest {
  import EventMigrationTransformerTest.*

  @Test
  def legacyEventsAreUpgradedThroughJsonTransformers(): Unit = {
    val legacy: JsValue = Json.parse(
      """{
        |  "id": "evt-1001",
        |  "kind": "signup",
        |  "user": {"name": "Ada Lovelace", "email": "ada@example.org", "pwd": "s3cret"},
        |  "tags": ["Beta", " Newsletter "],
        |  "ts": 1700000000
        |}""".stripMargin
    )

    val upgraded: JsObject = legacy.transform(upgradeEvent).get
    assertEquals(
      Json.obj(
        "eventId" -> "evt-1001",
        "type" -> "signup",
        "actor" -> Json.obj("name" -> "Ada Lovelace", "email" -> "ada@example.org"),
        "tags" -> Json.arr("beta", "newsletter"),
        "occurredAtMillis" -> 1700000000000L,
        "schemaVersion" -> 2,
        "audit" -> Json.obj("migrated" -> true, "source" -> "legacy-store")
      ),
      upgraded
    )

    val contactProjection: JsResult[JsObject] = upgraded.transform((__ \ "actor" \ "email").json.pickBranch)
    assertEquals(
      Some(Json.obj("actor" -> Json.obj("email" -> "ada@example.org"))),
      contactProjection.asOpt
    )

    val redacted: JsObject = upgraded.transform((__ \ "actor").json.prune andThen (__ \ "audit").json.prune).get
    assertEquals(Set("eventId", "type", "tags", "occurredAtMillis", "schemaVersion"), redacted.keys)

    val missingKind: JsValue = legacy.as[JsObject] - "kind"
    val failedPaths: Set[JsPath] = missingKind.transform(upgradeEvent) match {
      case JsError(errors) => errors.map(_._1).toSet
      case JsSuccess(value, _) => throw new AssertionError(s"Event without a kind was upgraded to $value")
    }
    assertEquals(Set(__ \ "kind"), failedPaths)
  }
}

object EventMigrationTransformerTest {

  /** Lower-cases and trims every tag while keeping the array in place. */
  private val normalizeTags: Reads[JsArray] =
    Reads.of[JsArray].map(tags =>
      JsArray(tags.value.collect { case JsString(tag) => JsString(tag.trim.toLowerCase) })
    )

  /** Rewrites a legacy event into the version-two field layout. */
  private val reshapeEvent: Reads[JsObject] = (
    (__ \ "eventId").json.copyFrom((__ \ "id").json.pick) and
      (__ \ "type").json.copyFrom((__ \ "kind").json.pick) and
      (__ \ "actor").json.copyFrom((__ \ "user").json.pick(using (__ \ "pwd").json.prune)) and
      (__ \ "tags").json.copyFrom((__ \ "tags").json.pick(using normalizeTags)) and
      (__ \ "occurredAtMillis").json.copyFrom(
        (__ \ "ts").json.pick(using Reads.of[JsNumber].map(seconds => JsNumber(seconds.value * 1000)))
      ) and
      (__ \ "schemaVersion").json.put(JsNumber(2))
  ).reduce

  /** Adds the audit block to an already reshaped event and keeps every other field. */
  private val stampAudit: Reads[JsObject] =
    __.json.update(
      (__ \ "audit").json.put(Json.obj("migrated" -> JsBoolean(true), "source" -> "legacy-store"))
    )

  val upgradeEvent: Reads[JsObject] = reshapeEvent andThen stampAudit
}
