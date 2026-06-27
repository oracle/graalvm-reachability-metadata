/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_literal_3

import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.Json
import io.circe.KeyEncoder
import io.circe.ParsingFailure
import io.circe.jawn.parse
import io.circe.literal._
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters._

final case class LiteralWidget(id: Int, label: String, enabled: Boolean)

object LiteralWidget {
  given Encoder[LiteralWidget] = Encoder.forProduct3("id", "label", "enabled") { widget =>
    (widget.id, widget.label, widget.enabled)
  }

  given Decoder[LiteralWidget] = Decoder.forProduct3("id", "label", "enabled")(LiteralWidget.apply)
}

final case class LiteralFieldName(value: String)

object LiteralFieldName {
  given KeyEncoder[LiteralFieldName] = KeyEncoder.instance(fieldName => s"field-${fieldName.value}")
}

class Circe_literal_3Test {
  @Test
  def buildsNestedLiteralDocumentsWithEveryJsonValueKind(): Unit = {
    val document: Json = json"""
      {
        "name": "circe literal",
        "active": true,
        "archived": false,
        "missing": null,
        "count": 42,
        "decimal": -12.50,
        "scientific": 6.022e23,
        "escaped": "quote: \" slash: \\ newline: \n snowman: ☃",
        "items": [1, "two", false, null, { "nested": "value" }]
      }
    """

    val cursor = document.hcursor
    assertThat(document.isObject).isTrue
    assertThat(expectDecoded(cursor.get[String]("name"))).isEqualTo("circe literal")
    assertThat(expectDecoded(cursor.get[Boolean]("active"))).isTrue
    assertThat(expectDecoded(cursor.get[Boolean]("archived"))).isFalse
    assertThat(cursor.downField("missing").focus.exists(_.isNull)).isTrue
    assertThat(expectDecoded(cursor.get[Int]("count"))).isEqualTo(42)
    assertThat(expectDecoded(cursor.get[BigDecimal]("decimal"))).isEqualTo(BigDecimal("-12.50"))
    assertThat(expectDecoded(cursor.get[BigDecimal]("scientific"))).isEqualTo(BigDecimal("6.022E+23"))
    assertThat(expectDecoded(cursor.get[String]("escaped")))
      .isEqualTo("quote: \" slash: \\ newline: \n snowman: ☃")
    assertThat(expectDecoded(cursor.downField("items").downN(4).get[String]("nested"))).isEqualTo("value")
  }

  @Test
  def constructsTopLevelScalarArrayAndObjectLiterals(): Unit = {
    val nullLiteral: Json = json""" null """
    val booleanLiteral: Json = json""" false """
    val numberLiteral: Json = json""" 9007199254740993 """
    val stringLiteral: Json = json""" "standalone" """
    val arrayLiteral: Json = json""" ["alpha", "beta", "gamma"] """
    val objectLiteral: Json = json""" { "left": 1, "right": [2, 3] } """

    assertThat(nullLiteral.isNull).isTrue
    assertThat(booleanLiteral.asBoolean).isEqualTo(Some(false))
    assertThat(numberLiteral.asNumber.flatMap(_.toBigDecimal)).isEqualTo(Some(BigDecimal("9007199254740993")))
    assertThat(stringLiteral.asString).isEqualTo(Some("standalone"))
    assertThat(expectDecoded(arrayLiteral.as[List[String]]).asJava).containsExactly("alpha", "beta", "gamma")
    assertThat(expectDecoded(objectLiteral.hcursor.downField("right").as[List[Int]]).asJava).containsExactly(2, 3)
  }

  @Test
  def constructsEmptyArrayAndObjectLiteralsAtTopLevelAndNestedPositions(): Unit = {
    val emptyArray: Json = json""" [] """
    val emptyObject: Json = json""" {} """
    val document: Json = json"""
      {
        "emptyArray": [],
        "emptyObject": {},
        "nested": [[], {}]
      }
    """

    assertThat(expectOption(emptyArray.asArray).asJava).isEmpty()
    assertThat(expectOption(emptyObject.asObject).isEmpty).isTrue
    assertThat(expectOption(document.hcursor.downField("emptyArray").focus).isArray).isTrue
    assertThat(expectDecoded(document.hcursor.get[List[Json]]("emptyArray")).asJava).isEmpty()
    assertThat(expectOption(document.hcursor.downField("emptyObject").focus).isObject).isTrue
    assertThat(expectOption(document.hcursor.downField("emptyObject").focus).asObject.exists(_.isEmpty)).isTrue
    assertThat(expectDecoded(document.hcursor.downField("nested").downN(0).as[List[Json]]).asJava).isEmpty()
    assertThat(expectOption(document.hcursor.downField("nested").downN(1).focus).asObject.exists(_.isEmpty)).isTrue
  }

