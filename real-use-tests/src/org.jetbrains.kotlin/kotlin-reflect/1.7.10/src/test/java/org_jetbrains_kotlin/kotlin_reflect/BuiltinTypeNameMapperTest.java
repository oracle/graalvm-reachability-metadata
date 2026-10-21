/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_reflect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KCallable;
import kotlin.reflect.KClass;
import kotlin.reflect.KProperty1;
import kotlin.reflect.KTypeParameter;
import kotlin.reflect.KVariance;
import kotlin.reflect.full.KClasses;
import kotlin.text.Regex;
import org.junit.jupiter.api.Test;

/**
 * Kotlin built-in class mapping: a schema exporter renders the JVM classes of an RPC contract
 * under the Kotlin names a Kotlin client sees, through the KClass that kotlin-reflect maps each
 * Java primitive, wrapper, array and collection interface to.
 *
 * The exporter keeps no name table. `JvmClassMappingKt.getKotlinClass` yields the built-in
 * `KClass` (`kotlin.Int` for both `int` and `Integer`, `kotlin.collections.List` for
 * `java.util.List`), `getJavaPrimitiveType` and `getJavaObjectType` tell whether a field may be
 * stored unboxed, and the built-in class model supplies the read-only collection interfaces' type
 * parameters with their variance and the members of `kotlin.String`.
 */
class BuiltinTypeNameMapperTest {

    /** Renders JVM classes as Kotlin type names for a contract schema. */
    static final class SchemaExporter {

        /** The Kotlin name of `javaClass`, with type parameters and their variance. */
        static String kotlinName(Class<?> javaClass) {
            KClass<?> kClass = JvmClassMappingKt.getKotlinClass(javaClass);
            List<KTypeParameter> parameters = kClass.getTypeParameters();
            if (parameters.isEmpty()) {
                return kClass.getQualifiedName();
            }
            StringBuilder name = new StringBuilder(kClass.getQualifiedName()).append('<');
            for (int i = 0; i < parameters.size(); i++) {
                KTypeParameter parameter = parameters.get(i);
                if (i > 0) {
                    name.append(", ");
                }
                if (parameter.getVariance() == KVariance.OUT) {
                    name.append("out ");
                } else if (parameter.getVariance() == KVariance.IN) {
                    name.append("in ");
                }
                name.append(parameter.getName());
            }
            return name.append('>').toString();
        }

        /** Whether a field of `javaClass` can be stored as a JVM primitive. */
        static boolean unboxable(Class<?> javaClass) {
            return JvmClassMappingKt.getJavaPrimitiveType(JvmClassMappingKt.getKotlinClass(javaClass)) != null;
        }

        /** One schema line per field: name, Kotlin type, and `unboxed` when it can be. */
        static List<String> describe(Map<String, Class<?>> fields) {
            List<String> lines = new ArrayList<>();
            for (Map.Entry<String, Class<?>> field : fields.entrySet()) {
                String line = field.getKey() + ": " + kotlinName(field.getValue());
                lines.add(unboxable(field.getValue()) ? line + " (unboxed)" : line);
            }
            return lines;
        }
    }

    @Test
    void primitivesAndWrappersShareOneKotlinClass() {
        Class<?>[][] pairs = {
            {int.class, Integer.class},
            {long.class, Long.class},
            {short.class, Short.class},
            {byte.class, Byte.class},
            {double.class, Double.class},
            {float.class, Float.class},
            {char.class, Character.class},
            {boolean.class, Boolean.class},
        };
        List<String> names = new ArrayList<>();
        for (Class<?>[] pair : pairs) {
            KClass<?> primitive = JvmClassMappingKt.getKotlinClass(pair[0]);
            KClass<?> wrapper = JvmClassMappingKt.getKotlinClass(pair[1]);
            assertThat(primitive).isEqualTo(wrapper);
            assertThat(JvmClassMappingKt.getJavaPrimitiveType(wrapper)).isSameAs(pair[0]);
            assertThat(JvmClassMappingKt.getJavaObjectType(primitive)).isSameAs(pair[1]);
            names.add(primitive.getQualifiedName());
        }
        assertThat(names).containsExactly("kotlin.Int", "kotlin.Long", "kotlin.Short", "kotlin.Byte",
                "kotlin.Double", "kotlin.Float", "kotlin.Char", "kotlin.Boolean");
    }

