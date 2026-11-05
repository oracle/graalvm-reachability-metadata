/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Kryo's custom serializer subsystem to persist a compact application checkpoint.
public class CustomSerializerCheckpointTest {
    @Test
    @Timeout(60)
    void roundTripsCheckpointWithApplicationSerializer() {
        Kryo kryo = new Kryo();
        kryo.setAsmEnabled(false);
        kryo.register(Checkpoint.class, new CheckpointSerializer());

        Checkpoint checkpoint = new Checkpoint("orders", 1842, "ready");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Output output = new Output(bytes);
        kryo.writeObject(output, checkpoint);
        output.close();

        Input input = new Input(new ByteArrayInputStream(bytes.toByteArray()));
        Checkpoint restored = kryo.readObject(input, Checkpoint.class);
        input.close();

        assertEquals("orders", restored.getStream());
        assertEquals(1842, restored.getOffset());
        assertEquals("ready", restored.getStatus());
    }

    public static class Checkpoint {
        private String stream;
        private int offset;
        private String status;

        public Checkpoint() {
        }

        Checkpoint(String stream, int offset, String status) {
            this.stream = stream;
            this.offset = offset;
            this.status = status;
        }

        public String getStream() {
            return stream;
        }

        public int getOffset() {
            return offset;
        }

        public String getStatus() {
            return status;
        }
    }

    public static class CheckpointSerializer extends Serializer<Checkpoint> {
        @Override
        public void write(Kryo kryo, Output output, Checkpoint checkpoint) {
            output.writeString(checkpoint.stream);
            output.writeInt(checkpoint.offset, true);
            output.writeString(checkpoint.status);
        }

        @Override
        public Checkpoint read(Kryo kryo, Input input, Class<Checkpoint> type) {
            return new Checkpoint(input.readString(), input.readInt(true), input.readString());
        }
    }
}
