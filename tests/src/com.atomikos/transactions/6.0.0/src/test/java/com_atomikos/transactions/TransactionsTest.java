/*
 * Copyright and related rights waived via CC0
 *
 * You should have received the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_atomikos.transactions;

import com.atomikos.icatch.CompositeTransaction;
import com.atomikos.icatch.CompositeTransactionManager;
import com.atomikos.icatch.RollbackException;
import com.atomikos.icatch.config.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TransactionsTest {

    private CompositeTransactionManager tm;

    @BeforeEach
    void setUp() {
        tm = Configuration.getCompositeTransactionManager();

        // Best-effort cleanup in case a previous test left a TX on this thread
        CompositeTransaction current = tm.getCompositeTransaction();
        if (current != null) {
            try {
                current.rollback();
            } catch (Exception ignored) {
            }
        }
    }

    @AfterEach
    void tearDown() {
        // Ensure no transaction leaks to next test
        CompositeTransaction current = tm.getCompositeTransaction();
        if (current != null) {
            try {
                current.rollback();
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    void beginAndCommitRootTransaction() throws Exception {
        CompositeTransaction tx = tm.createCompositeTransaction(10_000);
        assertThat(tx).as("root transaction created").isNotNull();
        assertThat(tx.getParent()).as("root has no parent").isNull();
        assertThat(tm.getCompositeTransaction()).as("current transaction bound to thread").isEqualTo(tx);
        assertThat(tx.getTid()).as("transaction id present").isNotBlank();

        tx.commit();

        assertThat(tm.getCompositeTransaction()).as("no tx after commit").isNull();
    }

    @Test
    void subtransactionCommitThenRootCommit() throws Exception {
        CompositeTransaction root = tm.createCompositeTransaction(10_000);
        CompositeTransaction child = root.createSubTransaction();

        assertThat(child).isNotNull();
        assertThat(child.getParent()).isEqualTo(root);

        // Commit subtransaction first, then the root
        child.commit();
        root.commit();

        assertThat(tm.getCompositeTransaction()).as("no tx after commit").isNull();
    }

    @Test
    void rollbackOnlyPreventsCommit() throws Exception {
        CompositeTransaction tx = tm.createCompositeTransaction(10_000);
        tx.setRollbackOnly();

        assertThatThrownBy(tx::commit)
            .isInstanceOf(RollbackException.class);

        assertThat(tm.getCompositeTransaction()).as("no tx after failed commit").isNull();
    }

    @Test
    void suspendAndResumeTransaction() throws Exception {
        CompositeTransaction tx1 = tm.createCompositeTransaction(10_000);
        String tid = tx1.getTid();

        CompositeTransaction suspended = tm.suspend();
        assertThat(suspended).isNotNull();
        assertThat(suspended.getTid()).isEqualTo(tid);
        assertThat(tm.getCompositeTransaction()).as("thread context cleared on suspend").isNull();

        tm.resume(suspended);
        assertThat(tm.getCompositeTransaction()).isNotNull();
        assertThat(tm.getCompositeTransaction().getTid()).isEqualTo(tid);

        // Commit after resume
        tm.getCompositeTransaction().commit();

        assertThat(tm.getCompositeTransaction()).as("no tx after commit").isNull();
    }
}
