/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.base.Supplier;
import org.testcontainers.shaded.com.google.common.collect.ListMultimap;
import org.testcontainers.shaded.com.google.common.collect.Multimaps;
import org.testcontainers.shaded.org.apache.commons.lang3.SerializationUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MultimapsInnerCustomListMultimapTest {
    @Test
    @SuppressWarnings("unchecked")
    void preservesCustomListFactoriesWhenSerialized() {
        ListMultimap<String, String> values = Multimaps.newListMultimap(new HashMap<>(), new ListFactory());
        values.put("key", "one");
        values.put("key", "two");

        ListMultimap<String, String> restored = (ListMultimap<String, String>) SerializationUtils.roundtrip(
            (Serializable) values
        );

        assertThat(restored.asMap().get("key")).containsExactly("one", "two");
    }

    public static class ListFactory implements Supplier<List<String>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public List<String> get() {
            return new ArrayList<>();
        }
    }
}
