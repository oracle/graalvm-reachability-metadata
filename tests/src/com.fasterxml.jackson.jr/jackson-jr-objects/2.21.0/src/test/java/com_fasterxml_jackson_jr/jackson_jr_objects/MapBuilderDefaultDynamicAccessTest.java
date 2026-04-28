/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.jr.ob.api.MapBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MapBuilderDefaultDynamicAccessTest {
    @BeforeEach
    void resetConstructorCalls() {
        CountsMap.resetConstructorCalls();
    }

    @Test
    void readsEmptyObjectsIntoConfiguredMapImplementation() throws Exception {
        Map<String, Object> counts = jsonWithCountsMaps().mapFrom("{}");

        assertThat(counts).isInstanceOf(CountsMap.class).isEmpty();
        assertThat(CountsMap.getConstructorCalls()).isEqualTo(1);
    }

    @Test
    void readsSingletonObjectsIntoConfiguredMapImplementation() throws Exception {
        Map<String, Object> counts = jsonWithCountsMaps().mapFrom("{\"only\":99}");

        assertThat(counts).isInstanceOf(CountsMap.class);
        assertThat(counts).containsEntry("only", 99);
        assertThat(CountsMap.getConstructorCalls()).isEqualTo(1);
    }

    @Test
    void readsNestedObjectsIntoConfiguredMapImplementationForEachObject() throws Exception {
        Map<String, Object> counts = jsonWithCountsMaps().mapFrom("{\"outer\":{\"alpha\":1,\"beta\":2},\"count\":3}");

        assertThat(counts).isInstanceOf(CountsMap.class);
        assertThat(counts).containsEntry("count", 3);
        assertThat(counts.get("outer")).isInstanceOf(CountsMap.class);
        assertThat((Map<?, ?>) counts.get("outer")).containsEntry("alpha", 1).containsEntry("beta", 2);
        assertThat(CountsMap.getConstructorCalls()).isEqualTo(2);
    }

    private static JSON jsonWithCountsMaps() {
        return JSON.std.with(MapBuilder.defaultImpl().newBuilder(CountsMap.class));
    }

    public static final class CountsMap extends LinkedHashMap<String, Object> {
        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();

        public CountsMap() {
            CONSTRUCTOR_CALLS.incrementAndGet();
        }

        static void resetConstructorCalls() {
            CONSTRUCTOR_CALLS.set(0);
        }

        static int getConstructorCalls() {
            return CONSTRUCTOR_CALLS.get();
        }
    }
}
