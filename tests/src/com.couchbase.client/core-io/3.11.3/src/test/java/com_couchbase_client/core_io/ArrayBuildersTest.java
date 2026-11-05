/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.util.ArrayBuilders;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class ArrayBuildersTest {
    @Test
    void mapsApplicationData() throws Exception {
        String[] initial = {"alpha", "beta"};
        String[] inserted = ArrayBuilders.insertInListNoDup(initial, "gamma");
        assertArrayEquals(new String[]{"gamma", "alpha", "beta"}, inserted);
        assertArrayEquals(new String[]{"beta", "alpha"}, ArrayBuilders.insertInListNoDup(initial, "beta"));
        assertArrayEquals(new String[]{"alpha", "beta"}, initial);
    }
}
