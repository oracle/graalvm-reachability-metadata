/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_fx_coroutines_jvm

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.ExperimentalRaiseAccumulateApi
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.right
import arrow.fx.coroutines.ScopedRaiseAccumulate
import arrow.fx.coroutines.parMapOrAccumulate
import arrow.fx.coroutines.parZipOrAccumulate
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Exercises Arrow's `ScopedRaiseAccumulate` branch scope: inside `parZipOrAccumulate` and
 * `parMapOrAccumulate` branches, soft checks (`ensureOrAccumulate`, `ensureNotNullOrAccumulate`,
 * `bindOrAccumulate`, `bindAllOrAccumulate`) keep a branch running past its first problem, their
 * values are read by delegation, and the scope doubles as a `CoroutineScope` for nested `async`.
 *
 * An insurer's claim intake checks the policy, scans the attached documents and prices the line
 * items in parallel. A rejected claim gets one letter listing every problem found by every
 * branch, joined with the `combine` function of the `Raise<String>` overload.
 */
@OptIn(ExperimentalRaiseAccumulateApi::class)
public class ClaimIntakeScopedAccumulateTest {

    /** An insurance policy on file. */
    public data class Policy(val number: String, val holder: String, val active: Boolean, val limitCents: Long)

    /** A document attached to a claim. */
    public data class Attachment(val name: String, val pages: Int)

    /** A document whose scan was accepted. */
    public data class ScannedDocument(val name: String, val pages: Int, val scannedBy: String)

    /** A claimed expense. */
    public data class LineItem(val code: String, val amountCents: Long)

    /** A submitted claim. */
    public data class Claim(
        val id: String,
        val policyNumber: String,
        val attachments: List<Attachment>,
        val items: List<LineItem>,
    )

    /** A claim that passed intake. */
    public data class AcceptedClaim(
        val id: String,
        val holder: String,
        val documents: List<ScannedDocument>,
        val payableCents: Long,
    )

    /** Claim intake backed by in-memory policy and tariff registers. */
    public class ClaimIntake {

        private val policies: Map<String, Policy> = mapOf(
            "P-100" to Policy("P-100", "Ada Byron", active = true, limitCents = 50_000),
            "P-200" to Policy("P-200", "Grace Hopper", active = false, limitCents = 10_000),
        )

        private val tariffs: Map<String, Long> = mapOf("XRAY" to 12_000, "VISIT" to 4_000, "CAST" to 9_000)

        /** Every check that ran, so tests can see a branch kept going after a soft problem. */
        public val checksRun: MutableSet<String> = ConcurrentHashMap.newKeySet()

        /** Validates the three parts of [claim] in parallel and reports all problems as one letter. */
        public suspend fun intake(claim: Claim): Either<String, AcceptedClaim> = either {
            parZipOrAccumulate(
                Dispatchers.Default + CoroutineName("intake-${claim.id}"),
                { left: String, right: String -> "$left; $right" },
                { checkPolicy(claim) },
                { scanAttachments(claim) },
                { priceItems(claim) },
            ) { policy: Policy, documents: List<ScannedDocument>, payable: Long ->
                AcceptedClaim(claim.id, policy.holder, documents, minOf(payable, policy.limitCents))
            }
        }

        /** Validates a batch of claim ids; each id's soft problems accumulate into its own entry. */
        public suspend fun precheck(claimIds: List<String>, policyOf: Map<String, String>): Either<String, List<String>> =
            claimIds.parMapOrAccumulate(
                Dispatchers.Default,
                2,
                { left: String, right: String -> "$left | $right" },
            ) { claimId: String ->
                checksRun.add("precheck:$claimId")
                ensureOrAccumulate(claimId.startsWith("C-")) { "$claimId: malformed id" }
                val policy: Policy by ensureNotNullOrAccumulate(policies[policyOf[claimId]]) {
                    "$claimId: no policy"
                }
                "$claimId:${policy.number}"
            }

        private fun ScopedRaiseAccumulate<String>.checkPolicy(claim: Claim): Policy {
            checksRun.add("policy:${claim.policyNumber}")
            val policy: Policy? = policies[claim.policyNumber]
            if (policy == null) {
                raise("policy ${claim.policyNumber} not found")
            }
            val active: Unit by ensureOrAccumulate(policy.active) { "policy ${policy.number} lapsed" }
            checksRun.add("policy-holder:${policy.number}")
            val named: Unit by ensureOrAccumulate(policy.holder.isNotBlank()) { "policy ${policy.number} has no holder" }
            // Reading a soft check's value raises every problem the branch accumulated so far.
            return policy.also { active; named }
        }

