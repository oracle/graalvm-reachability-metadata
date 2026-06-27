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
import io.circe.JsonNumber
import io.circe.KeyEncoder
import io.circe.literal.*
import org.assertj.core.api.Assertions.assertThat
import scala.compiletime.testing.typeChecks
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

final case class ProjectKey(namespace: String, name: String)

object ProjectKey {
  given KeyEncoder[ProjectKey] = KeyEncoder.instance(key => s"${key.namespace}/${key.name}")
}

final case class Deployment(name: String, replicas: Int, labels: Map[String, String])

object Deployment {
  given Encoder.AsObject[Deployment] = Encoder.forProduct3("name", "replicas", "labels") { deployment =>
    (deployment.name, deployment.replicas, deployment.labels)
  }

  given Decoder[Deployment] = Decoder.forProduct3("name", "replicas", "labels")(Deployment.apply)
}

final case class EncodedSecret(value: String)

object EncodedSecret {
  given Encoder[EncodedSecret] = Encoder.encodeString.contramap(secret => secret.value.reverse)
}

class Circe_literal_3Test {
  @Test
  def buildsNestedObjectLiteralsAtCompileTime(): Unit = {
    val document: Json = json"""
      {
        "service": "circe-literal",
        "enabled": true,
        "retries": 3,
        "empty": null,
        "tags": ["json", "macro", "scala-3"],
        "limits": {
          "cpu": 2,
          "memory": 512.5
        }
      }
      """

    assertThat(document.isObject).isTrue
    assertThat(document.hcursor.get[String]("service")).isEqualTo(Right("circe-literal"))
    assertThat(document.hcursor.get[Boolean]("enabled")).isEqualTo(Right(true))
    assertThat(document.hcursor.get[Int]("retries")).isEqualTo(Right(3))
    assertThat(document.hcursor.downField("empty").focus.exists(_.isNull)).isTrue
    assertThat(document.hcursor.get[List[String]]("tags")).isEqualTo(Right(List("json", "macro", "scala-3")))
    assertThat(document.hcursor.downField("limits").get[BigDecimal]("memory")).isEqualTo(Right(BigDecimal("512.5")))
  }

  @Test
  def supportsTopLevelScalarAndArrayLiterals(): Unit = {
    val stringJson: Json = json""" "standalone" """
    val booleanJson: Json = json"""false"""
    val nullJson: Json = json"""null"""
    val numberJson: Json = json"""12345678901234567890.125"""
    val arrayJson: Json = json"""[1, "two", true, null, {"nested": "value"}]"""

    assertThat(stringJson.asString).isEqualTo(Some("standalone"))
    assertThat(booleanJson.asBoolean).isEqualTo(Some(false))
    assertThat(nullJson.isNull).isTrue
    assertThat(numberJson.asNumber.flatMap(_.toBigDecimal))
      .isEqualTo(Some(BigDecimal("12345678901234567890.125")))
    assertThat(arrayJson.asArray.map(_.size)).isEqualTo(Some(5))
    assertThat(arrayJson.hcursor.downN(4).get[String]("nested")).isEqualTo(Right("value"))
  }

  @Test
  def preservesEscapedStringsUnicodeAndNumericForms(): Unit = {
    val document: Json = json"""
      {
        "message": "line\nsnowman \u2603 quote \" slash /",
        "decimal": 12345678901234567890.12345,
        "exponent": -1.25e2
      }
      """

    assertThat(document.hcursor.get[String]("message")).isEqualTo(Right("line\nsnowman ☃ quote \" slash /"))
    assertThat(document.hcursor.get[BigDecimal]("decimal"))
      .isEqualTo(Right(BigDecimal("12345678901234567890.12345")))
    assertThat(document.hcursor.get[BigDecimal]("exponent")).isEqualTo(Right(BigDecimal("-125")))
  }

  @Test
  def interpolatesMapsWithCustomKeyEncodersAsJsonObjects(): Unit = {
    val replicasByProject: Map[ProjectKey, Int] = Map(
      ProjectKey("prod", "api") -> 6,
      ProjectKey("prod", "worker") -> 2
    )

    val document: Json = json"""
      {
        "replicasByProject": $replicasByProject
      }
      """

    assertThat(document.hcursor.get[Map[String, Int]]("replicasByProject"))
      .isEqualTo(Right(Map("prod/api" -> 6, "prod/worker" -> 2)))
  }

  @Test
  def interpolatesPrimitiveCollectionAndOptionalValuesThroughEncoders(): Unit = {
    val name: String = "worker-a"
    val replicas: Int = 4
    val enabled: Boolean = true
    val ratios: Vector[BigDecimal] = Vector(BigDecimal("0.25"), BigDecimal("0.75"))
    val tags: List[String] = List("blue", "stable")
    val metadata: Map[String, Int] = Map("priority" -> 7, "weight" -> 100)
    val note: Option[String] = Some("scheduled")
    val absent: Option[String] = None

    val document: Json = json"""
      {
        "name": $name,
        "replicas": $replicas,
        "enabled": $enabled,
        "ratios": $ratios,
        "tags": $tags,
        "metadata": $metadata,
        "note": $note,
        "absent": $absent
      }
      """

    assertThat(document.hcursor.get[String]("name")).isEqualTo(Right("worker-a"))
    assertThat(document.hcursor.get[Int]("replicas")).isEqualTo(Right(4))
    assertThat(document.hcursor.get[Boolean]("enabled")).isEqualTo(Right(true))
    assertThat(document.hcursor.get[Vector[BigDecimal]]("ratios")).isEqualTo(Right(ratios))
    assertThat(document.hcursor.get[List[String]]("tags")).isEqualTo(Right(tags))
    assertThat(document.hcursor.get[Map[String, Int]]("metadata")).isEqualTo(Right(metadata))
    assertThat(document.hcursor.get[Option[String]]("note")).isEqualTo(Right(note))
    assertThat(document.hcursor.downField("absent").focus.exists(_.isNull)).isTrue
  }

