/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_literal_3

import io.circe.Encoder
import io.circe.Json
import io.circe.KeyEncoder
import io.circe.literal.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

object Circe_literal_3Test {
  final case class Participant(name: String, points: Int)

  object Participant {
    given Encoder[Participant] = Encoder.instance { participant =>
      Json.obj(
        "name" -> Json.fromString(participant.name),
        "points" -> Json.fromInt(participant.points)
      )
    }
  }

  final case class ParticipantId(value: String)

  object ParticipantId {
    given KeyEncoder[ParticipantId] = KeyEncoder.instance(id => s"participant-${id.value}")
  }
}

class Circe_literal_3Test {
  import Circe_literal_3Test.*

  @Test
  def parsesNestedJsonLiteralWithAllPrimitiveKinds(): Unit = {
    val document: Json = json"""
      {
        "name": "circe-literal",
        "enabled": true,
        "disabled": false,
        "missing": null,
        "count": 42,
        "ratio": -12.50,
        "items": ["alpha", 0, { "nested": "value" }]
      }
    """

    assertThat(document).isEqualTo(
      Json.obj(
        "name" -> Json.fromString("circe-literal"),
        "enabled" -> Json.True,
        "disabled" -> Json.False,
        "missing" -> Json.Null,
        "count" -> Json.fromInt(42),
        "ratio" -> Json.fromBigDecimal(BigDecimal("-12.50")),
        "items" -> Json.arr(
          Json.fromString("alpha"),
          Json.fromInt(0),
          Json.obj("nested" -> Json.fromString("value"))
        )
      )
    )
  }

  @Test
  def preservesJsonStringEscapesAndUnicodeCharacters(): Unit = {
    val escaped: Json = json""""line\nquote: \" snowman: ☃ path: C:\\tmp""""

    assertThat(escaped.asString).isEqualTo(Some("line\nquote: \" snowman: ☃ path: C:\\tmp"))
  }

  @Test
  def encodesInterpolatedValuesInObjectsAndArrays(): Unit = {
    val name: String = "Ada"
    val age: Int = 37
    val active: Boolean = true
    val roles: List[String] = List("admin", "maintainer")

    val profile: Json = json"""
      {
        "name": $name,
        "age": $age,
        "active": $active,
        "roles": $roles,
        "audit": ["created", $name, $age, $active]
      }
    """

    assertThat(profile).isEqualTo(
      Json.obj(
        "name" -> Json.fromString("Ada"),
        "age" -> Json.fromInt(37),
        "active" -> Json.True,
        "roles" -> Json.arr(Json.fromString("admin"), Json.fromString("maintainer")),
        "audit" -> Json.arr(
          Json.fromString("created"),
          Json.fromString("Ada"),
          Json.fromInt(37),
          Json.True
        )
      )
    )
  }

  @Test
  def supportsTopLevelInterpolatedJsonValue(): Unit = {
    val values: Vector[Int] = Vector(1, 2, 3)

    val literal: Json = json"$values"

    assertThat(literal).isEqualTo(Json.arr(Json.fromInt(1), Json.fromInt(2), Json.fromInt(3)))
  }

  @Test
  def usesCustomEncodersForInterpolatedValuesAndKeys(): Unit = {
    val participantId: ParticipantId = ParticipantId("blue")
    val participant: Participant = Participant("Grace", 99)

    val scoreboard: Json = json"""
      {
        $participantId: $participant,
        "status": "complete"
      }
    """

    assertThat(scoreboard).isEqualTo(
      Json.obj(
        "participant-blue" -> Json.obj(
          "name" -> Json.fromString("Grace"),
          "points" -> Json.fromInt(99)
        ),
        "status" -> Json.fromString("complete")
      )
    )
  }
}
