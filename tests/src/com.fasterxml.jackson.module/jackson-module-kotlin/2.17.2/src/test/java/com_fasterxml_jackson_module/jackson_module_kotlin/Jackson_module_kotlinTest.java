/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import kotlin.Pair;
import kotlin.Triple;
import kotlin.Unit;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Jackson_module_kotlinTest {

    private static JsonMapper newMapper() {
        return JsonMapper.builder()
                .addModule(new KotlinModule.Builder().build())
                .build();
    }

    @Test
    void pair_roundTrip_singleValue() throws Exception {
        JsonMapper mapper = newMapper();

        Pair<String, Integer> original = new Pair<>("left", 42);

        String json = mapper.writeValueAsString(original);
        Pair<String, Integer> restored = mapper.readValue(json, new TypeReference<Pair<String, Integer>>() {});

        assertThat(restored).isEqualTo(original);
    }

    @Test
    void pair_roundTrip_inList() throws Exception {
        JsonMapper mapper = newMapper();

        List<Pair<String, Integer>> original = Arrays.asList(
                new Pair<>("a", 1),
                new Pair<>("b", 2),
                new Pair<>("c", 3)
        );

        String json = mapper.writeValueAsString(original);
        List<Pair<String, Integer>> restored = mapper.readValue(
                json,
                new TypeReference<List<Pair<String, Integer>>>() {}
        );

        assertThat(restored).containsExactlyElementsOf(original);
    }

    @Test
    void pair_roundTrip_inMapValues() throws Exception {
        JsonMapper mapper = newMapper();

        Map<String, Pair<Integer, String>> original = new LinkedHashMap<>();
        original.put("first", new Pair<>(10, "ten"));
        original.put("second", new Pair<>(20, "twenty"));

        String json = mapper.writeValueAsString(original);
        Map<String, Pair<Integer, String>> restored = mapper.readValue(
                json,
                new TypeReference<Map<String, Pair<Integer, String>>>() {}
        );

        assertThat(restored).isEqualTo(original);
    }

    @Test
    void triple_roundTrip_singleValue() throws Exception {
        JsonMapper mapper = newMapper();

        Triple<String, Integer, Boolean> original = new Triple<>("name", 7, Boolean.TRUE);

        String json = mapper.writeValueAsString(original);
        Triple<String, Integer, Boolean> restored = mapper.readValue(
                json,
                new TypeReference<Triple<String, Integer, Boolean>>() {}
        );

        assertThat(restored).isEqualTo(original);
    }

    @Test
    void unit_serializesToJsonNull_andDeserializesToJavaNull() throws Exception {
        JsonMapper mapper = newMapper();

        String json = mapper.writeValueAsString(Unit.INSTANCE);
        assertThat(json).isEqualTo("null");

        Unit restored = mapper.readValue(json, Unit.class);
        assertThat(restored).isNull();
    }
}
