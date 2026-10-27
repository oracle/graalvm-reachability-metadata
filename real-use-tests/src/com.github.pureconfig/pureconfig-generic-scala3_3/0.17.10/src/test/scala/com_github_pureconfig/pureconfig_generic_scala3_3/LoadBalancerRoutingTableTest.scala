/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_pureconfig.pureconfig_generic_scala3_3

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

import com.typesafe.config.{Config, ConfigList, ConfigObject, ConfigRenderOptions, ConfigValueType}
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue, fail}
import org.junit.jupiter.api.Test
import pureconfig.{ConfigReader, ConfigSource, ConfigWriter}
import pureconfig.error.{ConfigReaderFailures, ConvertFailure, WrongSizeList, WrongType}
import pureconfig.generic.semiauto.{deriveReader, deriveWriter}

/**
 * Exercises tuple derivation: Scala tuples in a load balancer's routing table are read from and
 * written to positional HOCON lists by derived readers and writers, both as explicitly derived
 * givens (weighted backends, the canary target, a standalone maintenance window) and as tuple
 * fields that case-class derivation derives inline (port ranges, retry policies with a default),
 * with the failures an operator sees for lists of the wrong size or shape.
 */
class LoadBalancerRoutingTableTest {
  import LoadBalancerRoutingTableTest.*
  import LoadBalancerRoutingTableTest.given

  @Test
  def loadsRoutesWhoseBackendsRangesAndRetriesArePositionalLists(): Unit = {
    val table: RoutingTable = ConfigSource.string(RoutingConfig).at("routing").loadOrThrow[RoutingTable]

    assertEquals((8000, 8099), table.listenPorts)
    assertEquals(
      List(
        Route(
          pathPrefix = "/api",
          backends = List((Endpoint("10.0.0.11", 8080), 3), (Endpoint("10.0.0.12", 8080), 1)),
          portRange = (8080, 8081),
          retry = (250.millis, 3)
        ),
        Route(
          pathPrefix = "/static",
          backends = List((Endpoint("cdn-origin.internal", 443), 1)),
          portRange = (443, 443)
        )
      ),
      table.routes
    )
    assertEquals((100.millis, 1), table.routes(1).retry)
    assertEquals(Some(("10.0.0.99", 0.05)), table.canary)

    val totalApiWeight: Int = table.routes.head.backends.map(_._2).sum
    assertEquals(4, totalApiWeight)
  }

  @Test
  def rendersTuplesAsListsThatReloadUnchanged(): Unit = {
    val table: RoutingTable = ConfigSource.string(RoutingConfig).at("routing").loadOrThrow[RoutingTable]
    val rendered: ConfigObject = ConfigWriter[RoutingTable].to(table).asInstanceOf[ConfigObject]
    val renderedConfig: Config = rendered.toConfig

    assertEquals(List(8000, 8099), renderedConfig.getIntList("listen-ports").asScala.toList.map(_.intValue))
    assertEquals(List("10.0.0.99", 0.05), renderedConfig.getList("canary").unwrapped.asScala.toList)

    val apiRoute: Config = renderedConfig.getConfigList("routes").get(0)
    assertEquals(List(8080, 8081), apiRoute.getIntList("port-range").asScala.toList.map(_.intValue))
    val firstBackend: ConfigList = apiRoute.getList("backends").get(0).asInstanceOf[ConfigList]
    assertEquals(2, firstBackend.size)
    assertEquals(ConfigValueType.OBJECT, firstBackend.get(0).valueType)
    assertEquals("10.0.0.11", firstBackend.get(0).asInstanceOf[ConfigObject].toConfig.getString("host"))
    assertEquals(3, firstBackend.get(1).unwrapped)
    assertEquals(ConfigValueType.LIST, apiRoute.getValue("retry").valueType)

    val reloaded: RoutingTable =
      ConfigSource.string(rendered.render(ConfigRenderOptions.concise())).loadOrThrow[RoutingTable]
    assertEquals(table, reloaded)
  }

  @Test
  def loadsAndWritesAStandaloneTupleThroughDerivedInstances(): Unit = {
    val window: MaintenanceWindow =
      ConfigSource.string("""maintenance-window = ["02:30", 90 minutes, true]""")
        .at("maintenance-window")
        .loadOrThrow[MaintenanceWindow]

    assertEquals(("02:30", 90.minutes, true), window)

    val written: ConfigList = ConfigWriter[MaintenanceWindow].to(("04:00", 15.minutes, false)).asInstanceOf[ConfigList]
    assertEquals(3, written.size)
    assertEquals("04:00", written.get(0).unwrapped)
    assertEquals(false, written.get(2).unwrapped)
    assertEquals(
      ("04:00", 15.minutes, false),
      ConfigReader[MaintenanceWindow].from(written).fold(f => fail(f.prettyPrint()), identity)
    )
  }

