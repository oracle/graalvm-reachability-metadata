/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_2_13

import com.typesafe.config.{Config, ConfigFactory, ConfigMemorySize}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import play.api.{ConfigLoader, Configuration}
import play.api.mvc.{Result, Results}

import scala.concurrent.duration._

/** Exercises Play typed configuration for a document intake policy with deployment overrides. */
@Timeout(60)
class ConfiguredDocumentIntakeTest {
  case class IntakePolicy(
      enabled: Boolean,
      maxBytes: Long,
      mediaTypes: Seq[String],
      retention: FiniteDuration,
      collection: String
  )

  implicit val policyLoader: ConfigLoader[IntakePolicy] = new ConfigLoader[IntakePolicy] {
    override def load(config: Config, path: String): IntakePolicy = {
      val settings: Configuration = Configuration(config.getConfig(path))
      IntakePolicy(
        settings.get[Boolean]("enabled"),
        settings.get[ConfigMemorySize]("max-size").toBytes,
        settings.get[Seq[String]]("media-types"),
        settings.get[FiniteDuration]("retention"),
        settings.getOptional[String]("collection").getOrElse("inbox")
      )
    }
  }

  class DocumentIntake(configuration: Configuration) {
    private val policy: IntakePolicy = configuration.get[IntakePolicy]("documents")

    def reserve(id: String, mediaType: String, bytes: Long): Result = {
      if (!policy.enabled) Results.ServiceUnavailable("Document intake is paused")
      else if (!policy.mediaTypes.contains(mediaType)) Results.UnsupportedMediaType
      else if (bytes > policy.maxBytes) Results.EntityTooLarge
      else Results.Created.withHeaders(
        "Location" -> s"/collections/${policy.collection}/documents/$id",
        "X-Retention-Seconds" -> policy.retention.toSeconds.toString
      )
    }
  }

  @Test
  def appliesDeploymentPolicyWhileRetainingApplicationDefaults(): Unit = {
    val defaults: Configuration = Configuration(ConfigFactory.parseString("""
      documents {
        enabled = true
        max-size = 1 MiB
        media-types = ["application/pdf"]
        retention = 24 hours
      }
    """))
    val standard: DocumentIntake = new DocumentIntake(defaults)
    val defaultReservation: Result = standard.reserve("DOC-42", "application/pdf", 4096)
    assertEquals(201, defaultReservation.header.status)
    assertEquals(Some("/collections/inbox/documents/DOC-42"),
      defaultReservation.header.headers.get("Location"))
    assertEquals(Some("86400"), defaultReservation.header.headers.get("X-Retention-Seconds"))
    assertEquals(415, standard.reserve("DOC-43", "image/png", 512).header.status)

    val deployment: Configuration = Configuration(ConfigFactory.parseString("""
      documents {
        max-size = 2 KiB
        media-types = ["application/pdf", "image/png"]
        collection = "claims"
      }
    """)).withFallback(defaults)
    val claims: DocumentIntake = new DocumentIntake(deployment)
    val reservation: Result = claims.reserve("DOC-43", "image/png", 2048)
    assertEquals(201, reservation.header.status)
    assertEquals(Some("/collections/claims/documents/DOC-43"),
      reservation.header.headers.get("Location"))
    assertEquals(Some("86400"), reservation.header.headers.get("X-Retention-Seconds"))
    assertEquals(201, claims.reserve("DOC-44", "application/pdf", 1024).header.status)
    assertEquals(413, claims.reserve("DOC-45", "image/png", 2049).header.status)
    assertEquals(415, claims.reserve("DOC-46", "text/html", 512).header.status)

    val paused: DocumentIntake = new DocumentIntake(
      Configuration("documents.enabled" -> false).withFallback(deployment)
    )
    assertEquals(503, paused.reserve("DOC-47", "application/pdf", 512).header.status)
    assertEquals(201, standard.reserve("DOC-48", "application/pdf", 4096).header.status)
  }
}
