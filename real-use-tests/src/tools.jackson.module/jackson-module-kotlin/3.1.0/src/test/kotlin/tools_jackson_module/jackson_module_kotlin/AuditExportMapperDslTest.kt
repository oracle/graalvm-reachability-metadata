/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.deser.std.StdDeserializer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.std.StdSerializer
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.addDeserializer
import tools.jackson.module.kotlin.addMixIn
import tools.jackson.module.kotlin.addSerializer
import tools.jackson.module.kotlin.jsonMapper
import tools.jackson.module.kotlin.kotlinModule
import tools.jackson.module.kotlin.readValue
import tools.jackson.module.kotlin.withConfigOverride

/**
 * The Kotlin mapper-configuration DSL (`jsonMapper {}` with `kotlinModule {}`, reified
 * `addMixIn`, `KClass`-keyed serializer registration on `SimpleModule` and reified
 * `withConfigOverride`), as an audit service builds the one mapper it exports entries with.
 */
public class AuditExportMapperDslTest {

    /** A money type from a billing SDK the audit service cannot annotate. */
    class Money(val minorUnits: Long, val currency: String)

    /** An account type from a vendor SDK, shaped for JSON only through a mix-in. */
    class VendorAccount(val accountNumber: String, val displayName: String, val riskScore: Int = 0)

    abstract class VendorAccountMixin {
        @get:JsonProperty("name")
        abstract val displayName: String

        @get:JsonIgnore
        abstract val riskScore: Int
    }

    /** Writes money as `"12.50 EUR"`, the form auditors read in exported reports. */
    class MoneySerializer : StdSerializer<Money>(Money::class.java) {
        override fun serialize(value: Money, gen: JsonGenerator, ctxt: SerializationContext) {
            val whole: Long = value.minorUnits / 100
            val cents: String = (value.minorUnits % 100).toString().padStart(2, '0')
            gen.writeString("$whole.$cents ${value.currency}")
        }
    }

    class MoneyDeserializer : StdDeserializer<Money>(Money::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Money {
            val (amount: String, currency: String) = p.string.split(' ')
            val (whole: String, cents: String) = amount.split('.')
            return Money(whole.toLong() * 100 + cents.toLong(), currency)
        }
    }

    data class AuditEntry(
        val id: Long,
        val action: String,
        val account: VendorAccount,
        val charged: Money,
        val note: String? = null,
        val approvedBy: String? = null,
    )

    data class Snapshot(val label: String, val previousValue: String?)

    private val mapper: JsonMapper = jsonMapper {
        addModule(kotlinModule {
            withReflectionCacheSize(64)
            enable(KotlinFeature.NullIsSameAsDefault)
        })
        addModule(
            SimpleModule("billing")
                .addSerializer(Money::class, MoneySerializer())
                .addDeserializer(Money::class, MoneyDeserializer()),
        )
        addMixIn<VendorAccount, VendorAccountMixin>()
        withConfigOverride<AuditEntry> {
            it.setInclude(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, null))
        }
    }

    private val entry = AuditEntry(
        id = 41,
        action = "refund.issued",
        account = VendorAccount("ACC-0042", "Northwind Traders", riskScore = 87),
        charged = Money(1250, "EUR"),
        note = "customer goodwill",
    )

    /** The exported entry carries the custom money form and the mix-in's naming and omission. */
    @Test
    fun exportsEntryShapedByModulesMixInsAndConfigOverride() {
        val tree: JsonNode = mapper.readTree(mapper.writeValueAsString(entry))

        assertThat(tree.get("charged").asString()).isEqualTo("12.50 EUR")
        assertThat(tree.get("account").get("name").asString()).isEqualTo("Northwind Traders")
        assertThat(tree.get("account").get("accountNumber").asString()).isEqualTo("ACC-0042")
        assertThat(tree.get("account").has("displayName")).isFalse()
        assertThat(tree.get("account").has("riskScore")).isFalse()
        assertThat(tree.get("note").asString()).isEqualTo("customer goodwill")
        assertThat(tree.has("approvedBy")).isFalse()
    }

    /** The config override is scoped to its type: other classes still write their nulls. */
    @Test
    fun configOverrideAppliesOnlyToItsTargetType() {
        val tree: JsonNode = mapper.readTree(mapper.writeValueAsString(Snapshot("before", null)))

        assertThat(tree.has("previousValue")).isTrue()
        assertThat(tree.get("previousValue").isNull).isTrue()
    }

    /** An exported entry imports back through the same mapper into an equal Kotlin value. */
    @Test
    fun importsExportedEntryBackThroughTheSameMapper() {
        val restored: AuditEntry = mapper.readValue(mapper.writeValueAsString(entry))

        assertThat(restored.id).isEqualTo(41L)
        assertThat(restored.action).isEqualTo("refund.issued")
        assertThat(restored.charged.minorUnits).isEqualTo(1250L)
        assertThat(restored.charged.currency).isEqualTo("EUR")
        assertThat(restored.account.accountNumber).isEqualTo("ACC-0042")
        assertThat(restored.account.displayName).isEqualTo("Northwind Traders")
        assertThat(restored.account.riskScore).isEqualTo(0)
        assertThat(restored.note).isEqualTo("customer goodwill")
        assertThat(restored.approvedBy).isNull()
    }

    /** Hand-written import files use the vendor's renamed field and the auditors' money form. */
    @Test
    fun importsHandWrittenEntryWithNullsFallingBackToDefaults() {
        val json = """
            {
              "id": 7,
              "action": "charge.disputed",
              "account": {"accountNumber": "ACC-0099", "name": "Contoso", "riskScore": 55},
              "charged": "3.05 USD",
              "note": null
            }
        """.trimIndent()

        val imported: AuditEntry = mapper.readValue(json)

        assertThat(imported.account.displayName).isEqualTo("Contoso")
        assertThat(imported.account.riskScore).isEqualTo(0)
        assertThat(imported.charged.minorUnits).isEqualTo(305L)
        assertThat(imported.charged.currency).isEqualTo("USD")
        assertThat(imported.note).isNull()
    }
}
