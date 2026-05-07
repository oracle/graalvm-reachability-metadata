/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.core.registry.RegistryEvent;
import io.github.resilience4j.retry.MaxRetriesExceeded;
import io.github.resilience4j.retry.MaxRetriesExceededException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.retry.event.RetryEvent;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class Resilience4j_retryTest {
    @Test
    void retriesSupplierFailuresAndPublishesSuccessMetrics() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(4)
            .waitDuration(Duration.ZERO)
            .retryExceptions(TransientServiceException.class)
            .build();
        Retry retry = Retry.of("supplier", config, Map.of("component", "orders"));
        List<RetryEvent.Type> eventTypes = new ArrayList<>();
        List<Integer> retryAttempts = new ArrayList<>();
        List<String> retryMessages = new ArrayList<>();

        retry.getEventPublisher()
            .onRetry(event -> {
                eventTypes.add(event.getEventType());
                retryAttempts.add(event.getNumberOfRetryAttempts());
                retryMessages.add(event.getLastThrowable().getMessage());
                assertThat(event.getWaitInterval()).isEqualTo(Duration.ZERO);
            })
            .onSuccess(event -> {
                eventTypes.add(event.getEventType());
                retryAttempts.add(event.getNumberOfRetryAttempts());
                retryMessages.add(event.getLastThrowable().getMessage());
            });

        AtomicInteger calls = new AtomicInteger();
        Supplier<String> supplier = Retry.decorateSupplier(retry, () -> {
            int attempt = calls.incrementAndGet();
            if (attempt < 3) {
                throw new TransientServiceException("temporary-" + attempt);
            }
            return "created";
        });

        assertThat(supplier.get()).isEqualTo("created");
        assertThat(calls).hasValue(3);
        assertThat(retry.getName()).isEqualTo("supplier");
        assertThat(retry.getTags()).containsEntry("component", "orders");
        assertThat(eventTypes).containsExactly(
            RetryEvent.Type.RETRY,
            RetryEvent.Type.RETRY,
            RetryEvent.Type.SUCCESS);
        assertThat(retryAttempts).containsExactly(1, 2, 2);
        assertThat(retryMessages).containsExactly("temporary-1", "temporary-2", "temporary-2");
        assertThat(retry.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt()).isEqualTo(1);
        assertThat(retry.getMetrics().getNumberOfSuccessfulCallsWithoutRetryAttempt()).isZero();
        assertThat(retry.getMetrics().getNumberOfFailedCallsWithRetryAttempt()).isZero();
        assertThat(retry.getMetrics().getNumberOfFailedCallsWithoutRetryAttempt()).isZero();
        assertThat(retry.getMetrics().getNumberOfTotalCalls()).isEqualTo(3);
    }

    @Test
    void retriesUnsatisfactoryResultsAndConsumesThemBeforeNextAttempt() {
        List<String> consumedResults = new ArrayList<>();
        List<Integer> intervalAttempts = new ArrayList<>();
        RetryConfig config = RetryConfig.<String>custom()
            .maxAttempts(4)
            .retryOnResult(result -> result.startsWith("retry"))
            .consumeResultBeforeRetryAttempt((attempt, result) -> consumedResults.add(attempt + ":" + result))
            .intervalBiFunction((attempt, either) -> {
                intervalAttempts.add(attempt);
                return 0L;
            })
            .build();
        Retry retry = Retry.of("results", config);
        List<RetryEvent.Type> eventTypes = new ArrayList<>();
        retry.getEventPublisher()
            .onRetry(event -> eventTypes.add(event.getEventType()))
            .onSuccess(event -> eventTypes.add(event.getEventType()));

        AtomicInteger calls = new AtomicInteger();
        String result = retry.executeSupplier(() -> {
            int attempt = calls.incrementAndGet();
            if (attempt == 1) {
                return "retry-warming";
            }
            if (attempt == 2) {
                return "retry-cache-miss";
            }
            return "ready";
        });

        assertThat(result).isEqualTo("ready");
        assertThat(calls).hasValue(3);
        assertThat(consumedResults).containsExactly("1:retry-warming", "2:retry-cache-miss");
        assertThat(intervalAttempts).containsExactly(1, 2);
        assertThat(eventTypes).containsExactly(
            RetryEvent.Type.RETRY,
            RetryEvent.Type.RETRY,
            RetryEvent.Type.SUCCESS);
        assertThat(retry.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt()).isEqualTo(1);
        assertThat(retry.getMetrics().getNumberOfTotalCalls()).isEqualTo(3);
    }

    @Test
    void throwsMaxRetriesExceededExceptionForUnsatisfactoryResultsWhenConfigured() {
        RetryConfig config = RetryConfig.<String>custom()
            .maxAttempts(2)
            .waitDuration(Duration.ZERO)
            .retryOnResult("retry"::equals)
            .failAfterMaxAttempts(true)
            .writableStackTraceEnabled(false)
            .build();
        Retry retry = Retry.of("exhausted-results", config);
        List<RetryEvent.Type> eventTypes = new ArrayList<>();
        List<Integer> attempts = new ArrayList<>();
        List<Throwable> errorCauses = new ArrayList<>();
        retry.getEventPublisher()
            .onRetry(event -> {
                eventTypes.add(event.getEventType());
                attempts.add(event.getNumberOfRetryAttempts());
            })
            .onError(event -> {
                eventTypes.add(event.getEventType());
                attempts.add(event.getNumberOfRetryAttempts());
                errorCauses.add(event.getLastThrowable());
            });

        assertThatThrownBy(() -> retry.executeSupplier(() -> "retry"))
            .isInstanceOf(MaxRetriesExceededException.class)
            .hasMessage("Retry 'exhausted-results' has exhausted all attempts (2)")
            .satisfies(throwable -> assertThat(((MaxRetriesExceededException) throwable).getCausingRetryName())
                .isEqualTo("exhausted-results"));

        assertThat(eventTypes).containsExactly(RetryEvent.Type.RETRY, RetryEvent.Type.ERROR, RetryEvent.Type.ERROR);
        assertThat(attempts).containsExactly(1, 2, 3);
        assertThat(errorCauses).hasSize(2);
        Throwable resultRetryError = errorCauses.get(0);
        assertThat(resultRetryError).isInstanceOf(MaxRetriesExceeded.class);
        assertThat(resultRetryError).hasMessage("max retries is reached out for the result predicate check");
        Throwable thrownRetryError = errorCauses.get(1);
        assertThat(thrownRetryError).isInstanceOf(MaxRetriesExceededException.class);
        assertThat(thrownRetryError).hasMessage("Retry 'exhausted-results' has exhausted all attempts (2)");
        assertThat(retry.getMetrics().getNumberOfFailedCallsWithRetryAttempt()).isEqualTo(2);
        assertThat(retry.getMetrics().getNumberOfTotalCalls()).isEqualTo(4);
    }

    @Test
    void ignoresConfiguredExceptionsWithoutRetrying() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ZERO)
            .retryExceptions(RuntimeException.class)
            .ignoreExceptions(IllegalArgumentException.class)
            .build();
        Retry retry = Retry.of("ignored", config);
        List<RetryEvent.Type> eventTypes = new ArrayList<>();
        retry.getEventPublisher().onIgnoredError(event -> {
            eventTypes.add(event.getEventType());
            assertThat(event.getName()).isEqualTo("ignored");
            assertThat(event.getNumberOfRetryAttempts()).isZero();
            assertThat(event.getLastThrowable()).isInstanceOf(IllegalArgumentException.class);
        });

        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> retry.executeRunnable(() -> {
            calls.incrementAndGet();
            throw new IllegalArgumentException("bad request");
        })).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("bad request");

        assertThat(calls).hasValue(1);
        assertThat(eventTypes).containsExactly(RetryEvent.Type.IGNORED_ERROR);
        assertThat(retry.getMetrics().getNumberOfFailedCallsWithoutRetryAttempt()).isEqualTo(1);
        assertThat(retry.getMetrics().getNumberOfTotalCalls()).isEqualTo(1);
    }

    @Test
    void decoratesCheckedAndFunctionalInterfaces() throws Throwable {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(2)
            .waitDuration(Duration.ZERO)
            .retryOnException(throwable -> throwable instanceof IOException
                || throwable instanceof TransientServiceException)
            .build();
        Retry retry = Retry.of("decorators", config);

        AtomicInteger checkedSupplierCalls = new AtomicInteger();
        String supplied = retry.executeCheckedSupplier(() -> {
            if (checkedSupplierCalls.incrementAndGet() == 1) {
                throw new IOException("supplier unavailable");
            }
            return "supplied";
        });

        AtomicInteger functionCalls = new AtomicInteger();
        Function<String, String> function = Retry.decorateFunction(retry, value -> {
            if (functionCalls.incrementAndGet() == 1) {
                throw new TransientServiceException("function unavailable");
            }
            return value.toUpperCase();
        });

        AtomicInteger consumerCalls = new AtomicInteger();
        List<String> consumed = new ArrayList<>();
        Consumer<String> consumer = retry.decorateConsumer(value -> {
            if (consumerCalls.incrementAndGet() == 1) {
                throw new TransientServiceException("consumer unavailable");
            }
            consumed.add(value);
        });

        assertThat(supplied).isEqualTo("supplied");
        assertThat(Retry.decorateCheckedFunction(retry, (String value) -> value + "!").apply("checked"))
            .isEqualTo("checked!");
        assertThat(function.apply("ok")).isEqualTo("OK");
        consumer.accept("stored");
        assertThat(Retry.decorateCallable(retry, () -> "called").call()).isEqualTo("called");
        retry.decorateCheckedRunnable(() -> { }).run();
        Retry.decorateCheckedConsumer(retry, (String value) -> consumed.add(value + "-checked")).accept("stored");

        assertThat(checkedSupplierCalls).hasValue(2);
        assertThat(functionCalls).hasValue(2);
        assertThat(consumerCalls).hasValue(2);
        assertThat(consumed).containsExactly("stored", "stored-checked");
        assertThat(retry.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt()).isEqualTo(3);
        assertThat(retry.getMetrics().getNumberOfSuccessfulCallsWithoutRetryAttempt()).isEqualTo(4);
        assertThat(retry.getMetrics().getNumberOfTotalCalls()).isEqualTo(10);
    }

    @Test
    void retriesCompletionStagesForErrorsAndResults() throws Exception {
        RetryConfig config = RetryConfig.<String>custom()
            .maxAttempts(4)
            .waitDuration(Duration.ZERO)
            .retryOnResult(result -> !"ready".equals(result))
            .retryExceptions(TransientServiceException.class)
            .build();
        Retry retry = Retry.of("async", config);
        List<RetryEvent.Type> eventTypes = new ArrayList<>();
        retry.getEventPublisher()
            .onRetry(event -> eventTypes.add(event.getEventType()))
            .onSuccess(event -> eventTypes.add(event.getEventType()));

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            AtomicInteger calls = new AtomicInteger();
            Supplier<CompletionStage<String>> stageSupplier = () -> {
                int attempt = calls.incrementAndGet();
                if (attempt == 1) {
                    return CompletableFuture.failedFuture(new TransientServiceException("async unavailable"));
                }
                if (attempt == 2) {
                    return CompletableFuture.completedFuture("warming");
                }
                return CompletableFuture.completedFuture("ready");
            };

            CompletionStage<String> stage = retry.executeCompletionStage(scheduler, stageSupplier);

            assertThat(stage.toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo("ready");
            assertThat(calls).hasValue(3);
        } finally {
            scheduler.shutdownNow();
            assertThat(scheduler.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(eventTypes).containsExactly(
            RetryEvent.Type.RETRY,
            RetryEvent.Type.RETRY,
            RetryEvent.Type.SUCCESS);
        assertThat(retry.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt()).isEqualTo(1);
        assertThat(retry.getMetrics().getNumberOfTotalCalls()).isEqualTo(3);
    }

    @Test
    void registryCreatesTaggedRetriesFromSharedConfigurations() {
        RetryConfig defaultConfig = RetryConfig.custom()
            .maxAttempts(1)
            .waitDuration(Duration.ZERO)
            .build();
        RetryConfig namedConfig = RetryConfig.custom()
            .maxAttempts(5)
            .waitDuration(Duration.ofMillis(1))
            .build();
        RetryRegistry registry = RetryRegistry.custom()
            .withRetryConfig(defaultConfig)
            .addRetryConfig("named", namedConfig)
            .withTags(Map.of("service", "billing", "environment", "test"))
            .build();

        Retry fromNamedConfig = registry.retry("payment", "named", Map.of("service", "orders", "region", "local"));
        Retry sameInstance = registry.retry("payment", defaultConfig);
        Retry fromSupplier = registry.retry("shipping", () -> defaultConfig);

        assertThat(fromNamedConfig).isSameAs(sameInstance);
        assertThat(fromNamedConfig.getRetryConfig().getMaxAttempts()).isEqualTo(5);
        assertThat(fromNamedConfig.getTags()).containsEntry("service", "orders")
            .containsEntry("environment", "test")
            .containsEntry("region", "local");
        assertThat(fromSupplier.getRetryConfig().getMaxAttempts()).isEqualTo(1);
        assertThat(registry.getAllRetries()).containsExactlyInAnyOrder(fromNamedConfig, fromSupplier);
    }

    @Test
    void registryPublishesLifecycleEventsWhenEntriesAreReplacedAndRemoved() {
        RetryConfig initialConfig = RetryConfig.custom()
            .maxAttempts(1)
            .waitDuration(Duration.ZERO)
            .build();
        RetryConfig replacementConfig = RetryConfig.custom()
            .maxAttempts(2)
            .waitDuration(Duration.ZERO)
            .build();
        RetryRegistry registry = RetryRegistry.of(initialConfig);
        List<RegistryEvent.Type> eventTypes = new ArrayList<>();
        List<String> eventEntries = new ArrayList<>();

        registry.getEventPublisher()
            .onEntryAdded(event -> {
                eventTypes.add(event.getEventType());
                eventEntries.add("added:" + event.getAddedEntry().getName());
            })
            .onEntryReplaced(event -> {
                eventTypes.add(event.getEventType());
                eventEntries.add("replaced:"
                    + event.getOldEntry().getRetryConfig().getMaxAttempts()
                    + "->"
                    + event.getNewEntry().getRetryConfig().getMaxAttempts());
            })
            .onEntryRemoved(event -> {
                eventTypes.add(event.getEventType());
                eventEntries.add("removed:" + event.getRemovedEntry().getName());
            });

        Retry original = registry.retry("inventory", Map.of("phase", "initial"));
        Retry replacement = Retry.of("inventory", replacementConfig, Map.of("phase", "replacement"));
        Optional<Retry> replaced = registry.replace("inventory", replacement);
        Optional<Retry> current = registry.find("inventory");
        Optional<Retry> removed = registry.remove("inventory");

        assertThat(replaced).isPresent();
        assertThat(replaced.get()).isSameAs(original);
        assertThat(current).isPresent();
        assertThat(current.get()).isSameAs(replacement);
        assertThat(removed).isPresent();
        assertThat(removed.get()).isSameAs(replacement);
        assertThat(registry.find("inventory")).isEmpty();
        assertThat(registry.remove("missing")).isEmpty();
        assertThat(eventTypes).containsExactly(
            RegistryEvent.Type.ADDED,
            RegistryEvent.Type.REPLACED,
            RegistryEvent.Type.REMOVED);
        assertThat(eventEntries).containsExactly("added:inventory", "replaced:1->2", "removed:inventory");
    }

    private static final class TransientServiceException extends RuntimeException {
        private TransientServiceException(String message) {
            super(message);
        }
    }
}
