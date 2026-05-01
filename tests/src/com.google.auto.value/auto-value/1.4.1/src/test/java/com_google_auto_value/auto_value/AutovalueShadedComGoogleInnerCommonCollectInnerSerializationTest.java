/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import autovalue.shaded.com.google$.common.collect.$HashMultimap;
import autovalue.shaded.com.google$.common.collect.$HashMultiset;
import autovalue.shaded.com.google$.common.collect.$ImmutableSetMultimap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AutovalueShadedComGoogleInnerCommonCollectInnerSerializationTest {
    private static final MethodHandle WRITE_MAP = serializationMethod(
            "writeMap",
            MethodType.methodType(void.class, Map.class, ObjectOutputStream.class)
    );
    private static final MethodHandle POPULATE_MAP = serializationMethod(
            "populateMap",
            MethodType.methodType(void.class, Map.class, ObjectInputStream.class)
    );

    @Test
    void packagePrivateMapSerializationHelpersRoundTripLinkedHashMapEntries() throws Throwable {
        LinkedHashMap<String, Integer> original = new LinkedHashMap<>();
        original.put("wins", 12);
        original.put("losses", 2);

        LinkedHashMap<String, Integer> restored = new LinkedHashMap<>();
        populateMapPayload(restored, serializeMapPayload(original));

        assertThat(restored).containsExactlyEntriesOf(original);
    }

    @Test
    void mutableHashMultisetPreservesDistinctElementCountsAcrossRoundTrip() throws Exception {
        $HashMultiset<String> original = $HashMultiset.create();
        original.add("alpha", 3);
        original.add("beta", 1);

        $HashMultiset<String> restored = roundTrip(original, $HashMultiset.class);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.count("alpha")).isEqualTo(3);
        assertThat(restored.count("beta")).isEqualTo(1);
        assertThat(restored.elementSet()).containsExactlyInAnyOrder("alpha", "beta");
    }

    @Test
    void mutableHashMultimapPreservesKeysAndValuesAcrossRoundTrip() throws Exception {
        $HashMultimap<String, String> original = $HashMultimap.create();
        original.put("team", "ada");
        original.put("team", "grace");
        original.put("language", "java");

        $HashMultimap<String, String> restored = roundTrip(original, $HashMultimap.class);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.get("team")).containsExactlyInAnyOrder("ada", "grace");
        assertThat(restored.get("language")).containsExactly("java");
    }

    @Test
    void immutableSetMultimapRebuildsFieldsAcrossRoundTrip() throws Exception {
        $ImmutableSetMultimap<String, String> original = $ImmutableSetMultimap.<String, String>builder()
                .put("team", "ada")
                .put("team", "grace")
                .put("language", "java")
                .build();

        $ImmutableSetMultimap<String, String> restored = roundTrip(original, $ImmutableSetMultimap.class);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.get("team")).containsExactlyInAnyOrder("ada", "grace");
        assertThat(restored.inverse().get("ada")).containsExactly("team");
    }

    private static void populateMapPayload(Map<?, ?> target, byte[] serialized) throws Throwable {
        Map<?, ?> map = target;
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            POPULATE_MAP.invokeExact(map, objectInputStream);
        }
    }

    private static byte[] serializeMapPayload(Map<?, ?> value) throws Throwable {
        Map<?, ?> map = value;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            WRITE_MAP.invokeExact(map, objectOutputStream);
        }
        return outputStream.toByteArray();
    }

    private static MethodHandle serializationMethod(String methodName, MethodType methodType) {
        try {
            Class<?> serializationClass = Class.forName("autovalue.shaded.com.google$.common.collect.$Serialization");
            return MethodHandles.privateLookupIn(serializationClass, MethodHandles.lookup())
                    .findStatic(serializationClass, methodName, methodType);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static <T> T roundTrip(Serializable value, Class<T> expectedType) throws IOException, ClassNotFoundException {
        byte[] serialized = serialize(value);
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(expectedType);
            return expectedType.cast(restored);
        }
    }

    private static byte[] serialize(Serializable value) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
        }
        return outputStream.toByteArray();
    }
}
