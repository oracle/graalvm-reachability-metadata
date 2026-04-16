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

import com.mchange.io.SerializableUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class IoSerializableUtilsTest {
    @Test
    void marshallObjectToFileAndUnmarshallObjectFromFileRoundTripJdkSerializableTypes(@TempDir Path tempDir)
        throws Exception {
        LinkedHashMap<String, Object> original = new LinkedHashMap<>();
        original.put("name", "alpha");
        original.put("count", 7);
        original.put("values", new ArrayList<>(List.of("one", "two", "three")));
        original.put("flags", new ArrayList<>(List.of(Boolean.TRUE, Boolean.FALSE)));
        Path file = tempDir.resolve("legacy-payload.ser");

        SerializableUtils.marshallObjectToFile(original, file.toFile());
        Object restored = SerializableUtils.unmarshallObjectFromFile(file.toFile());

        assertThat(Files.exists(file)).isTrue();
        assertThat(Files.size(file)).isPositive();
        assertThat(restored).isEqualTo(original);
    }
}
