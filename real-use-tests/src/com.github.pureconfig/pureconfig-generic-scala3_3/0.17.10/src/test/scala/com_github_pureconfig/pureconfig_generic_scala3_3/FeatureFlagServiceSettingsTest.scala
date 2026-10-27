/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_pureconfig.pureconfig_generic_scala3_3

import scala.concurrent.duration.*

import cats.data.{NonEmptyChain, NonEmptyMap, NonEmptySet, NonEmptyVector}
import cats.syntax.all.*
import com.typesafe.config.{ConfigObject, ConfigRenderOptions, ConfigValue}
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue, fail}
import org.junit.jupiter.api.Test
import pureconfig.{ConfigObjectSource, ConfigReader, ConfigSource, ConfigWriter}
import pureconfig.error.{ConfigReaderFailures, ConvertFailure, KeyNotFound}
import pureconfig.generic.semiauto.{deriveReader, deriveWriter}
import pureconfig.module.cats.*
import pureconfig.module.cats.instances.*

/**
 * Exercises derived instances composed through cats type classes: semiauto-derived readers for two
 * views of one flat HOCON object joined with `mapN`, their derived writers joined with `tupled` and
 * `contramap`, derived feature flags holding `NonEmptySet`, `NonEmptyMap`, `NonEmptyChain` and
 * `NonEmptyVector` fields, configuration layers stacked with the `ConfigObjectSource` monoid, and the
 * failures of independent sections merged with the `ConfigReaderFailures` semigroup.
 */
class FeatureFlagServiceSettingsTest {
  import FeatureFlagServiceSettingsTest.*
  import FeatureFlagServiceSettingsTest.given

  @Test
  def loadsFlagsFromLayeredSourcesWithOverridesTakingPrecedence(): Unit = {
    val settings: FlagServiceSettings = load(layeredSource)

    assertEquals(ServiceIdentity(name = "flagd", environment = "staging"), settings.service.identity)
    assertEquals(ServiceLimits(maxFlags = 64, evaluationTimeout = 250.millis), settings.service.limits)

    assertEquals(Set("checkout-v2", "dark-mode"), settings.flags.keySet)

    val checkout: Flag = settings.flags("checkout-v2")
    assertEquals("payments-team", checkout.owner)
    assertEquals(NonEmptySet.of("eu-west", "us-east"), checkout.audience.regions)
    assertEquals(NonEmptyVector.of("beta-testers", "employees"), checkout.audience.cohorts)
    assertEquals(NonEmptyMap.of("control" -> 80, "treatment" -> 20), checkout.variants)
    assertEquals(NonEmptyChain("payment-errors", "latency-p99"), checkout.killSwitches)
    assertEquals(100, checkout.variants.toSortedMap.values.sum)

    val darkMode: Flag = settings.flags("dark-mode")
    assertEquals(NonEmptySet.one("global"), darkMode.audience.regions)
    assertEquals(NonEmptyMap.one("on", 100), darkMode.variants)
    assertEquals(NonEmptyChain.one("support-tickets"), darkMode.killSwitches)
  }

  @Test
  def rendersTheJoinedWritersAsOneFlatSectionAndReloadsUnchanged(): Unit = {
    val settings: FlagServiceSettings = load(layeredSource)

    val rendered: ConfigValue = ConfigWriter[FlagServiceSettings].to(settings)
    val service: ConfigObject = rendered.asInstanceOf[ConfigObject].toConfig.getObject("service")
    assertEquals(Set("name", "environment", "max-flags", "evaluation-timeout"), keysOf(service))
    assertEquals("staging", service.toConfig.getString("environment"))
    assertEquals(64, service.toConfig.getInt("max-flags"))

    val checkout: ConfigObject = rendered.asInstanceOf[ConfigObject].toConfig.getObject("flags.checkout-v2")
    assertEquals(
      List("eu-west", "us-east"),
      checkout.toConfig.getStringList("audience.regions").toArray.toList
    )
    assertEquals(80, checkout.toConfig.getInt("variants.control"))
    assertEquals(
      List("payment-errors", "latency-p99"),
      checkout.toConfig.getStringList("kill-switches").toArray.toList
    )

    val reloaded: FlagServiceSettings = load(ConfigSource.string(rendered.render(ConfigRenderOptions.concise())))
    assertEquals(settings, reloaded)
  }

