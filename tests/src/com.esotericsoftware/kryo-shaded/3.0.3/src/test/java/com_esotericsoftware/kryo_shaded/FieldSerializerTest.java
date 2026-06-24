/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import com.esotericsoftware.kryo.util.UnsafeUtil;
import com.esotericsoftware.kryo.util.Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FieldSerializerTest {
    @BeforeEach
    void disableRuntimeCodeGeneration() {
        Util.isAndroid = false;
        UnsafeUtil.unsafe();
        Util.isAndroid = true;
    }

    @Test
    void serializesPojoFieldsWithUnsafeBackedFieldSerializer() {
        Kryo kryo = new Kryo();
        kryo.setAsmEnabled(false);
        kryo.setReferences(false);

        FieldSerializer<Message> serializer = new FieldSerializer<>(kryo, Message.class);
        kryo.register(Message.class, serializer);

        Message original = new Message("kryo", 303, true);
        Output output = new Output(128, -1);
        kryo.writeObject(output, original, serializer);
        output.close();

        Input input = new Input(output.toBytes());
        Message restored = kryo.readObject(input, Message.class, serializer);
        input.close();

        assertMessage(restored, original.text, original.code, original.active);
        assertThat(fieldNames(serializer)).containsExactly("active", "code", "text");
        assertThat(serializer.getUseAsmEnabled()).isFalse();
    }

    @Test
    void rebuildsCachedFieldsAfterConfigurationChanges() {
        Kryo kryo = new Kryo();
        kryo.setAsmEnabled(false);
        kryo.setReferences(false);

        FieldSerializer<Message> serializer = new FieldSerializer<>(kryo, Message.class);
        serializer.setFieldsCanBeNull(false);
        serializer.setFixedFieldTypes(true);

        Message copy = serializer.copy(kryo, new Message("copy", 7, false));

        assertMessage(copy, "copy", 7, false);
        assertThat(serializer.getFields()).hasSize(3);
        assertThat(serializer.getUseAsmEnabled()).isFalse();
    }

    private static String[] fieldNames(FieldSerializer<Message> serializer) {
        FieldSerializer.CachedField[] fields = serializer.getFields();
        String[] names = new String[fields.length];
        for (int i = 0; i < fields.length; i++) {
            names[i] = fields[i].toString();
        }
        return names;
    }

    private static void assertMessage(Message message, String text, int code, boolean active) {
        assertThat(message.text).isEqualTo(text);
        assertThat(message.code).isEqualTo(code);
        assertThat(message.active).isEqualTo(active);
    }

    public static class Message {
        private String text;
        private int code;
        private boolean active;

        public Message() {
        }

        Message(String text, int code, boolean active) {
            this.text = text;
            this.code = code;
            this.active = active;
        }
    }
}
