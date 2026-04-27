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

import com.fasterxml.jackson.jr.ob.api.MapBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MapBuilderDefaultDynamicAccessTest {
    @BeforeEach
    void resetConstructorCalls() {
        ConstructorTrackingMap.CONSTRUCTOR_CALLS.set(0);
    }

    @Test
    void instantiatesConfiguredMapImplementationWhenStartingABuilder() {
        MapBuilder builder = MapBuilder.defaultImpl().newBuilder(ConstructorTrackingMap.class);

        Map<String, Object> counts = builder.start()
                .put("alpha", 1)
                .put("beta", 2)
                .build();

        assertThat(counts).isInstanceOf(ConstructorTrackingMap.class);
        assertThat(counts).containsEntry("alpha", 1).containsEntry("beta", 2);
        assertThat(ConstructorTrackingMap.CONSTRUCTOR_CALLS).hasValue(1);
    }

    @Test
    void instantiatesConfiguredMapImplementationForEmptyMaps() throws Exception {
        MapBuilder builder = MapBuilder.defaultImpl().newBuilder(ConstructorTrackingMap.class);

        Map<String, Object> counts = builder.emptyMap();

        assertThat(counts).isInstanceOf(ConstructorTrackingMap.class).isEmpty();
        assertThat(ConstructorTrackingMap.CONSTRUCTOR_CALLS).hasValue(1);
    }

    @Test
    void instantiatesConfiguredMapImplementationForSingletonMaps() throws Exception {
        MapBuilder builder = MapBuilder.defaultImpl().newBuilder(ConstructorTrackingMap.class);

        Map<String, Object> counts = builder.singletonMap("only", 99);

        assertThat(counts).isInstanceOf(ConstructorTrackingMap.class);
        assertThat(counts).containsEntry("only", 99);
        assertThat(ConstructorTrackingMap.CONSTRUCTOR_CALLS).hasValue(1);
    }

    public static final class ConstructorTrackingMap extends LinkedHashMap<String, Object> {
        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();

        public ConstructorTrackingMap() {
            CONSTRUCTOR_CALLS.incrementAndGet();
        }
    }
}
