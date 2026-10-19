/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_pureconfig.pureconfig_generic_scala3_3

import scala.concurrent.duration.*

import com.typesafe.config.{ConfigObject, ConfigRenderOptions, ConfigValue}
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue, fail}
import org.junit.jupiter.api.Test
import pureconfig.{ConfigReader, ConfigSource, ConfigWriter}
import pureconfig.error.{CannotConvert, ConfigReaderFailures, ConvertFailure, UserValidationFailed}
import pureconfig.generic.semiauto.{deriveReader, deriveWriter}

/**
 * Exercises derived readers refined by reader combinators: semiauto-derived product readers for an
 * API gateway's tenant quotas, checked for cross-field invariants with `ensure`, converted into
 * domain types with `emap`, and falling back with `orElse` to a derived reader for the legacy quota
 * schema, so that a mixed-schema file loads, is rendered back in the current schema by derived
 * writers, and reports validation failures at the paths an operator edits.
 */
class GatewayTenantQuotasTest {
  import GatewayTenantQuotasTest.*
  import GatewayTenantQuotasTest.given

  @Test
  def loadsTenantsWrittenInTheCurrentAndTheLegacyQuotaSchema(): Unit = {
    val gateway: GatewayConfig = loadGateway(MixedSchemaConfig)

    assertEquals(Set("acme", "globex", "initech"), gateway.tenants.keySet)

    val acme: Tenant = gateway.tenants("acme")
    assertEquals(RateLimit(requests = 100, per = 1.second, burst = 250), acme.rateLimit)
    assertEquals(Some(QuietPeriod(fromHour = 22, toHour = 6)), acme.quietHours)
    assertTrue(acme.quietHours.exists(_.covers(23)))
    assertTrue(acme.quietHours.exists(_.covers(3)))
    assertFalse(acme.quietHours.exists(_.covers(12)))

    val globex: Tenant = gateway.tenants("globex")
    assertEquals(RateLimit(requests = 600, per = 1.minute, burst = 600), globex.rateLimit)
    assertEquals(None, globex.quietHours)

    val initech: Tenant = gateway.tenants("initech")
    assertEquals(RateLimit(requests = 5000, per = 1.hour, burst = 5000), initech.rateLimit)
    assertEquals(Some(QuietPeriod(fromHour = 1, toHour = 4)), initech.quietHours)
    assertTrue(initech.quietHours.exists(_.covers(2)))
    assertFalse(initech.quietHours.exists(_.covers(4)))

    assertEquals(RateLimit(requests = 10, per = 1.second, burst = 20), gateway.anonymousLimit)
  }

  @Test
  def rendersLegacyQuotasInTheCurrentSchemaAndReloadsThem(): Unit = {
    val gateway: GatewayConfig = loadGateway(MixedSchemaConfig)

    val rendered: ConfigValue = ConfigWriter[GatewayConfig].to(gateway)
    val globexLimit: ConfigObject = rendered
      .asInstanceOf[ConfigObject]
      .toConfig
      .getObject("tenants.globex.rate-limit")
    assertEquals(Set("requests", "per", "burst"), keysOf(globexLimit))
    assertEquals(600, globexLimit.toConfig.getInt("requests"))
    assertEquals(600, globexLimit.toConfig.getInt("burst"))
    assertEquals(java.time.Duration.ofMinutes(1), globexLimit.toConfig.getDuration("per"))

    val acmeQuietHours: ConfigObject = rendered
      .asInstanceOf[ConfigObject]
      .toConfig
      .getObject("tenants.acme.quiet-hours")
    assertEquals(Set("from-hour", "to-hour"), keysOf(acmeQuietHours))
    assertFalse(rendered.asInstanceOf[ConfigObject].toConfig.hasPath("tenants.globex.quiet-hours"))

    val dump: String = rendered.render(ConfigRenderOptions.concise())
    assertFalse(dump.contains("calls-per-minute"), dump)
    assertFalse(dump.contains("calls-per-hour"), dump)

    val reloaded: GatewayConfig = ConfigSource.string(dump).load[GatewayConfig] match {
      case Right(config) => config
      case Left(failures) => fail(s"The rendered gateway config does not reload: ${failures.prettyPrint()}")
    }
    assertEquals(gateway, reloaded)
  }

