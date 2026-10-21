/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_pureconfig.pureconfig_generic_scala3_3

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
 * Exercises coproduct derivation for hierarchical sealed families: an incident notifier family
 * whose direct children are sealed sub-families is tagged by an outer `medium` field, and each
 * sub-family picks its case its own way (a `provider` field, untagged first-success matching, or
 * the default `type` field of a sub-family derived inline without givens), rendered back with
 * both discriminator levels and reloaded unchanged, with a sub-family also read on its own, and
 * the failures reported at each level of the hierarchy.
 */
class IncidentNotificationRoutingTest {
  import IncidentNotificationRoutingTest.*
  import IncidentNotificationRoutingTest.given

  @Test
  def loadsRoutesWhoseNotifiersAreTaggedAtTwoLevels(): Unit = {
    val routing: IncidentRouting = loadRouting()

    assertEquals(Slack("https://hooks.example.com/T000/B000", room = "#incidents"), routing.defaultRoute)
    assertEquals(Teams("https://outlook.example.com/webhook/sre-leads"), routing.escalationChat)
    assertEquals(
      Map(
        "database" -> List(
          PagerDuty(routingKey = "DB-ROUTING-KEY"),
          Teams("https://outlook.example.com/webhook/dba")
        ),
        "billing" -> List(
          Email(to = List("billing@example.com", "finance-oncall@example.com")),
          Opsgenie(apiKey = "og-billing-key", team = "payments")
        ),
        "storefront" -> List(
          Sms(numbers = List("+15550100", "+15550101")),
          PagerDuty(routingKey = "WEB-ROUTING-KEY", severity = "warning"),
          Slack("https://hooks.example.com/T000/B111", room = "#storefront")
        ),
        "staging" -> List(Suppressed)
      ),
      routing.routes
    )

    assertEquals(
      Map("billing" -> List("payments"), "database" -> List("DB-ROUTING-KEY"), "storefront" -> List("WEB-ROUTING-KEY")),
      pagingTargets(routing)
    )
  }

  @Test
  def rendersBothDiscriminatorLevelsAndReloadsUnchanged(): Unit = {
    val routing: IncidentRouting = loadRouting()
    val rendered: ConfigObject = ConfigWriter[IncidentRouting].to(routing).asInstanceOf[ConfigObject]
    val config: Config = rendered.toConfig

    assertEquals("chat", config.getString("default-route.medium"))
    assertEquals("slack", config.getString("default-route.provider"))
    assertEquals("#incidents", config.getString("default-route.room"))

    assertEquals("teams", config.getString("escalation-chat.provider"))
    assertFalse(config.hasPath("escalation-chat.medium"))

    val database: List[Config] = config.getConfigList("routes.database").asScala.toList
    assertEquals("paging", database(0).getString("medium"))
    assertEquals(Set("medium", "routing-key", "severity"), database(0).root().keySet().asScala.toSet)
    assertEquals("critical", database(0).getString("severity"))
    assertEquals("teams", database(1).getString("provider"))

    val billing: List[Config] = config.getConfigList("routes.billing").asScala.toList
    assertEquals("message", billing(0).getString("medium"))
    assertEquals("email", billing(0).getString("type"))
    assertEquals(
      List("billing@example.com", "finance-oncall@example.com"),
      billing(0).getStringList("to").asScala.toList
    )
    assertEquals(Set("medium", "api-key", "team"), billing(1).root().keySet().asScala.toSet)

    val staging: List[Config] = config.getConfigList("routes.staging").asScala.toList
    assertEquals(Set("medium"), staging(0).root().keySet().asScala.toSet)
    assertEquals("suppressed", staging(0).getString("medium"))

    val reloaded: IncidentRouting =
      ConfigSource.string(rendered.render(ConfigRenderOptions.concise())).loadOrThrow[IncidentRouting]
    assertEquals(routing, reloaded)
  }

  @Test
  def readsAndWritesASubFamilyOnItsOwn(): Unit = {
    val overrides: String = """
      on-call-chat = [
        { provider = slack, webhook-url = "https://hooks.example.com/T000/B222", room = "#war-room" }
        { provider = teams, connector-url = "https://outlook.example.com/webhook/incident-bridge" }
      ]
      """

    val chats: List[ChatNotifier] = ConfigSource.string(overrides).at("on-call-chat").loadOrThrow[List[ChatNotifier]]
    assertEquals(
      List(
        Slack("https://hooks.example.com/T000/B222", room = "#war-room"),
        Teams("https://outlook.example.com/webhook/incident-bridge")
      ),
      chats
    )

    val asChat: Config = ConfigWriter[ChatNotifier].to(chats.head).asInstanceOf[ConfigObject].toConfig
    assertEquals("slack", asChat.getString("provider"))
    assertFalse(asChat.hasPath("medium"))

    val asNotifier: Config = ConfigWriter[Notifier].to(chats.head).asInstanceOf[ConfigObject].toConfig
    assertEquals("chat", asNotifier.getString("medium"))
    assertEquals("slack", asNotifier.getString("provider"))
  }

