/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_generic_3

import io.circe.CursorOp
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Json
import io.circe.generic.semiauto.deriveDecoder
import io.circe.parser.parse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises accumulating decoder derivation for worker configuration diagnostics. */
@Timeout(60)
class WorkerConfigurationTest {
  final case class QueueSettings(name: String, concurrency: Int, enabled: Boolean)

  object QueueSettings {
    given Decoder[QueueSettings] = deriveDecoder[QueueSettings]
  }

  final case class WorkerConfiguration(queues: List[QueueSettings], maxRetries: Int, owner: Option[String])

  object WorkerConfiguration {
    given Decoder[WorkerConfiguration] = deriveDecoder[WorkerConfiguration]
  }

  @Test
  def reportsAllInvalidSettingsBeforeAcceptingTheCorrectedConfiguration(): Unit = {
    val invalid: Json = parseConfiguration("""
      {
        "queues": [
          {"name": "invoices", "concurrency": "many", "enabled": "yes"},
          {"name": "receipts", "concurrency": 2, "enabled": true}
        ],
        "maxRetries": false
      }
      """)
    val failures: List[DecodingFailure] = Decoder[WorkerConfiguration]
      .decodeAccumulating(invalid.hcursor)
      .fold(_.toList, _ => throw new AssertionError("Invalid configuration was accepted"))
    val invalidSettings: List[String] = failures.map(failure => CursorOp.opsToPath(failure.history)).sorted

    assertThat(invalidSettings).isEqualTo(List(
      ".maxRetries",
      ".queues[0].concurrency",
      ".queues[0].enabled"
    ))
    assertThat(failures.forall(_.message.nonEmpty)).isTrue()

    val corrected: Json = parseConfiguration("""
      {
        "queues": [
          {"name": "invoices", "concurrency": 4, "enabled": true},
          {"name": "receipts", "concurrency": 2, "enabled": false}
        ],
        "maxRetries": 3
      }
      """)
    val configuration: WorkerConfiguration = Decoder[WorkerConfiguration]
      .decodeAccumulating(corrected.hcursor)
      .fold(errors => throw new AssertionError(s"Corrected configuration was rejected: $errors"), identity)

    assertThat(configuration).isEqualTo(WorkerConfiguration(
      List(QueueSettings("invoices", 4, true), QueueSettings("receipts", 2, false)),
      3,
      None
    ))
    val activeQueues: List[(String, Int)] = configuration.queues
      .filter(_.enabled)
      .map(queue => queue.name -> queue.concurrency)
    assertThat(activeQueues).isEqualTo(List("invoices" -> 4))
    assertThat(Decoder[WorkerConfiguration].decodeJson(corrected)).isEqualTo(Right(configuration))
  }

  private def parseConfiguration(input: String): Json = parse(input).fold(
    error => throw new AssertionError(s"Invalid configuration JSON: $error"),
    identity
  )
}
