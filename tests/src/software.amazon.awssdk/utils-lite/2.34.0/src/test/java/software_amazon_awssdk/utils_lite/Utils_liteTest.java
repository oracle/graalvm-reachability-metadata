/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.utils_lite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.fail;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.utilslite.SdkInternalThreadLocal;

public class Utils_liteTest {
    private static final Duration THREAD_JOIN_TIMEOUT = Duration.ofSeconds(5);

    @BeforeEach
    @AfterEach
    void clearStorage() {
        SdkInternalThreadLocal.clear();
    }

    @Test
    void putGetContainsAndRemoveManageCurrentThreadValues() {
        assertThat(SdkInternalThreadLocal.get("missing")).isNull();
        assertThat(SdkInternalThreadLocal.containsKey("missing")).isFalse();
        assertThat(SdkInternalThreadLocal.remove("missing")).isNull();

        SdkInternalThreadLocal.put("request-id", "request-1");
        SdkInternalThreadLocal.put("tenant", "tenant-a");

        assertThat(SdkInternalThreadLocal.get("request-id")).isEqualTo("request-1");
        assertThat(SdkInternalThreadLocal.get("tenant")).isEqualTo("tenant-a");
        assertThat(SdkInternalThreadLocal.containsKey("request-id")).isTrue();
        assertThat(SdkInternalThreadLocal.containsKey("tenant")).isTrue();

        SdkInternalThreadLocal.put("request-id", "request-2");

        assertThat(SdkInternalThreadLocal.get("request-id")).isEqualTo("request-2");
        assertThat(SdkInternalThreadLocal.remove("request-id")).isEqualTo("request-2");
        assertThat(SdkInternalThreadLocal.get("request-id")).isNull();
        assertThat(SdkInternalThreadLocal.containsKey("request-id")).isFalse();
        assertThat(SdkInternalThreadLocal.get("tenant")).isEqualTo("tenant-a");
    }

    @Test
    void putWithNullValueRemovesOnlyThatKey() {
        SdkInternalThreadLocal.put("unused", null);
        assertThat(SdkInternalThreadLocal.containsKey("unused")).isFalse();

        SdkInternalThreadLocal.put("trace-id", "trace-123");
        SdkInternalThreadLocal.put("span-id", "span-456");

        SdkInternalThreadLocal.put("trace-id", null);

        assertThat(SdkInternalThreadLocal.get("trace-id")).isNull();
        assertThat(SdkInternalThreadLocal.containsKey("trace-id")).isFalse();
        assertThat(SdkInternalThreadLocal.get("span-id")).isEqualTo("span-456");

        SdkInternalThreadLocal.put("span-id", null);

        assertThat(SdkInternalThreadLocal.get("span-id")).isNull();
        assertThat(SdkInternalThreadLocal.containsKey("span-id")).isFalse();
    }

    @Test
    void clearRemovesAllValuesForCurrentThread() {
        SdkInternalThreadLocal.put("first", "one");
        SdkInternalThreadLocal.put("second", "two");

        SdkInternalThreadLocal.clear();

        assertThat(SdkInternalThreadLocal.get("first")).isNull();
        assertThat(SdkInternalThreadLocal.get("second")).isNull();
        assertThat(SdkInternalThreadLocal.containsKey("first")).isFalse();
        assertThat(SdkInternalThreadLocal.containsKey("second")).isFalse();
    }

    @Test
    void putWithNullValueForLastKeyRemovesInheritedStorage() throws InterruptedException {
        SdkInternalThreadLocal.put("detached-key", "parent-value");
        SdkInternalThreadLocal.put("detached-key", null);

        runInThread(() -> {
            assertThat(SdkInternalThreadLocal.get("detached-key")).isNull();

            SdkInternalThreadLocal.put("child-key", "child-value");

            assertThat(SdkInternalThreadLocal.get("child-key")).isEqualTo("child-value");
        });

        assertThat(SdkInternalThreadLocal.get("child-key")).isNull();
        assertThat(SdkInternalThreadLocal.containsKey("child-key")).isFalse();
    }

    @Test
    void nullKeysAreRejectedByAllKeyBasedOperations() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> SdkInternalThreadLocal.put(null, "value"))
            .withMessage("Key cannot be null");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> SdkInternalThreadLocal.get(null))
            .withMessage("Key cannot be null");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> SdkInternalThreadLocal.remove(null))
            .withMessage("Key cannot be null");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> SdkInternalThreadLocal.containsKey(null))
            .withMessage("Key cannot be null");
    }

    @Test
    void threadWithoutInheritedContextUsesIndependentStorage() throws InterruptedException {
        SdkInternalThreadLocal.clear();

        runInThread(() -> {
            SdkInternalThreadLocal.put("worker-only", "worker-value");
            assertThat(SdkInternalThreadLocal.get("worker-only")).isEqualTo("worker-value");
        });

        assertThat(SdkInternalThreadLocal.get("worker-only")).isNull();
        assertThat(SdkInternalThreadLocal.containsKey("worker-only")).isFalse();
    }

    @Test
    void childThreadInheritsExistingContextAndCanClearItsOwnReference() throws InterruptedException {
        SdkInternalThreadLocal.put("parent-key", "parent-value");

        runInThread(() -> {
            assertThat(SdkInternalThreadLocal.get("parent-key")).isEqualTo("parent-value");
            assertThat(SdkInternalThreadLocal.containsKey("parent-key")).isTrue();

            SdkInternalThreadLocal.clear();

            assertThat(SdkInternalThreadLocal.get("parent-key")).isNull();
            assertThat(SdkInternalThreadLocal.containsKey("parent-key")).isFalse();
        });

        assertThat(SdkInternalThreadLocal.get("parent-key")).isEqualTo("parent-value");
        assertThat(SdkInternalThreadLocal.containsKey("parent-key")).isTrue();
    }

    @Test
    void childThreadMutationsAreVisibleThroughInheritedContext() throws InterruptedException {
        SdkInternalThreadLocal.put("shared-key", "parent-value");
        SdkInternalThreadLocal.put("removed-key", "value-to-remove");

        runInThread(() -> {
            assertThat(SdkInternalThreadLocal.get("shared-key")).isEqualTo("parent-value");
            assertThat(SdkInternalThreadLocal.remove("removed-key")).isEqualTo("value-to-remove");

            SdkInternalThreadLocal.put("shared-key", "child-value");
            SdkInternalThreadLocal.put("child-key", "child-only-value");
        });

        assertThat(SdkInternalThreadLocal.get("shared-key")).isEqualTo("child-value");
        assertThat(SdkInternalThreadLocal.get("removed-key")).isNull();
        assertThat(SdkInternalThreadLocal.containsKey("removed-key")).isFalse();
        assertThat(SdkInternalThreadLocal.get("child-key")).isEqualTo("child-only-value");
    }

    private static void runInThread(Runnable action) throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                action.run();
            } catch (Throwable e) {
                failure.set(e);
            } finally {
                SdkInternalThreadLocal.clear();
            }
        }, "sdk-internal-thread-local-test");

        thread.start();
        thread.join(THREAD_JOIN_TIMEOUT.toMillis());
        if (thread.isAlive()) {
            thread.interrupt();
            fail("Worker thread did not finish within %s", THREAD_JOIN_TIMEOUT);
        }
        assertThat(failure.get()).isNull();
    }
}
