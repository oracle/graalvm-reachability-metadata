/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.hadoop.util.GenericsUtil;
import org.junit.jupiter.api.Test;

public class GenericsUtilTest {
    @Test
    void toArrayWithExplicitClassReturnsArrayWithRequestedComponentType() {
        List<String> values = Arrays.asList("first", "second");

        String[] result = GenericsUtil.toArray(String.class, values);

        assertThat(result).containsExactly("first", "second");
        assertThat(result.getClass().getComponentType()).isEqualTo(String.class);
    }

    @Test
    void toArrayWithExplicitClassSupportsEmptyLists() {
        String[] result = GenericsUtil.toArray(String.class, Collections.emptyList());

        assertThat(result).isEmpty();
        assertThat(result.getClass().getComponentType()).isEqualTo(String.class);
    }
}
