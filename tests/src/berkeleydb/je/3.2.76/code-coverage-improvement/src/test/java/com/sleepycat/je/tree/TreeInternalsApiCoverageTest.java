/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.sleepycat.je.tree;

import com.sleepycat.je.dbi.DatabaseId;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TreeInternalsApiCoverageTest {

    @Test
    void deltaRecordsRoundTripAndDuplicateReferencesExposeTheirKeys() throws Exception {
        DeltaInfo delta = new DeltaInfo(new byte[] {1, 2}, 42L, (byte) 0);
        assertThat(delta.getLogSize()).isPositive();
        assertThat(delta.getTransactionId()).isZero();
        ByteBuffer buffer = ByteBuffer.allocate(delta.getLogSize());
        delta.writeToLog(buffer);
        buffer.flip();
        DeltaInfo copy = new DeltaInfo();
        copy.readFromLog(buffer, (byte) 0);
        java.lang.StringBuffer dump = new java.lang.StringBuffer();
        copy.dumpLog(dump, true);
        assertThat(copy.getKey()).containsExactly(1, 2);
        assertThat(copy.getLsn()).isEqualTo(42L);
        assertThat(dump).isNotEmpty();

        DBINReference reference = new DBINReference(7L, new DatabaseId(8),
                new byte[] {3}, new byte[] {4, 5});
        assertThat(reference.getData()).containsExactly(3);
        assertThat(reference.getKey()).containsExactly(4, 5);
        assertThat(reference.toString()).contains("dupKey");

        assertThatThrownBy(() -> new INDeleteInfo().optionalLog(null, null))
                .isInstanceOf(Throwable.class);
        assertThatThrownBy(() -> new INDupDeleteInfo().optionalLog(null, null))
                .isInstanceOf(Throwable.class);
        assertThatThrownBy(() -> new BINDelta().reconstituteBIN(null))
                .isInstanceOf(Throwable.class);
    }

    @Test
    void treeSearchHandlesAnEmptyLatchedParent() throws Exception {
        Tree tree = new Tree();
        IN parent = new IN();
        parent.latch();
        ArrayList ladder = new ArrayList();
        tree.searchDeletableSubTree(parent, new byte[] {1}, ladder);
        parent.releaseLatch();
        assertThat(ladder).isEmpty();
        assertThat(tree.getDatabase()).isNull();
    }
}
