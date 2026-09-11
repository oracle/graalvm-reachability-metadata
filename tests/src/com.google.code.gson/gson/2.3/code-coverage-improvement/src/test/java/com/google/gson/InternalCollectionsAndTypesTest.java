/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.google.gson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.google.gson.annotations.Expose;
import com.google.gson.internal.$Gson$Types;
import com.google.gson.internal.ConstructorConstructor;
import com.google.gson.internal.Excluder;
import com.google.gson.internal.LazilyParsedNumber;
import com.google.gson.internal.LinkedHashTreeMap;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.internal.Primitives;
import com.google.gson.internal.bind.ObjectTypeAdapter;
import com.google.gson.internal.bind.TypeAdapters;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import org.junit.jupiter.api.Test;

class InternalCollectionsAndTypesTest {
    @Test
    void linkedMapsPreserveInsertionOrderAndSupportCollectionViews() {
        LinkedHashTreeMap<Integer, String> hashTree = new LinkedHashTreeMap<>();
        LinkedTreeMap<Integer, String> tree = new LinkedTreeMap<>();
        for (int i = 0; i < 20; i++) {
            assertThat(hashTree.put(i, "v" + i)).isNull();
            assertThat(tree.put(i, "v" + i)).isNull();
        }
        assertThat(hashTree.size()).isEqualTo(20);
        assertThat(tree.size()).isEqualTo(20);
        assertThat(hashTree.containsKey(10)).isTrue();
        assertThat(tree.containsKey(10)).isTrue();
        assertThat(hashTree.get(10)).isEqualTo("v10");
        assertThat(tree.get(10)).isEqualTo("v10");
        assertThat(hashTree.keySet()).containsExactlyElementsOf(tree.keySet());
        assertThat(hashTree.entrySet()).hasSize(20);
        assertThat(tree.entrySet()).hasSize(20);
        assertThat(hashTree.remove(10)).isEqualTo("v10");
        assertThat(tree.remove(10)).isEqualTo("v10");
        assertThat(hashTree.remove(100)).isNull();
        assertThat(tree.remove(100)).isNull();
        hashTree.entrySet().iterator().next();
        tree.keySet().remove(0);
        assertThat(tree.containsKey(0)).isFalse();
        hashTree.clear();
        tree.clear();
        assertThat(hashTree).isEmpty();
        assertThat(tree).isEmpty();

        LinkedHashTreeMap<String, Integer> reverse = new LinkedHashTreeMap<>(Comparator.reverseOrder());
        reverse.put("a", 1);
        reverse.put("b", 2);
        assertThat(reverse.keySet()).containsExactly("a", "b");
    }

    @Test
    void linkedHashTreeMapPublicOperationsRebalanceAndRemoveSubtrees() {
        LinkedHashTreeMap<CollidingKey, String> rotateRight = new LinkedHashTreeMap<>();
        rotateRight.put(new CollidingKey(3), "three");
        rotateRight.put(new CollidingKey(2), "two");
        rotateRight.put(new CollidingKey(1), "one");
        assertThat(rotateRight.get(new CollidingKey(2))).isEqualTo("two");

        LinkedHashTreeMap<CollidingKey, String> rotateLeft = new LinkedHashTreeMap<>();
        rotateLeft.put(new CollidingKey(1), "one");
        rotateLeft.put(new CollidingKey(2), "two");
        rotateLeft.put(new CollidingKey(3), "three");
        assertThat(rotateLeft.get(new CollidingKey(2))).isEqualTo("two");

        LinkedHashTreeMap<CollidingKey, String> removeFirst = new LinkedHashTreeMap<>();
        removeFirst.put(new CollidingKey(2), "two");
        removeFirst.put(new CollidingKey(1), "one");
        removeFirst.put(new CollidingKey(3), "three");
        assertThat(removeFirst.remove(new CollidingKey(2))).isEqualTo("two");

        LinkedHashTreeMap<CollidingKey, String> removeLast = new LinkedHashTreeMap<>();
        removeLast.put(new CollidingKey(4), "four");
        removeLast.put(new CollidingKey(2), "two");
        removeLast.put(new CollidingKey(6), "six");
        removeLast.put(new CollidingKey(1), "one");
        removeLast.put(new CollidingKey(3), "three");
        removeLast.put(new CollidingKey(5), "five");
        removeLast.put(new CollidingKey(0), "zero");
        assertThat(removeLast.remove(new CollidingKey(4))).isEqualTo("four");
    }

