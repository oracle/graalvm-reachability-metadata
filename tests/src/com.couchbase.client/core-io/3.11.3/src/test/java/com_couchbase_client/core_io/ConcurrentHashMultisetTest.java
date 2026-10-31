/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.collect.ConcurrentHashMultiset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConcurrentHashMultisetTest {
    @Test
    void preservesContents() throws Exception {
        ConcurrentHashMultiset<String> original = ConcurrentHashMultiset.create();
        original.add("apple", 4);
        original.add("pear", 2);
        ConcurrentHashMultiset<String> restored = SerializationSupport.roundTrip(original);
        assertEquals(original, restored);
        assertEquals(4, restored.count("apple"));
        restored.remove("apple", 2);
        assertEquals(2, restored.count("apple"));
        assertEquals(4, original.count("apple"));
    }
}
