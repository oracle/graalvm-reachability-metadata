/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import com.google.gwt.user.client.rpc.SerializationStreamWriter;
import com.google.gwt.user.server.rpc.core.java.util.EnumMap_ServerCustomFieldSerializer;

import org.junit.jupiter.api.Test;

public class EnumMapServerCustomFieldSerializerTest {
    @Test
    void serializeInstanceWritesKeyUniverseExemplarBeforeMapEntries() throws Exception {
        EnumMap<SampleKey, String> map = new EnumMap<>(SampleKey.class);
        map.put(SampleKey.SECOND, "second-value");
        RecordingStreamWriter writer = new RecordingStreamWriter();
        EnumMap_ServerCustomFieldSerializer serializer = new EnumMap_ServerCustomFieldSerializer();

        serializer.serializeInstance(writer, map);

        assertThat(writer.values()).containsExactly(SampleKey.FIRST, 1, SampleKey.SECOND,
                "second-value");
    }

    private enum SampleKey {
        FIRST,
        SECOND
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
