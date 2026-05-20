/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_kotlin

import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.bulkhead.BulkheadFullException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State
import io.github.resilience4j.kotlin.bulkhead.BulkheadConfig as KotlinBulkheadConfig
import io.github.resilience4j.kotlin.bulkhead.ThreadPoolBulkheadConfig as KotlinThreadPoolBulkheadConfig
import io.github.resilience4j.kotlin.bulkhead.bulkhead as resilienceBulkhead
import io.github.resilience4j.kotlin.bulkhead.executeSuspendFunction as executeBulkheadSuspendFunction
import io.github.resilience4j.kotlin.circuitbreaker.CircuitBreakerConfig as KotlinCircuitBreakerConfig
import io.github.resilience4j.kotlin.circuitbreaker.CircuitBreakerRegistry as KotlinCircuitBreakerRegistry
import io.github.resilience4j.kotlin.circuitbreaker.addCircuitBreakerConfig
import io.github.resilience4j.kotlin.circuitbreaker.circuitBreaker as resilienceCircuitBreaker
import io.github.resilience4j.kotlin.circuitbreaker.executeSuspendFunction as executeCircuitBreakerSuspendFunction
import io.github.resilience4j.kotlin.circuitbreaker.withCircuitBreakerConfig
import io.github.resilience4j.kotlin.micrometer.TimerConfig as KotlinTimerConfig
import io.github.resilience4j.kotlin.micrometer.executeSuspendFunction as executeTimerSuspendFunction
import io.github.resilience4j.kotlin.micrometer.timer as resilienceTimer
import io.github.resilience4j.kotlin.ratelimiter.RateLimiterConfig as KotlinRateLimiterConfig
import io.github.resilience4j.kotlin.ratelimiter.decorateFunction as decorateRateLimitedFunction
import io.github.resilience4j.kotlin.ratelimiter.rateLimiter as resilienceRateLimiter
import io.github.resilience4j.kotlin.ratelimiter.executeSuspendFunction as executeRateLimiterSuspendFunction
import io.github.resilience4j.kotlin.retry.RetryConfig as KotlinRetryConfig
import io.github.resilience4j.kotlin.retry.RetryRegistry as KotlinRetryRegistry
import io.github.resilience4j.kotlin.retry.addRetryConfig
import io.github.resilience4j.kotlin.retry.decorateFunction as decorateRetryFunction
import io.github.resilience4j.kotlin.retry.executeSuspendFunction as executeRetrySuspendFunction
import io.github.resilience4j.kotlin.retry.retry as resilienceRetry
import io.github.resilience4j.kotlin.retry.withRetryConfig
import io.github.resilience4j.kotlin.timelimiter.TimeLimiterConfig as KotlinTimeLimiterConfig
import io.github.resilience4j.kotlin.timelimiter.decorateFunction as decorateTimeLimiterFunction
import io.github.resilience4j.kotlin.timelimiter.executeFunction as executeTimeLimiterFunction
import io.github.resilience4j.kotlin.timelimiter.executeSuspendFunction as executeTimeLimiterSuspendFunction
import io.github.resilience4j.kotlin.timelimiter.timeLimiter as resilienceTimeLimiter
import io.github.resilience4j.micrometer.Timer as ResilienceTimer
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.timelimiter.TimeLimiter
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

