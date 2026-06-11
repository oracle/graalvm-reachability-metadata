/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_literal_3

import io.circe.Json
import io.circe.literal._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Circe_literal_3Test {
  @Test
  def jsonInterpolatorBuildsTopLevelLiteralValues(): Unit = {
    val topLevelString: Json = json"""
      "top-level string"
    """

    assertEquals(Json.Null, json"null")
    assertEquals(Json.True, json"true")
    assertEquals(Json.False, json"false")
    assertEquals(Json.fromInt(123), json"123")
    assertEquals(Json.fromString("top-level string"), topLevelString)
  }

  @Test
  def jsonInterpolatorBuildsCompleteLiteralDocuments(): Unit = {
    val document: Json = json"""
      {
        "array": [1, true, false, null, "text"],
        "object": {
          "decimal": 0.0001,
          "unicode": "snowman ☃",
          "escaped": "quote: \" and slash: \/"
        },
        "emptyArray": [],
        "emptyObject": {}
      }
    """

    val expected: Json = Json.obj(
      "array" -> Json.arr(
        Json.fromInt(1),
        Json.True,
        Json.False,
        Json.Null,
        Json.fromString("text")
      ),
      "object" -> Json.obj(
        "decimal" -> Json.fromBigDecimal(BigDecimal("0.0001")),
        "unicode" -> Json.fromString("snowman ☃"),
        "escaped" -> Json.fromString("quote: \" and slash: /")
      ),
      "emptyArray" -> Json.arr(),
      "emptyObject" -> Json.obj()
    )

    assertEquals(expected, document)
  }

  @Test
  def jsonInterpolatorEncodesTopLevelInterpolatedValues(): Unit = {
    val text: String = "line one\nline two with ☕"
    val integer: Int = 42
    val decimal: BigDecimal = BigDecimal("12345.6789")
    val truth: Boolean = true

    assertEquals(Json.fromString(text), json"$text")
    assertEquals(Json.fromInt(integer), json"$integer")
    assertEquals(Json.fromBigDecimal(decimal), json"$decimal")
    assertEquals(Json.fromBoolean(truth), json"$truth")
  }

  @Test
  def jsonInterpolatorEncodesValuesInsideObjectsAndArrays(): Unit = {
    val userId: Int = 7
    val name: String = "Ada Lovelace"
    val flags: List[Boolean] = List(true, false, true)
    val scores: Map[String, List[Int]] = Map("recent" -> List(9, 10), "empty" -> Nil)

    val payload: Json = json"""
      {
        "user": {
          "id": $userId,
          "name": $name
        },
        "flags": $flags,
        "scores": $scores,
        "mixed": [$userId, $name, $flags]
      }
    """

    assertEquals(
      Some(Json.fromInt(userId)),
      payload.hcursor.downField("user").downField("id").focus
    )
    assertEquals(
      Some(Json.fromString(name)),
      payload.hcursor.downField("user").downField("name").focus
    )
    assertEquals(
      Some(Json.arr(Json.True, Json.False, Json.True)),
      payload.hcursor.downField("flags").focus
    )
    assertEquals(
      Some(Json.arr(Json.fromInt(9), Json.fromInt(10))),
      payload.hcursor.downField("scores").downField("recent").focus
    )
    assertEquals(
      Some(Json.arr()),
      payload.hcursor.downField("scores").downField("empty").focus
    )
    assertEquals(
      Some(
        Json.arr(
          Json.fromInt(userId),
          Json.fromString(name),
          Json.arr(Json.True, Json.False, Json.True)
        )
      ),
      payload.hcursor.downField("mixed").focus
    )
  }

  @Test
  def jsonInterpolatorSupportsJsonValuesAsInterpolatedArguments(): Unit = {
    val nested: Json = json"""{ "numbers": [1, 2, 3], "enabled": true }"""

    val wrapper: Json = json"""
      {
        "payload": $nested,
        "copies": [$nested, $nested],
        "literal": "payload"
      }
    """

    assertEquals(Some(nested), wrapper.hcursor.downField("payload").focus)
    assertEquals(Some(Json.arr(nested, nested)), wrapper.hcursor.downField("copies").focus)
    assertEquals(Some(Json.fromString("payload")), wrapper.hcursor.downField("literal").focus)
  }

  @Test
  def jsonInterpolatorEncodesInterpolatedObjectKeys(): Unit = {
    val stringKey: String = "name with spaces and \"quotes\""
    val numericKey: Int = 2024

    val payload: Json = json"""
      {
        $stringKey: "string key value",
        $numericKey: "numeric key value"
      }
    """

    assertEquals(
      Some(Json.fromString("string key value")),
      payload.hcursor.downField(stringKey).focus
    )
    assertEquals(
      Some(Json.fromString("numeric key value")),
      payload.hcursor.downField(numericKey.toString).focus
    )
  }
}
