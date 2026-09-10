/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.kstream.SessionWindowedDeserializer;
import org.apache.kafka.streams.kstream.SessionWindowedSerializer;
import org.apache.kafka.streams.kstream.TimeWindowedDeserializer;
import org.apache.kafka.streams.kstream.TimeWindowedSerializer;
import org.apache.kafka.streams.kstream.UnlimitedWindows;
import org.apache.kafka.streams.kstream.Window;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.WindowedSerdes;
import org.apache.kafka.streams.kstream.internals.Change;
import org.apache.kafka.streams.kstream.internals.ChangedDeserializer;
import org.apache.kafka.streams.kstream.internals.ChangedSerializer;
import org.apache.kafka.streams.kstream.internals.FullChangeSerde;
import org.apache.kafka.streams.kstream.internals.SessionWindow;
import org.apache.kafka.streams.kstream.internals.TimeWindow;
import org.apache.kafka.streams.kstream.SessionWindows;
import org.apache.kafka.streams.kstream.SlidingWindows;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises window value objects and the serde adapters used by state stores. */
public class WindowAndSerdeApiCoverageTest {
    @Test
    void shouldRoundTripTimeAndSessionWindowedKeys() {
        Windowed<String> timeKey = new Windowed<>("key", new TimeWindow(100, 200));
        TimeWindowedSerializer<String> timeSerializer = new TimeWindowedSerializer<>(Serdes.String().serializer());
        timeSerializer.configure(Map.of(), false);
        byte[] encoded = timeSerializer.serialize("topic", timeKey);
        assertThat(timeSerializer.serializeBaseKey("topic", timeKey)).isNotEmpty();
        TimeWindowedDeserializer<String> timeDeserializer = new TimeWindowedDeserializer<>(Serdes.String().deserializer(), 100L);
        timeDeserializer.configure(Map.of(), false);
        assertThat(timeDeserializer.getWindowSize()).isEqualTo(100L);
        assertThat(timeDeserializer.deserialize("topic", encoded)).isEqualTo(timeKey);
        timeDeserializer.setIsChangelogTopic(true);
        timeSerializer.close();
        timeDeserializer.close();

        Windowed<String> sessionKey = new Windowed<>("key", new SessionWindow(100, 200));
        SessionWindowedSerializer<String> sessionSerializer = new SessionWindowedSerializer<>(Serdes.String().serializer());
        SessionWindowedDeserializer<String> sessionDeserializer = new SessionWindowedDeserializer<>(Serdes.String().deserializer());
        byte[] sessionBytes = sessionSerializer.serialize("topic", sessionKey);
        assertThat(sessionSerializer.serializeBaseKey("topic", sessionKey)).isNotEmpty();
        assertThat(sessionDeserializer.deserialize("topic", sessionBytes)).isEqualTo(sessionKey);
        sessionSerializer.close();
        sessionDeserializer.close();
        assertThat(new WindowedSerdes()).isNotNull();
        assertThat(WindowedSerdes.sessionWindowedSerdeFrom(String.class)).isNotNull();
    }

    @Test
    void shouldSerializeChangesAndPreserveChangeValueSemantics() {
        Change<String> change = new Change<>("new", "old", true);
        ChangedSerializer<String> serializer = new ChangedSerializer<>(Serdes.String().serializer());
        ChangedDeserializer<String> deserializer = new ChangedDeserializer<>(Serdes.String().deserializer());
        serializer.configure(Map.of(), false);
        deserializer.configure(Map.of(), false);
        byte[] bytes = serializer.serialize("topic", change);
        assertThat(deserializer.deserialize("topic", bytes)).isEqualTo(change);
        ChangedSerializer rawSerializer = serializer;
        assertThat(rawSerializer.serialize("topic", (Object) change)).isNotEmpty();
        assertThat(deserializer.deserialize("topic", bytes)).isEqualTo(change);
        assertThat(serializer.inner()).isNotNull();
        assertThat(deserializer.inner()).isNotNull();
        serializer.close();
        deserializer.close();

        FullChangeSerde<String> full = FullChangeSerde.wrap(Serdes.String());
        Change<byte[]> parts = full.serializeParts("topic", change);
        assertThat(parts.newValue).isNotEmpty();
        assertThat(full.deserializeParts("topic", parts)).isEqualTo(change);
        java.nio.ByteBuffer legacy = java.nio.ByteBuffer.allocate(12);
        legacy.putInt(2).put(new byte[] {1, 2}).putInt(2).put(new byte[] {3, 4});
        assertThat(FullChangeSerde.decomposeLegacyFormattedArrayIntoChangeArrays(legacy.array())).isNotNull();
        assertThat(full.innerSerde()).isNotNull();
        assertThat(change.toString()).contains("new");
        assertThat(change.hashCode()).isEqualTo(new Change<>("new", "old", true).hashCode());
    }

    @Test
    void shouldDescribeWindowBoundariesAndWindowPolicies() {
        Window time = new TimeWindow(10, 20);
        assertThat(time.startTime()).isEqualTo(Instant.ofEpochMilli(10));
        assertThat(time.endTime()).isEqualTo(Instant.ofEpochMilli(20));
        assertThat(time.toString()).contains("10", "20");
        assertThat(time.overlap(new TimeWindow(15, 25))).isTrue();
        assertThat(time.overlap(new TimeWindow(20, 30))).isFalse();
        Window session = new SessionWindow(10, 20);
        assertThat(session.overlap(new SessionWindow(15, 25))).isTrue();

        TimeWindows timeWindows = TimeWindows.of(Duration.ofSeconds(10)).advanceBy(Duration.ofSeconds(5)).grace(Duration.ofSeconds(2));
        assertThat(timeWindows.windowsFor(12_000L)).isNotEmpty();
        assertThat(timeWindows.toString()).contains("sizeMs");
        assertThat(timeWindows.equals(TimeWindows.of(Duration.ofSeconds(10)).advanceBy(Duration.ofSeconds(5)).grace(Duration.ofSeconds(2)))).isTrue();
        assertThat(timeWindows.hashCode()).isEqualTo(timeWindows.hashCode());
        SessionWindows sessions = SessionWindows.with(Duration.ofSeconds(4)).grace(Duration.ofSeconds(1));
        assertThat(sessions.toString()).contains("gapMs");
        assertThat(sessions.equals(SessionWindows.with(Duration.ofSeconds(4)).grace(Duration.ofSeconds(1)))).isTrue();
        assertThat(sessions.hashCode()).isEqualTo(sessions.hashCode());
        SlidingWindows sliding = SlidingWindows.withTimeDifferenceAndGrace(Duration.ofSeconds(3), Duration.ofSeconds(1));
        assertThat(sliding.toString()).contains("sizeMs");
        assertThat(sliding.equals(SlidingWindows.withTimeDifferenceAndGrace(Duration.ofSeconds(3), Duration.ofSeconds(1)))).isTrue();
        assertThat(sliding.hashCode()).isEqualTo(sliding.hashCode());

        UnlimitedWindows unlimited = UnlimitedWindows.of().startOn(Instant.ofEpochMilli(100));
        assertThat(unlimited.size()).isEqualTo(Long.MAX_VALUE);
        assertThat(unlimited.gracePeriodMs()).isZero();
        assertThat(unlimited.windowsFor(200)).isNotEmpty();
        assertThat(unlimited.toString()).contains("startMs");
        assertThat(unlimited.equals(UnlimitedWindows.of().startOn(Instant.ofEpochMilli(100)))).isTrue();
    }
}