  @Test
  def interpolatesPrimitiveCollectionOptionalAndTopLevelValuesThroughEncoders(): Unit = {
    val serviceName: String = "search"
    val port: Int = 9443
    val weights: List[Double] = List(0.25, 0.5, 1.25)
    val description: Option[String] = Some("primary endpoint")
    val absent: Option[String] = None

    val document: Json = json"""
      {
        "service": $serviceName,
        "port": $port,
        "weights": $weights,
        "description": $description,
        "absent": $absent
      }
    """
    val topLevelArray: Json = json""" $weights """
    val topLevelString: Json = json""" $serviceName """

    assertThat(expectDecoded(document.hcursor.get[String]("service"))).isEqualTo("search")
    assertThat(expectDecoded(document.hcursor.get[Int]("port"))).isEqualTo(9443)
    assertThat(expectDecoded(document.hcursor.get[List[Double]]("weights"))).isEqualTo(weights)
    assertThat(expectDecoded(document.hcursor.get[Option[String]]("description"))).isEqualTo(description)
    assertThat(expectDecoded(document.hcursor.get[Option[String]]("absent"))).isEqualTo(absent)
    assertThat(expectDecoded(topLevelArray.as[List[Double]])).isEqualTo(weights)
    assertThat(topLevelString.asString).isEqualTo(Some("search"))
  }

  @Test
  def interpolatesCustomObjectKeysAndDomainObjectsWithPublicCirceCodecs(): Unit = {
    val primaryKey: LiteralFieldName = LiteralFieldName("primary")
    val secondaryKey: LiteralFieldName = LiteralFieldName("secondary")
    val primaryWidget: LiteralWidget = LiteralWidget(7, "literal", enabled = true)
    val secondaryWidget: LiteralWidget = LiteralWidget(8, "fallback", enabled = false)

    val document: Json = json"""
      {
        $primaryKey: $primaryWidget,
        $secondaryKey: $secondaryWidget,
        "all": [$primaryWidget, $secondaryWidget]
      }
    """

    assertThat(expectDecoded(document.hcursor.downField("field-primary").get[Int]("id"))).isEqualTo(7)
    assertThat(expectDecoded(document.hcursor.downField("field-primary").get[String]("label"))).isEqualTo("literal")
    assertThat(expectDecoded(document.hcursor.downField("field-primary").get[Boolean]("enabled"))).isTrue
    assertThat(expectDecoded(document.hcursor.downField("field-secondary").get[Int]("id"))).isEqualTo(8)
    assertThat(expectDecoded(document.hcursor.downField("all").downN(1).as[LiteralWidget])).isEqualTo(secondaryWidget)
  }

  @Test
  def producesTheSameJsonTreeAsRuntimeParsingForEquivalentInput(): Unit = {
    val literalDocument: Json = json"""
      {
        "users": [
          { "name": "Ada", "scores": [1, 2, 3] },
          { "name": "Grace", "scores": [5, 8, 13] }
        ],
        "metadata": { "generated": false, "source": "literal" }
      }
    """
    val parsedDocument: Json = expectParsed(parse("""
      {
        "users": [
          { "name": "Ada", "scores": [1, 2, 3] },
          { "name": "Grace", "scores": [5, 8, 13] }
        ],
        "metadata": { "generated": false, "source": "literal" }
      }
    """))

    assertThat(literalDocument).isEqualTo(parsedDocument)
    assertThat(literalDocument.noSpacesSortKeys).isEqualTo(parsedDocument.noSpacesSortKeys)
    assertThat(expectDecoded(literalDocument.hcursor.downField("users").downN(1).get[String]("name")))
      .isEqualTo("Grace")
  }

  @Test
  def literalResultsSupportNormalCirceCursorUpdatesAndDecodingFailures(): Unit = {
    val document: Json = json"""
      {
        "users": [
          { "name": "Ada", "active": true },
          { "name": "Grace", "active": false }
        ]
      }
    """

    val updated: Json = expectOption(
      document.hcursor
        .downField("users")
        .downN(1)
        .downField("active")
        .withFocus(_ => Json.True)
        .top
    )

    assertThat(expectDecoded(updated.hcursor.downField("users").downN(1).get[Boolean]("active"))).isTrue
    assertThat(updated.hcursor.downField("users").downN(2).succeeded).isFalse

    val decodingFailure: DecodingFailure = document.hcursor.downField("users").downN(0).get[Int]("name") match {
      case Left(error) => error
      case Right(value) => fail(s"Expected decoding to fail, but decoded: $value")
    }
    assertThat(decodingFailure.message).isNotBlank
    assertThat(decodingFailure.history.asJava).isNotEmpty
  }

  private def expectParsed[A](result: Either[ParsingFailure, A]): A = {
    result.fold(error => fail(s"Expected parsing success but got: $error"), identity)
  }

  private def expectDecoded[A](result: Either[DecodingFailure, A]): A = {
    result.fold(error => fail(s"Expected decoding success but got: $error"), identity)
  }

  private def expectOption[A](option: Option[A]): A = {
    option.getOrElse(fail("Expected value to be present"))
  }
}
