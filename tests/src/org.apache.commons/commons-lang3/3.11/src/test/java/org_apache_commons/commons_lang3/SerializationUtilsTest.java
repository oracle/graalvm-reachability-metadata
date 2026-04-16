/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

public class SerializationUtilsTest {

    @Test
    void cloneDeepCopiesSerializableObjectGraphs() {
        final SerializableEnvelope original = new SerializableEnvelope(
                "commons-lang",
                new ArrayList<>(List.of("clone"))
        );

        final SerializableEnvelope cloned = SerializationUtils.clone(original);
        original.getTags().add("mutated-after-clone");

        assertThat(cloned).isNotNull();
        assertThat(cloned).isNotSameAs(original);
        assertThat(cloned.getName()).isEqualTo("commons-lang");
        assertThat(cloned.getTags()).containsExactly("clone");
        assertThat(cloned.getTags()).isNotSameAs(original.getTags());
    }

    @Test
    void serializeWritesObjectsThatCanBeReadBack() {
        final SerializableEnvelope original = new SerializableEnvelope(
                "commons-lang",
                new ArrayList<>(List.of("serialize", "bytes"))
        );

        final byte[] serialized = SerializationUtils.serialize(original);
        final SerializableEnvelope restored = SerializationUtils.deserialize(serialized);

        assertThat(serialized).isNotEmpty();
        assertThat(restored.getName()).isEqualTo("commons-lang");
        assertThat(restored.getTags()).containsExactly("serialize", "bytes");
    }

    @Test
    void deserializeReadsObjectsFromInputStreams() throws IOException {
        final SerializableEnvelope original = new SerializableEnvelope(
                "commons-lang",
                new ArrayList<>(List.of("deserialize", "stream"))
        );
        final byte[] serialized = serializeWithJdk(original);

        final SerializableEnvelope restored = SerializationUtils.deserialize(new ByteArrayInputStream(serialized));

        assertThat(restored).isNotNull();
        assertThat(restored).isNotSameAs(original);
        assertThat(restored.getName()).isEqualTo("commons-lang");
        assertThat(restored.getTags()).containsExactly("deserialize", "stream");
    }

    private static byte[] serializeWithJdk(final Serializable object) throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(object);
        }
        return outputStream.toByteArray();
    }

    private static final class SerializableEnvelope implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final List<String> tags;

        private SerializableEnvelope(final String name, final List<String> tags) {
            this.name = name;
            this.tags = tags;
        }

        private String getName() {
            return name;
        }

        private List<String> getTags() {
            return tags;
        }
    }
}