public class Resilience4j_kotlinTest {
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun dslBuildersCreateTaggedRegistriesAndConfigs(): Unit {
        val circuitBreakerRegistry = KotlinCircuitBreakerRegistry {
            withCircuitBreakerConfig {
                slidingWindowSize(2)
                minimumNumberOfCalls(2)
                failureRateThreshold(50.0f)
                waitDurationInOpenState(Duration.ofMillis(25))
            }
            addCircuitBreakerConfig("short-window") {
                slidingWindowSize(4)
                minimumNumberOfCalls(2)
            }
            withTags(mapOf("component" to "checkout"))
        }
        val circuitBreaker = circuitBreakerRegistry.circuitBreaker("orders", "short-window")

        val retryRegistry = KotlinRetryRegistry {
            withRetryConfig<String> {
                maxAttempts(3)
                waitDuration(Duration.ZERO)
                retryOnResult { result: String -> result == "retry" }
            }
            addRetryConfig<String>("fast") {
                maxAttempts(2)
                waitDuration(Duration.ZERO)
            }
            withTags(mapOf("component" to "checkout"))
        }
        val retry = retryRegistry.retry("inventory", "fast")

        val rateLimiterConfig = KotlinRateLimiterConfig {
            limitForPeriod(1)
            limitRefreshPeriod(Duration.ofSeconds(30))
            timeoutDuration(Duration.ZERO)
        }
        val bulkheadConfig = KotlinBulkheadConfig {
            maxConcurrentCalls(1)
            maxWaitDuration(Duration.ZERO)
        }
        val threadPoolBulkheadConfig = KotlinThreadPoolBulkheadConfig {
            coreThreadPoolSize(1)
            maxThreadPoolSize(1)
            queueCapacity(1)
        }
        val timeLimiterConfig = KotlinTimeLimiterConfig {
            timeoutDuration(Duration.ofMillis(100))
            cancelRunningFuture(true)
        }

        assertThat(circuitBreaker.tags).containsEntry("component", "checkout")
        assertThat(circuitBreaker.circuitBreakerConfig.slidingWindowSize).isEqualTo(4)
        assertThat(retry.tags).containsEntry("component", "checkout")
        assertThat(retry.retryConfig.maxAttempts).isEqualTo(2)
        assertThat(rateLimiterConfig.limitForPeriod).isEqualTo(1)
        assertThat(bulkheadConfig.maxConcurrentCalls).isEqualTo(1)
        assertThat(threadPoolBulkheadConfig.coreThreadPoolSize).isEqualTo(1)
        assertThat(timeLimiterConfig.timeoutDuration).isEqualTo(Duration.ofMillis(100))
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun retryExtensionsRetrySuspendFunctionsFunctionsAndFlows(): Unit = runBlocking {
        val retryConfig = KotlinRetryConfig<String> {
            maxAttempts(3)
            waitDuration(Duration.ZERO)
            retryExceptions(TransientServiceException::class.java)
            retryOnResult { result: String -> result == "retry" }
        }
        val retry = Retry.of("suspend-retry", retryConfig)
        val attempts = AtomicInteger()

        val result = retry.executeRetrySuspendFunction {
            when (attempts.incrementAndGet()) {
                1 -> throw TransientServiceException("temporary outage")
                2 -> "retry"
                else -> "ready"
            }
        }
        val decoratedFunction = retry.decorateRetryFunction { "decorated" }

        assertThat(result).isEqualTo("ready")
        assertThat(attempts).hasValue(3)
        assertThat(decoratedFunction()).isEqualTo("decorated")
        assertThat(retry.metrics.numberOfSuccessfulCallsWithRetryAttempt).isEqualTo(1)
        assertThat(retry.metrics.numberOfTotalCalls).isEqualTo(4)

        val flowRetry = Retry.of(
            "flow-retry",
            KotlinRetryConfig<String> {
                maxAttempts(2)
                waitDuration(Duration.ZERO)
                retryOnResult { result: String -> result == "retry" }
            },
        )
        val subscriptions = AtomicInteger()
        val values = flow {
            if (subscriptions.incrementAndGet() == 1) {
                emit("retry")
            } else {
                emit("ready")
            }
        }.resilienceRetry(flowRetry).toList()

        assertThat(values).containsExactly("ready")
        assertThat(subscriptions).hasValue(2)
        assertThat(flowRetry.metrics.numberOfSuccessfulCallsWithRetryAttempt).isEqualTo(1)
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun circuitBreakerExtensionsRecordSuspendAndFlowOutcomes(): Unit = runBlocking {
        val config = KotlinCircuitBreakerConfig {
            slidingWindowSize(2)
            minimumNumberOfCalls(2)
            failureRateThreshold(50.0f)
            waitDurationInOpenState(Duration.ofSeconds(1))
            recordExceptions(IllegalStateException::class.java)
        }
        val circuitBreaker = CircuitBreaker.of("suspend-circuit", config)

        assertThat(circuitBreaker.executeCircuitBreakerSuspendFunction { "success" }).isEqualTo("success")
        val recordedFailure = runCatching {
            circuitBreaker.executeCircuitBreakerSuspendFunction<String> {
                throw IllegalStateException("backend failed")
            }
        }.exceptionOrNull()
        val blockedCall = runCatching {
            circuitBreaker.executeCircuitBreakerSuspendFunction { "blocked" }
        }.exceptionOrNull()

        assertThat(recordedFailure).isInstanceOf(IllegalStateException::class.java)
        assertThat(circuitBreaker.state).isEqualTo(State.OPEN)
        assertThat(blockedCall).isInstanceOf(CallNotPermittedException::class.java)
        assertThat(circuitBreaker.metrics.numberOfSuccessfulCalls).isEqualTo(1)
        assertThat(circuitBreaker.metrics.numberOfFailedCalls).isEqualTo(1)

        val flowCircuitBreaker = CircuitBreaker.of("flow-circuit", config)
        val values = flowOf("one", "two").resilienceCircuitBreaker(flowCircuitBreaker).toList()

        assertThat(values).containsExactly("one", "two")
        assertThat(flowCircuitBreaker.metrics.numberOfSuccessfulCalls).isEqualTo(1)
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun rateLimiterExtensionsPermitSuspendCallsFunctionsAndFlows(): Unit = runBlocking {
        val config = KotlinRateLimiterConfig {
            limitForPeriod(1)
            limitRefreshPeriod(Duration.ofSeconds(30))
            timeoutDuration(Duration.ZERO)
        }
        val rateLimiter = RateLimiter.of("api", config)

        assertThat(rateLimiter.executeRateLimiterSuspendFunction { "accepted" }).isEqualTo("accepted")
        val rejected = runCatching {
            rateLimiter.executeRateLimiterSuspendFunction { "rejected" }
        }.exceptionOrNull()

        assertThat(rejected).isInstanceOf(RequestNotPermitted::class.java)
        assertThat(rateLimiter.metrics.availablePermissions).isZero()

        val decoratedLimiter = RateLimiter.of("decorated-api", config)
        val decorated = decoratedLimiter.decorateRateLimitedFunction { "decorated" }
        assertThat(decorated()).isEqualTo("decorated")

        val flowLimiter = RateLimiter.of("flow-api", config)
        val values = flowOf("single").resilienceRateLimiter(flowLimiter).toList()
        assertThat(values).containsExactly("single")
        assertThat(flowLimiter.metrics.availablePermissions).isZero()
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun bulkheadExtensionsReleasePermitsForSuspendFunctionsAndFlows(): Unit = runBlocking {
        val config = KotlinBulkheadConfig {
            maxConcurrentCalls(1)
            maxWaitDuration(Duration.ZERO)
        }
        val bulkhead = Bulkhead.of("semaphore", config)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val firstCall = async(Dispatchers.Default) {
            bulkhead.executeBulkheadSuspendFunction {
                entered.complete(Unit)
                release.await()
                "first"
            }
        }

        withTimeout(Duration.ofSeconds(5).toMillis()) {
            entered.await()
        }
        val rejected = runCatching {
            bulkhead.executeBulkheadSuspendFunction { "second" }
        }.exceptionOrNull()
        release.complete(Unit)

        assertThat(rejected).isInstanceOf(BulkheadFullException::class.java)
        assertThat(firstCall.await()).isEqualTo("first")
        assertThat(bulkhead.metrics.availableConcurrentCalls).isEqualTo(1)

        val flowBulkhead = Bulkhead.of("flow-semaphore", config)
        val values = flowOf("flow-value").resilienceBulkhead(flowBulkhead).toList()
        assertThat(values).containsExactly("flow-value")
        assertThat(flowBulkhead.metrics.availableConcurrentCalls).isEqualTo(1)
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun micrometerTimerExtensionsRecordSuspendFunctionsAndFlows(): Unit = runBlocking {
        val meterRegistry = SimpleMeterRegistry()
        try {
            val timer = ResilienceTimer.of(
                "backend",
                meterRegistry,
                KotlinTimerConfig {
                    metricNames("custom.timer.operations")
                    onFailureTagResolver { _: Throwable -> "business" }
                },
                mapOf("component" to "checkout"),
            )

            val result = timer.executeTimerSuspendFunction {
                delay(1)
                "timed"
            }
            val flowValues = flowOf("flow").resilienceTimer(timer).toList()
            val failure = runCatching {
                timer.executeTimerSuspendFunction<String> {
                    throw IllegalArgumentException("sold out")
                }
            }.exceptionOrNull()

            assertThat(result).isEqualTo("timed")
            assertThat(flowValues).containsExactly("flow")
            assertThat(failure).isInstanceOf(IllegalArgumentException::class.java)
            assertThat(timer.tags).containsEntry("component", "checkout")
            assertThat(
                meterRegistry.get("custom.timer.operations")
                    .tag("name", "backend")
                    .tag("component", "checkout")
                    .tag("kind", "successful")
                    .timer()
                    .count(),
            ).isEqualTo(2L)
            assertThat(
                meterRegistry.get("custom.timer.operations")
                    .tag("name", "backend")
                    .tag("component", "checkout")
                    .tag("kind", "failed")
                    .tag("failure", "business")
                    .timer()
                    .count(),
            ).isEqualTo(1L)
        } finally {
            meterRegistry.close()
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun timeLimiterExtensionsExecuteAndDecorateBlockingFunctions(): Unit {
        val timeLimiter = TimeLimiter.of(
            "blocking-limiter",
            KotlinTimeLimiterConfig {
                timeoutDuration(Duration.ofMillis(100))
                cancelRunningFuture(true)
            },
        )

        val directResult = timeLimiter.executeTimeLimiterFunction { "direct" }
        val decoratedFunction = timeLimiter.decorateTimeLimiterFunction { "decorated" }

        assertThat(directResult).isEqualTo("direct")
        assertThat(decoratedFunction()).isEqualTo("decorated")

        val timeoutLimiter = TimeLimiter.of(
            "blocking-timeout-limiter",
            KotlinTimeLimiterConfig {
                timeoutDuration(Duration.ofMillis(20))
                cancelRunningFuture(true)
            },
        )
        val timeout = runCatching {
            timeoutLimiter.executeTimeLimiterFunction {
                Thread.sleep(250)
                "late"
            }
        }.exceptionOrNull()

        assertThat(timeout).isInstanceOf(TimeoutException::class.java)
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun timeLimiterExtensionsCompleteFastSuspendFunctionsAndTimeoutSlowOnes(): Unit = runBlocking {
        val fastLimiter = TimeLimiter.of(
            "fast-limiter",
            KotlinTimeLimiterConfig {
                timeoutDuration(Duration.ofMillis(100))
                cancelRunningFuture(true)
            },
        )

        val fastResult = fastLimiter.executeTimeLimiterSuspendFunction {
            delay(1)
            "fast"
        }
        val flowValues = flowOf("a", "b").resilienceTimeLimiter(fastLimiter).toList()

        assertThat(fastResult).isEqualTo("fast")
        assertThat(flowValues).containsExactly("a", "b")

        val timeoutLimiter = TimeLimiter.of(
            "timeout-limiter",
            KotlinTimeLimiterConfig {
                timeoutDuration(Duration.ofMillis(20))
                cancelRunningFuture(true)
            },
        )
        val timeout = runCatching {
            timeoutLimiter.executeTimeLimiterSuspendFunction {
                delay(250)
                "late"
            }
        }.exceptionOrNull()

        assertThat(timeout).isInstanceOf(TimeoutException::class.java)
    }

    private class TransientServiceException(message: String) : RuntimeException(message)
}
