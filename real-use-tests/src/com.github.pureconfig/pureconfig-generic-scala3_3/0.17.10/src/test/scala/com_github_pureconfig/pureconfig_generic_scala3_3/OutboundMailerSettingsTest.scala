/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_pureconfig.pureconfig_generic_scala3_3

import scala.jdk.CollectionConverters.*

import com.typesafe.config.{Config, ConfigObject, ConfigRenderOptions, ConfigValue}
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue, fail}
import org.junit.jupiter.api.Test
import pureconfig.{ConfigCursor, ConfigReader, ConfigSource, ConfigWriter, ReadsMissingKeys, WritesMissingKeys}
import pureconfig.error.{ConfigReaderFailures, ConvertFailure, FailureReason, KeyNotFound}
import pureconfig.generic.ProductHint
import pureconfig.generic.semiauto.{deriveReader, deriveWriter}

/**
 * Exercises missing-key and null handling in product derivation: semiauto-derived readers and
 * writers for an outbound mailer's settings where absent `Option` fields read as `None`, explicit
 * `null` overrides a `Some` default argument, a `ProductHint` with `useDefaultArgs = false` makes
 * defaulted fields mandatory, and application-written `ReadsMissingKeys` and `WritesMissingKeys`
 * instances let derivation read an absent key and omit an empty value when rendering.
 */
class OutboundMailerSettingsTest {
  import OutboundMailerSettingsTest.*
  import OutboundMailerSettingsTest.given

  @Test
  def fillsAbsentKeysFromDefaultsOptionsAndMissingKeyReaders(): Unit = {
    val settings: MailerSettings = ConfigSource.string(MinimalConfig).at("mailer").loadOrThrow[MailerSettings]

    assertEquals(SmtpRelay("smtp.example.com", port = 587, username = None, startTls = true), settings.relay)
    assertEquals("noreply@example.com", settings.sender)
    assertEquals(None, settings.replyTo)
    assertEquals(Some(DefaultProxy), settings.proxy)
    assertEquals(Labels.Empty, settings.labels)
    assertEquals(AuditSettings(retentionDays = 90, archiveBucket = None), settings.audit)
  }

  @Test
  def explicitNullsClearOptionalSettingsAndOverrideDefaults(): Unit = {
    val settings: MailerSettings = ConfigSource.string(OverridingConfig).at("mailer").loadOrThrow[MailerSettings]

    assertEquals(
      SmtpRelay("relay.internal", port = 2525, username = Some("mailer-svc"), startTls = false),
      settings.relay
    )
    assertEquals(None, settings.replyTo)
    assertEquals(None, settings.proxy)
    assertEquals(Labels(List("transactional", "eu")), settings.labels)
    assertEquals(AuditSettings(retentionDays = 30, archiveBucket = Some("mail-audit-eu")), settings.audit)
  }

  @Test
  def rendersOnlyPresentValuesAndReloadsThroughTheSameRules(): Unit = {
    val settings: MailerSettings = ConfigSource.string(OverridingConfig).at("mailer").loadOrThrow[MailerSettings]
    val rendered: ConfigObject = ConfigWriter[MailerSettings].to(settings).asInstanceOf[ConfigObject]
    val renderedConfig: Config = rendered.toConfig

    assertEquals(Set("relay", "sender", "labels", "audit"), rendered.keySet.asScala.toSet)
    assertEquals(List("transactional", "eu"), renderedConfig.getStringList("labels").asScala.toList)
    assertEquals(2525, renderedConfig.getInt("relay.port"))
    assertEquals("mailer-svc", renderedConfig.getString("relay.username"))
    assertFalse(renderedConfig.getBoolean("relay.start-tls"))
    assertEquals(30, renderedConfig.getInt("audit.retention-days"))

    // A cleared proxy is omitted when rendered, so a reload falls back to the default argument again.
    val reloaded: MailerSettings =
      ConfigSource.string(rendered.render(ConfigRenderOptions.concise())).loadOrThrow[MailerSettings]
    assertEquals(settings.copy(proxy = Some(DefaultProxy)), reloaded)

    val withoutLabels: MailerSettings = reloaded.copy(labels = Labels.Empty, replyTo = Some("support@example.com"))
    val renderedWithoutLabels: ConfigObject = ConfigWriter[MailerSettings].to(withoutLabels).asInstanceOf[ConfigObject]
    assertFalse(renderedWithoutLabels.containsKey("labels"))
    assertEquals("support@example.com", renderedWithoutLabels.toConfig.getString("reply-to"))
    assertEquals("egress.internal", renderedWithoutLabels.toConfig.getString("proxy.host"))
    val reloadedWithoutLabels: MailerSettings =
      ConfigSource.fromConfig(renderedWithoutLabels.toConfig).loadOrThrow[MailerSettings]
    assertEquals(withoutLabels, reloadedWithoutLabels)
  }

