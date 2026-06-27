/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_literal_3

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.KeyEncoder
import io.circe.literal._
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters._

final case class LiteralUser(name: String, age: Int, tags: List[String])

object LiteralUser {
  given Encoder[LiteralUser] = Encoder.forProduct3("name", "age", "tags") { user =>
    (user.name, user.age, user.tags)
  }

  given Decoder[LiteralUser] = Decoder.forProduct3("name", "age", "tags")(LiteralUser.apply)
}

final case class LiteralKey(namespace: String, value: Int)

object LiteralKey {
  given KeyEncoder[LiteralKey] = KeyEncoder.instance(key => s"${key.namespace}:${key.value}")
}

class Circe_literal_3Test {
  @Test
  def createsStaticJsonDocumentsAtCompileTime(): Unit = {
    val document: Json = json"""
      {
        "service": "literal",
        "enabled": true,
        "retries": 3,
        "threshold": 12.5,
        "nullable": null,
        "escaped": "line\nbreak and \"quotes\"",
        "items": [1, false, { "kind": "nested" }]
      }
    """

    assertThat(document.isObject).isTrue
    assertThat(document.hcursor.downField("service").as[String]).isEqualTo(Right("literal"))
    assertThat(document.hcursor.downField("enabled").as[Boolean]).isEqualTo(Right(true))
    assertThat(document.hcursor.downField("retries").as[Int]).isEqualTo(Right(3))
    assertThat(document.hcursor.downField("threshold").as[BigDecimal]).isEqualTo(Right(BigDecimal("12.5")))
    assertThat(document.hcursor.downField("nullable").focus).isEqualTo(Some(Json.Null))
    assertThat(document.hcursor.downField("escaped").as[String]).isEqualTo(Right("line\nbreak and \"quotes\""))
    assertThat(document.hcursor.downField("items").downN(2).downField("kind").as[String])
      .isEqualTo(Right("nested"))
    assertThat(document.deepDropNullValues.noSpacesSortKeys)
      .isEqualTo(
        """{"enabled":true,"escaped":"line\nbreak and \"quotes\"","items":[1,false,{"kind":"nested"}],"retries":3,"service":"literal","threshold":12.5}"""
      )
  }

  @Test
  def supportsTopLevelJsonValues(): Unit = {
    val topLevelNull: Json = json"null"
    val topLevelTrue: Json = json"true"
    val topLevelFalse: Json = json"false"
    val topLevelString: Json = json""""standalone""""
    val topLevelNumber: Json = json"-12345.6789"
    val topLevelArray: Json = json"""[null, true, "value"]"""

    assertThat(topLevelNull).isEqualTo(Json.Null)
    assertThat(topLevelTrue).isEqualTo(Json.True)
    assertThat(topLevelFalse).isEqualTo(Json.False)
    assertThat(topLevelString.asString).isEqualTo(Some("standalone"))
    assertThat(topLevelNumber.asNumber.flatMap(_.toBigDecimal)).isEqualTo(Some(BigDecimal("-12345.6789")))
    assertThat(topLevelArray.hcursor.downN(2).as[String]).isEqualTo(Right("value"))
  }

  @Test
  def interpolatesValuesUsingAvailableEncoders(): Unit = {
    val user: LiteralUser = LiteralUser("Ada", 37, List("admin", "ops"))
    val count: Int = 2
    val maybeRegion: Option[String] = Some("eu-west")
    val absentValue: Option[String] = None
    val embedded: Json = json"""{ "source": "prebuilt", "safe": true }"""
    val document: Json = json"""
      {
        "user": $user,
        "count": $count,
        "region": $maybeRegion,
        "absent": $absentValue,
        "embedded": $embedded,
        "matrix": ${List(List(1, 2), List(3, 4))},
        "stringWithEscapes": ${"quotes, backslash \\ and newline\n"}
      }
    """

    assertThat(document.hcursor.downField("user").as[LiteralUser])
      .isEqualTo(Right(user))
    assertThat(document.hcursor.downField("count").as[Int]).isEqualTo(Right(2))
    assertThat(document.hcursor.downField("region").as[Option[String]]).isEqualTo(Right(Some("eu-west")))
    assertThat(document.hcursor.downField("absent").focus).isEqualTo(Some(Json.Null))
    assertThat(document.hcursor.downField("embedded").downField("source").as[String])
      .isEqualTo(Right("prebuilt"))
    assertThat(document.hcursor.downField("matrix").downN(1).downN(0).as[Int]).isEqualTo(Right(3))
    assertThat(document.hcursor.downField("stringWithEscapes").as[String])
      .isEqualTo(Right("quotes, backslash \\ and newline\n"))
  }

  @Test
  def interpolatesObjectKeysUsingKeyEncoders(): Unit = {
    val literalKey: LiteralKey = LiteralKey("tenant", 42)
    val stringKey: String = "display name"
    val numericKey: Int = 7
    val nestedValue: Json = json"""{ "active": true }"""
    val document: Json = json"""
      {
        $literalKey: $nestedValue,
        $stringKey: "Ada Lovelace",
        $numericKey: [1, 2, 3]
      }
    """

    val fields: Iterable[String] = document.asObject.map(_.keys).getOrElse(Iterable.empty)

    assertThat(fields.toSeq.asJava).containsExactly("tenant:42", "display name", "7")
    assertThat(document.hcursor.downField("tenant:42").downField("active").as[Boolean])
      .isEqualTo(Right(true))
    assertThat(document.hcursor.downField("display name").as[String]).isEqualTo(Right("Ada Lovelace"))
    assertThat(document.hcursor.downField("7").downN(2).as[Int]).isEqualTo(Right(3))
  }

  @Test
  def supportsTopLevelInterpolatedValues(): Unit = {
    val user: LiteralUser = LiteralUser("Katherine", 32, List("math", "flight"))
    val document: Json = json"$user"

    assertThat(document.as[LiteralUser]).isEqualTo(Right(user))
    assertThat(document.hcursor.downField("name").as[String]).isEqualTo(Right("Katherine"))
    assertThat(document.hcursor.downField("tags").downN(1).as[String]).isEqualTo(Right("flight"))
  }

  @Test
  def combinesLiteralStructureAndRepeatedInterpolations(): Unit = {
    val user: LiteralUser = LiteralUser("Grace", 41, List("compiler", "navy"))
    val key: LiteralKey = LiteralKey("user", 100)
    val enabled: Boolean = true
    val document: Json = json"""
      {
        "audit": [
          { "actor": $user, "enabled": $enabled },
          { "actor": $user, "enabled": false }
        ],
        "lookup": {
          $key: $user
        }
      }
    """

    assertThat(document.hcursor.downField("audit").downN(0).downField("actor").as[LiteralUser])
      .isEqualTo(Right(user))
    assertThat(document.hcursor.downField("audit").downN(0).downField("enabled").as[Boolean])
      .isEqualTo(Right(true))
    assertThat(document.hcursor.downField("audit").downN(1).downField("enabled").as[Boolean])
      .isEqualTo(Right(false))
    assertThat(document.hcursor.downField("lookup").downField("user:100").as[LiteralUser])
      .isEqualTo(Right(user))
  }
}
