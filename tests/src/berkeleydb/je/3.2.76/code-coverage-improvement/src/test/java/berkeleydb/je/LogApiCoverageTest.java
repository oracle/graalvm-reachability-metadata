/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.TransactionStats;
import com.sleepycat.je.util.DbDump;
import com.sleepycat.je.cleaner.FileSummary;
import com.sleepycat.je.cleaner.OffsetList;
import com.sleepycat.je.cleaner.PackedOffsets;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.log.FileHeader;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.log.StatsFileReader;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.recovery.CheckpointEnd;
import com.sleepycat.je.recovery.CheckpointStart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class LogApiCoverageTest {

    @Test
    void loggableValuesRoundTripAndDescribeTheirState() throws Exception {
        DatabaseId databaseId = new DatabaseId(17);
        DatabaseId copy = new DatabaseId();
        ByteBuffer idBuffer = ByteBuffer.allocate(databaseId.getLogSize());
        databaseId.writeToLog(idBuffer);
        idBuffer.flip();
        copy.readFromLog(idBuffer, (byte) 0);
        assertThat(copy).isEqualTo(databaseId);
        assertThat(copy.compareTo(databaseId)).isZero();
        assertThat(copy.getTransactionId()).isEqualTo(0L);
        StringBuffer idDump = new java.lang.StringBuffer();
        copy.dumpLog(idDump, true);
        assertThat(idDump).contains("id");

        FileHeader header = new FileHeader();
        StringBuffer headerDump = new java.lang.StringBuffer();
        header.dumpLog(headerDump, false);
        assertThat(header.getLogVersion()).isZero();
        assertThat(header.getTransactionId()).isZero();
        assertThat(header.toString()).isNotEmpty();
        assertThat(headerDump).isNotEmpty();

        CheckpointStart start = new CheckpointStart(31L, "coverage-start");
        ByteBuffer startBuffer = ByteBuffer.allocate(start.getLogSize());
        start.writeToLog(startBuffer);
        startBuffer.flip();
        CheckpointStart startCopy = new CheckpointStart();
        startCopy.readFromLog(startBuffer, (byte) 0);
        StringBuffer startDump = new java.lang.StringBuffer();
        startCopy.dumpLog(startDump, true);
        assertThat(startCopy.getTransactionId()).isZero();
        assertThat(startDump).contains("coverage-start");

        CheckpointEnd end = new CheckpointEnd("coverage-end", 1L, 2L, 3L, 4L, 5, 6L, 7L);
        ByteBuffer endBuffer = ByteBuffer.allocate(end.getLogSize());
        end.writeToLog(endBuffer);
        endBuffer.flip();
        CheckpointEnd endCopy = new CheckpointEnd();
        endCopy.readFromLog(endBuffer, (byte) 0);
        StringBuffer endDump = new java.lang.StringBuffer();
        endCopy.dumpLog(endDump, false);
        assertThat(endCopy.getTransactionId()).isZero();
        assertThat(endCopy.toString()).isNotEmpty();
        assertThat(endDump).contains("coverage-end");
    }

    @Test
    void xidAndCleanerSummariesPreserveValues() {
        LogUtils.XidImpl xid = new LogUtils.XidImpl(7, new byte[] {1, 2}, new byte[] {3, 4});
        ByteBuffer buffer = ByteBuffer.allocate(LogUtils.getXidSize(xid));
        LogUtils.writeXid(buffer, xid);
        buffer.flip();
        LogUtils.XidImpl copy = (LogUtils.XidImpl) LogUtils.readXid(buffer);
        assertThat(copy).isEqualTo(xid);
        assertThat(copy.hashCode()).isEqualTo(xid.hashCode());
        assertThat(copy.getFormatId()).isEqualTo(7);
        assertThat(copy.getGlobalTransactionId()).containsExactly(1, 2);
        assertThat(copy.getBranchQualifier()).containsExactly(3, 4);
        assertThat(copy.toString()).contains("formatId");
        new LogUtils();

        FileSummary summary = new FileSummary();
        summary.totalCount = 4;
        summary.totalSize = 40;
        summary.totalLNCount = 3;
        summary.totalLNSize = 30;
        summary.obsoleteLNCount = 1;
        summary.obsoleteLNSize = 10;
        assertThat(summary.getNonObsoleteCount()).isEqualTo(2);
        assertThat(summary.getTransactionId()).isEqualTo(-1L);
        StringBuffer dump = new java.lang.StringBuffer();
        summary.dumpLog(dump, true);
        assertThat(summary.toString()).contains("totalCount");
        assertThat(dump).isNotEmpty();

        PackedOffsets offsets = new PackedOffsets();
        offsets.pack(new long[] {4L, 8L, 12L});
        StringBuffer offsetsDump = new java.lang.StringBuffer();
        offsets.dumpLog(offsetsDump, false);
        assertThat(offsets.getTransactionId()).isEqualTo(-1L);
        assertThat(offsets.toString()).isNotEmpty();
        assertThat(offsetsDump).isNotEmpty();

        OffsetList list = new OffsetList();
        assertThat(list.add(10L, false)).isFalse();
        assertThat(list.add(20L, false)).isFalse();
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.toArray()).containsExactly(10L, 20L);
    }

    @Test
    void transactionStatisticsAndLogTypesExposeDiagnosticContracts() {
        TransactionStats stats = new TransactionStats();
        stats.setLastCheckpointTime(11L);
        stats.setLastTxnId(12L);
        stats.setNBegins(2);
        stats.setNCommits(3);
        stats.setNAborts(4);
        stats.setNXACommits(5);
        stats.setNXAAborts(6);
        stats.setNXAPrepares(7);
        assertThat(stats.getLastCheckpointTime()).isEqualTo(11L);
        assertThat(stats.getLastTxnId()).isEqualTo(12L);
        assertThat(stats.getNBegins()).isEqualTo(2);
        assertThat(stats.getNCommits()).isEqualTo(3);
        assertThat(stats.getNAborts()).isEqualTo(4);
        assertThat(stats.getNXACommits()).isEqualTo(5);
        assertThat(stats.getNXAAborts()).isEqualTo(6);
        assertThat(stats.getNXAPrepares()).isEqualTo(7);
        stats.setActiveTxns(new TransactionStats.Active[] {
                new TransactionStats.Active("worker", 9L, 8L)});
        assertThat(stats.getActiveTxns()).hasSize(1);
        assertThat(stats.toString()).contains("nBegins");

        TransactionStats.Active active = stats.getActiveTxns()[0];
        assertThat(active.getName()).isEqualTo("worker");
        assertThat(active.getId()).isEqualTo(9L);
        assertThat(active.getParentId()).isEqualTo(8L);
        assertThat(active.toString()).contains("worker");
        assertThat(LogEntryType.LOG_LN.equalsType((byte) 0)).isFalse();
        assertThat(LogEntryType.isNodeType((byte) 1, (byte) 0)).isTrue();
        assertThat(LogEntryType.getAllTypes()).isNotEmpty();
    }

    @Test
    void dbDumpMainProducesAPortableDump(@TempDir Path home) throws Exception {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
        Environment environment = new Environment(home.toFile(), config);
        com.sleepycat.je.DatabaseConfig databaseConfig = new com.sleepycat.je.DatabaseConfig();
        databaseConfig.setAllowCreate(true);
        com.sleepycat.je.Database database = environment.openDatabase(null, "records", databaseConfig);
        database.put(null, new DatabaseEntry(new byte[] {1}), new DatabaseEntry(new byte[] {2}));
        database.close();
        environment.close();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(output));
            DbDump.main(new String[] {"-h", home.toString(), "-s", "records"});
        } finally {
            System.setOut(original);
        }
        assertThat(output.toString()).contains("VERSION=3", "DATA=END");
    }

    @Test
    void statisticsReaderSummarizesCheckpointEntries(@org.junit.jupiter.api.io.TempDir Path home)
            throws Exception {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
        Environment environment = new Environment(home.toFile(), config);
        try {
            com.sleepycat.je.DatabaseConfig databaseConfig = new com.sleepycat.je.DatabaseConfig();
            databaseConfig.setAllowCreate(true);
            com.sleepycat.je.Database database = environment.openDatabase(null, "stats", databaseConfig);
            database.put(null, new DatabaseEntry(new byte[] {1}),
                    new DatabaseEntry(new byte[] {2}));
            database.close();
            environment.checkpoint(new com.sleepycat.je.CheckpointConfig());
            StatsFileReader reader = new StatsFileReader(
                    com.sleepycat.je.DbInternal.envGetEnvironmentImpl(environment),
                    0, 0L, -1L, null, null, false);
            while (reader.readNextEntry()) {
                // Consume the generated log through the reader's public entry point.
            }
            reader.summarize();
        } finally {
            environment.close();
        }
    }
}