    @Test
    void publicGsonRoutesUseRawCreatorsAndSortedMapImplementations() {
        Gson withRawCreator = new GsonBuilder()
                .registerTypeAdapter(List.class, new InstanceCreator<List>() {
                    @Override public List createInstance(Type type) {
                        return new LinkedList();
                    }
                })
                .create();
        Type listType = new TypeToken<List<String>>() { }.getType();
        List<String> values = withRawCreator.fromJson("[\"raw\"]", listType);
        assertThat(values).containsExactly("raw");
        assertThat(values).isInstanceOf(LinkedList.class);

        Gson gson = new Gson();
        Type sortedMapType = new TypeToken<SortedMap<String, Integer>>() { }.getType();
        Map<String, Integer> sorted = gson.fromJson("{\"one\":1}", sortedMapType);
        assertThat(sorted).isInstanceOf(SortedMap.class);
        assertThat(sorted).containsEntry("one", 1);

        Type booleanMapType = new TypeToken<Map<Boolean, String>>() { }.getType();
        Map<Boolean, String> booleanMap = new LinkedHashMap<>();
        booleanMap.put(Boolean.FALSE, "no");
        assertThat(gson.toJson(booleanMap, booleanMapType)).isEqualTo("{\"false\":\"no\"}");
    }

    @Test
    void typeTokensAndTypesDescribeGenericCollectionsAndWildcards() {
        TypeToken<List<String>> strings = new TypeToken<List<String>>() { };
        TypeToken<List<Object>> objects = new TypeToken<List<Object>>() { };
        TypeToken<List<String>> sameStrings = new TypeToken<List<String>>() { };
        assertThat(strings.toString()).isEqualTo("java.util.List<java.lang.String>");
        assertThat(strings).isEqualTo(sameStrings);
        assertThat(strings.isAssignableFrom(ArrayList.class)).isFalse();
        assertThat(strings.isAssignableFrom(strings)).isTrue();
        assertThat(strings.isAssignableFrom(strings.getType())).isTrue();
        assertThat(objects.isAssignableFrom(strings.getType())).isFalse();
        assertThat(TypeToken.get(String.class).isAssignableFrom(String.class)).isTrue();
        assertThat(TypeToken.get(String.class).isAssignableFrom((Type) null)).isFalse();
        Type listArray = new TypeToken<List<String>[]>() { }.getType();
        Type arrayListArray = new TypeToken<ArrayList<String>[]>() { }.getType();
        assertThat(TypeToken.get(listArray).isAssignableFrom(arrayListArray)).isTrue();
        Type variable = Generic.class.getTypeParameters()[0];
        assertThatThrownBy(() -> TypeToken.get(variable).isAssignableFrom(String.class))
                .isInstanceOf(AssertionError.class);

        assertThat($Gson$Types.getCollectionElementType(strings.getType(), List.class))
                .isEqualTo(String.class);
        Type[] mapTypes = $Gson$Types.getMapKeyAndValueTypes(
                new TypeToken<Map<String, Integer>>() { }.getType(), Map.class);
        assertThat(mapTypes).containsExactly(String.class, Integer.class);
        assertThat($Gson$Types.getMapKeyAndValueTypes(Properties.class, Properties.class))
                .containsExactly(String.class, String.class);
        assertThat($Gson$Types.subtypeOf(CharSequence.class).toString())
                .isEqualTo("? extends java.lang.CharSequence");
        assertThat($Gson$Types.supertypeOf(String.class).toString())
                .isEqualTo("? super java.lang.String");
        assertThat($Gson$Types.typeToString(String.class)).isEqualTo("java.lang.String");
        assertThat($Gson$Types.typeToString(strings.getType())).contains("java.util.List");
        assertThat($Gson$Types.arrayOf(String.class).toString()).isEqualTo("java.lang.String[]");
    }

