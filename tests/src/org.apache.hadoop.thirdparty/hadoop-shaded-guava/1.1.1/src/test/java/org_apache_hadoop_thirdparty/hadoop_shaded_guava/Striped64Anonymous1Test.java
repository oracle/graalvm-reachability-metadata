/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.apache.hadoop.thirdparty.com.google.common.hash.BloomFilter;
import org.apache.hadoop.thirdparty.com.google.common.hash.Funnels;
import org.junit.jupiter.api.Test;

public class Striped64Anonymous1Test {
    @Test
    void bloomFilterUsesHashPackageLongAdderForBitCounting() {
        BloomFilter<CharSequence> filter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                100,
                0.01);

        assertThat(filter.put("alpha")).isTrue();
        filter.put("beta");
        assertThat(filter.put("alpha")).isFalse();

        assertThat(filter.mightContain("alpha")).isTrue();
        assertThat(filter.mightContain("beta")).isTrue();
        assertThat(filter.approximateElementCount()).isBetween(1L, 3L);
    }
}
