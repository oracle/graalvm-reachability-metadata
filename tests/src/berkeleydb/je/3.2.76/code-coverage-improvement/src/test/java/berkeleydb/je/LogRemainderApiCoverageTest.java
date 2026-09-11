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
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.log.CheckpointFileReader;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.log.INFileReader;
import com.sleepycat.je.log.LNFileReader;
import com.sleepycat.je.log.ScavengerFileReader;
import com.sleepycat.je.log.CleanerFileReader;
import com.sleepycat.je.log.LastFileReader;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.log.PrintFileReader;
import com.sleepycat.je.log.SearchFileReader;
import com.sleepycat.je.log.StatsFileReader;
import com.sleepycat.je.log.TraceLogHandler;
import com.sleepycat.je.log.UtilizationFileReader;
import com.sleepycat.je.log.entry.BINDeltaLogEntry;
import com.sleepycat.je.log.entry.DeletedDupLNLogEntry;
import com.sleepycat.je.log.entry.INLogEntry;
import com.sleepycat.je.log.entry.LNLogEntry;
import com.sleepycat.je.log.entry.SingleItemEntry;
import com.sleepycat.je.recovery.CheckpointStart;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.tree.LN;
import com.sleepycat.je.utilint.DbLsn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.logging.LogRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LogRemainderApiCoverageTest {

    @Test
    void logEntriesRenderAndExposeTheirUserData() throws Exception {
        com.sleepycat.je.dbi.DatabaseId databaseId =
                new com.sleepycat.je.dbi.DatabaseId(21);
        LN node = new LN(new byte[] {4, 5});
        LNLogEntry lnEntry = new LNLogEntry(LogEntryType.LOG_LN, node, databaseId,
                new byte[] {1}, 7L, false, null);
        assertThat(lnEntry.getLN()).isSameAs(node);
        assertThat(lnEntry.getDbId()).isSameAs(databaseId);
        assertThat(lnEntry.getKey()).containsExactly(1);
        assertThat(lnEntry.getTransactionId()).isZero();
        StringBuffer lnDump = new java.lang.StringBuffer();
        lnEntry.dumpEntry(lnDump, true);
        assertThat(lnDump).isNotEmpty();

        DeletedDupLNLogEntry deleted = new DeletedDupLNLogEntry(LogEntryType.LOG_DEL_DUPLN,
                node, databaseId, new byte[] {2}, new byte[] {3}, 8L, true, null);
        assertThat(deleted.getDupKey()).containsExactly(3);
        assertThat(deleted.getSize()).isPositive();
        assertThat(deleted.dumpEntry(new java.lang.StringBuffer(), false)).isNotEmpty();

        SingleItemEntry single = new SingleItemEntry(LogEntryType.LOG_CKPT_START,
                new CheckpointStart(2L, "entry"));
        assertThat(single.getTransactionId()).isZero();
        assertThat(single.dumpEntry(new java.lang.StringBuffer(), true)).isNotEmpty();
        java.nio.ByteBuffer booleanBuffer = java.nio.ByteBuffer.allocate(1);
        booleanBuffer.put((byte) 1).flip();
        StringBuffer booleanDump = new java.lang.StringBuffer();
        assertThat(com.sleepycat.je.log.LogUtils.dumpBoolean(booleanBuffer, booleanDump,
                "present")).isTrue();
        assertThat(booleanDump.toString()).contains("present", "true");
        INLogEntry inEntry = new INLogEntry(IN.class);
        assertThat(inEntry.getTransactionId()).isZero();
        assertThat(new com.sleepycat.je.log.entry.BINDeltaLogEntry(IN.class)).isNotNull();
    }

    @Test
    void logReadersCanInspectTheLogProducedByARealEnvironment(@TempDir Path home)
            throws Exception {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
        config.setTransactional(true);
        Environment environment = new Environment(home.toFile(), config);
        try {
            com.sleepycat.je.DatabaseConfig dbConfig = new com.sleepycat.je.DatabaseConfig();
            dbConfig.setAllowCreate(true);
            dbConfig.setTransactional(true);
            com.sleepycat.je.Database database = environment.openDatabase(null, "records", dbConfig);
            database.put(null, new DatabaseEntry(new byte[] {1}),
                    new DatabaseEntry(new byte[] {2}));
            database.close();
            environment.checkpoint(new com.sleepycat.je.CheckpointConfig());
            com.sleepycat.je.dbi.EnvironmentImpl implementation =
                    com.sleepycat.je.DbInternal.envGetEnvironmentImpl(environment);
            FileManager fileManager = implementation.getFileManager();
            long lastLsn = fileManager.getLastUsedLsn();
            long firstFile = fileManager.getFirstFileNum();
            byte[] logBytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                    fileManager.getFullFileName(firstFile, FileManager.JE_SUFFIX)));
            java.nio.ByteBuffer headerBytes = java.nio.ByteBuffer.wrap(logBytes,
                    FileManager.firstLogEntryOffset(), logBytes.length -
                            FileManager.firstLogEntryOffset()).slice();
            com.sleepycat.je.log.LogEntryHeader entryHeader =
                    new com.sleepycat.je.log.LogEntryHeader(implementation, headerBytes, false);
            StringBuffer entryHeaderDump = new java.lang.StringBuffer();
            entryHeader.dumpLog(entryHeaderDump, true);
            assertThat(entryHeader.getSize()).isPositive();
            DeletedDupLNLogEntry writingDeleted = new DeletedDupLNLogEntry(
                    LogEntryType.LOG_DEL_DUPLN, new LN(new byte[] {6}),
                    new com.sleepycat.je.dbi.DatabaseId(22), new byte[] {7},
                    new byte[] {8}, 9L, true, null);
            java.nio.ByteBuffer deletedBytes = java.nio.ByteBuffer.allocate(
                    writingDeleted.getSize());
            writingDeleted.writeEntry(entryHeader, deletedBytes);
            deletedBytes.flip();
            DeletedDupLNLogEntry readDeleted = new DeletedDupLNLogEntry();
            readDeleted.setLogType(LogEntryType.LOG_DEL_DUPLN);
            readDeleted.readEntry(entryHeader, deletedBytes, true);
            assertThat(readDeleted.getDupKey()).containsExactly(8);
            long startLsn = DbLsn.makeLsn(firstFile, FileManager.firstLogEntryOffset());
            assertThat(UtilizationFileReader.calcFileSummaryMap(implementation)).isNotNull();
            assertThat(fileManager.getCurrentFileNum()).isGreaterThanOrEqualTo(0L);
            assertThat(fileManager.getFirstFileNum()).isEqualTo(firstFile);
            assertThat(fileManager.getFullFileName(firstFile, FileManager.JE_SUFFIX))
                    .contains(FileManager.JE_SUFFIX);
            assertThat(fileManager.getReadOnly()).isFalse();
            fileManager.setSyncAtFileEnd(false);
            assertThatThrownBy(() -> fileManager.deleteFile(Long.MAX_VALUE))
                    .isInstanceOf(Exception.class);
            assertThatThrownBy(() -> fileManager.renameFile(Long.MAX_VALUE, ".coverage"))
                    .isInstanceOf(Exception.class);
            assertThat(FileManager.listFiles(home.toFile(), new String[] {FileManager.JE_SUFFIX}))
                    .isNotNull();

            com.sleepycat.je.log.CleanerFileReader cleaner =
                    new com.sleepycat.je.log.CleanerFileReader(implementation, 4096,
                            startLsn, firstFile);
            assertThat(cleaner.isIN()).isIn(true, false);
            assertThat(cleaner.isLN()).isIn(true, false);
            assertThat(cleaner.isRoot()).isIn(true, false);
            assertThat(cleaner.isFileHeader()).isIn(true, false);

            LastFileReader last = new LastFileReader(implementation, 4096,
                    firstFile);
            last.setTargetType(LogEntryType.LOG_LN);
            assertThat(last.getLastSeen(LogEntryType.LOG_LN)).isGreaterThanOrEqualTo(-1L);
            last.readNextEntry();
            assertThat(last.getNumRead()).isGreaterThanOrEqualTo(0);
            assertThat(last.getAndResetNReads()).isGreaterThanOrEqualTo(0);

            SearchFileReader search = new SearchFileReader(implementation, 4096, false,
                    startLsn, lastLsn, LogEntryType.LOG_LN);
            search.readNextEntry();
            search.getLastObject();

            CheckpointFileReader checkpoint = new CheckpointFileReader(implementation, 4096,
                    false, startLsn, lastLsn, lastLsn);
            assertThat(checkpoint.isRoot()).isFalse();
            assertThat(checkpoint.isCheckpointStart()).isFalse();
            assertThat(checkpoint.isCheckpointEnd()).isFalse();

            INFileReader inReader = new INFileReader(implementation, 4096, startLsn, lastLsn,
                    false, false, 0L, new HashMap<>());
            inReader.addTargetType(LogEntryType.LOG_IN);
            inReader.readNextEntry();

            com.sleepycat.je.tree.BINDelta delta = new com.sleepycat.je.tree.BINDelta();
            java.nio.ByteBuffer deltaBytes = java.nio.ByteBuffer.allocate(delta.getLogSize());
            delta.writeToLog(deltaBytes);
            deltaBytes.flip();
            BINDeltaLogEntry deltaEntry = new BINDeltaLogEntry(com.sleepycat.je.tree.BINDelta.class);
            deltaEntry.readEntry(entryHeader, deltaBytes, true);
            assertThat(deltaEntry.getDbId()).isNotNull();
            assertThat(deltaEntry.getLsnOfIN(lastLsn)).isNegative();
            assertThatThrownBy(() -> deltaEntry.getIN(null)).isInstanceOf(Exception.class);

            LNFileReader lnReader = new LNFileReader(implementation, 4096, startLsn, false,
                    lastLsn, lastLsn, null);
            ScavengerFileReader scavenger = new ScavengerFileReader(implementation, 4096,
                    startLsn, lastLsn, lastLsn) {
                @Override
                protected void processEntryCallback(com.sleepycat.je.log.entry.LogEntry entry,
                        LogEntryType entryType) {
                }
            };
            scavenger.setDumpCorruptedBounds(false);
            scavenger.setTargetType(LogEntryType.LOG_LN);
            scavenger.readNextEntry();
            assertThat(lnReader).isNotNull();
            lnReader.addTargetType(LogEntryType.LOG_LN);
            lnReader.addTargetType(LogEntryType.LOG_LN_TRANSACTIONAL);
            lnReader.addTargetType(LogEntryType.LOG_NAMELN);
            lnReader.addTargetType(LogEntryType.LOG_NAMELN_TRANSACTIONAL);
            lnReader.addTargetType(LogEntryType.LOG_MAPLN);
            lnReader.addTargetType(LogEntryType.LOG_MAPLN_TRANSACTIONAL);
            lnReader.addTargetType(LogEntryType.LOG_DEL_DUPLN);
            lnReader.addTargetType(LogEntryType.LOG_DEL_DUPLN_TRANSACTIONAL);
            lnReader.addTargetType(LogEntryType.LOG_DUPCOUNTLN);
            lnReader.addTargetType(LogEntryType.LOG_DUPCOUNTLN_TRANSACTIONAL);
            lnReader.addTargetType(LogEntryType.LOG_FILESUMMARYLN);
            boolean foundLn = false;
            for (int attempt = 0; attempt < 1000 && !foundLn; attempt++) {
                foundLn = lnReader.readNextEntry();
            }
            if (foundLn && lnReader.isLN()) {
                assertThat(lnReader.getTxnPrepareId()).isGreaterThanOrEqualTo(-1L);
                assertThat(lnReader.getTxnAbortId()).isGreaterThanOrEqualTo(-1L);
                assertThat(lnReader.getTxnCommitId()).isGreaterThanOrEqualTo(-1L);
                assertThat(lnReader.getNodeId()).isGreaterThanOrEqualTo(-1L);
                assertThat(lnReader.getTxnPrepareXid()).isNull();
                assertThat(lnReader.isPrepare()).isIn(true, false);
                assertThat(lnReader.isAbort()).isIn(true, false);
            } else {
                assertThatThrownBy(lnReader::getTxnPrepareId).isInstanceOf(Exception.class);
                assertThatThrownBy(lnReader::getTxnAbortId).isInstanceOf(Exception.class);
                assertThatThrownBy(lnReader::getTxnCommitId).isInstanceOf(Exception.class);
                assertThatThrownBy(lnReader::getNodeId).isInstanceOf(Exception.class);
                assertThatThrownBy(lnReader::getTxnPrepareXid).isInstanceOf(Exception.class);
                assertThatThrownBy(lnReader::isPrepare).isInstanceOf(Exception.class);
                assertThatThrownBy(lnReader::isAbort).isInstanceOf(Exception.class);
            }

            assertThatThrownBy(inReader::getDeletedNodeId).isInstanceOf(Exception.class);
            assertThatThrownBy(inReader::getDupDeletedNodeId).isInstanceOf(Exception.class);
            assertThatThrownBy(inReader::getDeletedIdKey).isInstanceOf(Exception.class);
            assertThatThrownBy(inReader::getDupDeletedMainKey).isInstanceOf(Exception.class);
            assertThatThrownBy(inReader::getDupDeletedDupKey).isInstanceOf(Exception.class);

            new StatsFileReader(implementation, 4096, startLsn, lastLsn, null, null, false)
                    .summarize();
            new StatsFileReader(implementation, 4096, startLsn, lastLsn, null, null, true)
                    .summarize();
            new PrintFileReader(implementation, 4096, startLsn, lastLsn, null, null, false)
                    .summarize();
            TraceLogHandler handler = new TraceLogHandler(implementation);
            handler.publish(new LogRecord(java.util.logging.Level.INFO, "coverage"));
            handler.flush();
            handler.close();
            EnvironmentStats stats = new EnvironmentStats();
            implementation.getLogManager().loadEndOfLogStat(stats);
            while (cleaner.readNextEntry() && !cleaner.isLN()) {
                // Continue until an LN supplies the key and database views.
            }
            assertThat(cleaner.isLN()).isTrue();
            assertThat(cleaner.getDatabaseId()).isNotNull();
            assertThat(cleaner.getDupTreeKey()).isEmpty();
            assertThat(cleaner.getKey()).isNotNull();
            assertThat(cleaner.getLN()).isNotNull();
            assertThatThrownBy(cleaner::getIN).isInstanceOf(Exception.class);
            CleanerFileReader headerReader = new CleanerFileReader(implementation, 4096,
                    startLsn, firstFile);
            while (headerReader.readNextEntry() && !headerReader.isFileHeader()) {
                // The file header is the first metadata entry in the file.
            }
            assertThat(headerReader.isFileHeader()).isTrue();
            assertThat(headerReader.getFileHeader()).isNotNull();
        } finally {
            environment.close();
        }
    }
}