  @Test
  def interpolatesExistingJsonValuesWithoutStringifyingThem(): Unit = {
    val nested: Json = Json.obj(
      "kind" -> Json.fromString("embedded"),
      "active" -> Json.True,
      "values" -> Json.arr(Json.fromInt(1), Json.fromInt(2), Json.fromInt(3))
    )
    val fragments: List[Json] = List(Json.obj("id" -> Json.fromString("a")), Json.obj("id" -> Json.fromString("b")))
    val preciseNumber: Json = Json.fromJsonNumber(expectSome(JsonNumber.fromString("99999999999999999999.01")))

    val document: Json = json"""
      {
        "nested": $nested,
        "fragments": $fragments,
        "precise": $preciseNumber
      }
      """

    assertThat(document.hcursor.downField("nested").get[String]("kind")).isEqualTo(Right("embedded"))
    assertThat(document.hcursor.downField("nested").get[List[Int]]("values")).isEqualTo(Right(List(1, 2, 3)))
    assertThat(document.hcursor.get[List[Json]]("fragments")).isEqualTo(Right(fragments))
    assertThat(document.hcursor.get[BigDecimal]("precise")).isEqualTo(Right(BigDecimal("99999999999999999999.01")))
  }

  @Test
  def interpolatesObjectKeysWithPublicKeyEncoders(): Unit = {
    val primary: ProjectKey = ProjectKey("prod", "api")
    val secondary: ProjectKey = ProjectKey("prod", "worker")
    val primaryReplicas: Int = 6
    val secondaryReplicas: Int = 2

    val document: Json = json"""
      {
        $primary: $primaryReplicas,
        $secondary: $secondaryReplicas,
        "static": true
      }
      """

    assertThat(document.hcursor.get[Int]("prod/api")).isEqualTo(Right(6))
    assertThat(document.hcursor.get[Int]("prod/worker")).isEqualTo(Right(2))
    assertThat(document.hcursor.get[Boolean]("static")).isEqualTo(Right(true))
    assertThat(document.asObject.map(_.keys.toList)).isEqualTo(Some(List("prod/api", "prod/worker", "static")))
  }

  @Test
  def usesCustomEncodersForInterpolatedDomainValues(): Unit = {
    val deployment: Deployment = Deployment(
      name = "checkout",
      replicas = 3,
      labels = Map("tier" -> "backend", "language" -> "scala")
    )
    val secret: EncodedSecret = EncodedSecret("open-sesame")

    val document: Json = json"""
      {
        "deployment": $deployment,
        "history": [$deployment],
        "secret": $secret
      }
      """

    assertThat(document.hcursor.downField("deployment").get[String]("name")).isEqualTo(Right("checkout"))
    assertThat(document.hcursor.downField("deployment").get[Int]("replicas")).isEqualTo(Right(3))
    assertThat(document.hcursor.downField("deployment").get[Map[String, String]]("labels"))
      .isEqualTo(Right(deployment.labels))
    assertThat(document.hcursor.downField("history").downArray.as[Deployment]).isEqualTo(Right(deployment))
    assertThat(document.hcursor.get[String]("secret")).isEqualTo(Right("emases-nepo"))
  }

  @Test
  def rejectsMalformedJsonLiteralsAtCompileTime(): Unit = {
    val malformedLiteralTypeChecks: Boolean = typeChecks(
      "import io.circe.literal.*\nval invalidDocument = json\"{ \\\"items\\\": [1, 2, }\""
    )

    assertThat(malformedLiteralTypeChecks).isFalse
  }

  @Test
  def allowsInterpolatedScalarsAsCompleteJsonDocuments(): Unit = {
    val message: String = "line\nbreak and unicode ☕"
    val count: Long = 42L
    val enabled: Boolean = true
    val deployment: Deployment = Deployment("search", 5, Map("team" -> "platform"))

    val stringDocument: Json = json"""$message"""
    val numberDocument: Json = json"""$count"""
    val booleanDocument: Json = json"""$enabled"""
    val objectDocument: Json = json"""$deployment"""

    assertThat(stringDocument.as[String]).isEqualTo(Right(message))
    assertThat(numberDocument.as[Long]).isEqualTo(Right(42L))
    assertThat(booleanDocument.as[Boolean]).isEqualTo(Right(true))
    assertThat(objectDocument.as[Deployment]).isEqualTo(Right(deployment))
  }

  private def expectSome[A](option: Option[A]): A = {
    option.getOrElse(fail[A]("Expected value to be present"))
  }
}
