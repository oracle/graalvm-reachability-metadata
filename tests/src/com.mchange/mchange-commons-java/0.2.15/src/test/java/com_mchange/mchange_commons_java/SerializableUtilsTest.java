/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.mchange.v2.ser.SerializableUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializableUtilsTest {
    @Test
    void serializeToByteArrayAndDeserializeFromByteArrayRoundTripJdkSerializableTypes() throws Exception {
        LinkedHashMap<String, Object> original = samplePayload();

        byte[] bytes = SerializableUtils.serializeToByteArray(original);
        Object restored = SerializableUtils.deserializeFromByteArray(bytes);

        assertThat(bytes).isNotEmpty();
        assertThat(restored).isEqualTo(original);
    }

    @Test
    void marshallObjectToFileAndUnmarshallObjectFromFileRoundTripJdkSerializableTypes(@TempDir Path tempDir)
        throws Exception {
        LinkedHashMap<String, Object> original = samplePayload();
        Path file = tempDir.resolve("payload.ser");

        SerializableUtils.marshallObjectToFile(original, file.toFile());
        Object restored = SerializableUtils.unmarshallObjectFromFile(file.toFile());

        assertThat(Files.exists(file)).isTrue();
        assertThat(Files.size(file)).isPositive();
        assertThat(restored).isEqualTo(original);
    }

    private static LinkedHashMap<String, Object> samplePayload() {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "alpha");
        payload.put("count", 7);
        payload.put("values", new ArrayList<>(List.of("one", "two", "three")));
        payload.put("flags", new ArrayList<>(List.of(Boolean.TRUE, Boolean.FALSE)));
        return payload;
    }
}
