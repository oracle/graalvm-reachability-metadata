/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import autovalue.shaded.com.google$.common.base.$Supplier;
import autovalue.shaded.com.google$.common.collect.$Multimaps;
import autovalue.shaded.com.google$.common.collect.$SetMultimap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AutovalueShadedComGoogleInnerCommonCollectInnerMultimapsInnerCustomSetMultimapTest {
    @Test
    void customSetMultimapRestoresFactoryAndBackingMapAcrossSerializationRoundTrip() throws Exception {
        $SetMultimap<String, String> original = $Multimaps.newSetMultimap(
                new LinkedHashMap<>(),
                new SerializableLinkedHashSetSupplier<>()
        );
        original.put("team", "ada");
        original.put("team", "grace");
        original.put("team", "ada");
        original.put("language", "java");

        assertThat(original.getClass().getName())
                .isEqualTo("autovalue.shaded.com.google$.common.collect.$Multimaps$CustomSetMultimap");

        $SetMultimap<String, String> restored = roundTrip((Serializable) original);

        assertThat(restored).isNotSameAs(original);
        assertThat(restored.getClass().getName())
                .isEqualTo("autovalue.shaded.com.google$.common.collect.$Multimaps$CustomSetMultimap");
        assertThat(restored.get("team")).containsExactly("ada", "grace");
        assertThat(restored.get("language")).containsExactly("java");
        assertThat(restored.keySet()).containsExactly("team", "language");

        restored.put("team", "margaret");
        restored.put("team", "ada");
        restored.put("database", "postgres");

        assertThat(restored.get("team")).containsExactly("ada", "grace", "margaret");
        assertThat(restored.get("database")).containsExactly("postgres");
        assertThat(restored.keySet()).containsExactly("team", "language", "database");
    }

    private static $SetMultimap<String, String> roundTrip(
            Serializable value
    ) throws IOException, ClassNotFoundException {
        byte[] serialized = serialize(value);
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf($SetMultimap.class);
            @SuppressWarnings("unchecked")
            $SetMultimap<String, String> typedRestored = ($SetMultimap<String, String>) restored;
            return typedRestored;
        }
    }

    private static byte[] serialize(Serializable value) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
        }
        return outputStream.toByteArray();
    }

    private static final class SerializableLinkedHashSetSupplier<T> implements $Supplier<Set<T>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Set<T> get() {
            return new LinkedHashSet<>();
        }
    }
}
