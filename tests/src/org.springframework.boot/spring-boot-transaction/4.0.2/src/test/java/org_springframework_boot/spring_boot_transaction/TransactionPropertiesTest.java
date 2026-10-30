/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_transaction;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.boot.transaction.autoconfigure.TransactionProperties;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(30)
public class TransactionPropertiesTest {

    @Test
    void configuredTimeoutIsUsedWhenExecutingATransaction() {
        TransactionProperties properties = new TransactionProperties();
        properties.setDefaultTimeout(Duration.ofSeconds(45));
        RecordingTransactionManager manager = new RecordingTransactionManager();

        properties.customize(manager);
        String result = new TransactionTemplate(manager).execute(status -> {
            assertThat(status.isNewTransaction()).isTrue();
            return "saved";
        });

        assertThat(properties.getDefaultTimeout()).isEqualTo(Duration.ofSeconds(45));
        assertThat(manager.getDefaultTimeout()).isEqualTo(45);
        assertThat(result).isEqualTo("saved");
        assertThat(manager.getEvents()).containsExactly("begin:45", "commit");
    }

    @Test
    void unspecifiedPropertiesPreserveExistingManagerSettings() {
        TransactionProperties properties = new TransactionProperties();
        RecordingTransactionManager manager = new RecordingTransactionManager();
        manager.setDefaultTimeout(60);
        manager.setRollbackOnCommitFailure(true);

        properties.customize(manager);

        assertThat(properties.getDefaultTimeout()).isNull();
        assertThat(properties.getRollbackOnCommitFailure()).isNull();
        assertThat(manager.getDefaultTimeout()).isEqualTo(60);
        assertThat(manager.isRollbackOnCommitFailure()).isTrue();
    }

    @Test
    void enablingRollbackOnCommitFailureRollsBackTheTransaction() {
        TransactionProperties properties = new TransactionProperties();
        properties.setRollbackOnCommitFailure(true);
        RecordingTransactionManager manager = new RecordingTransactionManager();
        manager.setFailCommit(true);

        properties.customize(manager);

        assertThrows(TransactionSystemException.class,
                () -> new TransactionTemplate(manager).execute(status -> "saved"));
        assertThat(properties.getRollbackOnCommitFailure()).isTrue();
        assertThat(manager.getEvents()).containsExactly("begin:-1", "commit", "rollback");
    }

    @Test
    void explicitFalseDisablesRollbackOnCommitFailure() {
        TransactionProperties properties = new TransactionProperties();
        properties.setDefaultTimeout(Duration.ofSeconds(20));
        properties.setDefaultTimeout(null);
        properties.setRollbackOnCommitFailure(false);
        RecordingTransactionManager manager = new RecordingTransactionManager();
        manager.setDefaultTimeout(75);
        manager.setRollbackOnCommitFailure(true);
        manager.setFailCommit(true);

        properties.customize(manager);

        assertThrows(TransactionSystemException.class,
                () -> new TransactionTemplate(manager).execute(status -> "saved"));
        assertThat(properties.getDefaultTimeout()).isNull();
        assertThat(properties.getRollbackOnCommitFailure()).isFalse();
        assertThat(manager.isRollbackOnCommitFailure()).isFalse();
        assertThat(manager.getEvents()).containsExactly("begin:75", "commit");
    }
}
