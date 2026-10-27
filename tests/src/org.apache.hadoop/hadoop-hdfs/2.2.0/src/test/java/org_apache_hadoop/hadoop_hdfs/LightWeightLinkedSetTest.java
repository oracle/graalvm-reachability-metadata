/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_hdfs;

import org.apache.hadoop.hdfs.util.LightWeightLinkedSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LightWeightLinkedSetTest {
    @Test
    void toArrayExpandsUndersizedTypedArrayInInsertionOrder() {
        LightWeightLinkedSet<String> set = new LightWeightLinkedSet<String>();
        set.add("alpha");
        set.add("bravo");
        set.add("charlie");

        String[] values = set.toArray(new String[0]);

        assertThat(values)
                .isInstanceOf(String[].class)
                .containsExactly("alpha", "bravo", "charlie");
        assertThat(set).hasSize(3);
    }
}
