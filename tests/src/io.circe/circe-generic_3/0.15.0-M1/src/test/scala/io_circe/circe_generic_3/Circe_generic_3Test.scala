/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_generic_3

import io.circe.Codec
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.Json
import io.circe.generic.AutoDerivation
import io.circe.generic.semiauto.deriveCodec
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

final case class Money(currency: String, amount: BigDecimal)

object Money {
  given Codec.AsObject[Money] = deriveCodec[Money]
}

final case class LineItem(sku: String, quantity: Int, price: Money)

object LineItem {
  given Codec.AsObject[LineItem] = deriveCodec[LineItem]
}

final case class Customer(name: String, loyalty: Option[Int])

object Customer {
  given Codec.AsObject[Customer] = deriveCodec[Customer]
}

final case class Invoice(
    id: String,
    customer: Customer,
    items: List[LineItem],
    paid: Boolean,
    tags: Map[String, String],
    note: Option[String]
)

object Invoice {
  given Codec.AsObject[Invoice] = deriveCodec[Invoice]
}

final case class ServiceEndpoint(host: String, port: Int, secure: Boolean)

object ServiceEndpoint {
  given Encoder.AsObject[ServiceEndpoint] = deriveEncoder[ServiceEndpoint]
  given Decoder[ServiceEndpoint] = deriveDecoder[ServiceEndpoint]
}

final case class EncodedSecret(value: String)

object EncodedSecret {
  given Codec[EncodedSecret] = Codec.from(
    Decoder.decodeString.map(value => EncodedSecret(value.reverse)),
    Encoder.encodeString.contramap(secret => secret.value.reverse)
  )
}

final case class SecureNote(title: String, secret: EncodedSecret)

object SecureNote {
  given Codec.AsObject[SecureNote] = deriveCodec[SecureNote]
}

sealed trait AuditEvent

final case class UserCreated(id: String, role: String) extends AuditEvent

object UserCreated {
  given Codec.AsObject[UserCreated] = deriveCodec[UserCreated]
}

final case class PermissionChanged(id: String, enabled: Boolean) extends AuditEvent

object PermissionChanged {
  given Codec.AsObject[PermissionChanged] = deriveCodec[PermissionChanged]
}

case object SystemPaused extends AuditEvent

object AuditEvent {
  given Encoder.AsObject[AuditEvent] = deriveEncoder[AuditEvent]
  given Decoder[AuditEvent] = deriveDecoder[AuditEvent]
}

enum ReleaseStage {
  case Planned
  case Building(buildId: String, attempts: Int)
  case Published(version: String, notes: Option[String])
}

object ReleaseStage {
  given Codec.AsObject[ReleaseStage] = deriveCodec[ReleaseStage]
}

final case class AutoAddress(city: String, postalCode: String)

final case class AutoOwner(name: String, active: Boolean)

final case class AutoRegistration(owner: AutoOwner, addresses: List[AutoAddress], flags: Map[String, Boolean])

final case class AutoEnvelope(registration: AutoRegistration, priority: Int)

class Circe_generic_3Test {
  @Test
  def derivesSemiautomaticCodecsForNestedProducts(): Unit = {
    val invoice: Invoice = Invoice(
      id = "invoice-1",
      customer = Customer("Ada Lovelace", Some(7)),
      items = List(
        LineItem("book", 2, Money("EUR", BigDecimal("12.50"))),
        LineItem("pen", 5, Money("EUR", BigDecimal("1.20")))
      ),
      paid = false,
      tags = Map("region" -> "emea", "channel" -> "direct"),
      note = Some("deliver before noon")
    )

    val json: Json = invoice.asJson

    assertThat(json.hcursor.get[String]("id")).isEqualTo(Right("invoice-1"))
    assertThat(json.hcursor.downField("customer").get[String]("name")).isEqualTo(Right("Ada Lovelace"))
    assertThat(json.hcursor.downField("customer").get[Option[Int]]("loyalty")).isEqualTo(Right(Some(7)))
    assertThat(json.hcursor.downField("items").downArray.get[String]("sku")).isEqualTo(Right("book"))
    assertThat(json.hcursor.downField("tags").get[String]("region")).isEqualTo(Right("emea"))
    assertThat(json.as[Invoice]).isEqualTo(Right(invoice))
  }

