/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThat;

import autovalue.shaded.com.google.common.base.Supplier;
import autovalue.shaded.com.google.common.collect.Multimaps;
import autovalue.shaded.com.google.common.collect.SetMultimap;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class MultimapsInnerCustomSetMultimapTest {
    @Test
    void restoresCustomSetFactory() throws Exception {
        HashMap<String, Collection<String>> backingMap = new HashMap<>();
        SetMultimap<String, String> values = Multimaps.newSetMultimap(backingMap, new SetFactory());
        values.put("letters", "b");
        values.put("letters", "a");

        SetMultimap<String, String> restored = SerializationSupport.roundTrip(values);

        assertThat(restored.get("letters")).containsExactly("b", "a");
    }

    private static final class SetFactory implements Supplier<Set<String>>, Serializable {
        @Override
        public Set<String> get() {
            return new LinkedHashSet<>();
        }
    }
}
