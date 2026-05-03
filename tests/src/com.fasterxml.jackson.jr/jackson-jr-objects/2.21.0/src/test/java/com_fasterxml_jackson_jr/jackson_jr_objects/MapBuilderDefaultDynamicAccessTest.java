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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MapBuilderDefaultDynamicAccessTest {
    @BeforeEach
    void resetConstructorCalls() {
        ConstructorTrackingMap.CONSTRUCTOR_CALLS.set(0);
    }

    @Test
    void readsMultiEntryJsonObjectIntoConfiguredMapImplementation() throws Exception {
        Map<String, Object> counts = jsonWithConfiguredMaps().mapFrom("{\"alpha\":1,\"beta\":2}");

        assertThat(counts).isInstanceOf(ConstructorTrackingMap.class);
        assertThat(counts).containsEntry("alpha", 1).containsEntry("beta", 2);
        assertThat(ConstructorTrackingMap.CONSTRUCTOR_CALLS).hasValue(1);
    }

    @Test
    void readsEmptyJsonObjectIntoConfiguredMapImplementation() throws Exception {
        Map<String, Object> counts = jsonWithConfiguredMaps().mapFrom("{}");

        assertThat(counts).isInstanceOf(ConstructorTrackingMap.class).isEmpty();
        assertThat(ConstructorTrackingMap.CONSTRUCTOR_CALLS).hasValue(1);
    }

    @Test
    void readsSingletonJsonObjectIntoConfiguredMapImplementation() throws Exception {
        Map<String, Object> counts = jsonWithConfiguredMaps().mapFrom("{\"only\":99}");

        assertThat(counts).isInstanceOf(ConstructorTrackingMap.class);
        assertThat(counts).containsEntry("only", 99);
        assertThat(ConstructorTrackingMap.CONSTRUCTOR_CALLS).hasValue(1);
    }

    @Test
    void retainsConfiguredMapImplementationWhenRestartingABusyBuilder() {
        MapBuilder builder = configuredMapBuilder();

        Map<String, Object> counts = builder.start()
                .put("discarded", true)
                .start()
                .put("kept", true)
                .build();

        assertThat(counts).isInstanceOf(ConstructorTrackingMap.class);
        assertThat(counts).hasSize(1);
        assertThat(counts).containsEntry("kept", true);
        assertThat(counts).doesNotContainKey("discarded");
        assertThat(ConstructorTrackingMap.CONSTRUCTOR_CALLS).hasValue(2);
    }

    @Test
    void directBuilderFactoryMethodsInstantiateConfiguredMapImplementation() throws Exception {
        MapBuilder builder = configuredMapBuilder().newBuilder(JSON.Feature.READ_ONLY.mask());

        Map<String, Object> empty = builder.emptyMap();
        Map<String, Object> singleton = builder.singletonMap("answer", 42);

        assertThat(empty).isInstanceOf(ConstructorTrackingMap.class).isEmpty();
        assertThat(singleton).isInstanceOf(ConstructorTrackingMap.class).containsEntry("answer", 42);
        assertThat(ConstructorTrackingMap.CONSTRUCTOR_CALLS).hasValue(2);
    }

    @Test
    void reportsConfiguredMapTypesThatCannotBeInstantiated() {
        MapBuilder builder = MapBuilder.defaultImpl().newBuilder(PrivateConstructorMap.class);

        assertThatThrownBy(builder::start)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to create an instance of " + PrivateConstructorMap.class.getName());
    }

    private static JSON jsonWithConfiguredMaps() {
        return JSON.builder()
                .mapBuilder(configuredMapBuilder())
                .build();
    }

    private static MapBuilder configuredMapBuilder() {
        return MapBuilder.defaultImpl().newBuilder(ConstructorTrackingMap.class);
    }

    public static final class ConstructorTrackingMap extends LinkedHashMap<String, Object> {
        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();

        public ConstructorTrackingMap() {
            CONSTRUCTOR_CALLS.incrementAndGet();
        }
    }

    public static final class PrivateConstructorMap extends LinkedHashMap<String, Object> {
        private PrivateConstructorMap() {
        }
    }
}
