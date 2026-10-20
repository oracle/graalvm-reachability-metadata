/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_json_3

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import zio.json.*
import zio.json.ast.Json

/** Exercises configurable codec derivation for strict snake-case deployment manifests. */
class DeploymentManifestTest {
  final case class RolloutPolicy(maxUnavailable: Int, healthCheckPath: String)
  final case class DeploymentManifest(
    serviceName: String,
    imageTag: String,
    rolloutPolicy: RolloutPolicy,
    changeTicket: Option[String]
  )

  given JsonCodecConfiguration = JsonCodecConfiguration(
    fieldNameMapping = SnakeCase,
    allowExtraFields = false,
    explicitNulls = true
  )
  given JsonCodec[RolloutPolicy] = DeriveJsonCodec.gen[RolloutPolicy]
  given JsonCodec[DeploymentManifest] = DeriveJsonCodec.gen[DeploymentManifest]

  @Test
  @Timeout(30)
  def validateAndPublishDeploymentManifest(): Unit = {
    val incoming: String = """
      {
        "service_name": "catalog",
        "image_tag": "release-27",
        "rollout_policy": {"max_unavailable": 1, "health_check_path": "/ready"}
      }
      """
    val expected: DeploymentManifest = DeploymentManifest(
      "catalog", "release-27", RolloutPolicy(1, "/ready"), None
    )
    val decoded: Either[String, DeploymentManifest] = incoming.fromJson[DeploymentManifest]
    assertEquals(Right(expected), decoded)

    val accepted: DeploymentManifest = decoded.fold(error => fail[DeploymentManifest](error), identity)
    val approved: DeploymentManifest = accepted.copy(changeTicket = Some("CHG-412"))
    val published: String = approved.toJson
    val expectedPublished: String = """
      {
        "service_name": "catalog",
        "image_tag": "release-27",
        "rollout_policy": {"max_unavailable": 1, "health_check_path": "/ready"},
        "change_ticket": "CHG-412"
      }
      """
    assertEquals(expectedPublished.fromJson[Json], published.fromJson[Json])
    assertEquals(Right(approved), published.fromJson[DeploymentManifest])

    val withoutTicket: String = accepted.toJson
    assertTrue(withoutTicket.contains("\"change_ticket\":null"), withoutTicket)
    assertEquals(Right(expected), withoutTicket.fromJson[DeploymentManifest])

    // Unknown rollout settings must fail rather than silently weakening the policy.
    val misspelledPolicy: String = incoming.replace(
      "\"max_unavailable\": 1", "\"max_unavailable\": 1, \"max_unavailble\": 0"
    )
    val rejectedPolicy: Either[String, DeploymentManifest] = misspelledPolicy.fromJson[DeploymentManifest]
    assertTrue(rejectedPolicy.isLeft, "Unknown nested rollout settings must be rejected")
    val policyError: String = rejectedPolicy.swap.toOption.get
    assertTrue(policyError.contains("rollout_policy"), policyError)

    val unsupportedSetting: String = incoming.replace(
      "\"image_tag\": \"release-27\"", "\"image_tag\": \"release-27\", \"force_deploy\": true"
    )
    assertTrue(unsupportedSetting.fromJson[DeploymentManifest].isLeft,
      "Unknown deployment controls must be rejected")
  }
}