  @Test
  def reportsListsOfTheWrongSizeOrShape(): Unit = {
    val brokenConfig: String = """
      routing {
        listen-ports = [8000]
        routes = [
          {
            path-prefix = "/api"
            backends = [
              [{ host = "10.0.0.11", port = 8080 }, "heavy"]
              [{ host = "10.0.0.12", port = 8080 }]
            ]
            port-range { from = 8080, to = 8081 }
          }
        ]
        canary = ["10.0.0.99", 0.05, "extra"]
      }
      """

    ConfigSource.string(brokenConfig).at("routing").load[RoutingTable] match {
      case Right(table) => fail(s"Expected the broken routing table to be rejected, but loaded $table")
      case Left(failures) =>
        val sizeFailures: Map[String, WrongSizeList] = failures.toList.collect {
          case ConvertFailure(reason: WrongSizeList, _, path) => path -> reason
        }.toMap
        assertEquals(
          Map(
            "routing.listen-ports" -> WrongSizeList(2, 1),
            "routing.routes.0.backends.1" -> WrongSizeList(2, 1),
            "routing.canary" -> WrongSizeList(2, 3)
          ),
          sizeFailures,
          failures.prettyPrint()
        )
        assertTrue(
          failures.toList.exists {
            case ConvertFailure(WrongType(ConfigValueType.OBJECT, expected), _, "routing.routes.0.port-range") =>
              expected.contains(ConfigValueType.LIST)
            case _ => false
          },
          failures.prettyPrint()
        )
        assertTrue(failurePaths(failures).contains("routing.routes.0.backends.0.1"), failures.prettyPrint())
    }
  }

  private def failurePaths(failures: ConfigReaderFailures): Set[String] =
    failures.toList.collect { case ConvertFailure(_, _, path) => path }.toSet
}

object LoadBalancerRoutingTableTest {

  final case class Endpoint(host: String, port: Int)

  given ConfigReader[Endpoint] = deriveReader[Endpoint]
  given ConfigWriter[Endpoint] = deriveWriter[Endpoint]

  /** A backend and its relative weight, written as `[{ host, port }, weight]`. */
  type WeightedBackend = (Endpoint, Int)

  given ConfigReader[WeightedBackend] = deriveReader[WeightedBackend]
  given ConfigWriter[WeightedBackend] = deriveWriter[WeightedBackend]

  /** The host and the share of traffic diverted to a canary, written as `[host, share]`. */
  type Canary = (String, Double)

  given ConfigReader[Canary] = deriveReader[Canary]
  given ConfigWriter[Canary] = deriveWriter[Canary]

  /** Start time, length and whether traffic is drained, written as `[start, duration, drain]`. */
  type MaintenanceWindow = (String, FiniteDuration, Boolean)

  given ConfigReader[MaintenanceWindow] = deriveReader[MaintenanceWindow]
  given ConfigWriter[MaintenanceWindow] = deriveWriter[MaintenanceWindow]

  /** A route; its tuple fields have no given instances and are derived along with the route. */
  final case class Route(
      pathPrefix: String,
      backends: List[WeightedBackend],
      portRange: (Int, Int),
      retry: (FiniteDuration, Int) = (100.millis, 1)
  )

  given ConfigReader[Route] = deriveReader[Route]
  given ConfigWriter[Route] = deriveWriter[Route]

  final case class RoutingTable(listenPorts: (Int, Int), routes: List[Route], canary: Option[Canary])

  given ConfigReader[RoutingTable] = deriveReader[RoutingTable]
  given ConfigWriter[RoutingTable] = deriveWriter[RoutingTable]

  val RoutingConfig: String = """
    routing {
      listen-ports = [8000, 8099]
      routes = [
        {
          path-prefix = "/api"
          backends = [
            [{ host = "10.0.0.11", port = 8080 }, 3]
            [{ host = "10.0.0.12", port = 8080 }, 1]
          ]
          port-range = [8080, 8081]
          retry = [250 ms, 3]
        }
        {
          path-prefix = "/static"
          backends = [ [{ host = "cdn-origin.internal", port = 443 }, 1] ]
          port-range = [443, 443]
        }
      ]
      canary = ["10.0.0.99", 0.05]
    }
    """
}
