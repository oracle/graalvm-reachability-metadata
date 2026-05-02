/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v2.ser.SerializableUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializableUtilsTest {
    @Test
    void serializeToByteArrayAndDeserializeFromByteArrayRoundTripSerializablePayload() throws Exception {
        SerializablePayload original = createPayload("byte-array");

        byte[] serialized = SerializableUtils.serializeToByteArray(original);
        Object restored = SerializableUtils.deserializeFromByteArray(serialized);

        assertThat(serialized).isNotEmpty();
        assertThat(restored).isInstanceOf(SerializablePayload.class);

        SerializablePayload restoredPayload = (SerializablePayload) restored;

        assertThat(restoredPayload.getName()).isEqualTo(original.getName());
        assertThat(restoredPayload.getTags()).containsExactlyElementsOf(original.getTags());
        assertThat(restoredPayload.getAttributes()).containsExactlyEntriesOf(original.getAttributes());
    }

    @Test
    void marshallObjectToFileAndUnmarshallObjectFromFileRoundTripSerializablePayload(@TempDir Path tempDir) throws Exception {
        SerializablePayload original = createPayload("file");
        File serializedFile = tempDir.resolve("payload.ser").toFile();

        SerializableUtils.marshallObjectToFile(original, serializedFile);
        Object restored = SerializableUtils.unmarshallObjectFromFile(serializedFile);

        assertThat(serializedFile).exists().isFile();
        assertThat(serializedFile.length()).isGreaterThan(0L);
        assertThat(restored).isInstanceOf(SerializablePayload.class);

        SerializablePayload restoredPayload = (SerializablePayload) restored;

        assertThat(restoredPayload.getName()).isEqualTo(original.getName());
        assertThat(restoredPayload.getTags()).containsExactlyElementsOf(original.getTags());
        assertThat(restoredPayload.getAttributes()).containsExactlyEntriesOf(original.getAttributes());
    }

    private static SerializablePayload createPayload(String name) {
        List<String> tags = new ArrayList<>();
        tags.add(name + "-first");
        tags.add(name + "-second");

        Map<String, Integer> attributes = new LinkedHashMap<>();
        attributes.put("length", name.length());
        attributes.put("tagCount", tags.size());

        return new SerializablePayload(name, tags, attributes);
    }

    public static final class SerializablePayload implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final List<String> tags;
        private final Map<String, Integer> attributes;

        public SerializablePayload(String name, List<String> tags, Map<String, Integer> attributes) {
            this.name = name;
            this.tags = new ArrayList<>(tags);
            this.attributes = new LinkedHashMap<>(attributes);
        }

        public String getName() {
            return name;
        }

        public List<String> getTags() {
            return new ArrayList<>(tags);
        }

        public Map<String, Integer> getAttributes() {
            return new LinkedHashMap<>(attributes);
        }
    }
}
