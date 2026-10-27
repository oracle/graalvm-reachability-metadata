/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_pureconfig.pureconfig_generic_scala3_3

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

import com.typesafe.config.{Config, ConfigObject, ConfigRenderOptions}
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue, fail}
import org.junit.jupiter.api.Test
import pureconfig.{ConfigConvert, ConfigReader, ConfigSource, ConfigWriter}
import pureconfig.error.{CannotConvert, ConfigReaderFailures, ConvertFailure, KeyNotFound}
import pureconfig.generic.{CoproductHint, FieldCoproductHint, FirstSuccessCoproductHint}
import pureconfig.generic.error.{NoValidCoproductOptionFound, UnexpectedValueForFieldCoproductHint}
import pureconfig.generic.semiauto.{deriveConvert, deriveReader, deriveWriter}

/**
 * Exercises coproduct derivation for Scala 3 ADT enums: enums mixing parameterized and singleton
 * cases in a deployment rollout plan are read and written as tagged coproducts (the default `type`
 * discriminator and a custom `probe` one) or untagged ones, with a hand-written instance for one
 * enum case taking precedence over derivation, and the failures an operator sees for broken cases.
 */
class DeploymentRolloutPlanTest {
  import DeploymentRolloutPlanTest.*
  import DeploymentRolloutPlanTest.given

  @Test
  def loadsServicesWhoseStrategiesProbesAndUpstreamsAreEnumCases(): Unit = {
    val plan: DeploymentPlan = ConfigSource.string(PlanConfig).at("deployment").loadOrThrow[DeploymentPlan]

    assertEquals("2026.10.3", plan.release)
    assertEquals(
      List(
        ServiceRollout(
          service = "checkout",
          upstreams = List(Upstream.Endpoint("10.0.4.17", 8443), Upstream.Discovered("pricing")),
          strategy = RolloutStrategy.Canary(initialPercent = 5, interval = 10.minutes),
          healthChecks = List(HealthCheck.Http("/healthz"), HealthCheck.Tcp(8443))
        ),
        ServiceRollout(
          service = "ledger",
          upstreams = List(Upstream.Discovered("postgres-primary", namespace = "data")),
          strategy = RolloutStrategy.BlueGreen(switchAfter = 30.minutes, keepPreviousFor = Some(2.hours)),
          healthChecks = List(HealthCheck.Exec(List("/bin/ledger-ctl", "ping"))),
          rollbackStrategy = RolloutStrategy.Canary(initialPercent = 50, stepPercent = 50, interval = 1.minute)
        ),
        ServiceRollout(
          service = "static-assets",
          upstreams = Nil,
          strategy = RolloutStrategy.AllAtOnce,
          healthChecks = List(HealthCheck.Http("/", expectedStatus = 204))
        )
      ),
      plan.services
    )
    assertEquals(List(5, 15, 25, 35, 45, 55, 65, 75, 85, 95, 100), trafficSteps(plan.services(0).strategy))
    assertEquals(List(100), trafficSteps(plan.services(1).strategy))
    assertEquals(List(50, 100), trafficSteps(plan.services(1).rollbackStrategy))
    assertEquals(List(100), trafficSteps(plan.services(2).strategy))
  }

