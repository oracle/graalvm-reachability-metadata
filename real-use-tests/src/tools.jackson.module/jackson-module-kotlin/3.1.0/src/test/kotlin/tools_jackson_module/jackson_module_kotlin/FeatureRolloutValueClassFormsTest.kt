/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonKey
import com.fasterxml.jackson.annotation.JsonValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.util.UUID

/**
 * Value classes with custom JSON forms: UUID-backed, nullable-underlying and generic value
 * classes, @JsonValue and @JsonKey on value classes, and value-class creators in companions,
 * as a feature-rollout service publishes and reloads per-tenant flag plans.
 */
public class FeatureRolloutValueClassFormsTest {

    @JvmInline
    value class TenantId(val uuid: UUID)

    @JvmInline
    value class ReleaseNote(val text: String?)

    @JvmInline
    value class Tagged<T>(val value: T)

    @JvmInline
    value class Share(val basisPoints: Int) {
        @JsonValue
        fun asPercent(): String = "%d.%02d%%".format(basisPoints / 100, basisPoints % 100)

        companion object {
            @JvmStatic
            @JsonCreator
            fun parse(percent: String): Share {
                val (whole, fraction) = percent.removeSuffix("%").split('.')
                return Share(whole.toInt() * 100 + fraction.toInt())
            }
        }
    }

    @JvmInline
    value class FlagKey(val name: String) {
        @JsonKey
        fun qualified(): String = "flag:$name"
    }

    data class FlagRollout(
        val share: Share,
        val note: ReleaseNote,
        val owner: TenantId? = null,
        val label: Tagged<String>,
        val weight: Tagged<Int>,
    )

    data class TenantPlan(
        val tenant: TenantId,
        val rollouts: List<FlagRollout>,
        val pilots: Map<TenantId, Share>,
    )

    data class PlanExport(val tenant: TenantId, val rollouts: Map<FlagKey, Share>)

    private val mapper = jacksonObjectMapper()

    private val acme: TenantId = TenantId(UUID.fromString("2f1d6a3e-8c47-4b5e-9a21-0d3c7e9b4f10"))
    private val globex: TenantId = TenantId(UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7"))

    @Test
    fun publishesValueClassesInTheirUnderlyingAndCustomForms(): Unit {
        val plan = TenantPlan(
            tenant = acme,
            rollouts = listOf(
                FlagRollout(
                    share = Share(1250),
                    note = ReleaseNote("staged rollout"),
                    owner = globex,
                    label = Tagged("checkout-v2"),
                    weight = Tagged(3),
                ),
                FlagRollout(
                    share = Share(10000),
                    note = ReleaseNote(null),
                    label = Tagged("dark-mode"),
                    weight = Tagged(1),
                ),
            ),
            pilots = linkedMapOf(globex to Share(505)),
        )

        val tree: JsonNode = mapper.readTree(mapper.writeValueAsString(plan))

        assertThat(tree.get("tenant").asString()).isEqualTo("2f1d6a3e-8c47-4b5e-9a21-0d3c7e9b4f10")
        val first: JsonNode = tree.get("rollouts").get(0)
        assertThat(first.get("share").asString()).isEqualTo("12.50%")
        assertThat(first.get("note").asString()).isEqualTo("staged rollout")
        assertThat(first.get("owner").asString()).isEqualTo("7c9e6679-7425-40de-944b-e07fc1f90ae7")
        assertThat(first.get("label").asString()).isEqualTo("checkout-v2")
        assertThat(first.get("weight").intValue()).isEqualTo(3)
        val second: JsonNode = tree.get("rollouts").get(1)
        assertThat(second.get("share").asString()).isEqualTo("100.00%")
        assertThat(second.get("note").isNull).isTrue()
        assertThat(second.get("owner").isNull).isTrue()
        assertThat(tree.get("pilots").propertyNames()).containsExactly(globex.uuid.toString())
        assertThat(tree.get("pilots").get(globex.uuid.toString()).asString()).isEqualTo("5.05%")
    }

    @Test
    fun reloadsAPlanThroughUuidNullableAndGenericValueClasses(): Unit {
        val body = """
            {
              "tenant": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
              "rollouts": [
                {"share": "33.33%", "note": null, "label": "search-ranking", "weight": 7},
                {
                  "share": "0.25%",
                  "note": "canary only",
                  "owner": "2f1d6a3e-8c47-4b5e-9a21-0d3c7e9b4f10",
                  "label": "new-billing",
                  "weight": 2
                }
              ],
              "pilots": {"2f1d6a3e-8c47-4b5e-9a21-0d3c7e9b4f10": "50.00%"}
            }
        """.trimIndent()

        val plan: TenantPlan = mapper.readValue(body)

        assertThat(plan.tenant).isEqualTo(globex)
        assertThat(plan.rollouts.map { it.share.basisPoints }).containsExactly(3333, 25)
        assertThat(plan.rollouts[0].note).isEqualTo(ReleaseNote(null))
        assertThat(plan.rollouts[0].owner).isNull()
        assertThat(plan.rollouts[1].note.text).isEqualTo("canary only")
        assertThat(plan.rollouts[1].owner).isEqualTo(acme)
        assertThat(plan.rollouts.map { it.label.value }).containsExactly("search-ranking", "new-billing")
        assertThat(plan.rollouts.sumOf { it.weight.value }).isEqualTo(9)
        assertThat(plan.pilots).containsExactlyEntriesOf(mapOf(acme to Share(5000)))
    }

    @Test
    fun roundTripsAPlanWithoutLosingTypedValues(): Unit {
        val plan = TenantPlan(
            tenant = globex,
            rollouts = listOf(
                FlagRollout(
                    share = Share(1),
                    note = ReleaseNote("hotfix"),
                    owner = acme,
                    label = Tagged("payments"),
                    weight = Tagged(Int.MAX_VALUE),
                ),
            ),
            pilots = mapOf(acme to Share(9999), globex to Share(0)),
        )

        val echoed: TenantPlan = mapper.readValue(mapper.writeValueAsString(plan))

        assertThat(echoed).isEqualTo(plan)
    }

    @Test
    fun exportsFlagMapsUnderJsonKeyQualifiedNames(): Unit {
        val export = PlanExport(
            tenant = acme,
            rollouts = linkedMapOf(FlagKey("checkout-v2") to Share(2000), FlagKey("dark-mode") to Share(75)),
        )

        val json: String = mapper.writeValueAsString(export)

        assertThat(json).isEqualTo(
            """{"tenant":"2f1d6a3e-8c47-4b5e-9a21-0d3c7e9b4f10",""" +
                """"rollouts":{"flag:checkout-v2":"20.00%","flag:dark-mode":"0.75%"}}""",
        )
    }
}
