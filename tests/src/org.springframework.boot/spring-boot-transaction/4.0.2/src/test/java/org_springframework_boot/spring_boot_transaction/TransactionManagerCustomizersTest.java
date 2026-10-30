/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_transaction;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizer;
import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizers;
import org.springframework.boot.transaction.autoconfigure.TransactionProperties;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
public class TransactionManagerCustomizersTest {

    @Test
    void callbacksApplyInSuppliedOrderBeforeTransactionExecution() {
        TransactionProperties properties = new TransactionProperties();
        properties.setDefaultTimeout(Duration.ofSeconds(15));
        TransactionManagerCustomizers customizers = TransactionManagerCustomizers.of(List.of(
                properties, new IncrementTimeoutCustomizer(), new DoubleTimeoutCustomizer()));
        RecordingTransactionManager manager = new RecordingTransactionManager();

        customizers.customize(manager);
        Integer result = new TransactionTemplate(manager).execute(status -> 42);

        assertThat(result).isEqualTo(42);
        assertThat(manager.getDefaultTimeout()).isEqualTo(32);
        assertThat(manager.getEvents()).containsExactly("begin:32", "commit");
    }

    @Test
    void callbacksForOtherManagerTypesAreNotInvoked() {
        RecordingTransactionManager manager = new RecordingTransactionManager();
        manager.setDefaultTimeout(30);
        TransactionManagerCustomizers customizers = TransactionManagerCustomizers.of(List.of(
                new OtherManagerCustomizer(), new IncrementTimeoutCustomizer()));

        customizers.customize(manager);

        assertThat(manager.getDefaultTimeout()).isEqualTo(31);
    }

    @Test
    void collectionIsCopiedAndCanCustomizeMultipleManagers() {
        List<TransactionManagerCustomizer<?>> callbacks = new ArrayList<>();
        callbacks.add(new IncrementTimeoutCustomizer());
        TransactionManagerCustomizers customizers = TransactionManagerCustomizers.of(callbacks);
        callbacks.clear();
        callbacks.add(new DoubleTimeoutCustomizer());
        RecordingTransactionManager first = new RecordingTransactionManager();
        first.setDefaultTimeout(10);
        RecordingTransactionManager second = new RecordingTransactionManager();
        second.setDefaultTimeout(20);

        customizers.customize(first);
        customizers.customize(second);

        assertThat(first.getDefaultTimeout()).isEqualTo(11);
        assertThat(second.getDefaultTimeout()).isEqualTo(21);
    }

    @Test
    void absentAndEmptyCallbacksLeaveTheManagerUnchanged() {
        RecordingTransactionManager manager = new RecordingTransactionManager();
        manager.setDefaultTimeout(50);
        manager.setRollbackOnCommitFailure(true);

        TransactionManagerCustomizers.of(null).customize(manager);
        TransactionManagerCustomizers.of(List.of()).customize(manager);

        assertThat(manager.getDefaultTimeout()).isEqualTo(50);
        assertThat(manager.isRollbackOnCommitFailure()).isTrue();
        assertThat(manager.getEvents()).isEmpty();
    }

    public static final class IncrementTimeoutCustomizer
            implements TransactionManagerCustomizer<RecordingTransactionManager> {

        @Override
        public void customize(RecordingTransactionManager manager) {
            manager.setDefaultTimeout(manager.getDefaultTimeout() + 1);
        }
    }

    public static final class DoubleTimeoutCustomizer
            implements TransactionManagerCustomizer<RecordingTransactionManager> {

        @Override
        public void customize(RecordingTransactionManager manager) {
            manager.setDefaultTimeout(manager.getDefaultTimeout() * 2);
        }
    }

    public static final class OtherManagerCustomizer
            implements TransactionManagerCustomizer<OtherTransactionManager> {

        @Override
        public void customize(OtherTransactionManager manager) {
            throw new AssertionError("A callback must only receive a compatible transaction manager");
        }
    }

    public static final class OtherTransactionManager implements TransactionManager {
    }
}