  @Test
  def rendersEnumCasesWithTheirDiscriminatorsAndReloadsThemUnchanged(): Unit = {
    val plan: DeploymentPlan = ConfigSource.string(PlanConfig).at("deployment").loadOrThrow[DeploymentPlan]
    val rendered: ConfigObject = ConfigWriter[DeploymentPlan].to(plan).asInstanceOf[ConfigObject]
    val services: List[Config] = rendered.toConfig.getConfigList("services").asScala.toList

    val checkout: Config = services(0)
    assertEquals("canary", checkout.getString("strategy.type"))
    assertEquals(10, checkout.getInt("strategy.step-percent"))
    assertEquals("10.0.4.17:8443", checkout.getList("upstreams").get(0).unwrapped)
    val discovered: Config = checkout.getList("upstreams").get(1).asInstanceOf[ConfigObject].toConfig
    assertEquals("pricing", discovered.getString("service-name"))
    assertFalse(discovered.hasPath("type"))
    assertEquals("http", checkout.getConfigList("health-checks").get(0).getString("probe"))
    assertEquals(200, checkout.getConfigList("health-checks").get(0).getInt("expected-status"))
    assertEquals("tcp", checkout.getConfigList("health-checks").get(1).getString("probe"))
    assertEquals(Set("type"), checkout.getConfig("rollback-strategy").root().keySet().asScala.toSet)
    assertEquals("all-at-once", checkout.getString("rollback-strategy.type"))

    val ledger: Config = services(1)
    assertEquals("blue-green", ledger.getString("strategy.type"))
    assertEquals(2.hours.toMillis, ledger.getDuration("strategy.keep-previous-for").toMillis)
    assertEquals("exec", ledger.getConfigList("health-checks").get(0).getString("probe"))
    assertEquals("data", ledger.getConfigList("upstreams").get(0).getString("namespace"))

    val singleStep: ConfigObject = ConfigWriter[RolloutStrategy]
      .to(RolloutStrategy.BlueGreen(5.minutes, None))
      .asInstanceOf[ConfigObject]
    assertEquals(Set("type", "switch-after"), singleStep.keySet().asScala.toSet)

    val reloaded: DeploymentPlan =
      ConfigSource.string(rendered.render(ConfigRenderOptions.concise())).loadOrThrow[DeploymentPlan]
    assertEquals(plan, reloaded)
  }

  @Test
  def reportsUnknownCasesMissingCaseFieldsAndUnparsableUpstreams(): Unit = {
    val brokenConfig: String = """
      deployment {
        release = "2026.10.4"
        services = [
          {
            service = "checkout"
            upstreams = [ "pricing-without-port" ]
            strategy { type = rolling, batch-size = 3 }
            health-checks = [ { probe = http }, { probe = grpc, port = 9090 } ]
          }
          {
            service = "ledger"
            upstreams = []
            strategy { type = canary, initial-percent = 10 }
            health-checks = []
          }
        ]
      }
      """

    ConfigSource.string(brokenConfig).at("deployment").load[DeploymentPlan] match {
      case Right(plan) => fail(s"Expected the broken plan to be rejected, but loaded $plan")
      case Left(failures) =>
        assertTrue(
          failures.toList.exists {
            case ConvertFailure(UnexpectedValueForFieldCoproductHint(value), _, path) =>
              value.unwrapped == "rolling" && path == "deployment.services.0.strategy.type"
            case _ => false
          },
          failures.prettyPrint()
        )
        assertTrue(
          failures.toList.exists {
            case ConvertFailure(UnexpectedValueForFieldCoproductHint(value), _, path) =>
              value.unwrapped == "grpc" && path == "deployment.services.0.health-checks.1.probe"
            case _ => false
          },
          failures.prettyPrint()
        )
        assertTrue(
          failures.toList.exists {
            case ConvertFailure(KeyNotFound("path", _), _, "deployment.services.0.health-checks.0") => true
            case _ => false
          },
          failures.prettyPrint()
        )
        assertTrue(
          failures.toList.exists {
            case ConvertFailure(KeyNotFound("interval", _), _, "deployment.services.1.strategy") => true
            case _ => false
          },
          failures.prettyPrint()
        )

        val unmatched: Option[NoValidCoproductOptionFound] = failures.toList.collectFirst {
          case ConvertFailure(reason: NoValidCoproductOptionFound, _, "deployment.services.0.upstreams.0") => reason
        }
        assertTrue(unmatched.isDefined, failures.prettyPrint())
        assertEquals(List("Discovered", "Endpoint"), unmatched.get.optionFailures.map(_._1).toList)
        val endpointFailures: ConfigReaderFailures = unmatched.get.optionFailures.toMap.apply("Endpoint")
        assertTrue(
          endpointFailures.toList.exists {
            case ConvertFailure(CannotConvert("pricing-without-port", "Endpoint", _), _, _) => true
            case _ => false
          },
          endpointFailures.prettyPrint()
        )
    }
  }
}

object DeploymentRolloutPlanTest {

