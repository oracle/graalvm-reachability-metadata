/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.collect.ImmutableSetMultimap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SerializationInnerFieldSetterTest {
    @Test
    void preservesContents() throws Exception {
        ImmutableSetMultimap<String, String> original = ImmutableSetMultimap.of("group", "a", "group", "b");
        ImmutableSetMultimap<String, String> restored = SerializationSupport.roundTrip(original);
        assertEquals(2, restored.size());
        assertEquals(original.asMap(), restored.asMap());
        assertEquals(1, restored.keySet().size());
    }
}
