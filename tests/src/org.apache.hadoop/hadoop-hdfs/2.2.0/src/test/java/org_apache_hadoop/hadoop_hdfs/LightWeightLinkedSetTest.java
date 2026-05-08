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
    void toArrayCreatesComponentTypedArrayWhenInputIsTooSmall() {
        LightWeightLinkedSet<String> values = new LightWeightLinkedSet<String>();
        values.add("first");
        values.add("second");

        String[] provided = new String[0];
        String[] result = values.toArray(provided);

        assertThat(result).isNotSameAs(provided);
        assertThat(result).isInstanceOf(String[].class);
        assertThat(result).containsExactly("first", "second");
        assertThat(values).containsExactly("first", "second");
    }
}
