/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_module.jackson_module_kotlin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

/**
 * Kotlin value classes and unsigned integer types, used as strongly typed identifiers,
 * amounts and map keys, as a ledger service publishes and ingests account snapshots.
 */
public class LedgerValueClassBindingTest {

    @JvmInline
    value class AccountId(val value: String)

    @JvmInline
    value class Cents(val amount: Long) {
        operator fun plus(other: Cents): Cents = Cents(amount + other.amount)
    }

    @JvmInline
    value class BranchCode(val code: Int)

    data class Posting(val account: AccountId, val amount: Cents, val sequence: ULong)

    data class LedgerSnapshot(
        val branch: BranchCode,
        val postings: List<Posting>,
        val balances: Map<AccountId, Cents>,
        val openSessions: UInt,
        val retryBudget: UShort = 3u,
        val auditor: AccountId? = null,
    ) {
        val postedTotal: Cents
            get() = postings.fold(Cents(0)) { total, posting -> total + posting.amount }
    }

    private val mapper = jacksonObjectMapper()

    private val alice = AccountId("acc-alice")
    private val bob = AccountId("acc-bob")

    @Test
    fun writesValueClassesAsTheirUnderlyingJsonValues(): Unit {
        val snapshot = LedgerSnapshot(
            branch = BranchCode(204),
            postings = listOf(
                Posting(account = alice, amount = Cents(12_500), sequence = 1uL),
                Posting(account = bob, amount = Cents(-2_500), sequence = ULong.MAX_VALUE),
            ),
            balances = linkedMapOf(alice to Cents(12_500), bob to Cents(-2_500)),
            openSessions = 4_000_000_000u,
            auditor = bob,
        )

        val tree: JsonNode = mapper.readTree(mapper.writeValueAsString(snapshot))

        assertThat(tree.get("branch").intValue()).isEqualTo(204)
        assertThat(tree.get("auditor").asString()).isEqualTo("acc-bob")
        assertThat(tree.get("openSessions").longValue()).isEqualTo(4_000_000_000L)
        assertThat(tree.get("retryBudget").intValue()).isEqualTo(3)
        val firstPosting: JsonNode = tree.get("postings").get(0)
        assertThat(firstPosting.get("account").asString()).isEqualTo("acc-alice")
        assertThat(firstPosting.get("amount").longValue()).isEqualTo(12_500L)
        assertThat(tree.get("postings").get(1).get("sequence").bigIntegerValue().toString())
            .isEqualTo("18446744073709551615")
        assertThat(tree.get("balances").propertyNames()).containsExactly("acc-alice", "acc-bob")
        assertThat(tree.get("balances").get("acc-bob").longValue()).isEqualTo(-2_500L)
    }

    @Test
    fun readsValueClassesAndUnsignedNumbersFromAnIngestedSnapshot(): Unit {
        val body = """
            {
              "branch": 17,
              "postings": [
                {"account": "acc-alice", "amount": 700, "sequence": 9223372036854775808},
                {"account": "acc-bob", "amount": 300, "sequence": 42}
              ],
              "balances": {"acc-alice": 700, "acc-bob": 300},
              "openSessions": 3000000000
            }
        """.trimIndent()

        val snapshot: LedgerSnapshot = mapper.readValue(body)

        assertThat(snapshot.branch).isEqualTo(BranchCode(17))
        assertThat(snapshot.postings.map { it.account }).containsExactly(alice, bob)
        assertThat(snapshot.postings[0].sequence).isEqualTo(9_223_372_036_854_775_808uL)
        assertThat(snapshot.postings[1].sequence).isEqualTo(42uL)
        assertThat(snapshot.balances).containsEntry(alice, Cents(700)).containsEntry(bob, Cents(300))
        assertThat(snapshot.openSessions).isEqualTo(3_000_000_000u)
        assertThat(snapshot.retryBudget).isEqualTo(3.toUShort())
        assertThat(snapshot.auditor).isNull()
        assertThat(snapshot.postedTotal).isEqualTo(Cents(1_000))
    }

    @Test
    fun roundTripsASnapshotWithoutLosingTypedIdentifiers(): Unit {
        val snapshot = LedgerSnapshot(
            branch = BranchCode(9),
            postings = listOf(Posting(account = alice, amount = Cents(1), sequence = 7uL)),
            balances = mapOf(alice to Cents(1)),
            openSessions = UInt.MAX_VALUE,
            retryBudget = UShort.MAX_VALUE,
            auditor = alice,
        )

        val echoed: LedgerSnapshot = mapper.readValue(mapper.writeValueAsString(snapshot))

        assertThat(echoed).isEqualTo(snapshot)
        assertThat(echoed.balances.keys.single().value).isEqualTo("acc-alice")
    }

    @Test
    fun bindsATopLevelMapKeyedByAValueClass(): Unit {
        val limits: Map<AccountId, ULong> = mapper.readValue(
            """{"acc-alice": 18446744073709551615, "acc-bob": 0}""",
        )

        assertThat(limits).containsEntry(alice, ULong.MAX_VALUE).containsEntry(bob, 0uL)
        assertThat(mapper.writeValueAsString(limits))
            .isEqualTo("""{"acc-alice":18446744073709551615,"acc-bob":0}""")
    }
}
