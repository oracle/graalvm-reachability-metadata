/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_twelvemonkeys_common.common_lang;

import com.twelvemonkeys.util.CollectionUtil;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectionUtilTest {
    @Test
    void createsTypedArraysAndInvertsMaps() {
        String[] merged = (String[]) CollectionUtil.mergeArrays(
                new String[] {"first", "second"}, new String[] {"third"});
        String[] subset = CollectionUtil.subArray(merged, 1, 2);

        Map<String, Integer> source = new LinkedHashMap<>();
        source.put("one", 1);
        source.put("two", 2);
        Map<Integer, String> inverted = CollectionUtil.invert(source);

        assertThat(merged).containsExactly("first", "second", "third");
        assertThat(subset).containsExactly("second", "third");
        assertThat(inverted).isInstanceOf(LinkedHashMap.class).containsEntry(1, "one").containsEntry(2, "two");
    }
}
