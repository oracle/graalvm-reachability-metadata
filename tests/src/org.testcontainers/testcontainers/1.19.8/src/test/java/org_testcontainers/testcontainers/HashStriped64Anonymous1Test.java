/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.hash.BloomFilter;
import org.testcontainers.shaded.com.google.common.hash.Funnels;

import static org.assertj.core.api.Assertions.assertThat;

public class HashStriped64Anonymous1Test {
    @Test
    void countsInsertionsWhileEstimatingBloomFilterPopulation() {
        BloomFilter<CharSequence> filter = BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), 32);
        filter.put("first");
        filter.put("second");

        assertThat(filter.mightContain("first")).isTrue();
        assertThat(filter.approximateElementCount()).isBetween(1L, 3L);
    }
}
