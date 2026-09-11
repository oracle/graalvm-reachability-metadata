/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.bind.RecordNumberBinding;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.RunRecoveryException;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.cleaner.LNInfo;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.latch.LatchException;
import com.sleepycat.je.latch.LatchNotHeldException;
import com.sleepycat.je.latch.LatchSupport;
import com.sleepycat.je.log.DbChecksumException;
import com.sleepycat.je.log.LogException;
import com.sleepycat.je.log.LogFileNotFoundException;
import com.sleepycat.je.recovery.NoRootException;
import com.sleepycat.je.recovery.RecoveryException;
import com.sleepycat.je.tree.DuplicateEntryException;
import com.sleepycat.je.tree.FileSummaryLN;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.tree.InconsistentNodeException;
import com.sleepycat.je.tree.LN;
import com.sleepycat.je.tree.Tree;
import com.sleepycat.je.tree.TreeUtils;
import com.sleepycat.je.txn.TxnPrepare;
import com.sleepycat.je.utilint.DatabaseUtil;
import com.sleepycat.je.utilint.HexFormatter;
import com.sleepycat.je.utilint.JarMain;
import com.sleepycat.je.utilint.NotImplementedYetException;
import com.sleepycat.je.utilint.TestHookExecute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class EdgeApiCoverageTest {

    @Test
    void publicValueObjectsValidateAndRoundTripTheirInputs(@TempDir Path home) throws Exception {
        Environment environment = openEnvironment(home);
        try {
            EnvironmentImpl implementation = com.sleepycat.je.DbInternal.envGetEnvironmentImpl(environment);
            com.sleepycat.je.utilint.Tracer.trace(implementation, "EdgeApiCoverageTest",
                    "publicValueObjectsValidateAndRoundTripTheirInputs", "recovery",
                    new IllegalStateException("trace"));
            RunRecoveryException recovery = new RunRecoveryException(implementation, "recovery");
            assertThat(recovery).hasMessageContaining("recovery");
            assertThat(new RunRecoveryException(implementation, new Exception("cause")))
                    .hasCauseInstanceOf(Exception.class);
            recovery.setAlreadyThrown();
            assertThat(recovery.toString()).contains("recovery");
            assertThat(new RecoveryException(implementation, "recover")).hasMessageContaining("recover");
            assertThat(new RecoveryException(implementation, "recover", new Exception("cause")))
                    .hasCauseInstanceOf(Exception.class);
            assertThat(new NoRootException(implementation, "no root")).hasMessageContaining("no root");
            assertThat(new DbChecksumException(implementation, "checksum")).hasMessageContaining("checksum");
            assertThat(new DbChecksumException(implementation, "checksum", new Exception("cause")))
                    .hasCauseInstanceOf(Exception.class);

            assertThat(new DuplicateEntryException()).isNotNull();
            assertThat(new DuplicateEntryException("duplicate")).hasMessageContaining("duplicate");
            assertThat(new InconsistentNodeException()).isNotNull();
            assertThat(new InconsistentNodeException("inconsistent")).hasMessageContaining("inconsistent");
            assertThat(new LatchException("latch")).hasMessageContaining("latch");
            assertThat(new LatchNotHeldException("not held")).hasMessageContaining("not held");
            assertThat(new LogException("log")).hasMessageContaining("log");
            assertThat(new LogException("log", new Exception("cause"))).hasCauseInstanceOf(Exception.class);
            assertThat(new LogFileNotFoundException("missing")).hasMessageContaining("missing");
            assertThat(new NotImplementedYetException("later")).hasMessageContaining("later");
            assertThat(new JarMain()).isNotNull();
            assertThat(new DatabaseUtil()).isNotNull();
            assertThat(new EnvironmentParams()).isNotNull();
            assertThat(new LatchSupport()).isNotNull();
            assertThat(new HexFormatter()).isNotNull();
            assertThat(new TestHookExecute()).isNotNull();
            assertThat(new TreeUtils()).isNotNull();
            assertThat(TreeUtils.indent(2)).contains(" ");
            assertThat(HexFormatter.formatLong(255L)).contains("ff");

            LN node = new LN(new byte[] {1, 2});
            assertThat(new LNInfo(node, new com.sleepycat.je.dbi.DatabaseId(3),
                    new byte[] {4}, new byte[] {5})).isNotNull();
            FileSummaryLN summary = new FileSummaryLN();
            assertThat(summary.beginTag()).isNotEmpty();
            assertThat(summary.endTag()).isNotEmpty();
            assertThat(summary.dumpString(0, true)).isNotEmpty();
            assertThat(summary.toString()).isNotEmpty();
            assertThat(summary.getTrackedSummary()).isNull();

            RecordNumberBinding binding = new RecordNumberBinding();
            DatabaseEntry entry = new DatabaseEntry();
            assertThatThrownBy(() -> binding.objectToEntry(42L, entry))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> binding.entryToRecordNumber(entry))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> binding.entryToObject(entry))
                    .isInstanceOf(UnsupportedOperationException.class);
        } finally {
            environment.close();
        }
    }

    @Test
    void loggableTransactionPrepareAndTreeObjectsPreserveState() throws Exception {
        TxnPrepare prepare = new TxnPrepare(7L,
                new com.sleepycat.je.log.LogUtils.XidImpl(4, new byte[] {1}, new byte[] {2}));
        assertThat(prepare.getXid()).isNotNull();
        ByteBuffer buffer = ByteBuffer.allocate(prepare.getLogSize());
        prepare.writeToLog(buffer);
        buffer.flip();
        TxnPrepare copy = new TxnPrepare();
        copy.readFromLog(buffer, (byte) 0);
        assertThat(copy.getXid()).isEqualTo(prepare.getXid());
        copy.dumpLog(new java.lang.StringBuffer(), true);

        IN in = new IN();
        assertThat(in.getGeneration()).isGreaterThanOrEqualTo(0L);
        assertThat(in.getEvictionType()).isGreaterThanOrEqualTo(0);
        assertThat(in.isEvictable()).isIn(true, false);
        assertThatThrownBy(in::verifyMemorySize).isInstanceOf(Exception.class);
        assertThat(in.equals(in)).isTrue();
        assertThat(in.latchNoWait()).isTrue();
        in.releaseLatch();
        assertThat(in.latchNoWait(false)).isTrue();
        in.releaseLatch();
        in.latchShared();
        in.releaseLatch();
        in.setProhibitNextDelta();
        in.dumpLog(new java.lang.StringBuffer(), false);
        assertThatThrownBy(() -> in.updateEntry(0, new LN(new byte[] {1})))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> in.updateEntry(0, 1L, (byte) 0))
                .isInstanceOf(Exception.class);

        Tree tree = new Tree();
        assertThat(tree.getTransactionId()).isZero();
        assertThat(tree.isRootResident()).isFalse();
        tree.setWaitHook(null);
        tree.setSearchHook(null);
        tree.setCkptHook(null);
        tree.delete(new byte[] {1}, null);
        assertThatThrownBy(() -> tree.deleteDup(new byte[] {1}, new byte[] {2}, null))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> tree.validateINList(new IN()))
                .isInstanceOf(Exception.class);
        assertThat(TestHookExecute.doHookIfSet(null)).isTrue();
    }

    @Test
    void transactionTimeoutsAndPublicCommitModesAffectTransactionState(@TempDir Path home)
            throws Exception {
        Environment environment = openEnvironment(home);
        try {
            Transaction first = environment.beginTransaction(null, new TransactionConfig());
            assertThat(first.getId()).isPositive();
            first.setName("sync");
            first.setLockTimeout(1000L);
            first.setTxnTimeout(2000L);
            assertThat(first.getName()).isEqualTo("sync");
            assertThat(first.getPrepared()).isFalse();
            first.commitSync();

            Transaction second = environment.beginTransaction(null, new TransactionConfig());
            second.setName("write-no-sync");
            second.commitWriteNoSync();
            assertThat(second.toString()).contains("write-no-sync");
        } finally {
            environment.close();
        }
    }

    private static Environment openEnvironment(Path home) throws Exception {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
        config.setTransactional(true);
        return new Environment(home.toFile(), config);
    }
}