        private suspend fun ScopedRaiseAccumulate<String>.scanAttachments(claim: Claim): List<ScannedDocument> {
            val branch: String = coroutineContext[CoroutineName]?.name ?: "unnamed"
            val scans: List<Either<String, ScannedDocument>> = claim.attachments
                .map { attachment -> async(Dispatchers.IO) { scan(attachment, branch) } }
                .awaitAll()
            val documents: List<ScannedDocument> by scans.bindAllOrAccumulate()
            val attached: Unit by ensureOrAccumulate(claim.attachments.isNotEmpty()) { "no documents attached" }
            return documents.also { attached }
        }

        private fun scan(attachment: Attachment, branch: String): Either<String, ScannedDocument> {
            checksRun.add("scan:${attachment.name}")
            return if (attachment.pages > 0) {
                ScannedDocument(attachment.name, attachment.pages, branch).right()
            } else {
                "document ${attachment.name} is empty".left()
            }
        }

        private fun ScopedRaiseAccumulate<String>.priceItems(claim: Claim): Long {
            val prices: List<Long> = claim.items.mapOrAccumulate { item: LineItem ->
                checksRun.add("price:${item.code}")
                val tariff: Long = tariffs[item.code] ?: raise("unknown tariff ${item.code}")
                ensure(item.amountCents <= tariff) { "${item.code} exceeds tariff $tariff" }
                item.amountCents
            }
            val priced: Either<String, Long> = if (prices.isEmpty()) "no line items".left() else prices.sum().right()
            val total: Long by priced.bindOrAccumulate()
            return total
        }
    }

    @Test
    public fun acceptsAClaimByCombiningEveryBranch(): Unit = runBlocking {
        val intake = ClaimIntake()
        val claim = Claim(
            "C-1",
            "P-100",
            listOf(Attachment("referral.pdf", 2), Attachment("invoice.pdf", 1)),
            listOf(LineItem("XRAY", 11_000), LineItem("VISIT", 4_000)),
        )

        val result: Either<String, AcceptedClaim> = intake.intake(claim)

        val expected = AcceptedClaim(
            "C-1",
            "Ada Byron",
            listOf(
                ScannedDocument("referral.pdf", 2, "intake-C-1"),
                ScannedDocument("invoice.pdf", 1, "intake-C-1"),
            ),
            15_000,
        )
        assertEquals(Either.Right(expected), result)
        assertEquals(
            setOf(
                "policy:P-100",
                "policy-holder:P-100",
                "scan:referral.pdf",
                "scan:invoice.pdf",
                "price:XRAY",
                "price:VISIT",
            ),
            intake.checksRun,
        )
    }

    @Test
    public fun rejectionLetterListsEveryProblemFromEveryBranch(): Unit = runBlocking {
        val intake = ClaimIntake()
        val claim = Claim(
            "C-2",
            "P-200",
            listOf(Attachment("photo.jpg", 0), Attachment("note.txt", 1), Attachment("blank.pdf", 0)),
            listOf(LineItem("CAST", 9_500), LineItem("MRI", 30_000), LineItem("VISIT", 3_000)),
        )

        val result: Either<String, AcceptedClaim> = intake.intake(claim)

        assertEquals(
            Either.Left(
                "policy P-200 lapsed; " +
                    "document photo.jpg is empty; document blank.pdf is empty; " +
                    "CAST exceeds tariff 9000; unknown tariff MRI",
            ),
            result,
        )
        assertEquals(true, intake.checksRun.contains("policy-holder:P-200"))
        assertEquals(true, intake.checksRun.containsAll(listOf("scan:photo.jpg", "scan:note.txt", "scan:blank.pdf")))
        assertEquals(true, intake.checksRun.containsAll(listOf("price:CAST", "price:MRI", "price:VISIT")))
    }

    @Test
    public fun hardFailureEndsOnlyItsOwnBranch(): Unit = runBlocking {
        val intake = ClaimIntake()
        val claim = Claim("C-3", "P-999", emptyList(), emptyList())

        val result: Either<String, AcceptedClaim> = intake.intake(claim)

        assertEquals(
            Either.Left("policy P-999 not found; no documents attached; no line items"),
            result,
        )
        assertEquals(setOf("policy:P-999"), intake.checksRun)
    }

    @Test
    public fun batchPrecheckAccumulatesEachClaimsSoftProblems(): Unit = runBlocking {
        val intake = ClaimIntake()
        val policyOf: Map<String, String> = mapOf("C-10" to "P-100", "C-11" to "P-200", "X-12" to "P-404")

        val passed: Either<String, List<String>> = intake.precheck(listOf("C-10", "C-11"), policyOf)
        val failed: Either<String, List<String>> = intake.precheck(listOf("C-10", "X-12", "C-13"), policyOf)

        assertEquals(Either.Right(listOf("C-10:P-100", "C-11:P-200")), passed)
        assertEquals(Either.Left("X-12: malformed id | X-12: no policy | C-13: no policy"), failed)
        assertEquals(
            setOf("precheck:C-10", "precheck:C-11", "precheck:X-12", "precheck:C-13"),
            intake.checksRun,
        )
    }
}