  /** How a release shifts traffic, tagged by the default kebab-case `type` field. */
  enum RolloutStrategy {
    case AllAtOnce
    case Canary(initialPercent: Int, stepPercent: Int = 10, interval: FiniteDuration)
    case BlueGreen(switchAfter: FiniteDuration, keepPreviousFor: Option[FiniteDuration])
  }

  given ConfigConvert[RolloutStrategy] = deriveConvert[RolloutStrategy]

  /** The traffic percentages a strategy moves through until the new version takes all of it. */
  def trafficSteps(strategy: RolloutStrategy): List[Int] = strategy match {
    case RolloutStrategy.AllAtOnce => List(100)
    case RolloutStrategy.Canary(initial, step, _) =>
      Iterator.iterate(initial)(_ + step).takeWhile(_ < 100).toList :+ 100
    case RolloutStrategy.BlueGreen(_, _) => List(100)
  }

  /** How a new instance is judged healthy, tagged by a lower-case `probe` field. */
  enum HealthCheck {
    case Http(path: String, expectedStatus: Int = 200)
    case Tcp(port: Int)
    case Exec(command: List[String])
  }

  given CoproductHint[HealthCheck] = new FieldCoproductHint[HealthCheck]("probe") {
    override protected def fieldValue(name: String): String = name.toLowerCase
  }
  given ConfigReader[HealthCheck] = deriveReader[HealthCheck]
  given ConfigWriter[HealthCheck] = deriveWriter[HealthCheck]

  /**
   * Where a service sends its traffic, written untagged: a fixed endpoint is a `host:port` string read
   * by the instances below instead of a derived one, a discovered service is an object.
   */
  enum Upstream {
    case Endpoint(host: String, port: Int)
    case Discovered(serviceName: String, namespace: String = "default")
  }

  given ConfigReader[Upstream.Endpoint] = ConfigReader[String].emap { address =>
    address.split(':') match {
      case Array(host, port) if host.nonEmpty && port.toIntOption.isDefined => Right(Upstream.Endpoint(host, port.toInt))
      case _ => Left(CannotConvert(address, "Endpoint", "expected an address of the form host:port"))
    }
  }
  given ConfigWriter[Upstream.Endpoint] = ConfigWriter[String].contramap(endpoint => s"${endpoint.host}:${endpoint.port}")

  given CoproductHint[Upstream] = new FirstSuccessCoproductHint[Upstream]
  given ConfigReader[Upstream] = deriveReader[Upstream]
  given ConfigWriter[Upstream] = deriveWriter[Upstream]

  final case class ServiceRollout(
      service: String,
      upstreams: List[Upstream],
      strategy: RolloutStrategy,
      healthChecks: List[HealthCheck],
      rollbackStrategy: RolloutStrategy = RolloutStrategy.AllAtOnce
  )

  given ConfigReader[ServiceRollout] = deriveReader[ServiceRollout]
  given ConfigWriter[ServiceRollout] = deriveWriter[ServiceRollout]

  final case class DeploymentPlan(release: String, services: List[ServiceRollout])

  given ConfigReader[DeploymentPlan] = deriveReader[DeploymentPlan]
  given ConfigWriter[DeploymentPlan] = deriveWriter[DeploymentPlan]

  val PlanConfig: String = """
    deployment {
      release = "2026.10.3"
      services = [
        {
          service = "checkout"
          upstreams = [ "10.0.4.17:8443", { service-name = "pricing" } ]
          strategy { type = canary, initial-percent = 5, interval = 10 minutes }
          health-checks = [
            { probe = http, path = "/healthz" }
            { probe = tcp, port = 8443 }
          ]
        }
        {
          service = "ledger"
          upstreams = [ { service-name = "postgres-primary", namespace = "data" } ]
          strategy { type = blue-green, switch-after = 30 minutes, keep-previous-for = 2 hours }
          health-checks = [ { probe = exec, command = ["/bin/ledger-ctl", "ping"] } ]
          rollback-strategy { type = canary, initial-percent = 50, step-percent = 50, interval = 1 minute }
        }
        {
          service = "static-assets"
          upstreams = []
          strategy { type = all-at-once }
          health-checks = [ { probe = http, path = "/", expected-status = 204 } ]
        }
      ]
    }
    """
}