  @Test
  def accumulatesFailuresFromBothJoinedReadersAndMergesSectionFailures(): Unit = {
    val brokenService: String = """
      service {
        name = flagd
        evaluation-timeout = 250ms
      }
    """
    val serviceFailures: ConfigReaderFailures = ConfigSource.string(brokenService).at("service").load[Service] match {
      case Right(service) => fail(s"A service section missing two keys was accepted: $service")
      case Left(failures) => failures
    }
    assertEquals(
      Set("service" -> KeyNotFound("environment"), "service" -> KeyNotFound("max-flags")),
      reasonsByPath(serviceFailures).map { case (path, reason) => path -> stripCandidates(reason) },
      serviceFailures.prettyPrint()
    )

    val brokenFlags: String = """
      flags {
        empty-audience {
          owner = growth-team
          audience { regions = [], cohorts = [everyone] }
          variants { on = 100 }
          kill-switches = [error-rate]
        }
        no-variants {
          owner = growth-team
          audience { regions = [global], cohorts = [] }
          variants {}
          kill-switches = []
        }
      }
    """
    val flagFailures: ConfigReaderFailures =
      ConfigSource.string(brokenFlags).at("flags").load[Map[String, Flag]] match {
        case Right(flags) => fail(s"Flags with empty required collections were accepted: $flags")
        case Left(failures) => failures
      }
    val emptyPaths: Set[String] = reasonsByPath(flagFailures).collect {
      case (path, EmptyTraversableFound(_)) => path
    }
    assertEquals(
      Set(
        "flags.empty-audience.audience.regions",
        "flags.no-variants.audience.cohorts",
        "flags.no-variants.variants",
        "flags.no-variants.kill-switches"
      ),
      emptyPaths,
      flagFailures.prettyPrint()
    )

    val merged: ConfigReaderFailures = serviceFailures |+| flagFailures
    assertEquals(serviceFailures.toList.size + flagFailures.toList.size, merged.toList.size)
    assertTrue(merged === ConfigReaderFailures(serviceFailures.head, (serviceFailures.tail ++ flagFailures.toList)*))
    assertFalse(merged === serviceFailures)
  }
}

object FeatureFlagServiceSettingsTest {

  /** Who the flag service is, read from the shared `service` section. */
  final case class ServiceIdentity(name: String, environment: String)

  /** How much work the flag service accepts, read from the same `service` section. */
  final case class ServiceLimits(maxFlags: Int, evaluationTimeout: FiniteDuration)

  /** The `service` section, whose keys are owned by two independently derived views. */
  final case class Service(identity: ServiceIdentity, limits: ServiceLimits)

  given ConfigReader[Service] =
    (deriveReader[ServiceIdentity], deriveReader[ServiceLimits]).mapN(Service.apply)
  given ConfigWriter[Service] =
    (deriveWriter[ServiceIdentity], deriveWriter[ServiceLimits]).tupled
      .contramap[Service](service => (service.identity, service.limits))

  /** The regions and user cohorts a flag is served to; neither may be empty. */
  final case class Audience(regions: NonEmptySet[String], cohorts: NonEmptyVector[String])

  given ConfigReader[Audience] = deriveReader[Audience]
  given ConfigWriter[Audience] = deriveWriter[Audience]

  /** A feature flag with its weighted variants and the alerts that switch it off. */
  final case class Flag(
      owner: String,
      audience: Audience,
      variants: NonEmptyMap[String, Int],
      killSwitches: NonEmptyChain[String]
  )

  given ConfigReader[Flag] = deriveReader[Flag]
  given ConfigWriter[Flag] = deriveWriter[Flag]

  final case class FlagServiceSettings(service: Service, flags: Map[String, Flag])

  given ConfigReader[FlagServiceSettings] = deriveReader[FlagServiceSettings]
  given ConfigWriter[FlagServiceSettings] = deriveWriter[FlagServiceSettings]

  /** Defaults bundled with the service. */
  val Defaults: String = """
    service {
      name = flagd
      environment = development
      max-flags = 32
      evaluation-timeout = 1s
    }
    flags {
      dark-mode {
        owner = design-team
        audience { regions = [global], cohorts = [everyone] }
        variants { on = 100 }
        kill-switches = [support-tickets]
      }
    }
  """

  /** The staging environment's layer. */
  val Staging: String = """
    service {
      environment = staging
      max-flags = 64
    }
    flags {
      checkout-v2 {
        owner = payments-team
        audience { regions = [us-east, eu-west, us-east], cohorts = [beta-testers] }
        variants { control = 90, treatment = 10 }
        kill-switches = [payment-errors]
      }
    }
  """

  /** An operator's runtime overrides, applied last. */
  val Overrides: String = """
    service.evaluation-timeout = 250ms
    flags.checkout-v2 {
      audience.cohorts = [beta-testers, employees]
      variants { control = 80, treatment = 20 }
      kill-switches = [payment-errors, latency-p99]
    }
  """

  /** The layers combined with the cats monoid, each later layer overriding the ones before it. */
  def layeredSource: ConfigObjectSource =
    List(Defaults, Staging, Overrides).map(text => ConfigSource.string(text)).combineAll

  def load(source: ConfigObjectSource): FlagServiceSettings =
    source.load[FlagServiceSettings] match {
      case Right(settings) => settings
      case Left(failures) => fail(s"The flag service settings do not load: ${failures.prettyPrint()}")
    }

  def reasonsByPath(failures: ConfigReaderFailures): Set[(String, Any)] =
    failures.toList.collect { case ConvertFailure(reason, _, path) => path -> reason }.toSet

  /** Drops the spelling suggestions a `KeyNotFound` carries, which depend on the other keys present. */
  def stripCandidates(reason: Any): Any = reason match {
    case KeyNotFound(key, _) => KeyNotFound(key)
    case other => other
  }

  def keysOf(configObject: ConfigObject): Set[String] = {
    import scala.jdk.CollectionConverters.*
    configObject.keySet.asScala.toSet
  }
}