  @Test
  def reportsBrokenQuotasAtThePathsAnOperatorEdits(): Unit = {
    val brokenConfig: String = """
      gateway {
        anonymous-limit { requests = 10, per = 1s, burst = 20 }
        tenants {
          acme {
            rate-limit { requests = 100, per = 1s, burst = 40 }
          }
          globex {
            rate-limit { calls-per-minute = 600 }
            quiet-hours { from-hour = 25, to-hour = 6 }
          }
        }
      }
    """

    val failures: ConfigReaderFailures = ConfigSource.string(brokenConfig).at("gateway").load[GatewayConfig] match {
      case Right(config) => fail(s"A gateway config with broken quotas was accepted: $config")
      case Left(errors) => errors
    }
    val convertFailures: List[ConvertFailure] = failures.toList.collect { case failure: ConvertFailure =>
      failure
    }

    val acmeLimitReasons: Set[Any] =
      convertFailures.filter(_.path == "gateway.tenants.acme.rate-limit").map(_.reason).toSet
    assertEquals(
      Set(
        UserValidationFailed("burst 40 is below the 100 requests it must absorb"),
        UserValidationFailed("a legacy quota sets exactly one of calls-per-minute and calls-per-hour")
      ),
      acmeLimitReasons,
      failures.prettyPrint()
    )

    val quietHoursViolation: Option[ConvertFailure] = convertFailures.find(_.path == "gateway.tenants.globex.quiet-hours")
    quietHoursViolation.map(_.reason) match {
      case Some(CannotConvert(value, toType, because)) =>
        assertEquals("25-6", value)
        assertEquals("QuietPeriod", toType)
        assertEquals("hours must lie within 0..23 and differ", because)
      case other => fail(s"Unexpected quiet-hours failure: $other in ${failures.prettyPrint()}")
    }

    assertFalse(convertFailures.exists(_.path.startsWith("gateway.tenants.globex.rate-limit")), failures.prettyPrint())
    assertFalse(convertFailures.exists(_.path.startsWith("gateway.anonymous-limit")), failures.prettyPrint())
  }
}

object GatewayTenantQuotasTest {

  /** Requests a client may make per window, and how many of them it may spend at once. */
  final case class RateLimit(requests: Int, per: FiniteDuration, burst: Int)

  /** The current quota schema: a derived reader that rejects bursts too small for the window. */
  private val currentRateLimitReader: ConfigReader[RateLimit] = deriveReader[RateLimit].ensure(
    limit => limit.burst >= limit.requests,
    limit => s"burst ${limit.burst} is below the ${limit.requests} requests it must absorb"
  )

  /** The legacy quota schema, which only knew per-minute or per-hour call counts. */
  final case class LegacyQuota(callsPerMinute: Option[Int], callsPerHour: Option[Int])

  private val legacyRateLimitReader: ConfigReader[RateLimit] = deriveReader[LegacyQuota].emap {
    case LegacyQuota(Some(perMinute), None) => Right(RateLimit(perMinute, 1.minute, perMinute))
    case LegacyQuota(None, Some(perHour)) => Right(RateLimit(perHour, 1.hour, perHour))
    case LegacyQuota(_, _) =>
      Left(UserValidationFailed("a legacy quota sets exactly one of calls-per-minute and calls-per-hour"))
  }

  given ConfigReader[RateLimit] = currentRateLimitReader.orElse(legacyRateLimitReader)
  given ConfigWriter[RateLimit] = deriveWriter[RateLimit]

  /** Hours of the day, possibly wrapping past midnight, during which a tenant's jobs are paused. */
  final case class QuietPeriod(fromHour: Int, toHour: Int) {
    def covers(hour: Int): Boolean =
      if (fromHour < toHour) hour >= fromHour && hour < toHour
      else hour >= fromHour || hour < toHour
  }

  /** The configured shape of a quiet period, before its hours are validated. */
  final case class QuietHoursSpec(fromHour: Int, toHour: Int)

  given ConfigReader[QuietPeriod] = deriveReader[QuietHoursSpec].emap { spec =>
    val validHour: Int => Boolean = hour => hour >= 0 && hour <= 23
    if (validHour(spec.fromHour) && validHour(spec.toHour) && spec.fromHour != spec.toHour)
      Right(QuietPeriod(spec.fromHour, spec.toHour))
    else
      Left(CannotConvert(s"${spec.fromHour}-${spec.toHour}", "QuietPeriod", "hours must lie within 0..23 and differ"))
  }
  given ConfigWriter[QuietPeriod] =
    deriveWriter[QuietHoursSpec].contramap(period => QuietHoursSpec(period.fromHour, period.toHour))

  final case class Tenant(rateLimit: RateLimit, quietHours: Option[QuietPeriod])

  given ConfigReader[Tenant] = deriveReader[Tenant]
  given ConfigWriter[Tenant] = deriveWriter[Tenant]

  final case class GatewayConfig(anonymousLimit: RateLimit, tenants: Map[String, Tenant])

  given ConfigReader[GatewayConfig] = deriveReader[GatewayConfig]
  given ConfigWriter[GatewayConfig] = deriveWriter[GatewayConfig]

  /** A gateway file whose tenants were migrated to the current quota schema one at a time. */
  val MixedSchemaConfig: String = """
    gateway {
      anonymous-limit { requests = 10, per = 1s, burst = 20 }
      tenants {
        acme {
          rate-limit { requests = 100, per = 1s, burst = 250 }
          quiet-hours { from-hour = 22, to-hour = 6 }
        }
        globex {
          rate-limit { calls-per-minute = 600 }
        }
        initech {
          rate-limit { calls-per-hour = 5000 }
          quiet-hours { from-hour = 1, to-hour = 4 }
        }
      }
    }
  """

  def loadGateway(text: String): GatewayConfig =
    ConfigSource.string(text).at("gateway").load[GatewayConfig] match {
      case Right(config) => config
      case Left(failures) => fail(s"The gateway config does not load: ${failures.prettyPrint()}")
    }

  def keysOf(configObject: ConfigObject): Set[String] = {
    import scala.jdk.CollectionConverters.*
    configObject.keySet.asScala.toSet
  }
}
