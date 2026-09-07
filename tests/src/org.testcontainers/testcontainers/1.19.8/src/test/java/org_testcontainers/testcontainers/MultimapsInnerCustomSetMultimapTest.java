/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.base.Supplier;
import org.testcontainers.shaded.com.google.common.collect.Multimaps;
import org.testcontainers.shaded.com.google.common.collect.SetMultimap;
import org.testcontainers.shaded.org.apache.commons.lang3.SerializationUtils;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class MultimapsInnerCustomSetMultimapTest {
    @Test
    @SuppressWarnings("unchecked")
    void preservesCustomSetFactoriesWhenSerialized() {
        SetMultimap<String, String> values = Multimaps.newSetMultimap(new HashMap<>(), new SetFactory());
        values.put("key", "value");

        SetMultimap<String, String> restored = (SetMultimap<String, String>) SerializationUtils.roundtrip(
            (Serializable) values
        );

        assertThat(restored.asMap().get("key")).containsExactly("value");
    }

    public static class SetFactory implements Supplier<Set<String>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Set<String> get() {
            return new HashSet<>();
        }
    }
}
