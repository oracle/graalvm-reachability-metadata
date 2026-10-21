/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_pureconfig.pureconfig_generic_scala3_3

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import scala.concurrent.duration.*

import com.typesafe.config.{Config, ConfigObject}
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue, fail}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import pureconfig.{CamelCase, ConfigFieldMapping, ConfigReader, ConfigSource, ConfigWriter, KebabCase, PascalCase, SnakeCase}
import pureconfig.error.{ConfigReaderFailures, ConvertFailure, KeyNotFound, UnknownKey}
import pureconfig.generic.ProductHint
import pureconfig.generic.semiauto.{deriveReader, deriveWriter}

/**
 * Exercises hints-aware product derivation: semi-automatically derived readers and writers for a
 * payment gateway's nested case-class settings, loaded from an `application.conf` on disk layered
 * over bundled defaults, with per-type `ProductHint`s for field naming, default arguments and
 * strict unknown-key checking, and the derived writer rendering the effective settings back out.
 */
class PaymentGatewaySettingsTest {
  import PaymentGatewaySettingsTest.*
  import PaymentGatewaySettingsTest.given

  @TempDir
  var configDir: Path = null

  @Test
  def loadsDeployedSettingsLayeredOverBundledDefaults(): Unit = {
    val settings: GatewaySettings = loadSettings(writeDeployedConfig(DeployedConfig))

    assertEquals("payments-eu", settings.serviceName)
    assertEquals(HttpSettings("0.0.0.0", new Port(8443), 512), settings.http)
    assertEquals(2.seconds, settings.timeouts.connect)
    assertEquals(45.seconds, settings.timeouts.read)
    assertEquals(RetryPolicy(maxAttempts = 5, backoff = 250.millis, retryOn = List(502, 503, 504)), settings.retry)
    assertEquals(
      List(
        Merchant(new MerchantId("m-100"), "Acme Books", List("EUR", "USD"), Some(BigDecimal("0.0150"))),
        Merchant(new MerchantId("m-200"), "Globex", List("GBP"), None)
      ),
      settings.merchants
    )
    assertEquals(Map("EUR" -> BigDecimal("1.00"), "GBP" -> BigDecimal("0.50")), settings.minimumCharge)
    assertEquals(None, settings.auditSink)
    assertEquals(LegacyBridge(apiKeyHeader = "X-Legacy-Key", enabled = true), settings.legacyBridge)
  }

  @Test
  def reportsEveryMisconfiguredFieldWithItsPath(): Unit = {
    val brokenConfig: String = """
      payments {
        service-name = "payments-eu"
        http { host = "0.0.0.0", port = "not-a-port" }
        merchants = [ { merchant-id = "m-100", currencies = [EUR] } ]
        legacy_bridge { ApiKeyHeader = "X-Legacy-Key", ApiSecret = "leaked" }
      }
      """
    val result: ConfigReader.Result[GatewaySettings] = sourceFor(writeDeployedConfig(brokenConfig))
      .load[GatewaySettings]

    result match {
      case Right(settings) => fail(s"Expected the broken deployment to be rejected, but loaded $settings")
      case Left(failures) =>
        val paths: Set[String] = failurePaths(failures)
        assertTrue(paths.contains("payments.http.port"), failures.prettyPrint())
        assertTrue(
          failures.toList.exists {
            case ConvertFailure(KeyNotFound("display-name", _), _, "payments.merchants.0") => true
            case _ => false
          },
          failures.prettyPrint()
        )
        assertTrue(
          failures.toList.exists {
            case ConvertFailure(UnknownKey("ApiSecret"), _, path) => path.endsWith("legacy_bridge.ApiSecret")
            case _ => false
          },
          failures.prettyPrint()
        )
    }
  }

  @Test
  def rendersEffectiveSettingsThatReloadUnchanged(): Unit = {
    val settings: GatewaySettings = loadSettings(writeDeployedConfig(DeployedConfig))
    val rendered: ConfigObject = ConfigWriter[GatewaySettings].to(settings).asInstanceOf[ConfigObject]
    val renderedConfig: Config = rendered.toConfig

    assertEquals("payments-eu", renderedConfig.getString("service-name"))
    assertEquals(8443, renderedConfig.getInt("http.port"))
    assertEquals(5, renderedConfig.getInt("retry.max-attempts"))
    assertEquals("m-200", renderedConfig.getConfigList("merchants").get(1).getString("merchant-id"))
    assertFalse(renderedConfig.getConfigList("merchants").get(1).hasPath("fee-rate"))
    assertFalse(renderedConfig.hasPath("audit-sink"))
    assertEquals("X-Legacy-Key", renderedConfig.getString("legacy_bridge.ApiKeyHeader"))
    assertFalse(rendered.containsKey("serviceName"))

    val effectiveFile: Path = configDir.resolve("effective.conf")
    Files.writeString(effectiveFile, rendered.render(), StandardCharsets.UTF_8)
    val reloaded: GatewaySettings = ConfigSource.file(effectiveFile).loadOrThrow[GatewaySettings]
    assertEquals(settings, reloaded)
  }