  @Test
  def reportsAccumulatedFailuresFromDerivedProductDecoders(): Unit = {
    val invalid: Json = Json.obj(
      "id" -> Json.fromInt(100),
      "customer" -> Json.obj(
        "name" -> Json.fromBoolean(true),
        "loyalty" -> Json.fromString("frequent")
      ),
      "items" -> Json.arr(
        Json.obj(
          "sku" -> Json.fromString("book"),
          "quantity" -> Json.fromString("many"),
          "price" -> Json.obj("currency" -> Json.fromInt(10), "amount" -> Json.fromString("free"))
        )
      ),
      "paid" -> Json.fromString("no"),
      "tags" -> Json.arr(Json.fromString("not-an-object")),
      "note" -> Json.fromInt(123)
    )

    val failures: List[DecodingFailure] = summon[Decoder[Invoice]].decodeAccumulating(invalid.hcursor).fold(
      _.toList,
      value => fail(s"Expected derived invoice decoding to fail, but decoded: $value")
    )

    assertThat(failures.size).isGreaterThanOrEqualTo(3)
    assertThat(failures.map(_.history.nonEmpty).asJava).contains(true)
  }

  @Test
  def derivesSeparateSemiautomaticEncodersAndDecoders(): Unit = {
    val endpoint: ServiceEndpoint = ServiceEndpoint("api.example.test", 8443, secure = true)
    val json: Json = endpoint.asJson

    assertThat(json.noSpacesSortKeys).isEqualTo("""{"host":"api.example.test","port":8443,"secure":true}""")
    assertThat(json.as[ServiceEndpoint]).isEqualTo(Right(endpoint))

    val invalidPort: Either[DecodingFailure, ServiceEndpoint] = Json.obj(
      "host" -> Json.fromString("api.example.test"),
      "port" -> Json.fromString("https"),
      "secure" -> Json.True
    ).as[ServiceEndpoint]
    assertThat(invalidPort.isLeft).isTrue
  }

  @Test
  def derivedProductCodecsUseCustomMemberCodecs(): Unit = {
    val note: SecureNote = SecureNote("deployment", EncodedSecret("open-sesame"))
    val json: Json = note.asJson

    assertThat(json.hcursor.get[String]("title")).isEqualTo(Right("deployment"))
    assertThat(json.hcursor.get[String]("secret")).isEqualTo(Right("emases-nepo"))
    assertThat(json.as[SecureNote]).isEqualTo(Right(note))
  }

  @Test
  def derivesSemiautomaticCodecsForSealedHierarchies(): Unit = {
    val events: List[AuditEvent] = List(
      UserCreated("user-1", "admin"),
      PermissionChanged("user-1", enabled = false),
      SystemPaused
    )

    val json: Json = events.asJson
    val decoded: List[AuditEvent] = expectRight(json.as[List[AuditEvent]])

    assertThat(decoded.asJava).containsExactlyElementsOf(events.asJava)
    assertThat(json.asArray.map(_.size)).isEqualTo(Some(3))
  }

  @Test
  def derivesSemiautomaticCodecsForScala3Enums(): Unit = {
    val stages: List[ReleaseStage] = List(
      ReleaseStage.Planned,
      ReleaseStage.Building("build-42", attempts = 2),
      ReleaseStage.Published("stable", Some("ready for deployment"))
    )

    val json: Json = stages.asJson
    val decoded: List[ReleaseStage] = expectRight(json.as[List[ReleaseStage]])

    assertThat(json.asArray.map(_.size)).isEqualTo(Some(3))
    assertThat(decoded.asJava).containsExactlyElementsOf(stages.asJava)
  }

  @Test
  def fullyAutomaticDerivationSuppliesCodecsAtUseSite(): Unit = {
    import io.circe.generic.auto.*

    val registration: AutoRegistration = AutoRegistration(
      owner = AutoOwner("Grace Hopper", active = true),
      addresses = List(AutoAddress("Arlington", "22201"), AutoAddress("New York", "10001")),
      flags = Map("email" -> true, "sms" -> false)
    )
    val envelope: AutoEnvelope = AutoEnvelope(registration, priority = 3)

    val json: Json = envelope.asJson

    assertThat(json.hcursor.downField("registration").downField("owner").get[String]("name"))
      .isEqualTo(Right("Grace Hopper"))
    assertThat(json.hcursor.downField("registration").downField("addresses").downN(1).get[String]("postalCode"))
      .isEqualTo(Right("10001"))
    assertThat(json.hcursor.downField("registration").downField("flags").get[Boolean]("email"))
      .isEqualTo(Right(true))
    assertThat(json.as[AutoEnvelope]).isEqualTo(Right(envelope))
  }

  @Test
  def exposesGenericDerivationModulesThroughTheirPublicApi(): Unit = {
    val autoModule: AutoDerivation = io.circe.generic.auto
    val semiautoModule: AnyRef = io.circe.generic.semiauto

    assertThat(autoModule).isSameAs(io.circe.generic.auto)
    assertThat(semiautoModule).isSameAs(io.circe.generic.semiauto)
  }

  private def expectRight[A](result: Either[DecodingFailure, A]): A = {
    result.fold(error => fail(s"Expected decoding success but got: $error"), identity)
  }
}
