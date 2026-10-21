/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThat;

import autovalue.shaded.com.google.common.base.Supplier;
import autovalue.shaded.com.google.common.collect.Multimap;
import autovalue.shaded.com.google.common.collect.Multimaps;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

public class MultimapsInnerCustomMultimapTest {
    @Test
    void restoresCustomCollectionFactory() throws Exception {
        Multimap<String, String> values = Multimaps.newMultimap(new HashMap<>(), new QueueFactory());
        values.put("letters", "a");
        values.put("letters", "b");

        Multimap<String, String> restored = SerializationSupport.roundTrip(values);

        assertThat(restored.get("letters")).containsExactly("a", "b");
        assertThat(restored.put("letters", "c")).isTrue();
    }

    private static final class QueueFactory implements Supplier<Collection<String>>, Serializable {
        @Override
        public Collection<String> get() {
            return new ArrayDeque<>();
        }
    }
}