  private def writeDeployedConfig(content: String): Path = {
    val file: Path = configDir.resolve("application.conf")
    Files.writeString(file, content, StandardCharsets.UTF_8)
    file
  }

  private def sourceFor(deployedFile: Path): ConfigSource =
    ConfigSource.file(deployedFile).withFallback(ConfigSource.string(BundledDefaults)).at("payments")

  private def loadSettings(deployedFile: Path): GatewaySettings =
    sourceFor(deployedFile).loadOrThrow[GatewaySettings]

  private def failurePaths(failures: ConfigReaderFailures): Set[String] =
    failures.toList.collect { case ConvertFailure(_, _, path) => path }.toSet
}

object PaymentGatewaySettingsTest {
  final class Port(val value: Int) extends AnyVal {
    override def toString: String = s"Port($value)"
  }

  given ConfigReader[Port] = deriveReader[Port]
  given ConfigWriter[Port] = deriveWriter[Port]

  final class MerchantId(val value: String) extends AnyVal {
    override def toString: String = s"MerchantId($value)"
  }

  given ConfigReader[MerchantId] = deriveReader[MerchantId]
  given ConfigWriter[MerchantId] = deriveWriter[MerchantId]

  final case class HttpSettings(host: String, port: Port, maxConnections: Int = 256)

  given ConfigReader[HttpSettings] = deriveReader[HttpSettings]
  given ConfigWriter[HttpSettings] = deriveWriter[HttpSettings]

  final case class Timeouts(connect: FiniteDuration, read: FiniteDuration)

  given ConfigReader[Timeouts] = deriveReader[Timeouts]
  given ConfigWriter[Timeouts] = deriveWriter[Timeouts]

  final case class RetryPolicy(maxAttempts: Int, backoff: FiniteDuration, retryOn: List[Int] = List(502, 503, 504))

  given ConfigReader[RetryPolicy] = deriveReader[RetryPolicy]
  given ConfigWriter[RetryPolicy] = deriveWriter[RetryPolicy]

  final case class Merchant(
      merchantId: MerchantId,
      displayName: String,
      currencies: List[String],
      feeRate: Option[BigDecimal]
  )

  given ConfigReader[Merchant] = deriveReader[Merchant]
  given ConfigWriter[Merchant] = deriveWriter[Merchant]

  /** Settings shared with a legacy system that spells its keys in PascalCase and rejects typos. */
  final case class LegacyBridge(apiKeyHeader: String, enabled: Boolean = false)

  given ProductHint[LegacyBridge] = ProductHint[LegacyBridge](
    fieldMapping = ConfigFieldMapping(CamelCase, PascalCase),
    useDefaultArgs = true,
    allowUnknownKeys = false
  )
  given ConfigReader[LegacyBridge] = deriveReader[LegacyBridge]
  given ConfigWriter[LegacyBridge] = deriveWriter[LegacyBridge]

  final case class GatewaySettings(
      serviceName: String,
      http: HttpSettings,
      timeouts: Timeouts,
      retry: RetryPolicy,
      merchants: List[Merchant],
      minimumCharge: Map[String, BigDecimal],
      auditSink: Option[String],
      legacyBridge: LegacyBridge
  )

  /** The top-level settings keep kebab-case keys but name the legacy block in snake_case. */
  given ProductHint[GatewaySettings] = ProductHint[GatewaySettings](
    fieldMapping = {
      val kebab: ConfigFieldMapping = ConfigFieldMapping(CamelCase, KebabCase)
      val snake: ConfigFieldMapping = ConfigFieldMapping(CamelCase, SnakeCase)
      (field: String) => if (field == "legacyBridge") snake(field) else kebab(field)
    }
  )
  given ConfigReader[GatewaySettings] = deriveReader[GatewaySettings]
  given ConfigWriter[GatewaySettings] = deriveWriter[GatewaySettings]

  val BundledDefaults: String = """
    payments {
      http { host = "127.0.0.1", port = 8080, max-connections = 128 }
      timeouts { connect = 2 seconds, read = 30 seconds }
      retry { max-attempts = 3, backoff = 250 ms }
      minimum-charge { EUR = 1.00 }
      legacy_bridge { ApiKeyHeader = "X-Legacy-Key" }
    }
    """

  val DeployedConfig: String = """
    payments {
      service-name = "payments-eu"
      http { host = "0.0.0.0", port = 8443, max-connections = 512 }
      timeouts.read = 45s
      retry.max-attempts = 5
      merchants = [
        { merchant-id = "m-100", display-name = "Acme Books", currencies = [EUR, USD], fee-rate = 0.0150 },
        { merchant-id = "m-200", display-name = "Globex", currencies = [GBP] }
      ]
      minimum-charge.GBP = 0.50
      legacy_bridge.Enabled = true
    }
    """
}
