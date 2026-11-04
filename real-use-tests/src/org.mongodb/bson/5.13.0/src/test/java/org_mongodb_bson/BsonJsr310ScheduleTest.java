/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_bson;

import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDocumentWriter;
import org.bson.codecs.BsonValueCodecProvider;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.CollectionCodecProvider;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.jsr310.Jsr310CodecProvider;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises BSON JSR-310 codecs to store and restore a delivery schedule. */
class BsonJsr310ScheduleTest {

    @Test
    @Timeout(60)
    void storesJavaTimeValuesInAnApplicationSchedule() {
        Instant dispatchedAt = Instant.parse("2025-03-18T14:25:36Z");
        LocalDate serviceDate = LocalDate.of(2025, 3, 19);
        LocalDateTime pickupWindowStart = LocalDateTime.of(2025, 3, 19, 9, 30);
        LocalTime pickupWindowEnd = LocalTime.of(10, 15);
        Schedule schedule = new Schedule(
                "R-17", dispatchedAt, serviceDate, pickupWindowStart, pickupWindowEnd);

        CodecRegistry registry = CodecRegistries.fromProviders(
                new ValueCodecProvider(), new BsonValueCodecProvider(),
                new CollectionCodecProvider(), new Jsr310CodecProvider(),
                PojoCodecProvider.builder().automatic(true).build());
        Codec<Schedule> scheduleCodec = registry.get(Schedule.class);

        BsonDocument stored = encode(scheduleCodec, schedule);
        Schedule restored = decode(scheduleCodec, stored);

        assertThat(stored.getDateTime("dispatchedAt").getValue())
                .isEqualTo(dispatchedAt.toEpochMilli());
        assertThat(restored.getRoute()).isEqualTo("R-17");
        assertThat(restored.getDispatchedAt()).isEqualTo(dispatchedAt);
        assertThat(restored.getServiceDate()).isEqualTo(serviceDate);
        assertThat(restored.getPickupWindowStart()).isEqualTo(pickupWindowStart);
        assertThat(restored.getPickupWindowEnd()).isEqualTo(pickupWindowEnd);
    }

    private static <T> BsonDocument encode(Codec<T> codec, T value) {
        BsonDocument stored = new BsonDocument();
        BsonDocumentWriter writer = new BsonDocumentWriter(stored);
        codec.encode(writer, value, EncoderContext.builder().build());
        writer.close();
        return stored;
    }

    private static <T> T decode(Codec<T> codec, BsonDocument value) {
        BsonDocumentReader reader = new BsonDocumentReader(value);
        T result = codec.decode(reader, DecoderContext.builder().build());
        reader.close();
        return result;
    }

    public static final class Schedule {
        private String route;
        private Instant dispatchedAt;
        private LocalDate serviceDate;
        private LocalDateTime pickupWindowStart;
        private LocalTime pickupWindowEnd;

        public Schedule() {
        }

        private Schedule(
                String route,
                Instant dispatchedAt,
                LocalDate serviceDate,
                LocalDateTime pickupWindowStart,
                LocalTime pickupWindowEnd) {
            this.route = route;
            this.dispatchedAt = dispatchedAt;
            this.serviceDate = serviceDate;
            this.pickupWindowStart = pickupWindowStart;
            this.pickupWindowEnd = pickupWindowEnd;
        }

        public String getRoute() {
            return route;
        }

        public void setRoute(String route) {
            this.route = route;
        }

        public Instant getDispatchedAt() {
            return dispatchedAt;
        }

        public void setDispatchedAt(Instant dispatchedAt) {
            this.dispatchedAt = dispatchedAt;
        }

        public LocalDate getServiceDate() {
            return serviceDate;
        }

        public void setServiceDate(LocalDate serviceDate) {
            this.serviceDate = serviceDate;
        }

        public LocalDateTime getPickupWindowStart() {
            return pickupWindowStart;
        }

        public void setPickupWindowStart(LocalDateTime pickupWindowStart) {
            this.pickupWindowStart = pickupWindowStart;
        }

        public LocalTime getPickupWindowEnd() {
            return pickupWindowEnd;
        }

        public void setPickupWindowEnd(LocalTime pickupWindowEnd) {
            this.pickupWindowEnd = pickupWindowEnd;
        }
    }
}