  @Test
  def reportsMissingMandatoryKeysWithSimilarKeyCandidates(): Unit = {
    val brokenConfig: String = """
      mailer {
        relay { port = null }
        Sender = "noreply@example.com"
        audit { archive-bucket = "mail-audit" }
      }
      """

    ConfigSource.string(brokenConfig).at("mailer").load[MailerSettings] match {
      case Right(settings) => fail(s"Expected the incomplete mailer settings to be rejected, but loaded $settings")
      case Left(failures) =>
        val reasons: Map[String, List[FailureReason]] = reasonsByPath(failures)
        assertTrue(
          reasons.getOrElse("mailer.relay", Nil).contains(KeyNotFound("host", Set.empty)),
          failures.prettyPrint()
        )
        assertTrue(reasons.contains("mailer.relay.port"), failures.prettyPrint())
        assertTrue(
          reasons.getOrElse("mailer", Nil).contains(KeyNotFound("sender", Set("Sender"))),
          failures.prettyPrint()
        )
        assertTrue(
          reasons.getOrElse("mailer.audit", Nil).contains(KeyNotFound("retention-days", Set.empty)),
          failures.prettyPrint()
        )
        assertFalse(reasons.keys.exists(_.endsWith("archive-bucket")), failures.prettyPrint())
        assertFalse(reasons.keys.exists(_.endsWith("labels")), failures.prettyPrint())
        assertEquals(4, failures.toList.size, failures.prettyPrint())
    }
  }

  private def reasonsByPath(failures: ConfigReaderFailures): Map[String, List[FailureReason]] =
    failures.toList
      .collect { case ConvertFailure(reason, _, path) => path -> reason }
      .groupMap(_._1)(_._2)
}

object OutboundMailerSettingsTest {
  val MinimalConfig: String = """
    mailer {
      relay { host = "smtp.example.com" }
      sender = "noreply@example.com"
      audit { retention-days = 90 }
    }
    """

  val OverridingConfig: String = """
    mailer {
      relay {
        host = "relay.internal"
        port = 2525
        username = "mailer-svc"
        start-tls = false
      }
      sender = "noreply@example.com"
      reply-to = null
      proxy = null
      labels = [transactional, eu]
      audit {
        retention-days = 30
        archive-bucket = "mail-audit-eu"
      }
    }
    """

  val DefaultProxy: ProxySettings = ProxySettings("egress.internal", 3128)

  /** Labels stamped on every outgoing message; an absent key means no labels. */
  final case class Labels(values: List[String])

  object Labels {
    val Empty: Labels = Labels(Nil)
  }

  given ConfigReader[Labels] = new ConfigReader[Labels] with ReadsMissingKeys {
    def from(cur: ConfigCursor): ConfigReader.Result[Labels] =
      if (cur.isUndefined) Right(Labels.Empty)
      else ConfigReader[List[String]].from(cur).map(Labels(_))
  }

  given ConfigWriter[Labels] = new ConfigWriter[Labels] with WritesMissingKeys[Labels] {
    def to(labels: Labels): ConfigValue = ConfigWriter[List[String]].to(labels.values)
    def toOpt(labels: Labels): Option[ConfigValue] = if (labels.values.isEmpty) None else Some(to(labels))
  }

  final case class SmtpRelay(host: String, port: Int = 587, username: Option[String], startTls: Boolean = true)

  given ConfigReader[SmtpRelay] = deriveReader[SmtpRelay]
  given ConfigWriter[SmtpRelay] = deriveWriter[SmtpRelay]

  final case class ProxySettings(host: String, port: Int)

  given ConfigReader[ProxySettings] = deriveReader[ProxySettings]
  given ConfigWriter[ProxySettings] = deriveWriter[ProxySettings]

  /** Compliance settings that must be spelled out in every deployment, defaults notwithstanding. */
  final case class AuditSettings(retentionDays: Int = 30, archiveBucket: Option[String])

  given ProductHint[AuditSettings] = ProductHint[AuditSettings](useDefaultArgs = false)
  given ConfigReader[AuditSettings] = deriveReader[AuditSettings]
  given ConfigWriter[AuditSettings] = deriveWriter[AuditSettings]

  final case class MailerSettings(
      relay: SmtpRelay,
      sender: String,
      replyTo: Option[String],
      proxy: Option[ProxySettings] = Some(DefaultProxy),
      labels: Labels,
      audit: AuditSettings
  )

  given ConfigReader[MailerSettings] = deriveReader[MailerSettings]
  given ConfigWriter[MailerSettings] = deriveWriter[MailerSettings]
}
