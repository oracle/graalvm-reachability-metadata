/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.hash.BloomFilter;
import com.couchbase.client.core.deps.com.google.common.hash.Funnels;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HashStriped64Anonymous1Test {
    @Test
    void exercisesPublicApi() throws Exception {
        BloomFilter<CharSequence> filter = BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), 100);
        assertTrue(filter.put("alpha"));
        assertTrue(filter.put("beta"));
        assertTrue(filter.mightContain("alpha"));
        assertTrue(filter.mightContain("beta"));
        assertEquals(2, filter.approximateElementCount());
    }
}
