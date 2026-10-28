/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_generic_3

import io.circe.Codec
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser.decode
import io.circe.parser.parse
import io.circe.syntax.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises parameterized sealed-trait codec derivation for typed batch-export outcomes. */
@Timeout(60)
class BatchExportOutcomeTest {
  sealed trait Outcome[A]

  final case class Completed[A](value: A) extends Outcome[A]

  final case class Rejected[A](reason: String, retryable: Boolean) extends Outcome[A]

  object Outcome {
    given [A: Decoder: Encoder]: Codec.AsObject[Outcome[A]] = deriveCodec[Outcome[A]]
  }

  final case class Artifact(fileName: String, rows: Int)

  object Artifact {
    given Codec.AsObject[Artifact] = deriveCodec[Artifact]
  }

  @Test
  def restoresExportResultsAndPublishesTheRetriedJob(): Unit = {
    val stored: String = """
      [
        {"Completed": {"value": {"fileName": "orders.csv", "rows": 42}}},
        {"Rejected": {"reason": "destination unavailable", "retryable": true}},
        {"Rejected": {"reason": "unknown report", "retryable": false}}
      ]
      """
    val outcomes: List[Outcome[Artifact]] = decode[List[Outcome[Artifact]]](stored).fold(
      error => throw new AssertionError(s"Export outcomes were rejected: $error"),
      identity
    )
    assertThat(outcomes).isEqualTo(List(
      Completed(Artifact("orders.csv", 42)),
      Rejected[Artifact]("destination unavailable", true),
      Rejected[Artifact]("unknown report", false)
    ))
    assertThat(outcomes.asJson).isEqualTo(fixture(stored))

    val downloads: List[String] = outcomes.collect {
      case Completed(artifact) => artifact.fileName
    }
    val retryPositions: List[Int] = outcomes.zipWithIndex.collect {
      case (Rejected(_, true), index) => index
    }
    assertThat(downloads).isEqualTo(List("orders.csv"))
    assertThat(retryPositions).isEqualTo(List(1))

    val retried: List[Outcome[Artifact]] = outcomes.updated(
      retryPositions.head,
      Completed(Artifact("customers.csv", 17))
    )
    val published: Json = retried.asJson
    assertThat(published).isEqualTo(fixture("""
      [
        {"Completed": {"value": {"fileName": "orders.csv", "rows": 42}}},
        {"Completed": {"value": {"fileName": "customers.csv", "rows": 17}}},
        {"Rejected": {"reason": "unknown report", "retryable": false}}
      ]
      """))
    assertThat(decode[List[Outcome[Artifact]]](published.noSpaces)).isEqualTo(Right(retried))

    // The same result protocol carries a scalar receipt for the downstream upload stage.
    val upload: Outcome[String] = Completed("receipt-83")
    val uploadJson: Json = upload.asJson
    assertThat(uploadJson).isEqualTo(fixture("""
      {"Completed": {"value": "receipt-83"}}
      """))
    assertThat(decode[Outcome[String]](uploadJson.noSpaces)).isEqualTo(Right(upload))
    assertThat(decode[Outcome[Artifact]](uploadJson.noSpaces).isLeft).isTrue()
    assertThat(decode[Outcome[Artifact]]("""
      {"Completed": {"value": {"fileName": "orders.csv", "rows": "many"}}}
      """).isLeft).isTrue()
  }

  private def fixture(input: String): Json = parse(input).fold(
    error => throw new AssertionError(s"Invalid batch outcome fixture: $error"),
    identity
  )
}