    @Test
    void javaLangAndCollectionTypesRenderUnderKotlinNames() {
        assertThat(SchemaExporter.kotlinName(Object.class)).isEqualTo("kotlin.Any");
        assertThat(SchemaExporter.kotlinName(String.class)).isEqualTo("kotlin.String");
        assertThat(SchemaExporter.kotlinName(CharSequence.class)).isEqualTo("kotlin.CharSequence");
        assertThat(SchemaExporter.kotlinName(Number.class)).isEqualTo("kotlin.Number");
        assertThat(SchemaExporter.kotlinName(Throwable.class)).isEqualTo("kotlin.Throwable");
        assertThat(SchemaExporter.kotlinName(Comparable.class)).isEqualTo("kotlin.Comparable<in T>");
        assertThat(SchemaExporter.kotlinName(Iterable.class)).isEqualTo("kotlin.collections.Iterable<out T>");
        assertThat(SchemaExporter.kotlinName(Collection.class)).isEqualTo("kotlin.collections.Collection<out E>");
        assertThat(SchemaExporter.kotlinName(List.class)).isEqualTo("kotlin.collections.List<out E>");
        assertThat(SchemaExporter.kotlinName(Set.class)).isEqualTo("kotlin.collections.Set<out E>");
        assertThat(SchemaExporter.kotlinName(Iterator.class)).isEqualTo("kotlin.collections.Iterator<out T>");
        assertThat(SchemaExporter.kotlinName(Map.class)).isEqualTo("kotlin.collections.Map<K, out V>");
        assertThat(SchemaExporter.kotlinName(Map.Entry.class))
                .isEqualTo("kotlin.collections.Map.Entry<out K, out V>");
        assertThat(JvmClassMappingKt.getKotlinClass(Map.Entry.class).getSimpleName()).isEqualTo("Entry");

        assertThat(SchemaExporter.kotlinName(int[].class)).isEqualTo("kotlin.IntArray");
        assertThat(SchemaExporter.kotlinName(byte[].class)).isEqualTo("kotlin.ByteArray");
        assertThat(JvmClassMappingKt.getKotlinClass(String[].class).getQualifiedName()).isEqualTo("kotlin.Array");

        assertThat(SchemaExporter.kotlinName(TreeSet.class)).isEqualTo("java.util.TreeSet<E>");
        assertThat(SchemaExporter.kotlinName(Regex.class)).isEqualTo("kotlin.text.Regex");
    }

    @Test
    void contractFieldsAreDescribedWithStorageHints() {
        Map<String, Class<?>> fields = new LinkedHashMap<>();
        fields.put("id", long.class);
        fields.put("retries", Integer.class);
        fields.put("name", String.class);
        fields.put("payload", byte[].class);
        fields.put("tags", List.class);
        fields.put("attributes", Map.class);
        fields.put("enabled", boolean.class);

        assertThat(SchemaExporter.describe(fields)).containsExactly(
                "id: kotlin.Long (unboxed)",
                "retries: kotlin.Int (unboxed)",
                "name: kotlin.String",
                "payload: kotlin.ByteArray",
                "tags: kotlin.collections.List<out E>",
                "attributes: kotlin.collections.Map<K, out V>",
                "enabled: kotlin.Boolean (unboxed)");
    }

    @Test
    void builtInStringClassExposesItsKotlinMembers() {
        KClass<String> string = JvmClassMappingKt.getKotlinClass(String.class);

        List<String> functions = new ArrayList<>();
        for (KCallable<?> function : KClasses.getMemberFunctions(string)) {
            functions.add(function.getName());
        }
        assertThat(functions).contains("plus", "get", "subSequence", "compareTo");

        KProperty1<String, ?> length = null;
        for (KProperty1<String, ?> property : KClasses.getMemberProperties(string)) {
            if (property.getName().equals("length")) {
                length = property;
            }
        }
        assertThat(length).isNotNull();
        assertThat(length.getReturnType()).hasToString("kotlin.Int");
        assertThat(length.get("schema")).isEqualTo(6);
    }
}