  @Test
  def reportsFailuresAtEachLevelOfTheHierarchy(): Unit = {
    val brokenConfig: String = """
      incident-routing {
        default-route { medium = carrier-pigeon, loft = "rooftop" }
        escalation-chat { provider = discord, invite = "abc" }
        routes {
          database = [
            { medium = chat, provider = discord, invite = "abc" }
            { medium = paging, routing-token = "DB" }
            { medium = message, to = ["dba@example.com"] }
            { medium = message, type = sms }
            { provider = slack, webhook-url = "https://hooks.example.com/T000/B000", room = "#db" }
          ]
        }
      }
      """

    ConfigSource.string(brokenConfig).at("incident-routing").load[IncidentRouting] match {
      case Right(routing) => fail(s"Expected the broken routing to be rejected, but loaded $routing")
      case Left(failures) =>
        assertEquals(
          Map(
            "incident-routing.default-route.medium" -> "carrier-pigeon",
            "incident-routing.escalation-chat.provider" -> "discord",
            "incident-routing.routes.database.0.provider" -> "discord"
          ),
          unexpectedDiscriminators(failures),
          failures.prettyPrint()
        )

        val unmatched: Option[NoValidCoproductOptionFound] = failures.toList.collectFirst {
          case ConvertFailure(reason: NoValidCoproductOptionFound, _, "incident-routing.routes.database.1") => reason
        }
        assertTrue(unmatched.isDefined, failures.prettyPrint())
        assertEquals(List("Opsgenie", "PagerDuty"), unmatched.get.optionFailures.map(_._1).toList)

        assertEquals(
          Set(
            "type" -> "incident-routing.routes.database.2",
            "numbers" -> "incident-routing.routes.database.3",
            "medium" -> "incident-routing.routes.database.4"
          ),
          missingKeys(failures),
          failures.prettyPrint()
        )
        assertEquals(7, failures.toList.size, failures.prettyPrint())
    }
  }

  private def loadRouting(): IncidentRouting =
    ConfigSource.string(RoutingConfig).at("incident-routing").loadOrThrow[IncidentRouting]

  private def unexpectedDiscriminators(failures: ConfigReaderFailures): Map[String, Any] =
    failures.toList.collect { case ConvertFailure(UnexpectedValueForFieldCoproductHint(value), _, path) =>
      path -> value.unwrapped
    }.toMap

  private def missingKeys(failures: ConfigReaderFailures): Set[(String, String)] =
    failures.toList.collect { case ConvertFailure(KeyNotFound(key, _), _, path) => key -> path }.toSet
}

object IncidentNotificationRoutingTest {

  /** Every way an incident can be announced; the direct children are sealed sub-families. */
  sealed trait Notifier

  /** Chat integrations, selected by a `provider` field. */
  sealed trait ChatNotifier extends Notifier
  final case class Slack(webhookUrl: String, room: String) extends ChatNotifier
  final case class Teams(connectorUrl: String) extends ChatNotifier

  /** Paging services, written untagged and recognized by the keys each one requires. */
  sealed trait PagingNotifier extends Notifier
  final case class PagerDuty(routingKey: String, severity: String = "critical") extends PagingNotifier
  final case class Opsgenie(apiKey: String, team: String) extends PagingNotifier

  /** Direct messages; no givens, so derivation inlines it with the default `type` field. */
  sealed trait MessageNotifier extends Notifier
  final case class Email(to: List[String]) extends MessageNotifier
  final case class Sms(numbers: List[String]) extends MessageNotifier

  case object Suppressed extends Notifier

  given CoproductHint[ChatNotifier] = new FieldCoproductHint[ChatNotifier]("provider")
  given ConfigReader[ChatNotifier] = deriveReader[ChatNotifier]
  given ConfigWriter[ChatNotifier] = deriveWriter[ChatNotifier]

  given CoproductHint[PagingNotifier] = new FirstSuccessCoproductHint[PagingNotifier]
  given ConfigReader[PagingNotifier] = deriveReader[PagingNotifier]
  given ConfigWriter[PagingNotifier] = deriveWriter[PagingNotifier]

  /** `ChatNotifier` is tagged `chat`, `PagingNotifier` `paging`, and so on. */
  given CoproductHint[Notifier] = new FieldCoproductHint[Notifier]("medium") {
    override protected def fieldValue(name: String): String = name.stripSuffix("Notifier").toLowerCase
  }
  given ConfigReader[Notifier] = deriveReader[Notifier]
  given ConfigWriter[Notifier] = deriveWriter[Notifier]

  final case class IncidentRouting(
      defaultRoute: Notifier,
      escalationChat: ChatNotifier,
      routes: Map[String, List[Notifier]]
  )

  given ConfigReader[IncidentRouting] = deriveReader[IncidentRouting]
  given ConfigWriter[IncidentRouting] = deriveWriter[IncidentRouting]

  /** The paging identity each route wakes up, as an on-call roster would list it. */
  def pagingTargets(routing: IncidentRouting): Map[String, List[String]] =
    routing.routes
      .map { case (route, notifiers) =>
        route -> notifiers.collect { case paging: PagingNotifier =>
          paging match {
            case PagerDuty(routingKey, _) => routingKey
            case Opsgenie(_, team) => team
          }
        }
      }
      .filter { case (_, targets) => targets.nonEmpty }

  val RoutingConfig: String = """
    incident-routing {
      default-route {
        medium = chat, provider = slack
        webhook-url = "https://hooks.example.com/T000/B000", room = "#incidents"
      }
      escalation-chat { provider = teams, connector-url = "https://outlook.example.com/webhook/sre-leads" }
      routes {
        database = [
          { medium = paging, routing-key = "DB-ROUTING-KEY" }
          { medium = chat, provider = teams, connector-url = "https://outlook.example.com/webhook/dba" }
        ]
        billing = [
          { medium = message, type = email, to = ["billing@example.com", "finance-oncall@example.com"] }
          { medium = paging, api-key = "og-billing-key", team = "payments" }
        ]
        storefront = [
          { medium = message, type = sms, numbers = ["+15550100", "+15550101"] }
          { medium = paging, routing-key = "WEB-ROUTING-KEY", severity = "warning" }
          { medium = chat, provider = slack, webhook-url = "https://hooks.example.com/T000/B111", room = "#storefront" }
        ]
        staging = [ { medium = suppressed } ]
      }
    }
    """
}
