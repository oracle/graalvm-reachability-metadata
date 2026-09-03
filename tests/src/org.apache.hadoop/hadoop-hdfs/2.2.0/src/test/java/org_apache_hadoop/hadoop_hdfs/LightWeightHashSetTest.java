/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_hdfs;

import org.apache.hadoop.hdfs.util.LightWeightHashSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LightWeightHashSetTest {
    @Test
    void pollToArrayShrinksOversizedTypedArray() {
        LightWeightHashSet<String> set = new LightWeightHashSet<String>();
        set.add("alpha");
        set.add("bravo");

        String[] values = set.pollToArray(new String[5]);

        assertThat(values)
                .isInstanceOf(String[].class)
                .containsExactlyInAnyOrder("alpha", "bravo");
        assertThat(values).hasSize(2);
        assertThat(set).isEmpty();
    }

    @Test
    void toArrayExpandsUndersizedTypedArray() {
        LightWeightHashSet<String> set = new LightWeightHashSet<String>();
        set.add("charlie");
        set.add("delta");

        String[] values = set.toArray(new String[0]);

        assertThat(values)
                .isInstanceOf(String[].class)
                .containsExactlyInAnyOrder("charlie", "delta");
        assertThat(values).hasSize(2);
        assertThat(set).hasSize(2);
    }
}
