/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.util.internal.chmv8.ConcurrentHashMapV8;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class ConcurrentHashMapV8Test {
    @Test
    void serializesAndDeserializesEntries() throws Exception {
        ConcurrentHashMapV8<String, String> map = new ConcurrentHashMapV8<String, String>();
        map.put("first", "alpha");
        map.put("second", "bravo");
        map.put("third", "charlie");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }

        Object deserialized;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            deserialized = input.readObject();
        }

        assertThat(deserialized).isInstanceOf(ConcurrentHashMapV8.class);
        @SuppressWarnings("unchecked")
        ConcurrentHashMapV8<String, String> restored = (ConcurrentHashMapV8<String, String>) deserialized;
        assertThat(restored).hasSize(3);
        assertThat(restored).containsEntry("first", "alpha");
        assertThat(restored).containsEntry("second", "bravo");
        assertThat(restored).containsEntry("third", "charlie");
    }
}
