/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_json_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import play.api.libs.functional.syntax.*
import play.api.libs.json.Format
import play.api.libs.json.JsError
import play.api.libs.json.JsPath
import play.api.libs.json.JsResult
import play.api.libs.json.JsResultException
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.JsonValidationError
import play.api.libs.json.OFormat
import play.api.libs.json.Reads
import play.api.libs.json.Writes

/**
 * Payload version fallback and client-facing error reporting through `Reads.orElse` and the `JsResult`/`JsError` API.
 *
 * A subscription API still receives version 1 payloads from old clients while new clients send
 * version 2. Its reader tries the version 2 layout first and falls back to an adapter for the
 * legacy layout, while value types are wrapped through `Format` and `contramap` adapters.
 * Rejected requests are answered with an error envelope rendered by `JsError.toJson` and
 * `JsError.toFlatForm`; the API also merges errors from independent sub-documents, re-roots them
 * under the request field they came from, falls back to a default for an absent optional field, and
 * turns `as` failures into `JsResultException` values carrying the paths that failed.
 */
class SubscriptionApiVersionFallbackTest {
  import SubscriptionApiVersionFallbackTest.*

  @Test
  def legacyAndCurrentPayloadsAreAcceptedAndRejectionsAreReported(): Unit = {
    val current: JsValue = Json.parse(
      """{"accountId": "acc-17", "plan": "team", "seats": 12, "billingEmail": "ops@example.org"}"""
    )
    val legacy: JsValue = Json.parse("""{"account": "acc-3", "tier": "solo"}""")

    val fromCurrent: Subscription = current.as[Subscription]
    assertEquals(Subscription(AccountId("acc-17"), Plan.Team, 12, Some("ops@example.org")), fromCurrent)
    val fromLegacy: Subscription = legacy.as[Subscription]
    assertEquals(Subscription(AccountId("acc-3"), Plan.Solo, 1, None), fromLegacy)

    // Both versions are answered in the version 2 layout.
    assertEquals(
      Json.obj("accountId" -> "acc-3", "plan" -> "solo", "seats" -> 1),
      Json.toJson(fromLegacy)
    )
    assertEquals(current, Json.toJson(fromCurrent))

    // A version 2 payload that breaks several rules is reported as a whole error envelope.
    val rejected: JsResult[Subscription] = Json
      .parse("""{"accountId": "", "plan": "galaxy", "seats": 900}""")
      .validate[Subscription](using currentReads)
    val rejection: JsError = rejected match {
      case error: JsError => error
      case JsSuccess(value, _) => throw new AssertionError(s"Invalid subscription was accepted as $value")
    }
    assertEquals(
      Json.obj(
        "obj.accountId" -> Json.arr(Json.obj("msg" -> Json.arr("error.account.blank"), "args" -> Json.arr())),
        "obj.plan" -> Json.arr(Json.obj("msg" -> Json.arr("error.plan.unknown"), "args" -> Json.arr("galaxy"))),
        "obj.seats" -> Json.arr(Json.obj("msg" -> Json.arr("error.max"), "args" -> Json.arr(500)))
      ),
      JsError.toJson(rejection)
    )
    assertEquals(
      Map(
        "obj.accountId" -> Seq("error.account.blank" -> Nil),
        "obj.plan" -> Seq("error.plan.unknown" -> List("galaxy")),
        "obj.seats" -> Seq("error.max" -> List(MaxSeats))
      ),
      JsError
        .toFlatForm(rejection)
        .map((field, errors) => field -> errors.map(error => error.message -> error.args.toList).toSeq)
        .toMap
    )

    // A payload matching neither version reports the errors of the legacy fallback.
    val unknownLayout: JsResultException = assertThrows(
      classOf[JsResultException],
      () => Json.parse("""{"customer": "acc-9"}""").as[Subscription]
    )
    assertEquals(
      Set(
        (JsPath \ "account", "error.path.missing"),
        (JsPath \ "tier", "error.path.missing")
      ),
      unknownLayout.errors.flatMap((path, errors) => errors.map(error => (path, error.message))).toSet
    )
    assertEquals(None, Json.parse("""{"customer": "acc-9"}""").asOpt[Subscription])

    // A bulk request validates each subscription on its own and re-roots its errors.
    val bulk: JsValue = Json.parse(
      """{
        |  "subscriptions": [
        |    {"accountId": "acc-1", "plan": "solo"},
        |    {"accountId": "acc-2", "plan": "team", "seats": 0},
        |    {"account": "acc-4", "tier": "enterprise"}
        |  ],
        |  "notes": 42
        |}""".stripMargin
    )
    val outcome: JsResult[BulkRequest] = validateBulk(bulk)
    val bulkErrors: Map[String, Seq[String]] = outcome match {
      case JsError(errors) =>
        errors.map((path, messages) => path.toString -> messages.map(_.message).toSeq).toMap
      case JsSuccess(value, _) => throw new AssertionError(s"Invalid bulk request was accepted as $value")
    }
    assertEquals(
      Map(
        "/subscriptions(1)/seats" -> Seq("error.min"),
        "/notes" -> Seq("error.expected.jsstring")
      ),
      bulkErrors
    )

    // A clean bulk request recovers a default for its missing notes.
    val clean: JsValue = Json.obj(
      "subscriptions" -> Json.arr(legacy, current)
    )
    assertEquals(
      JsSuccess(BulkRequest(List(fromLegacy, fromCurrent), "no notes")),
      validateBulk(clean)
    )
    val summary: String = validateBulk(clean).fold(
      errors => s"rejected ${errors.size}",
      request => request.subscriptions.map(_.seats).sum.toString
    )
    assertEquals("13", summary)

    // Validation errors keep their arguments for message rendering.
    val seatLimit: JsonValidationError = JsonValidationError("error.max", MaxSeats)
    assertEquals(Seq(MaxSeats), seatLimit.args)
    assertEquals("error.max", seatLimit.message)
  }
}

