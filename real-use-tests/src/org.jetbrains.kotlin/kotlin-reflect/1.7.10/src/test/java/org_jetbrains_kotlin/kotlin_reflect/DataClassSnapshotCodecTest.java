/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_reflect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kotlin.Pair;
import kotlin.Triple;
import kotlin.collections.IndexedValue;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.ranges.IntRange;
import kotlin.reflect.KClass;
import kotlin.reflect.KClassifier;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.KProperty1;
import kotlin.reflect.KTypeParameter;
import kotlin.reflect.full.KClasses;
import kotlin.text.MatchGroup;
import org.junit.jupiter.api.Test;

/**
 * Primary-constructor introspection of Kotlin data classes: an event store snapshots any data
 * object into an ordered field map and rehydrates it through `KFunction.callBy`.
 *
 * The codec knows nothing about the concrete types. It walks the primary constructor's parameters
 * for the field order, reads each value through the matching `KProperty1`, and on restore binds
 * the stored values back to the constructor parameters by name.
 */
class DataClassSnapshotCodecTest {

    /** Generic snapshot codec an event store uses for Kotlin data payloads. */
    static final class SnapshotCodec {

        /** Ordered field names and values of a data object, in constructor order. */
        static Map<String, Object> snapshot(Object payload) {
            KClass<?> kClass = JvmClassMappingKt.getKotlinClass(payload.getClass());
            if (!kClass.isData()) {
                throw new IllegalArgumentException(kClass.getQualifiedName() + " is not a data class");
            }
            Map<String, KProperty1<?, ?>> properties = new HashMap<>();
            for (KProperty1<?, ?> property : KClasses.getMemberProperties(kClass)) {
                properties.put(property.getName(), property);
            }
            Map<String, Object> fields = new LinkedHashMap<>();
            for (KParameter parameter : primaryConstructor(kClass).getParameters()) {
                @SuppressWarnings("unchecked")
                KProperty1<Object, ?> property = (KProperty1<Object, ?>) properties.get(parameter.getName());
                fields.put(parameter.getName(), property.get(payload));
            }
            return fields;
        }

        /** Rebuilds a data object of `type` from a snapshot, binding values by parameter name. */
        static <T> T restore(Class<T> type, Map<String, Object> fields) {
            KFunction<T> constructor = primaryConstructor(JvmClassMappingKt.getKotlinClass(type));
            Map<KParameter, Object> arguments = new HashMap<>();
            for (KParameter parameter : constructor.getParameters()) {
                if (!fields.containsKey(parameter.getName())) {
                    throw new IllegalStateException("snapshot misses field " + parameter.getName());
                }
                arguments.put(parameter, fields.get(parameter.getName()));
            }
            return constructor.callBy(arguments);
        }

        /** Describes the schema as `name: Type` entries, as the store's catalog lists it. */
        static List<String> schema(Class<?> type) {
            List<String> columns = new ArrayList<>();
            for (KParameter parameter : primaryConstructor(JvmClassMappingKt.getKotlinClass(type)).getParameters()) {
                KClassifier classifier = parameter.getType().getClassifier();
                String typeName = classifier instanceof KTypeParameter
                        ? ((KTypeParameter) classifier).getName()
                        : ((KClass<?>) classifier).getSimpleName();
                columns.add(parameter.getName() + ": " + typeName + (parameter.getType().isMarkedNullable() ? "?" : ""));
            }
            return columns;
        }

        private static <T> KFunction<T> primaryConstructor(KClass<T> kClass) {
            KFunction<T> constructor = KClasses.getPrimaryConstructor(kClass);
            if (constructor == null) {
                throw new IllegalArgumentException(kClass.getQualifiedName() + " has no primary constructor");
            }
            return constructor;
        }
    }

    @Test
    void snapshotsAndRestoresGenericPairAndTriple() {
        Pair<String, Integer> reservation = new Pair<>("seat-14C", 2);
        Triple<String, Long, Boolean> shipment = new Triple<>("PKG-7731", 1_250L, true);

        Map<String, Object> reservationFields = SnapshotCodec.snapshot(reservation);
        Map<String, Object> shipmentFields = SnapshotCodec.snapshot(shipment);

        assertThat(reservationFields).containsExactly(
                Map.entry("first", "seat-14C"),
                Map.entry("second", 2));
        assertThat(shipmentFields).containsExactly(
                Map.entry("first", "PKG-7731"),
                Map.entry("second", 1_250L),
                Map.entry("third", true));

        Map<String, Object> amended = new LinkedHashMap<>(reservationFields);
        amended.put("second", 3);
        Pair<?, ?> restored = SnapshotCodec.restore(Pair.class, amended);
        assertThat(restored).isEqualTo(new Pair<>("seat-14C", 3));
        assertThat(SnapshotCodec.restore(Triple.class, shipmentFields)).isEqualTo(shipment);
    }

    @Test
    void catalogsConcreteAndGenericSchemas() {
        assertThat(SnapshotCodec.schema(Pair.class)).containsExactly("first: A", "second: B");
        assertThat(SnapshotCodec.schema(IndexedValue.class)).containsExactly("index: Int", "value: T");
        assertThat(SnapshotCodec.schema(MatchGroup.class)).containsExactly("value: String", "range: IntRange");
    }

    @Test
    void roundTripsNonGenericDataPayload() {
        MatchGroup token = new MatchGroup("ORD-2291", new IntRange(6, 13));

        Map<String, Object> fields = SnapshotCodec.snapshot(token);
        assertThat(fields.keySet()).containsExactly("value", "range");
        assertThat(fields.get("range")).isEqualTo(new IntRange(6, 13));

        MatchGroup restored = SnapshotCodec.restore(MatchGroup.class, fields);
        assertThat(restored.getValue()).isEqualTo("ORD-2291");
        assertThat(restored.getRange().getLast()).isEqualTo(13);

        IndexedValue<String> line = new IndexedValue<>(4, "refund approved");
        assertThat(SnapshotCodec.restore(IndexedValue.class, SnapshotCodec.snapshot(line))).isEqualTo(line);
    }

    @Test
    void rejectsNonDataPayloadsAndIncompleteSnapshots() {
        assertThatThrownBy(() -> SnapshotCodec.snapshot(new IntRange(1, 5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("kotlin.ranges.IntRange is not a data class");

        Map<String, Object> partial = new LinkedHashMap<>();
        partial.put("first", "seat-2A");
        assertThatThrownBy(() -> SnapshotCodec.restore(Pair.class, partial))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("snapshot misses field second");
    }
}
