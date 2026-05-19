/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_annotations;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.micrometer.annotation.Timer;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Resilience4j_annotationsTest {
    @Test
    void annotationsExposeRuntimeMethodAndTypeContracts() {
        assertRuntimeMethodAndTypeAnnotation(Bulkhead.class);
        assertRuntimeMethodAndTypeAnnotation(CircuitBreaker.class);
        assertRuntimeMethodAndTypeAnnotation(RateLimiter.class);
        assertRuntimeMethodAndTypeAnnotation(Retry.class);
        assertRuntimeMethodAndTypeAnnotation(TimeLimiter.class);
        assertRuntimeMethodAndTypeAnnotation(Timer.class);
    }

    @Test
    void annotationsExposeExpectedRequiredMembersAndDefaults() throws NoSuchMethodException {
        assertRequiredName(Bulkhead.class);
        assertRequiredName(CircuitBreaker.class);
        assertRequiredName(RateLimiter.class);
        assertRequiredName(Retry.class);
        assertRequiredName(TimeLimiter.class);
        assertRequiredName(Timer.class);

        assertDefaultValue(Bulkhead.class, "fallbackMethod", "");
        assertDefaultValue(Bulkhead.class, "type", Bulkhead.Type.SEMAPHORE);
        assertDefaultValue(CircuitBreaker.class, "fallbackMethod", "");
        assertDefaultValue(RateLimiter.class, "fallbackMethod", "");
        assertDefaultValue(RateLimiter.class, "permits", 1);
        assertDefaultValue(Retry.class, "fallbackMethod", "");
        assertDefaultValue(TimeLimiter.class, "fallbackMethod", "");
        assertDefaultValue(Timer.class, "fallbackMethod", "");
    }

    @Test
    void bulkheadTypeEnumCoversSemaphoreAndThreadPoolPolicies() {
        assertThat(Bulkhead.Type.values()).containsExactly(Bulkhead.Type.SEMAPHORE, Bulkhead.Type.THREADPOOL);
        assertThat(Bulkhead.Type.valueOf("SEMAPHORE")).isSameAs(Bulkhead.Type.SEMAPHORE);
        assertThat(Bulkhead.Type.valueOf("THREADPOOL")).isSameAs(Bulkhead.Type.THREADPOOL);
    }

    @Test
    void annotatedCodeExecutesAsPlainJavaUntilAFrameworkInterpretsTheAnnotations() {
        AnnotatedInventoryClient client = new AnnotatedInventoryClient("eu-west");

        assertThat(client.loadProduct("sku-1")).isEqualTo("eu-west:sku-1");
        assertThat(client.loadProduct(" sku-2 ")).isEqualTo("eu-west:sku-2");
        assertThat(client.rateLimitedLookup("sku-3")).isEqualTo("lookup:sku-3");
        assertThat(client.bulkheadProtectedRefresh()).isEqualTo("refreshed");
        assertThat(client.timedLookup("sku-4").toCompletableFuture()).isCompletedWithValue("timed:sku-4");
        assertThat(client.measuredOperation()).isEqualTo(2);
        assertThat(client.invocationCount()).isEqualTo(6);
    }

    @Test
    void annotationsCanBeDeclaredOnServiceInterfaceContractsAndDefaultMethods() {
        InterfaceInventoryGateway gateway = new InterfaceBackedInventoryGateway("ap-south");

        assertThat(gateway.loadProduct(" sku-7 ")).isEqualTo("ap-south:sku-7");
        assertThat(gateway.rateLimitedLookup("sku-8")).isEqualTo("interface-lookup:sku-8");
        assertThat(gateway.bulkheadProtectedRefresh()).isEqualTo("interface-refreshed");
        assertThat(gateway.timedLookup("sku-9").toCompletableFuture()).isCompletedWithValue("interface-timed:sku-9");
        assertThat(gateway.measuredOperation()).isEqualTo(4);
    }

    @Test
    void configuredAnnotationValuesAreReadableAtRuntimeForTypeAndMethodUse() throws NoSuchMethodException {
        CircuitBreaker typeCircuitBreaker = AnnotatedInventoryClient.class.getAnnotation(CircuitBreaker.class);
        Retry typeRetry = AnnotatedInventoryClient.class.getAnnotation(Retry.class);

        assertThat(typeCircuitBreaker.name()).isEqualTo("inventoryCircuitBreaker");
        assertThat(typeCircuitBreaker.fallbackMethod()).isEqualTo("circuitBreakerFallback");
        assertThat(typeRetry.name()).isEqualTo("inventoryRetry");
        assertThat(typeRetry.fallbackMethod()).isEqualTo("retryFallback");

        Method loadProduct = AnnotatedInventoryClient.class.getDeclaredMethod("loadProduct", String.class);
        assertThat(loadProduct.getAnnotation(CircuitBreaker.class).name()).isEqualTo("productCircuitBreaker");
        assertThat(loadProduct.getAnnotation(CircuitBreaker.class).fallbackMethod()).isEqualTo("productFallback");
        assertThat(loadProduct.getAnnotation(Retry.class).name()).isEqualTo("productRetry");
        assertThat(loadProduct.getAnnotation(Retry.class).fallbackMethod()).isEqualTo("productRetryFallback");

        Method rateLimitedLookup = AnnotatedInventoryClient.class.getDeclaredMethod("rateLimitedLookup", String.class);
        RateLimiter rateLimiter = rateLimitedLookup.getAnnotation(RateLimiter.class);
        assertThat(rateLimiter.name()).isEqualTo("lookupRateLimiter");
        assertThat(rateLimiter.fallbackMethod()).isEqualTo("lookupFallback");
        assertThat(rateLimiter.permits()).isEqualTo(2);

        Method bulkheadProtectedRefresh = AnnotatedInventoryClient.class.getDeclaredMethod("bulkheadProtectedRefresh");
        Bulkhead bulkhead = bulkheadProtectedRefresh.getAnnotation(Bulkhead.class);
        assertThat(bulkhead.name()).isEqualTo("refreshBulkhead");
        assertThat(bulkhead.fallbackMethod()).isEqualTo("refreshFallback");
        assertThat(bulkhead.type()).isEqualTo(Bulkhead.Type.THREADPOOL);

        Method timedLookup = AnnotatedInventoryClient.class.getDeclaredMethod("timedLookup", String.class);
        TimeLimiter timeLimiter = timedLookup.getAnnotation(TimeLimiter.class);
        assertThat(timeLimiter.name()).isEqualTo("asyncLookupTimeLimiter");
        assertThat(timeLimiter.fallbackMethod()).isEqualTo("asyncLookupFallback");

        Method measuredOperation = AnnotatedInventoryClient.class.getDeclaredMethod("measuredOperation");
        Timer timer = measuredOperation.getAnnotation(Timer.class);
        assertThat(timer.name()).isEqualTo("measuredInventoryTimer");
        assertThat(timer.fallbackMethod()).isEqualTo("timerFallback");
    }

    private static void assertRuntimeMethodAndTypeAnnotation(Class<? extends Annotation> annotationType) {
        Retention retention = annotationType.getAnnotation(Retention.class);
        Target target = annotationType.getAnnotation(Target.class);

        assertThat(annotationType.getAnnotation(Documented.class)).isNotNull();
        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(target).isNotNull();
        assertThat(Set.of(target.value())).containsExactlyInAnyOrder(ElementType.METHOD, ElementType.TYPE);
    }

    private static void assertRequiredName(Class<? extends Annotation> annotationType) throws NoSuchMethodException {
        assertThat(annotationType.getDeclaredMethod("name").getReturnType()).isSameAs(String.class);
        assertThat(annotationType.getDeclaredMethod("name").getDefaultValue()).isNull();
    }

    private static void assertDefaultValue(Class<? extends Annotation> annotationType, String memberName, Object value)
            throws NoSuchMethodException {
        assertThat(annotationType.getDeclaredMethod(memberName).getDefaultValue()).isEqualTo(value);
    }

    @CircuitBreaker(name = "interfaceInventoryCircuitBreaker", fallbackMethod = "interfaceCircuitBreakerFallback")
    @Retry(name = "interfaceInventoryRetry", fallbackMethod = "interfaceRetryFallback")
    private interface InterfaceInventoryGateway {
        @CircuitBreaker(name = "interfaceProductCircuitBreaker", fallbackMethod = "interfaceProductFallback")
        @Retry(name = "interfaceProductRetry", fallbackMethod = "interfaceProductRetryFallback")
        String loadProduct(String productId);

        @RateLimiter(name = "interfaceLookupRateLimiter", fallbackMethod = "interfaceLookupFallback", permits = 3)
        String rateLimitedLookup(String productId);

        @Bulkhead(name = "interfaceRefreshBulkhead", fallbackMethod = "interfaceRefreshFallback")
        default String bulkheadProtectedRefresh() {
            return "interface-refreshed";
        }

        @TimeLimiter(name = "interfaceAsyncLookupTimeLimiter", fallbackMethod = "interfaceAsyncLookupFallback")
        default CompletionStage<String> timedLookup(String productId) {
            return CompletableFuture.completedFuture("interface-timed:" + productId);
        }

        @Timer(name = "interfaceMeasuredInventoryTimer", fallbackMethod = "interfaceTimerFallback")
        default int measuredOperation() {
            return 4;
        }
    }

    private static final class InterfaceBackedInventoryGateway implements InterfaceInventoryGateway {
        private final String region;

        private InterfaceBackedInventoryGateway(String region) {
            this.region = region;
        }

        @Override
        public String loadProduct(String productId) {
            return region + ":" + productId.trim();
        }

        @Override
        public String rateLimitedLookup(String productId) {
            return "interface-lookup:" + productId;
        }
    }

    @CircuitBreaker(name = "inventoryCircuitBreaker", fallbackMethod = "circuitBreakerFallback")
    @Retry(name = "inventoryRetry", fallbackMethod = "retryFallback")
    private static final class AnnotatedInventoryClient {
        private final String region;
        private int invocationCount;

        private AnnotatedInventoryClient(String region) {
            this.region = region;
        }

        @CircuitBreaker(name = "productCircuitBreaker", fallbackMethod = "productFallback")
        @Retry(name = "productRetry", fallbackMethod = "productRetryFallback")
        private String loadProduct(String productId) {
            invocationCount++;
            return region + ":" + productId.trim();
        }

        @RateLimiter(name = "lookupRateLimiter", fallbackMethod = "lookupFallback", permits = 2)
        private String rateLimitedLookup(String productId) {
            invocationCount++;
            return "lookup:" + productId;
        }

        @Bulkhead(name = "refreshBulkhead", fallbackMethod = "refreshFallback", type = Bulkhead.Type.THREADPOOL)
        private String bulkheadProtectedRefresh() {
            invocationCount++;
            return "refreshed";
        }

        @TimeLimiter(name = "asyncLookupTimeLimiter", fallbackMethod = "asyncLookupFallback")
        private CompletionStage<String> timedLookup(String productId) {
            invocationCount++;
            return CompletableFuture.completedFuture("timed:" + productId);
        }

        @Timer(name = "measuredInventoryTimer", fallbackMethod = "timerFallback")
        private int measuredOperation() {
            invocationCount++;
            return invocationCount / 3;
        }

        @Bulkhead(name = "defaultBulkhead")
        @CircuitBreaker(name = "defaultCircuitBreaker")
        @RateLimiter(name = "defaultRateLimiter")
        @Retry(name = "defaultRetry")
        @TimeLimiter(name = "defaultTimeLimiter")
        @Timer(name = "defaultTimer")
        private int invocationCount() {
            return invocationCount;
        }
    }
}
