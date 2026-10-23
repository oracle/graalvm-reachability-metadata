/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Exercises Kotlin Duration JSON conversion for a budget-limited retry policy.
 * A job runner reads ISO-8601 timeouts and backoffs, plans attempts within its execution budget,
 * and publishes the policy so another worker can construct the same schedule.
 */
public class DurationRetryPolicyTest {

    public data class RetryPolicy(
        val jobType: String,
        val attemptTimeout: Duration,
        val backoffs: List<Duration>,
        val totalBudget: Duration,
    ) {
        public fun attemptStartOffsets(): List<Duration> {
            val starts: MutableList<Duration> = mutableListOf()
            var elapsed: Duration = Duration.ZERO
            for (delay: Duration in listOf(Duration.ZERO) + backoffs) {
                val start: Duration = elapsed + delay
                if (start + attemptTimeout > totalBudget) {
                    break
                }
                starts.add(start)
                elapsed = start + attemptTimeout
            }
            return starts
        }
    }

    @Test
    @Timeout(60)
    public fun plansBoundedRetriesAndPublishesPortablePolicyDurations(): Unit {
        val module: KotlinModule = KotlinModule.Builder()
            .enable(KotlinFeature.UseJavaDurationConversion)
            .build()
        val mapper: ObjectMapper = ObjectMapper()
            .registerModule(JavaTimeModule())
            .registerModule(module)
            .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
        val configurationJson: String = """
            {
              "jobType": "invoice-delivery",
              "attemptTimeout": "PT2S",
              "backoffs": ["PT0.25S", "PT1.5S", "PT5S"],
              "totalBudget": "PT8S"
            }
        """.trimIndent()

        val policy: RetryPolicy = mapper.readValue(configurationJson)

        assertEquals("invoice-delivery", policy.jobType)
        assertEquals(2.seconds, policy.attemptTimeout)
        assertEquals(listOf(250.milliseconds, 1_500.milliseconds, 5.seconds), policy.backoffs)
        assertEquals(8.seconds, policy.totalBudget)
        val expectedStarts: List<Duration> = listOf(
            Duration.ZERO,
            2_250.milliseconds,
            5_750.milliseconds,
        )
        assertEquals(expectedStarts, policy.attemptStartOffsets())
        assertEquals(7_750.milliseconds, policy.attemptStartOffsets().last() + policy.attemptTimeout)

        val publishedJson: String = mapper.writeValueAsString(policy)
        val published: JsonNode = mapper.readTree(publishedJson)
        assertEquals(mapper.readTree(configurationJson), published)
        val replacementPolicy: RetryPolicy = mapper.readValue(publishedJson)
        assertEquals(policy, replacementPolicy)
        assertEquals(expectedStarts, replacementPolicy.attemptStartOffsets())
    }
}
