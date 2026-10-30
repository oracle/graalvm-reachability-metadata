/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.util.CollectionUtils;

/** Verifies typed array allocation from a composite map's values view. */
public class CompositeCollectionTest {
    @Test
    void combinesVisibleValuesIntoTypedArray() {
        Map<String, String> first = new LinkedHashMap<>();
        first.put("shared", "primary");
        first.put("first", "one");
        Map<String, String> second = new LinkedHashMap<>();
        second.put("shared", "shadowed");
        second.put("second", "two");
        Collection<String> values = CollectionUtils.compositeMap(first, second).values();

        String[] result = values.toArray(new String[0]);

        assertThat(result).containsExactly("primary", "one", "two");
        assertThat(result.getClass()).isEqualTo(String[].class);
    }
}
