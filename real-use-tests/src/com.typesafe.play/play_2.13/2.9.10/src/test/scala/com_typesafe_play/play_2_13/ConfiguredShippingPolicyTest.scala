/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_2_13

import com.typesafe.config.ConfigFactory
import org.junit.jupiter.api.Assertions.{assertEquals, assertThrows, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import play.api.{ConfigLoader, Configuration, PlayException}

import scala.concurrent.duration._

/** Exercises Play typed configuration for a storefront's deployment-specific shipping policy. */
@Timeout(60)
class ConfiguredShippingPolicyTest {
  case class Zone(countries: Seq[String], feeCents: Int, deliveryTime: FiniteDuration)
  case class DeliveryQuote(feeCents: Int, currency: String, deliveryTime: FiniteDuration, notice: Option[String])

  case class ShippingPolicy(
      currency: String,
      freeAboveCents: Int,
      enabled: Boolean,
      notice: Option[String],
      zones: Map[String, Zone]
  ) {
    def quote(country: String, subtotalCents: Int): Either[String, DeliveryQuote] = {
      if (!enabled) Left("Shipping is temporarily unavailable")
      else zones.values.find(_.countries.contains(country)) match {
        case Some(zone) =>
          val fee: Int = if (subtotalCents >= freeAboveCents) 0 else zone.feeCents
          Right(DeliveryQuote(fee, currency, zone.deliveryTime, notice))
        case None => Left("We do not deliver to this country")
      }
    }
  }

  private implicit val zoneLoader: ConfigLoader[Zone] = ConfigLoader.configurationLoader.map { config =>
    Zone(config.get[Seq[String]]("countries"), config.get[Int]("feeCents"),
      config.get[FiniteDuration]("deliveryTime"))
  }

  private def loadPolicy(configuration: Configuration): ShippingPolicy = {
    val shipping: Configuration = configuration.get[Configuration]("shipping")
    ShippingPolicy(
      shipping.getAndValidate[String]("currency", Set("USD", "EUR")),
      shipping.get[Int]("freeAboveCents"),
      shipping.get[Boolean]("enabled"),
      shipping.getOptional[String]("notice"),
      shipping.get[Map[String, Zone]]("zones")
    )
  }

  @Test
  def appliesDeploymentOverridesAndRejectsInvalidStartupSettings(): Unit = {
    val defaults: Configuration = Configuration(ConfigFactory.parseString("""
      shipping {
        currency = "USD"
        freeAboveCents = 10000
        enabled = true
        zones {
          domestic {
            countries = ["US"]
            feeCents = 500
            deliveryTime = 2 days
          }
          international {
            countries = ["CA", "MX"]
            feeCents = 1500
            deliveryTime = 7 days
          }
        }
      }
    """))
    val standard: ShippingPolicy = loadPolicy(defaults)
    assertEquals(Right(DeliveryQuote(500, "USD", 2.days, None)), standard.quote("US", 8000))
    assertEquals(Right(DeliveryQuote(1500, "USD", 7.days, None)), standard.quote("CA", 3000))
    assertEquals(Left("We do not deliver to this country"), standard.quote("FR", 20000))

    // The deployment changes selected settings without repeating the entire zone configuration.
    val deployment: Configuration = Configuration(
      "shipping.freeAboveCents" -> 7500,
      "shipping.zones.domestic.feeCents" -> 650,
      "shipping.notice" -> "Tracked delivery included"
    ).withFallback(defaults)
    val promotional: ShippingPolicy = loadPolicy(deployment)
    val notice: Option[String] = Some("Tracked delivery included")
    assertEquals(Right(DeliveryQuote(650, "USD", 2.days, notice)), promotional.quote("US", 7499))
    assertEquals(Right(DeliveryQuote(0, "USD", 2.days, notice)), promotional.quote("US", 7500))
    assertEquals(Right(DeliveryQuote(1500, "USD", 7.days, notice)), promotional.quote("MX", 3000))
    assertEquals(Right(DeliveryQuote(500, "USD", 2.days, None)), standard.quote("US", 8000))

    val paused: ShippingPolicy = loadPolicy(
      Configuration("shipping.enabled" -> false).withFallback(deployment)
    )
    assertEquals(Left("Shipping is temporarily unavailable"), paused.quote("US", 7500))

    // Fail during settings loading, rather than publishing quotes in an unsupported currency.
    val invalid: Configuration = Configuration("shipping.currency" -> "unsupported").withFallback(defaults)
    val error: PlayException = assertThrows(classOf[PlayException], () => loadPolicy(invalid))
    assertTrue(error.getMessage.contains("USD") && error.getMessage.contains("EUR"), error.getMessage)
  }
}