    @Test
    void gsonConstructsInterfaceCollectionsMapsAndUnsafeUserTypes() {
        Gson gson = new Gson();
        SortedSet<String> sorted = gson.fromJson(
                "[]", new TypeToken<SortedSet<String>>() { }.getType());
        EnumSet<Day> enumSet = gson.fromJson("[]", new TypeToken<EnumSet<Day>>() { }.getType());
        Set<String> set = gson.fromJson("[]", new TypeToken<Set<String>>() { }.getType());
        Queue<String> queue = gson.fromJson("[]", new TypeToken<Queue<String>>() { }.getType());
        assertThat(sorted).isInstanceOf(SortedSet.class);
        assertThat(enumSet).isInstanceOf(EnumSet.class);
        assertThat(set).isInstanceOf(Set.class);
        assertThat(queue).isInstanceOf(Queue.class);

        Gson created = new GsonBuilder()
                .registerTypeAdapter(Custom.class,
                        (InstanceCreator<Custom>) type -> new Custom("created-by-gson"))
                .create();
        assertThat(created.fromJson("{}", Custom.class).name).isEqualTo("created-by-gson");
        try {
            UnsafeValue decoded = gson.fromJson("{\"value\":\"unsafe\"}", UnsafeValue.class);
            assertThat(decoded.value).isEqualTo("unsafe");
            assertThat(decoded.constructorInvoked).isFalse();
        } catch (RuntimeException exception) {
            assertThat(exception).hasMessageContaining("Unable to invoke no-args constructor");
        }

        Type booleanMap = new TypeToken<Map<Boolean, String>>() { }.getType();
        Map<Boolean, String> decoded = gson.fromJson("{\"true\":\"yes\"}", booleanMap);
        assertThat(decoded).containsEntry(true, "yes");
        Map<Boolean, String> values = new LinkedHashMap<>();
        values.put(true, "yes");
        assertThat(gson.toJson(values, booleanMap)).isEqualTo("{\"true\":\"yes\"}");

        Type integerMap = new TypeToken<Map<Integer, String>>() { }.getType();
        Gson complexMaps = new GsonBuilder().enableComplexMapKeySerialization().create();
        Map<Integer, String> integerValues = new LinkedHashMap<>();
        integerValues.put(3, "three");
        assertThat(complexMaps.toJson(integerValues, integerMap)).isEqualTo("{\"3\":\"three\"}");
        Map<Integer, String> decodedIntegers = complexMaps.fromJson(
                "{\"3\":\"three\"}", integerMap);
        assertThat(decodedIntegers).containsEntry(3, "three");
    }

    @Test
    void publicDeserializationAttemptsAllocationWithoutDefaultConstructor() {
        Gson gson = new Gson();

        try {
            NoDefaultConstructor decoded = gson.fromJson("{}", NoDefaultConstructor.class);
            assertThat(decoded).isNotNull();
        } catch (RuntimeException exception) {
            assertThat(exception).hasMessageContaining("Unable to invoke no-args constructor");
        }
    }

