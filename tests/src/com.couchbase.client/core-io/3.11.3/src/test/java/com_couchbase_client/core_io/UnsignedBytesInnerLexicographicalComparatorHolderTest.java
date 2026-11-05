/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.primitives.UnsignedBytes;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UnsignedBytesInnerLexicographicalComparatorHolderTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();
        assertTrue(comparator.compare(new byte[]{(byte) 0xff}, new byte[]{0}) > 0);
        assertTrue(comparator.compare(new byte[]{1, 2}, new byte[]{1, 2, 3}) < 0);
        assertEquals(0, comparator.compare(new byte[]{1, 2, 3}, new byte[]{1, 2, 3}));
    }
}