object SubscriptionApiVersionFallbackTest {

  /** Largest seat count a single subscription may hold. */
  val MaxSeats: Int = 500

  final case class AccountId(value: String)

  object AccountId {
    given Format[AccountId] = Format(
      Reads.of[String]
        .filter(JsonValidationError("error.account.blank"))(_.nonEmpty)
        .map(AccountId(_)),
      Writes.of[String].contramap(_.value)
    )
  }

  enum Plan(val wireName: String) {
    case Solo extends Plan("solo")
    case Team extends Plan("team")
    case Enterprise extends Plan("enterprise")
  }

  object Plan {
    given Writes[Plan] = Writes.of[String].contramap(_.wireName)

    /** Reads a plan name and reports unknown names with the rejected name as an argument. */
    val strictReads: Reads[Plan] = Reads.of[String].flatMapResult { name =>
      Plan.values.find(_.wireName == name) match {
        case Some(plan) => JsSuccess(plan)
        case None => JsError(JsonValidationError("error.plan.unknown", name))
      }
    }
  }

  final case class Subscription(account: AccountId, plan: Plan, seats: Int, billingEmail: Option[String])

  /** Version 2 layout, used for every response. */
  val currentReads: Reads[Subscription] = (
    (JsPath \ "accountId").read[AccountId] and
      (JsPath \ "plan").read[Plan](using Plan.strictReads) and
      (JsPath \ "seats").readWithDefault[Int](1)(using Reads.min(1).keepAnd(Reads.max(MaxSeats))) and
      (JsPath \ "billingEmail").readNullable[String]
  )(Subscription.apply)

  /** Version 1 layout: a tier name instead of a plan, a single seat and no billing contact. */
  val legacyReads: Reads[Subscription] = (
    (JsPath \ "account").read[AccountId] and
      (JsPath \ "tier").read[Plan](using Plan.strictReads)
  )((account, plan) => Subscription(account, plan, 1, None))

  /** Accepts both layouts, preferring version 2, and always writes version 2. */
  given OFormat[Subscription] = OFormat(
    currentReads.orElse(legacyReads),
    (subscription: Subscription) =>
      Json.obj(
        "accountId" -> subscription.account,
        "plan" -> subscription.plan,
        "seats" -> subscription.seats
      ) ++ subscription.billingEmail.fold(Json.obj())(email => Json.obj("billingEmail" -> email))
  )

  final case class BulkRequest(subscriptions: List[Subscription], notes: String)

  /** Validates each subscription separately so one bad entry does not hide another. */
  def validateBulk(json: JsValue): JsResult[BulkRequest] = {
    val entries: Seq[JsValue] = (json \ "subscriptions").as[Seq[JsValue]]
    val subscriptions: JsResult[List[Subscription]] = entries.zipWithIndex
      .map { (entry, index) =>
        val layout: Reads[Subscription] = if ((entry \ "accountId").isDefined) currentReads else legacyReads
        entry.validate[Subscription](using layout).repath(JsPath \ "subscriptions" \ index).map(List(_))
      }
      .foldLeft[JsResult[List[Subscription]]](JsSuccess(Nil))(merge(_, _)(_ ++ _))
    val notes: JsResult[String] = (json \ "notes")
      .validateOpt[String]
      .map(_.getOrElse("no notes"))
      .repath(JsPath \ "notes")
    merge(subscriptions, notes)(BulkRequest(_, _))
  }

  /** Combines two results, keeping the errors of both sides. */
  def merge[A, B, C](left: JsResult[A], right: JsResult[B])(combine: (A, B) => C): JsResult[C] =
    (left, right) match {
      case (JsSuccess(a, _), JsSuccess(b, _)) => JsSuccess(combine(a, b))
      case (first: JsError, second: JsError) => first ++ second
      case (error: JsError, _) => error
      case (_, error: JsError) => error
    }
}
