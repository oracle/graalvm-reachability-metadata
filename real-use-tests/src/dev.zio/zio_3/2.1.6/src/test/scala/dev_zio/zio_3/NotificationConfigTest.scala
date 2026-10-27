/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import zio.{Config, ConfigProvider, Duration, Runtime, Unsafe}

/** Exercises ZIO Config by loading and validating a notification service's SMTP settings. */
class NotificationConfigTest {
  private case class MailSettings(host: String, port: Int, tls: Boolean) {
    def deliveryRoute: String = {
      val protocol: String = if (tls) "smtps" else "smtp"
      s"$protocol://$host:$port"
    }
  }

  private val mailConfig: Config[MailSettings] = Config.string("host")
    .zipWith(
      Config.int("port")
        .validate("SMTP port must be between 1 and 65535")(port => port >= 1 && port <= 65535)
        .withDefault(587)
    )((host, port) => MailSettings(host, port, tls = true))
    .zipWith(Config.boolean("tls").withDefault(true))((settings, tls) => settings.copy(tls = tls))
    .nested("smtp")
    .nested("notifications")

  @Test
  @Timeout(60)
  def deploymentSettingsApplyDefaultsButRejectInvalidOrMissingRequiredValues(): Unit = {
    val production: Map[String, String] = Map(
      "notifications.smtp.host" -> "relay.example.test",
      "notifications.smtp.port" -> "2525",
      "notifications.smtp.tls" -> "false"
    )
    val backup: Map[String, String] = Map("notifications.smtp.host" -> "backup.example.test")
    val program = for {
      configured <- ConfigProvider.fromMap(production).load(mailConfig)
      defaults <- ConfigProvider.fromMap(backup).load(mailConfig)
      // A malformed deployment must fail, not silently use the default port.
      invalid <- ConfigProvider.fromMap(
        production.updated("notifications.smtp.port", "70000")
      ).load(mailConfig).either
      missing <- ConfigProvider.fromMap(
        production - "notifications.smtp.host"
      ).load(mailConfig).either
    } yield (configured, defaults, invalid, missing)

    val (configured, defaults, invalid, missing) = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(
        program.timeoutFail(new IllegalStateException("Notification configuration timed out"))(
          Duration.fromSeconds(30)
        )
      ).getOrThrowFiberFailure()
    }

    assertEquals(MailSettings("relay.example.test", 2525, tls = false), configured)
    assertEquals("smtp://relay.example.test:2525", configured.deliveryRoute)
    assertEquals(MailSettings("backup.example.test", 587, tls = true), defaults)
    assertEquals("smtps://backup.example.test:587", defaults.deliveryRoute)
    assertTrue(invalid.isLeft)
    assertTrue(invalid.left.exists(_.getMessage.contains("SMTP port must be between 1 and 65535")))
    assertTrue(missing.isLeft)
    assertTrue(missing.left.exists(error => error.isMissingDataOnly && error.getMessage.contains("host")))
  }
}
