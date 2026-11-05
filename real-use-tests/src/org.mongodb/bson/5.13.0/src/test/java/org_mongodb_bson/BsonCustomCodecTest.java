/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_bson;

import org.bson.BsonBinaryReader;
import org.bson.BsonBinaryWriter;
import org.bson.BsonReader;
import org.bson.BsonType;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.io.BasicOutputBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises custom BSON codec registration for an application's telemetry records. */
class BsonCustomCodecTest {

    @Test
    @Timeout(60)
    void registersAndUsesACodecForACompactTelemetryRecord() {
        TelemetryReading submitted = new TelemetryReading("line-7", 23.75, "C");
        CodecRegistry registry = CodecRegistries.fromProviders(new TelemetryReadingCodecProvider());
        Codec<TelemetryReading> codec = registry.get(TelemetryReading.class);

        byte[] encoded = encode(codec, submitted);
        TelemetryReading restored = decode(codec, encoded);

        assertThat(encoded).isNotEmpty();
        assertThat(restored.sensor()).isEqualTo("line-7");
        assertThat(restored.value()).isEqualTo(23.75);
        assertThat(restored.unit()).isEqualTo("C");
    }

    private static <T> byte[] encode(Codec<T> codec, T value) {
        try (BasicOutputBuffer output = new BasicOutputBuffer()) {
            BsonBinaryWriter writer = new BsonBinaryWriter(output);
            codec.encode(writer, value, EncoderContext.builder().build());
            writer.close();
            return output.toByteArray();
        }
    }

    private static <T> T decode(Codec<T> codec, byte[] encoded) {
        try (BsonBinaryReader reader = new BsonBinaryReader(ByteBuffer.wrap(encoded))) {
            return codec.decode(reader, DecoderContext.builder().build());
        }
    }

    private record TelemetryReading(String sensor, double value, String unit) {
    }

    private static final class TelemetryReadingCodecProvider implements CodecProvider {
        @Override
        @SuppressWarnings("unchecked")
        public <T> Codec<T> get(Class<T> type, CodecRegistry registry) {
            if (type == TelemetryReading.class) {
                return (Codec<T>) new TelemetryReadingCodec();
            }
            return null;
        }
    }

    private static final class TelemetryReadingCodec implements Codec<TelemetryReading> {
        @Override
        public void encode(BsonWriter writer, TelemetryReading value, EncoderContext context) {
            writer.writeStartDocument();
            writer.writeString("sensor", value.sensor());
            writer.writeDouble("value", value.value());
            writer.writeString("unit", value.unit());
            writer.writeEndDocument();
        }

        @Override
        public TelemetryReading decode(BsonReader reader, DecoderContext context) {
            reader.readStartDocument();
            String sensor = null;
            double value = 0.0;
            String unit = null;
            while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
                String fieldName = reader.readName();
                switch (fieldName) {
                    case "sensor" -> sensor = reader.readString();
                    case "value" -> value = reader.readDouble();
                    case "unit" -> unit = reader.readString();
                    default -> reader.skipValue();
                }
            }
            reader.readEndDocument();
            return new TelemetryReading(sensor, value, unit);
        }

        @Override
        public Class<TelemetryReading> getEncoderClass() {
            return TelemetryReading.class;
        }
    }
}
