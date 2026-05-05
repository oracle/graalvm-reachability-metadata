/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_pureconfig.pureconfig_generic_scala3_3

import com.typesafe.config.{Config, ConfigObject}
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue, fail}
import org.junit.jupiter.api.Test
import pureconfig.{CamelCase, ConfigFieldMapping, ConfigReader, ConfigSource, ConfigWriter, SnakeCase}
import pureconfig.error.{ConvertFailure, UnknownKey}
import pureconfig.generic.{CoproductHint, FieldCoproductHint, ProductHint}
import pureconfig.generic.semiauto.{deriveEnumerationConvert, deriveReader, deriveWriter}

object PureconfigGenericScala3Models {
  final class Port(val value: Int) extends AnyVal

  given ConfigReader[Port] = deriveReader[Port]
  given ConfigWriter[Port] = deriveWriter[Port]

  final case class Endpoint(host: String, port: Port)

  given ConfigReader[Endpoint] = deriveReader[Endpoint]
  given ConfigWriter[Endpoint] = deriveWriter[Endpoint]

  final case class ServiceSettings(
      name: String,
      endpoint: Endpoint,
      retryCount: Int = 3,
      tags: List[String] = Nil,
      limits: (Int, String),
      features: Map[String, Boolean]
  )

  given ConfigReader[ServiceSettings] = deriveReader[ServiceSettings]
  given ConfigWriter[ServiceSettings] = deriveWriter[ServiceSettings]

  final case class MaintenanceWindow(day: String, hour: Option[Int], approvers: Option[List[String]])

  given ConfigReader[MaintenanceWindow] = deriveReader[MaintenanceWindow]
  given ConfigWriter[MaintenanceWindow] = deriveWriter[MaintenanceWindow]

  final case class StrictFeature(maxConnections: Int, enabledByDefault: Boolean = true)

  given ProductHint[StrictFeature] = ProductHint[StrictFeature](
    fieldMapping = ConfigFieldMapping(CamelCase, SnakeCase),
    useDefaultArgs = true,
    allowUnknownKeys = false
  )
  given ConfigReader[StrictFeature] = deriveReader[StrictFeature]
  given ConfigWriter[StrictFeature] = deriveWriter[StrictFeature]

  sealed trait Shape
  final case class Circle(radius: Double) extends Shape
  final case class Rectangle(width: Double, height: Double) extends Shape

  given CoproductHint[Shape] = new FieldCoproductHint[Shape]("kind")
  given ConfigReader[Circle] = deriveReader[Circle]
  given ConfigWriter[Circle] = deriveWriter[Circle]
  given ConfigReader[Rectangle] = deriveReader[Rectangle]
  given ConfigWriter[Rectangle] = deriveWriter[Rectangle]
  given ConfigReader[Shape] = deriveReader[Shape]
  given ConfigWriter[Shape] = deriveWriter[Shape]

  enum DeploymentMode {
    case BlueGreen, Canary
  }

  given pureconfig.ConfigConvert[DeploymentMode] = deriveEnumerationConvert[DeploymentMode]

  final case class Release(mode: DeploymentMode)

  given ConfigReader[Release] = deriveReader[Release]
  given ConfigWriter[Release] = deriveWriter[Release]
}

class Pureconfig_generic_scala3_3Test {
  import PureconfigGenericScala3Models.*
  import PureconfigGenericScala3Models.given

  @Test
  def readsNestedProductsDefaultsAnyValsCollectionsAndTuples(): Unit = {
    val config: String = """
      service {
        name = "orders"
        endpoint {
          host = "127.0.0.1"
          port = 8443
        }
        tags = ["http", "public"]
        limits = [10, "burst"]
        features {
          tracing = true
          metrics = false
        }
      }
      """

    val settings: ServiceSettings = ConfigSource.string(config).at("service").loadOrThrow[ServiceSettings]

    assertEquals("orders", settings.name)
    assertEquals("127.0.0.1", settings.endpoint.host)
    assertEquals(8443, settings.endpoint.port.value)
    assertEquals(3, settings.retryCount)
    assertEquals(List("http", "public"), settings.tags)
    assertEquals((10, "burst"), settings.limits)
    assertEquals(Map("tracing" -> true, "metrics" -> false), settings.features)
  }

  @Test
  def readsAndWritesOptionalProductFields(): Unit = {
    val window: MaintenanceWindow = ConfigSource
      .string("""
        day = "sunday"
        approvers = ["ops", "security"]
        """)
      .loadOrThrow[MaintenanceWindow]

    assertEquals(MaintenanceWindow("sunday", None, Some(List("ops", "security"))), window)

    val writtenWindow: MaintenanceWindow = MaintenanceWindow("monday", Some(2), None)
    val written: ConfigObject = ConfigWriter[MaintenanceWindow].to(writtenWindow).asInstanceOf[ConfigObject]

    assertEquals("monday", written.toConfig.getString("day"))
    assertEquals(2, written.toConfig.getInt("hour"))

    val roundTripped: MaintenanceWindow = ConfigSource.fromConfig(written.toConfig).loadOrThrow[MaintenanceWindow]
    assertEquals(writtenWindow, roundTripped)
  }

