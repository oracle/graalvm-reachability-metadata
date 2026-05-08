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
    void toArrayCreatesComponentTypedArrayWhenInputIsTooSmall() {
        LightWeightHashSet<String> values = new LightWeightHashSet<String>();
        values.add("alpha");
        values.add("beta");

        String[] provided = new String[0];
        String[] result = values.toArray(provided);

        assertThat(result).isNotSameAs(provided);
        assertThat(result).isInstanceOf(String[].class);
        assertThat(result).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(values).containsExactlyInAnyOrder("alpha", "beta");
    }

    @Test
    void pollToArrayCreatesComponentTypedArrayWhenInputIsTooLarge() {
        LightWeightHashSet<String> values = new LightWeightHashSet<String>();
        values.add("gamma");
        values.add("delta");

        String[] provided = new String[8];
        String[] result = values.pollToArray(provided);

        assertThat(result).isNotSameAs(provided);
        assertThat(result).isInstanceOf(String[].class);
        assertThat(result).containsExactlyInAnyOrder("gamma", "delta");
        assertThat(values).isEmpty();
    }
}
