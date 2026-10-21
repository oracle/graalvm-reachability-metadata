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
import pureconfig.{ConfigReader, ConfigSource, ConfigWriter}
import pureconfig.error.{ConfigReaderFailures, ConvertFailure, KeyNotFound}
import pureconfig.generic.{CoproductHint, FieldCoproductHint, FirstSuccessCoproductHint}
import pureconfig.generic.error.{NoValidCoproductOptionFound, UnexpectedValueForFieldCoproductHint}
import pureconfig.generic.semiauto.{deriveReader, deriveWriter}

/**
 * Exercises coproduct derivation: sealed-trait families in an alert-routing configuration are read
 * and written through derived readers and writers that pick the concrete case either from a
 * discriminator field (`FieldCoproductHint`, including one with a custom value mapping) or by trying
 * each case in turn (`FirstSuccessCoproductHint`), with case objects, nested sealed families and the
 * failures an operator sees for an unknown discriminator or an untagged value no case accepts.
 */
class AlertRoutingRulesTest {
  import AlertRoutingRulesTest.*
  import AlertRoutingRulesTest.given

  @Test
  def loadsRulesWhoseChannelsThresholdsAndEscalationsAreSealedFamilies(): Unit = {
    val config: AlertingConfig = ConfigSource.string(RoutingConfig).at("alerting").loadOrThrow[AlertingConfig]

    assertEquals(
      List(
        AlertRule(
          name = "checkout-latency",
          metric = "http.server.p99",
          threshold = Above(750.0),
          channels = List(
            Email(List("sre@example.com", "payments@example.com")),
            PagerDuty(routingKey = "R0UT1NG", severity = "critical")
          ),
          escalation = Delayed(after = 5.minutes, repeat = 3)
        ),
        AlertRule(
          name = "disk-headroom",
          metric = "node.disk.free-ratio",
          threshold = Below(0.1),
          channels = List(Slack("https://hooks.example.com/T000/B000", Some("@storage-oncall"))),
          escalation = Immediate
        ),
        AlertRule(
          name = "queue-depth",
          metric = "orders.queue.depth",
          threshold = Between(min = 10.0, max = 5000.0),
          channels = List(Email(List("orders@example.com"), subjectPrefix = "[orders]"), Muted),
          escalation = Delayed(after = 30.seconds)
        )
      ),
      config.rules
    )
  }

  @Test
  def rendersDiscriminatorsAndUntaggedCasesThatReloadUnchanged(): Unit = {
    val config: AlertingConfig = ConfigSource.string(RoutingConfig).at("alerting").loadOrThrow[AlertingConfig]
    val rendered: ConfigObject = ConfigWriter[AlertingConfig].to(config).asInstanceOf[ConfigObject]
    val rules: List[Config] = rendered.toConfig.getConfigList("rules").asScala.toList

    val latencyRule: Config = rules(0)
    assertEquals(750.0, latencyRule.getDouble("threshold.above"))
    assertFalse(latencyRule.hasPath("threshold.type"))
    assertEquals("email", latencyRule.getConfigList("channels").get(0).getString("kind"))
    assertEquals("pager-duty", latencyRule.getConfigList("channels").get(1).getString("kind"))
    assertEquals("R0UT1NG", latencyRule.getConfigList("channels").get(1).getString("routing-key"))
    assertEquals("delayed", latencyRule.getString("escalation.strategy"))

    val diskRule: Config = rules(1)
    assertEquals("immediate", diskRule.getString("escalation.strategy"))
    assertEquals(Set("strategy"), diskRule.getConfig("escalation").root().keySet().asScala.toSet)

    val queueRule: Config = rules(2)
    assertEquals(5000.0, queueRule.getDouble("threshold.max"))
    assertEquals("muted", queueRule.getConfigList("channels").get(1).getString("kind"))

    val reloaded: AlertingConfig =
      ConfigSource.string(rendered.render(ConfigRenderOptions.concise())).loadOrThrow[AlertingConfig]
    assertEquals(config, reloaded)
  }

