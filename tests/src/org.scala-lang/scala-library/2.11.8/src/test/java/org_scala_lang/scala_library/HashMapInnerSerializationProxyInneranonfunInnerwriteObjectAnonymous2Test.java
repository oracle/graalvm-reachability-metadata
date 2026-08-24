/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_library;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import scala.collection.immutable.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises immutable hash-map proxy serialization. \u00A7FS-repository-functional-spec.5.2 */
public class HashMapInnerSerializationProxyInneranonfunInnerwriteObjectAnonymous2Test {

    @Test
    void serializesEveryKeyAndValue() throws Exception {
        HashMap<String, String> original = new HashMap<>();
        original = original.updated("first-key", "first-value");
        original = original.updated("second-key", "second-value");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        List<String> serializedStrings;
        try (StringRecordingObjectOutputStream output = new StringRecordingObjectOutputStream(bytes)) {
            output.writeObject(original);
            serializedStrings = output.serializedStrings();
        }

        assertThat(bytes.size()).isGreaterThan(0);
        assertThat(serializedStrings)
                .containsExactlyInAnyOrder("first-key", "first-value", "second-key", "second-value");
    }

    private static final class StringRecordingObjectOutputStream extends ObjectOutputStream {
        private final List<String> serializedStrings = new ArrayList<>();

        private StringRecordingObjectOutputStream(ByteArrayOutputStream output) throws IOException {
            super(output);
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) {
            if (object instanceof String) {
                serializedStrings.add((String) object);
            }
            return object;
        }

        private List<String> serializedStrings() {
            return serializedStrings;
        }
    }
}
