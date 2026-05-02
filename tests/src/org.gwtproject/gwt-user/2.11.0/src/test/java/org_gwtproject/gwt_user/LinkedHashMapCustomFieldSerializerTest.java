/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gwt.user.client.rpc.SerializationStreamWriter;
import com.google.gwt.user.client.rpc.core.java.util.LinkedHashMap_CustomFieldSerializer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class LinkedHashMapCustomFieldSerializerTest {
    @Test
    void serializesInsertionOrderLinkedHashMapAccessMode() throws Exception {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("first", "alpha");
        map.put("second", "beta");
        RecordingStreamWriter writer = new RecordingStreamWriter();

        LinkedHashMap_CustomFieldSerializer.serialize(writer, map);

        assertThat(writer.values()).containsExactly(false, 2, "first", "alpha", "second", "beta");
    }

    @Test
    void serializesAccessOrderLinkedHashMapAccessMode() throws Exception {
        LinkedHashMap<String, String> map = new LinkedHashMap<>(16, 0.75f, true);
        map.put("first", "alpha");
        map.put("second", "beta");
        map.get("first");
        RecordingStreamWriter writer = new RecordingStreamWriter();

        LinkedHashMap_CustomFieldSerializer.serialize(writer, map);

        assertThat(writer.values()).containsExactly(true, 2, "second", "beta", "first", "alpha");
    }

    private static final class RecordingStreamWriter implements SerializationStreamWriter {
        private final List<Object> values = new ArrayList<>();

        List<Object> values() {
            return values;
        }

        @Override
        public void writeBoolean(boolean value) {
            values.add(value);
        }

        @Override
        public void writeByte(byte value) {
            values.add(value);
        }

        @Override
        public void writeChar(char value) {
            values.add(value);
        }

        @Override
        public void writeDouble(double value) {
            values.add(value);
        }

        @Override
        public void writeFloat(float value) {
            values.add(value);
        }

        @Override
        public void writeInt(int value) {
            values.add(value);
        }

        @Override
        public void writeLong(long value) {
            values.add(value);
        }

        @Override
        public void writeObject(Object value) {
            values.add(value);
        }

        @Override
        public void writeShort(short value) {
            values.add(value);
        }

        @Override
        public void writeString(String value) {
            values.add(value);
        }

        @Override
        public String toString() {
            return values.toString();
        }
    }
}