  @Test
  def reportsUnknownDiscriminatorsMissingTagsAndUnmatchedUntaggedValues(): Unit = {
    val brokenConfig: String = """
      alerting.rules = [
        {
          name = "error-rate"
          metric = "http.server.errors"
          threshold { around = 0.05 }
          channels = [ { kind = "sms", number = "+15550100" }, { recipients = ["noc@example.com"] } ]
          escalation { strategy = "delayed", after = 1 minute }
        }
      ]
      """

    ConfigSource.string(brokenConfig).at("alerting").load[AlertingConfig] match {
      case Right(config) => fail(s"Expected the broken rules to be rejected, but loaded $config")
      case Left(failures) =>
        assertTrue(
          failures.toList.exists {
            case ConvertFailure(UnexpectedValueForFieldCoproductHint(value), _, path) =>
              value.unwrapped == "sms" && path == "alerting.rules.0.channels.0.kind"
            case _ => false
          },
          failures.prettyPrint()
        )
        assertTrue(
          failures.toList.exists {
            case ConvertFailure(KeyNotFound("kind", _), _, "alerting.rules.0.channels.1") => true
            case _ => false
          },
          failures.prettyPrint()
        )
        val unmatched: Option[NoValidCoproductOptionFound] = failures.toList.collectFirst {
          case ConvertFailure(reason: NoValidCoproductOptionFound, _, "alerting.rules.0.threshold") => reason
        }
        assertTrue(unmatched.isDefined, failures.prettyPrint())
        assertEquals(List("Above", "Below", "Between"), unmatched.get.optionFailures.map(_._1).toList)
        assertFalse(failurePaths(failures).exists(_.startsWith("alerting.rules.0.escalation")), failures.prettyPrint())
    }
  }

  private def failurePaths(failures: ConfigReaderFailures): Set[String] =
    failures.toList.collect { case ConvertFailure(_, _, path) => path }.toSet
}

object AlertRoutingRulesTest {

  /** Where an alert is delivered, tagged by a `kind` field in kebab-case (`pager-duty`). */
  sealed trait Channel
  final case class Email(recipients: List[String], subjectPrefix: String = "[alert]") extends Channel
  final case class Slack(webhookUrl: String, mention: Option[String]) extends Channel
  final case class PagerDuty(routingKey: String, severity: String) extends Channel
  case object Muted extends Channel

  given CoproductHint[Channel] = new FieldCoproductHint[Channel]("kind")
  given ConfigReader[Channel] = deriveReader[Channel]
  given ConfigWriter[Channel] = deriveWriter[Channel]

  /** When an alert fires; written untagged, so the reader tries each case until one accepts it. */
  sealed trait Threshold
  final case class Above(above: Double) extends Threshold
  final case class Below(below: Double) extends Threshold
  final case class Between(min: Double, max: Double) extends Threshold

  given CoproductHint[Threshold] = new FirstSuccessCoproductHint[Threshold]
  given ConfigReader[Threshold] = deriveReader[Threshold]
  given ConfigWriter[Threshold] = deriveWriter[Threshold]

  /** How an unacknowledged alert escalates, tagged by a lower-case `strategy` field. */
  sealed trait Escalation
  case object Immediate extends Escalation
  final case class Delayed(after: FiniteDuration, repeat: Int = 1) extends Escalation

  given CoproductHint[Escalation] = new FieldCoproductHint[Escalation]("strategy") {
    override protected def fieldValue(name: String): String = name.toLowerCase
  }
  given ConfigReader[Escalation] = deriveReader[Escalation]
  given ConfigWriter[Escalation] = deriveWriter[Escalation]

  final case class AlertRule(
      name: String,
      metric: String,
      threshold: Threshold,
      channels: List[Channel],
      escalation: Escalation
  )

  given ConfigReader[AlertRule] = deriveReader[AlertRule]
  given ConfigWriter[AlertRule] = deriveWriter[AlertRule]

  final case class AlertingConfig(rules: List[AlertRule])

  given ConfigReader[AlertingConfig] = deriveReader[AlertingConfig]
  given ConfigWriter[AlertingConfig] = deriveWriter[AlertingConfig]

  val RoutingConfig: String = """
    alerting {
      rules = [
        {
          name = "checkout-latency"
          metric = "http.server.p99"
          threshold { above = 750 }
          channels = [
            { kind = "email", recipients = ["sre@example.com", "payments@example.com"] }
            { kind = "pager-duty", routing-key = "R0UT1NG", severity = "critical" }
          ]
          escalation { strategy = "delayed", after = 5 minutes, repeat = 3 }
        }
        {
          name = "disk-headroom"
          metric = "node.disk.free-ratio"
          threshold { below = 0.1 }
          channels = [
            { kind = "slack", webhook-url = "https://hooks.example.com/T000/B000", mention = "@storage-oncall" }
          ]
          escalation { strategy = "immediate" }
        }
        {
          name = "queue-depth"
          metric = "orders.queue.depth"
          threshold { min = 10, max = 5000 }
          channels = [
            { kind = "email", recipients = ["orders@example.com"], subject-prefix = "[orders]" }
            { kind = "muted" }
          ]
          escalation { strategy = "delayed", after = 30s }
        }
      ]
    }
    """
}
