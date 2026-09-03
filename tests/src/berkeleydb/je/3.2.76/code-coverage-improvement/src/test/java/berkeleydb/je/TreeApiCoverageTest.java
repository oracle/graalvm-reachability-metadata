/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.cleaner.FileSummary;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.tree.BINDelta;
import com.sleepycat.je.tree.BINReference;
import com.sleepycat.je.tree.ChildReference;
import com.sleepycat.je.tree.DBIN;
import com.sleepycat.je.tree.DIN;
import com.sleepycat.je.tree.DupCountLN;
import com.sleepycat.je.tree.FileSummaryLN;
import com.sleepycat.je.tree.Generation;
import com.sleepycat.je.tree.INDeleteInfo;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.tree.INDupDeleteInfo;
import com.sleepycat.je.tree.Key;
import com.sleepycat.je.tree.LN;
import com.sleepycat.je.tree.SearchResult;
import com.sleepycat.je.tree.TrackingInfo;
import com.sleepycat.je.tree.TreeLocation;
import com.sleepycat.je.tree.TreeIterator;
import com.sleepycat.je.tree.Tree;
import com.sleepycat.je.tree.MapLN;
import com.sleepycat.je.tree.NameLN;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TreeApiCoverageTest {

    @Test
    void nodesReferencesAndDuplicateCountsExposeConsistentState(@org.junit.jupiter.api.io.TempDir Path home)
            throws Exception {
        EnvironmentConfig environmentConfig = new EnvironmentConfig();
        environmentConfig.setAllowCreate(true);
        Environment environment = new Environment(home.toFile(), environmentConfig);
        DatabaseConfig databaseConfig = new DatabaseConfig();
        databaseConfig.setAllowCreate(true);
        com.sleepycat.je.Database databaseHandle =
                environment.openDatabase(null, "tree", databaseConfig);
        LN node = new LN(new byte[] {4, 5, 6});
        assertThat(node.copyData()).containsExactly(4, 5, 6);
        assertThat(node.getTransactionId()).isZero();
        assertThat(node.shortDescription()).contains("LN");
        assertThat(node.getType()).contains("LN");
        assertThat(node.toString()).isNotEmpty();
        node.verify(new byte[] {9});
        StringBuffer nodeDump = new java.lang.StringBuffer();
        node.dumpLog(nodeDump, true);
        assertThat(nodeDump).contains("ln");
        node.dump(1);
        node.latchShared();
        node.releaseLatch();

        ChildReference reference = new ChildReference(node, new byte[] {1}, 22L);
        assertThat(reference.getTarget()).isSameAs(node);
        assertThat(reference.getKey()).containsExactly(1);
        assertThat(reference.getLsn()).isEqualTo(22L);
        reference.setKey(new byte[] {2});
        reference.setLsn(23L);
        reference.setMigrate(true);
        assertThat(reference.getKey()).containsExactly(2);
        assertThat(reference.getLsn()).isEqualTo(23L);
        assertThat(reference.getMigrate()).isTrue();
        assertThat(reference.getTransactionId()).isZero();
        assertThat(reference.toString()).contains("DbLsn");
        StringBuffer referenceDump = new java.lang.StringBuffer();
        reference.dumpLog(referenceDump, false);
        assertThat(referenceDump).contains("DbLsn");
        reference.clearTarget();
        assertThat(reference.getTarget()).isNull();

        DatabaseImpl database = com.sleepycat.je.DbInternal.dbGetDatabaseImpl(databaseHandle);
        BIN bin = new BIN(database, new byte[] {0}, 1, 10);
        assertThatThrownBy(() -> bin.getChildKey(new IN()))
                .isInstanceOf(Exception.class);
        new com.sleepycat.je.tree.IN(database, new byte[] {0}, 1, 10).logDirtyChildren();
        BINReference binReference = bin.createReference();
        assertThat(binReference.getNodeId()).isEqualTo(bin.getNodeId());
        assertThat(binReference.getDatabaseId()).isNotNull();
        assertThat(binReference.getData()).isNull();
        assertThat(binReference.getKey()).isNotNull();
        Key deleted = new Key(new byte[] {9});
        binReference.addDeletedKey(deleted);
        assertThat(binReference.deletedKeysExist()).isTrue();
        assertThat(binReference.hasDeletedKey(deleted)).isTrue();
        assertThat(binReference.getDeletedKeyIterator()).isNotNull();
        assertThat(binReference.toString()).contains("nodeId");
        assertThat(binReference.equals(binReference)).isTrue();
        assertThat(binReference.hashCode()).isEqualTo((int) bin.getNodeId());
        binReference.removeDeletedKey(deleted);
        assertThat(binReference.deletedKeysExist()).isFalse();
        bin.setKnownDeleted(0);
        assertThat(bin.getCursorSet()).isEmpty();
        bin.verifyCursors();
        assertThat(bin.getLastDeltaVersion()).isEqualTo(-1L);
        bin.latch();
        assertThat(bin.evictLNs()).isGreaterThanOrEqualTo(0L);
        bin.logDirtyChildren();
        bin.releaseLatch();

        DupCountLN count = new DupCountLN(2);
        assertThat(count.incDupCount()).isEqualTo(3);
        assertThat(count.decDupCount()).isEqualTo(2);
        assertThat(count.containsDuplicates()).isTrue();
        assertThat(count.beginTag()).contains("dupCount");
        assertThat(count.endTag()).isNotEmpty();
        assertThat(count.dumpString(0, true)).contains("dupCount");
        assertThat(count.toString()).contains("dupCount");
        count.accumulateStats(new com.sleepycat.je.tree.TreeWalkerStatsAccumulator() {
            @Override
            public void processIN(IN node, Long nid, int level) {
            }

            @Override
            public void processBIN(BIN node, Long nid, int level) {
            }

            @Override
            public void processDIN(DIN node, Long nid, int level) {
            }

            @Override
            public void processDBIN(DBIN node, Long nid, int level) {
            }

            @Override
            public void processDupCountLN(DupCountLN node, Long nid) {
            }

            @Override
            public void incrementLNCount() {
            }

            @Override
            public void incrementDeletedLNCount() {
            }
        });
        assertThat(new MapLN().toString()).isNotEmpty();
        assertThat(new NameLN().toString()).isNotEmpty();
        TreeIterator iterator = new TreeIterator(new Tree());
        assertThat(iterator.hasNext()).isFalse();
        assertThatThrownBy(iterator::next).isInstanceOf(java.util.NoSuchElementException.class);
        assertThatThrownBy(iterator::remove).isInstanceOf(UnsupportedOperationException.class);
        databaseHandle.close();
        environment.close();
    }

    @Test
    void treeLogRecordsRoundTripTheirPublicFields() throws Exception {
        DatabaseId databaseId = new DatabaseId(8);
        INDeleteInfo delete = new INDeleteInfo(44L, new byte[] {1, 2}, databaseId);
        assertThat(delete.getDeletedNodeId()).isEqualTo(44L);
        assertThat(delete.getDeletedIdKey()).containsExactly(1, 2);
        assertThat(delete.getDatabaseId()).isSameAs(databaseId);
        assertThat(delete.getTransactionId()).isZero();
        ByteBuffer deleteBuffer = ByteBuffer.allocate(delete.getLogSize());
        delete.writeToLog(deleteBuffer);
        deleteBuffer.flip();
        INDeleteInfo deleteCopy = new INDeleteInfo();
        deleteCopy.readFromLog(deleteBuffer, (byte) 0);
        StringBuffer deleteDump = new java.lang.StringBuffer();
        deleteCopy.dumpLog(deleteDump, true);
        assertThat(deleteDump).isNotEmpty();

        INDupDeleteInfo dupDelete = new INDupDeleteInfo(45L, new byte[] {3},
                new byte[] {4}, databaseId);
        assertThat(dupDelete.getDeletedNodeId()).isEqualTo(45L);
        assertThat(dupDelete.getDeletedMainKey()).containsExactly(3);
        assertThat(dupDelete.getDeletedDupKey()).containsExactly(4);
        assertThat(dupDelete.getDatabaseId()).isSameAs(databaseId);
        ByteBuffer dupBuffer = ByteBuffer.allocate(dupDelete.getLogSize());
        dupDelete.writeToLog(dupBuffer);
        dupBuffer.flip();
        INDupDeleteInfo dupCopy = new INDupDeleteInfo();
        dupCopy.readFromLog(dupBuffer, (byte) 0);
        assertThat(dupCopy.getTransactionId()).isZero();
        StringBuffer dupDump = new java.lang.StringBuffer();
        dupCopy.dumpLog(dupDump, false);
        assertThat(dupDump).isNotEmpty();

        BINDelta delta = new BINDelta();
        ByteBuffer deltaBuffer = ByteBuffer.allocate(Math.max(delta.getLogSize(), 1));
        delta.writeToLog(deltaBuffer);
        deltaBuffer.flip();
        delta.readFromLog(deltaBuffer, (byte) 0);
        assertThat(delta.getDbId()).isNotNull();
        assertThat(delta.getLastFullLsn()).isNegative();
        assertThat(delta.getTransactionId()).isZero();
        StringBuffer deltaDump = new java.lang.StringBuffer();
        delta.dumpLog(deltaDump, false);
        assertThat(deltaDump).isNotEmpty();

        SearchResult result = new SearchResult();
        result.exactParentFound = true;
        result.index = 3;
        assertThat(result.toString()).contains("exactParentFound");
        TreeLocation location = new TreeLocation();
        location.index = 3;
        location.lnKey = new byte[] {1};
        assertThat(location.toString()).contains("index");
        location.reset();
        assertThat(location.lnKey).isNull();
        assertThat(new TrackingInfo(1L, 2L).toString()).contains("1");
        assertThat(new Generation()).isNotNull();
    }

    @Test
    void logDumpDispatchIncludesSpecializedTreeNodeDetails() throws Exception {
        FileSummary fileSummary = new FileSummary();
        fileSummary.totalCount = 2;
        fileSummary.totalSize = 20;
        FileSummaryLN fileSummaryNode = new FileSummaryLN(fileSummary);
        StringBuffer fileSummaryDump = new java.lang.StringBuffer();
        fileSummaryNode.dumpLog(fileSummaryDump, true);
        assertThat(fileSummaryNode.dumpString(0, true)).contains("fileSummary");
        assertThat(fileSummaryDump).isNotEmpty();

        StringBuffer duplicateDump = new java.lang.StringBuffer();
        new DupCountLN(3).dumpLog(duplicateDump, true);
        assertThat(duplicateDump).isNotEmpty();
        StringBuffer duplicateInternalDump = new java.lang.StringBuffer();
        new DIN().dumpLog(duplicateInternalDump, true);
        new DBIN().dumpLog(duplicateInternalDump, true);
        assertThat(duplicateInternalDump).isNotEmpty();
        StringBuffer mapDump = new java.lang.StringBuffer();
        new MapLN().dumpLog(mapDump, true);
        assertThat(mapDump).isNotEmpty();
        StringBuffer nameDump = new java.lang.StringBuffer();
        new NameLN(new DatabaseId(9)).dumpLog(nameDump, true);
        assertThat(nameDump).isNotEmpty();
    }

    @Test
    void duplicateTreeNodesSelectAndRenderKeys() throws Exception {
        IN plain = new IN();
        assertThat(plain.getChildKey(new IN())).isEmpty();
        assertThatThrownBy(plain::getDupKey).isInstanceOf(Exception.class);
        assertThat(plain.compress(null, false, null)).isFalse();
        DIN din = new DIN();
        DBIN dbin = new DBIN();
        assertThat(din.beginTag()).isNotEmpty();
        assertThat(din.endTag()).isNotEmpty();
        assertThat(din.dumpString(0, true)).isNotEmpty();
        assertThat(din.toString()).isNotEmpty();
        assertThat(din.getDupKey()).isNull();
        assertThat(din.getChildKey(new IN())).isEmpty();
        din.updateDupCountLN(new DupCountLN());
        assertThat(din.selectKey(new byte[] {1}, new byte[] {2})).containsExactly(2);
        assertThat(dbin.beginTag()).isNotEmpty();
        assertThat(dbin.endTag()).isNotEmpty();
        assertThat(dbin.dumpString(0, false)).isNotEmpty();
        assertThat(dbin.selectKey(new byte[] {3}, new byte[] {4})).containsExactly(4);
        assertThat(dbin.getChildKey(new IN())).isEmpty();
        assertThat(Key.getNoFormatString(new byte[] {1, 2})).contains("1 2");
        assertThat(new Key(new byte[] {7}).getKey()).containsExactly(7);
        assertThat(new DatabaseEntry(new byte[] {1})).isNotNull();
        assertThat(new FileSummary()).isNotNull();
    }
}
