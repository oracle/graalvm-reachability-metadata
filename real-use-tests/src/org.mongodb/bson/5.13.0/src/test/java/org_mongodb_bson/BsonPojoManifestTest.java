/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_bson;

import org.bson.BsonBinaryReader;
import org.bson.BsonBinaryWriter;
import org.bson.codecs.BsonValueCodecProvider;
import org.bson.codecs.CollectionCodecProvider;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodecProvider;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.EnumCodecProvider;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.io.BasicOutputBuffer;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises BSON POJO codecs to persist typed shipment manifests. */
public class BsonPojoManifestTest {

    @Test
    @Timeout(60)
    void roundTripsATypedShipmentThroughTheBsonCodec() {
        ObjectId shipmentId = new ObjectId("65cbd4000000000000000043");
        Shipment shipment = new Shipment(
                shipmentId,
                "Riverside Books",
                List.of(new Parcel("PKG-204", 850), new Parcel("PKG-205", 1200)),
                DeliveryStatus.READY);

        CodecRegistry registry = CodecRegistries.fromProviders(
                new ValueCodecProvider(), new BsonValueCodecProvider(), new DocumentCodecProvider(),
                new CollectionCodecProvider(), new EnumCodecProvider(),
                PojoCodecProvider.builder().automatic(true).build());
        Codec<Shipment> codec = registry.get(Shipment.class);
        byte[] encoded = encode(codec, shipment);
        Shipment restored = decode(codec, encoded);

        assertThat(encoded).isNotEmpty();
        assertThat(restored.getId()).isEqualTo(shipmentId);
        assertThat(restored.getRecipient()).isEqualTo("Riverside Books");
        assertThat(restored.getStatus()).isEqualTo(DeliveryStatus.READY);
        assertThat(restored.getParcels())
                .extracting(Parcel::getTrackingCode)
                .containsExactly("PKG-204", "PKG-205");
        assertThat(restored.getParcels()).extracting(Parcel::getWeightGrams)
                .containsExactly(850, 1200);
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

    public enum DeliveryStatus {
        READY,
        DISPATCHED
    }

    public static final class Shipment {
        @BsonId
        private ObjectId id;
        @BsonProperty("recipient")
        private String recipient;
        private List<Parcel> parcels;
        private DeliveryStatus status;

        public Shipment() {
        }

        private Shipment(ObjectId id, String recipient, List<Parcel> parcels, DeliveryStatus status) {
            this.id = id;
            this.recipient = recipient;
            this.parcels = parcels;
            this.status = status;
        }

        public ObjectId getId() {
            return id;
        }

        public void setId(ObjectId id) {
            this.id = id;
        }

        public String getRecipient() {
            return recipient;
        }

        public void setRecipient(String recipient) {
            this.recipient = recipient;
        }

        public List<Parcel> getParcels() {
            return parcels;
        }

        public void setParcels(List<Parcel> parcels) {
            this.parcels = parcels;
        }

        public DeliveryStatus getStatus() {
            return status;
        }

        public void setStatus(DeliveryStatus status) {
            this.status = status;
        }
    }

    public static final class Parcel {
        private String trackingCode;
        private int weightGrams;

        public Parcel() {
        }

        private Parcel(String trackingCode, int weightGrams) {
            this.trackingCode = trackingCode;
            this.weightGrams = weightGrams;
        }

        public String getTrackingCode() {
            return trackingCode;
        }

        public void setTrackingCode(String trackingCode) {
            this.trackingCode = trackingCode;
        }

        public int getWeightGrams() {
            return weightGrams;
        }

        public void setWeightGrams(int weightGrams) {
            this.weightGrams = weightGrams;
        }
    }
}
