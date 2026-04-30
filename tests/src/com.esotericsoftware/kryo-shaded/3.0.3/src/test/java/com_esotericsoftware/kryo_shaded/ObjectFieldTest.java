/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import com.esotericsoftware.kryo.serializers.FieldSerializer.CachedField;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectFieldTest {
    @Test
    void writesAndReadsPrivateReferenceFieldThroughCachedObjectField() {
        FieldSerializer<MessageEnvelope> serializer = newMessageEnvelopeSerializer();
        CachedField<?> subjectField = serializer.getField("subject");
        MessageEnvelope source = new MessageEnvelope("integration-event");
        MessageEnvelope target = new MessageEnvelope("placeholder");

        Output output = new Output(64, -1);
        subjectField.write(output, source);
        subjectField.read(new Input(output.toBytes()), target);

        assertThat(subjectField.getClass().getName()).endsWith("ObjectField");
        assertThat(target.subject()).isEqualTo("integration-event");
    }

    @Test
    void copiesPrivateReferenceFieldThroughCachedObjectField() {
        FieldSerializer<MessageEnvelope> serializer = newMessageEnvelopeSerializer();
        CachedField<?> subjectField = serializer.getField("subject");
        MessageEnvelope source = new MessageEnvelope("copied-event");
        MessageEnvelope target = new MessageEnvelope("placeholder");

        subjectField.copy(source, target);

        assertThat(subjectField.getClass().getName()).endsWith("ObjectField");
        assertThat(target.subject()).isEqualTo("copied-event");
    }

    private static FieldSerializer<MessageEnvelope> newMessageEnvelopeSerializer() {
        Kryo kryo = new Kryo();
        kryo.setAsmEnabled(true);
        FieldSerializer<MessageEnvelope> serializer = new FieldSerializer<>(kryo, MessageEnvelope.class);
        kryo.register(MessageEnvelope.class, serializer);
        return serializer;
    }

    public static class MessageEnvelope {
        private String subject;

        public MessageEnvelope() {
        }

        MessageEnvelope(String subject) {
            this.subject = subject;
        }

        String subject() {
            return subject;
        }
    }
}
