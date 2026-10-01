/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.curator.shaded.com.google.common.collect.TreeMultimap;
import org.apache.curator.shaded.com.google.common.collect.TreeMultiset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TreeCollectionsSerializationTest {
    @Test
    void preservesSortedCollectionsWhenRoundTripped() throws Exception {
        TreeMultimap<String, Integer> multimap = TreeMultimap.create();
        multimap.put("key", 2);
        multimap.put("key", 1);
        TreeMultiset<Integer> multiset = TreeMultiset.create();
        multiset.add(2);
        multiset.add(1);

        assertThat(roundTrip(multimap)).isEqualTo(multimap);
        assertThat(roundTrip(multiset)).isEqualTo(multiset);
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) input.readObject();
        }
    }
}
