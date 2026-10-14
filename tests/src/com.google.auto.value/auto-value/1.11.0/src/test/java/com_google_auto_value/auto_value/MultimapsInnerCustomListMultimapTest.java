/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThat;

import autovalue.shaded.com.google.common.base.Supplier;
import autovalue.shaded.com.google.common.collect.ListMultimap;
import autovalue.shaded.com.google.common.collect.Multimaps;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

public class MultimapsInnerCustomListMultimapTest {
    @Test
    void restoresCustomListFactory() throws Exception {
        HashMap<String, Collection<String>> backingMap = new HashMap<>();
        ListMultimap<String, String> values = Multimaps.newListMultimap(backingMap, new ListFactory());
        values.put("letters", "a");
        values.put("letters", "a");

        ListMultimap<String, String> restored = SerializationSupport.roundTrip(values);

        assertThat(restored.get("letters")).containsExactly("a", "a");
    }

    private static final class ListFactory implements Supplier<List<String>>, Serializable {
        @Override
        public List<String> get() {
            return new ArrayList<>();
        }
    }
}
