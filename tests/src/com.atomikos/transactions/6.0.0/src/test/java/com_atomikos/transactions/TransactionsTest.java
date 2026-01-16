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
import com.atomikos.icatch.config.UserTransactionService;
import com.atomikos.icatch.config.UserTransactionServiceImp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class TransactionsTest {

    private CompositeTransactionManager tm;
    private UserTransactionService uts;

    @BeforeEach
    void setUp() {
        // Boot the Atomikos core transaction service so the CTM becomes available
        Properties props = new Properties();
        // Use temp directories for logs to keep tests isolated and writable
        String tmp = System.getProperty("java.io.tmpdir");
        props.setProperty("com.atomikos.icatch.log_base_dir", tmp);
        props.setProperty("com.atomikos.icatch.output_dir", tmp);

        uts = new UserTransactionServiceImp();
        uts.init(props);

        tm = Configuration.getCompositeTransactionManager();
        assertThat(tm).as("CompositeTransactionManager should be available after init").isNotNull();

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
        if (tm != null) {
            CompositeTransaction current = tm.getCompositeTransaction();
            if (current != null) {
                try {
                    current.rollback();
                } catch (Exception ignored) {
                }
            }
        }
        // Shut down the service to release resources/threads
        if (uts != null) {
            try {
                uts.shutdown(true);
            } catch (Exception ignored) {
            } finally {
                uts = null;
                tm = null;
            }
        }
    }

    @Test
    void beginAndCommitRootTransaction() throws Exception {
        CompositeTransaction tx = tm.createCompositeTransaction(10_000);
        assertThat(tx).as("root transaction created").isNotNull();
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
        assertThat(child.getTid()).as("child has distinct tid").isNotEqualTo(root.getTid());

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

    @Test
    void suspendOnOneThreadResumeOnAnotherAndCommit() throws Exception {
        CompositeTransaction tx = tm.createCompositeTransaction(10_000);
        String tid = tx.getTid();

        // Suspend on main test thread
        CompositeTransaction suspended = tm.suspend();
        assertThat(suspended).isNotNull();
        assertThat(tm.getCompositeTransaction()).as("main thread cleared on suspend").isNull();

        AtomicReference<Throwable> asyncError = new AtomicReference<>();

        Thread t = new Thread(() -> {
            try {
                // Initially no transaction bound on this worker thread
                assertThat(tm.getCompositeTransaction()).as("worker thread initially has no tx").isNull();

                // Resume the suspended transaction on the worker thread
                tm.resume(suspended);
                assertThat(tm.getCompositeTransaction()).isNotNull();
                assertThat(tm.getCompositeTransaction().getTid()).isEqualTo(tid);

                // Commit on the worker thread
                tm.getCompositeTransaction().commit();

                // After commit, the worker thread should have no tx bound
                assertThat(tm.getCompositeTransaction()).as("worker thread cleared after commit").isNull();
            } catch (Throwable e) {
                asyncError.set(e);
            }
        });

        t.start();
        t.join();

        assertThat(asyncError.get()).as("no error in worker thread").isNull();
        // Main thread should still have no transaction bound
        assertThat(tm.getCompositeTransaction()).as("main thread remains clear").isNull();
    }

    @Test
    void rollbackOnlyInChildPreventsRootCommit() throws Exception {
        CompositeTransaction root = tm.createCompositeTransaction(10_000);
        CompositeTransaction child = root.createSubTransaction();

        // Mark child for rollback and verify its commit fails
        child.setRollbackOnly();
        assertThatThrownBy(child::commit)
            .as("child commit should fail when marked rollback-only")
            .isInstanceOf(RollbackException.class);

        // Root commit should also fail due to failed child
        assertThatThrownBy(root::commit)
            .as("root commit should fail after child failure")
            .isInstanceOf(RollbackException.class);

        assertThat(tm.getCompositeTransaction()).as("no tx after failed commits").isNull();
    }
}
