/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import autovalue.shaded.com.google$.common.base.$Supplier;
import autovalue.shaded.com.google$.common.collect.$Multimap;
import autovalue.shaded.com.google$.common.collect.$Multimaps;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AutovalueShadedComGoogleInnerCommonCollectInnerMultimapsInnerCustomMultimapTest {
    @Test
    void customMultimapRestoresFactoryAndBackingMapAcrossSerializationRoundTrip() throws Exception {
        $Multimap<String, String> original = $Multimaps.newMultimap(
                new LinkedHashMap<>(),
                new SerializableArrayListCollectionSupplier<>()
        );
        original.put("team", "ada");
        original.put("team", "grace");
        original.put("team", "ada");
        original.put("language", "java");

        assertThat(original.getClass().getName())
                .isEqualTo("autovalue.shaded.com.google$.common.collect.$Multimaps$CustomMultimap");

        $Multimap<String, String> restored = roundTrip((Serializable) original);

        assertThat(restored).isNotSameAs(original);
        assertThat(restored.getClass().getName())
                .isEqualTo("autovalue.shaded.com.google$.common.collect.$Multimaps$CustomMultimap");
        assertThat(restored.get("team")).containsExactly("ada", "grace", "ada");
        assertThat(restored.get("language")).containsExactly("java");
        assertThat(restored.keySet()).containsExactly("team", "language");

        restored.put("team", "margaret");
        restored.put("database", "postgres");

        assertThat(restored.get("team")).containsExactly("ada", "grace", "ada", "margaret");
        assertThat(restored.get("database")).containsExactly("postgres");
        assertThat(restored.keySet()).containsExactly("team", "language", "database");
    }

    private static $Multimap<String, String> roundTrip(
            Serializable value
    ) throws IOException, ClassNotFoundException {
        byte[] serialized = serialize(value);
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf($Multimap.class);
            @SuppressWarnings("unchecked")
            $Multimap<String, String> typedRestored = ($Multimap<String, String>) restored;
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

    private static final class SerializableArrayListCollectionSupplier<T>
            implements $Supplier<Collection<T>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Collection<T> get() {
            return new ArrayList<>();
        }
    }
}
