/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import org.apache.curator.shaded.com.google.common.hash.BloomFilter;
import org.apache.curator.shaded.com.google.common.hash.Funnels;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HashStriped64Anonymous1Test {
    @Test
    void bloomFilterCountsBitsWithConcurrentLongCounter() {
        BloomFilter<CharSequence> filter = BloomFilter.create(Funnels.unencodedCharsFunnel(), 100);

        assertThat(filter.put("curator")).isTrue();
        assertThat(filter.mightContain("curator")).isTrue();
        assertThat(filter.approximateElementCount()).isEqualTo(1);
    }
}