    @Test
    void publicGsonDeserializationUsesLastResortAllocatorWhenRuntimeAllocatorsAreUnavailable()
            throws Exception {
        assumeFalse("runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode")),
                "The isolated class-loader scenario is JVM-only");

        UnsafeUnavailableClassLoader loader =
                new UnsafeUnavailableClassLoader(getClass().getClassLoader());
        Class<?> isolatedGsonClass = loader.loadClass(Gson.class.getName());
        Class<?> isolatedTargetClass = loader.loadClass(NoDefaultConstructor.class.getName());
        assertThat(isolatedGsonClass.getClassLoader()).isSameAs(loader);
        assertThat(isolatedTargetClass.getClassLoader()).isSameAs(loader);
        Object isolatedGson = isolatedGsonClass.getConstructor().newInstance();
        Method fromJson = isolatedGsonClass.getMethod("fromJson", String.class, Class.class);

        try {
            fromJson.invoke(isolatedGson, "{}", isolatedTargetClass);
            throw new AssertionError("Gson unexpectedly allocated without an unsafe allocator");
        } catch (InvocationTargetException exception) {
            assertThat(exception.getCause())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Unable to invoke no-args constructor");
            assertThat(exception.getCause().getCause())
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("Cannot allocate");
        }
        assertThat(loader.blockedUnsafeLookup).isTrue();
    }

    @Test
    void excluderFieldAttributesAndPrimitiveUtilitiesReflectConfiguration() throws Exception {
        Excluder excluder = Excluder.DEFAULT
                .withVersion(1.0)
                .withModifiers(Modifier.TRANSIENT)
                .disableInnerClassSerialization()
                .excludeFieldsWithoutExposeAnnotation()
                .withExclusionStrategy(new ExclusionStrategy() {
                    @Override public boolean shouldSkipField(FieldAttributes field) {
                        return field.getName().equals("blocked");
                    }
                    @Override public boolean shouldSkipClass(Class<?> clazz) {
                        return clazz == String.class;
                    }
                }, true, true);
        Field exposed = Annotated.class.getDeclaredField("exposed");
        Field blocked = Annotated.class.getDeclaredField("blocked");
        Field transientField = Annotated.class.getDeclaredField("transientField");
        FieldAttributes attributes = new FieldAttributes(exposed);
        assertThat(attributes.getDeclaredType()).isEqualTo(String.class);
        Method getAnnotation = FieldAttributes.class.getMethod("getAnnotation", Class.class);
        assertThat(getAnnotation.invoke(attributes, Expose.class)).isNotNull();
        Method getAnnotations = FieldAttributes.class.getMethod("getAnnotations");
        assertThat((Collection<?>) getAnnotations.invoke(attributes)).isNotEmpty();
        assertThat(attributes.hasModifier(Modifier.PRIVATE)).isTrue();
        assertThat(excluder.excludeField(exposed, true)).isFalse();
        assertThat(excluder.excludeField(blocked, true)).isTrue();
        assertThat(excluder.excludeField(transientField, true)).isTrue();
        assertThat(excluder.excludeClass(String.class, true)).isTrue();

        assertThat(Primitives.isWrapperType(Integer.class)).isTrue();
        assertThat(Primitives.isWrapperType(int.class)).isFalse();
        assertThat(Primitives.unwrap(Integer.class)).isEqualTo(int.class);
        assertThat(Primitives.unwrap(String.class)).isEqualTo(String.class);
        assertThat(Primitives.wrap(int.class)).isEqualTo(Integer.class);

        LazilyParsedNumber number = new LazilyParsedNumber("2147483648.5");
        assertThat(number.intValue()).isEqualTo(Integer.MIN_VALUE);
        assertThat(number.longValue()).isEqualTo(2147483648L);
        assertThat(number.floatValue()).isEqualTo(2147483648.5f);
        assertThat(number.doubleValue()).isEqualTo(2147483648.5d);
        assertThat(number.toString()).isEqualTo("2147483648.5");
    }

    @Test
    void constructorsAndObjectAdaptersHandleCommonUserTypes() throws Exception {
        Map<Type, InstanceCreator<?>> creators = new LinkedHashMap<>();
        creators.put(Custom.class, type -> new Custom("created"));
        ConstructorConstructor constructor = new ConstructorConstructor(creators);
        assertThat(constructor.get(TypeToken.get(Custom.class)).construct().name).isEqualTo("created");
        assertThat(constructor.get(new TypeToken<List<String>>() { }).construct()).isInstanceOf(List.class);
        assertThat(constructor.get(new TypeToken<Set<String>>() { }).construct()).isInstanceOf(Set.class);
        assertThat(constructor.get(new TypeToken<EnumSet<Day>>() { }).construct()).isInstanceOf(EnumSet.class);
        assertThat(constructor.toString()).contains("Custom");

        Gson gson = new Gson();
        TypeAdapter<UnsafeValue> unsafeAdapter = gson.getAdapter(UnsafeValue.class);
        try {
            UnsafeValue decoded = unsafeAdapter.fromJson("{\"value\":\"unsafe\"}");
            assertThat(decoded.value).isEqualTo("unsafe");
            assertThat(decoded.constructorInvoked).isFalse();
        } catch (RuntimeException exception) {
            assertThat(exception).hasMessageContaining("Unable to invoke no-args constructor");
        }

        TypeAdapter<Object> objectAdapter = gson.getAdapter(Object.class);
        JsonObject objectTree = new JsonObject();
        objectTree.addProperty("name", "Ada");
        objectTree.add("values", new JsonArray());
        Object decoded = objectAdapter.fromJsonTree(objectTree);
        assertThat(decoded).isInstanceOf(Map.class);
        String encoded = objectAdapter.toJson(Arrays.asList("a", "b"));
        assertThat(encoded).isEqualTo("[\"a\",\"b\"]");
        assertThat(ObjectTypeAdapter.FACTORY.create(gson, TypeToken.get(Object.class))).isNotNull();

        TypeAdapter<String> stringAdapter = new TypeAdapter<String>() {
            @Override public void write(com.google.gson.stream.JsonWriter out, String value)
                    throws java.io.IOException {
                out.value(value);
            }
            @Override public String read(com.google.gson.stream.JsonReader in)
                    throws java.io.IOException {
                return in.nextString();
            }
        };
        TypeAdapterFactory factory = TypeAdapters.newFactory(TypeToken.get(String.class), stringAdapter);
        assertThat(factory.create(gson, TypeToken.get(String.class))).isSameAs(stringAdapter);
    }

    static class Annotated {
        @Expose private String exposed;
        @Expose private String blocked;
        @Expose private transient String transientField;
    }

    static class Custom {
        String name;
        Custom(String name) {
            this.name = name;
        }
    }

    static class UnsafeValue {
        String value;
        private final transient boolean constructorInvoked;

        UnsafeValue(String value) {
            constructorInvoked = true;
            this.value = value;
        }
    }

    static class Generic<T> {
    }

    static final class NoDefaultConstructor {
        NoDefaultConstructor(String ignored) {
        }
    }

    static final class CollidingKey implements Comparable<CollidingKey> {
        private final int value;
        CollidingKey(int value) {
            this.value = value;
        }
        @Override public int compareTo(CollidingKey other) {
            return Integer.compare(value, other.value);
        }
        @Override public int hashCode() {
            return 0;
        }
        @Override public boolean equals(Object other) {
            return other instanceof CollidingKey && value == ((CollidingKey) other).value;
        }
    }

    enum Day { MONDAY }

    // Keep the fallback attempt behind Gson's public API.
    private static final class UnsafeUnavailableClassLoader extends ClassLoader {
        private boolean blockedUnsafeLookup;

        private UnsafeUnavailableClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if ("sun.misc.Unsafe".equals(name)) {
                blockedUnsafeLookup = true;
                throw new ClassNotFoundException(name);
            }
            if (!name.startsWith("com.google.gson")) {
                return super.loadClass(name, resolve);
            }

            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    byte[] bytes = classBytes(name);
                    loaded = defineClass(name, bytes, 0, bytes.length);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }

        private byte[] classBytes(String name) throws ClassNotFoundException {
            String resourceName = name.replace('.', '/') + ".class";
            try (InputStream input = getParent().getResourceAsStream(resourceName)) {
                if (input == null) {
                    throw new ClassNotFoundException(name);
                }
                return input.readAllBytes();
            } catch (Exception exception) {
                throw new ClassNotFoundException(name, exception);
            }
        }
    }
}