  @Test
  def writesNestedProductsUsingConfiguredGenericWriters(): Unit = {
    val settings: ServiceSettings = ServiceSettings(
      name = "billing",
      endpoint = Endpoint("localhost", new Port(9000)),
      retryCount = 4,
      tags = List("internal"),
      limits = (5, "steady"),
      features = Map("audit" -> true)
    )

    val written: ConfigObject = ConfigWriter[ServiceSettings].to(settings).asInstanceOf[ConfigObject]
    val renderedConfig: Config = written.toConfig

    assertEquals("billing", renderedConfig.getString("name"))
    assertEquals("localhost", renderedConfig.getString("endpoint.host"))
    assertEquals(9000, renderedConfig.getInt("endpoint.port"))
    assertEquals(4, renderedConfig.getInt("retry-count"))
    assertFalse(written.containsKey("retryCount"))

    val roundTripped: ServiceSettings = ConfigSource.fromConfig(renderedConfig).loadOrThrow[ServiceSettings]
    assertEquals(settings.name, roundTripped.name)
    assertEquals(settings.endpoint.host, roundTripped.endpoint.host)
    assertEquals(settings.endpoint.port.value, roundTripped.endpoint.port.value)
    assertEquals(settings.retryCount, roundTripped.retryCount)
    assertEquals(settings.tags, roundTripped.tags)
    assertEquals(settings.limits, roundTripped.limits)
    assertEquals(settings.features, roundTripped.features)
  }

  @Test
  def honorsCustomProductHintForSnakeCaseDefaultsAndUnknownKeys(): Unit = {
    val feature: StrictFeature = ConfigSource
      .string("max_connections = 32")
      .loadOrThrow[StrictFeature]

    assertEquals(StrictFeature(maxConnections = 32, enabledByDefault = true), feature)

    val written: ConfigObject = ConfigWriter[StrictFeature]
      .to(feature.copy(enabledByDefault = false))
      .asInstanceOf[ConfigObject]
    assertEquals(32, written.toConfig.getInt("max_connections"))
    assertFalse(written.toConfig.getBoolean("enabled_by_default"))
    assertFalse(written.containsKey("maxConnections"))

    val invalidResult: ConfigReader.Result[StrictFeature] = ConfigSource
      .string("""
        max_connections = 32
        extra_value = "not allowed"
        """)
      .load[StrictFeature]

    invalidResult match {
      case Left(failures) =>
        val hasUnknownKeyFailure: Boolean = failures.toList.exists {
          case ConvertFailure(UnknownKey("extra_value"), _, "extra_value") => true
          case _ => false
        }
        assertTrue(hasUnknownKeyFailure, failures.prettyPrint())
      case Right(value) => fail(s"Expected an unknown-key failure, but loaded $value")
    }
  }

  @Test
  def readsAndWritesSealedFamiliesWithFieldCoproductHint(): Unit = {
    val shapes: List[Shape] = ConfigSource
      .string("""
        shapes = [
          { kind = "circle", radius = 2.5 },
          { kind = "rectangle", width = 4.0, height = 3.0 }
        ]
        """)
      .at("shapes")
      .loadOrThrow[List[Shape]]

    assertEquals(List(Circle(2.5), Rectangle(4.0, 3.0)), shapes)

    val written: ConfigObject = ConfigWriter[Shape].to(Rectangle(6.0, 2.0)).asInstanceOf[ConfigObject]
    assertEquals("rectangle", written.toConfig.getString("kind"))
    assertEquals(6.0, written.toConfig.getDouble("width"), 0.000001)
    assertEquals(2.0, written.toConfig.getDouble("height"), 0.000001)

    val roundTripped: Shape = ConfigSource.fromConfig(written.toConfig).loadOrThrow[Shape]
    assertEquals(Rectangle(6.0, 2.0), roundTripped)
  }

  @Test
  def derivesEnumConvertersWithKebabCaseNames(): Unit = {
    val release: Release = ConfigSource
      .string("mode = blue-green")
      .loadOrThrow[Release]

    assertEquals(DeploymentMode.BlueGreen, release.mode)

    val written: ConfigObject = ConfigWriter[Release].to(Release(DeploymentMode.Canary)).asInstanceOf[ConfigObject]
    assertEquals("canary", written.toConfig.getString("mode"))

    val roundTripped: Release = ConfigSource.fromConfig(written.toConfig).loadOrThrow[Release]
    assertEquals(Release(DeploymentMode.Canary), roundTripped)
  }
}
