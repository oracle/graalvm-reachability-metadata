/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.core.convert.TypeDescriptor;

public class TypeConverterDelegateTest {

    @Test
    public void convertStringUsingPublicStringConstructor() {
        SimpleTypeConverter converter = new SimpleTypeConverter();

        StringConstructedValue value = converter.convertIfNecessary("spring", StringConstructedValue.class);

        assertThat(value).isEqualTo(new StringConstructedValue("spring"));
    }

    @Test
    public void convertStringToRawEnumPropertyUsingFullyQualifiedEnumFieldName() {
        RawEnumHolder target = new RawEnumHolder();
        BeanWrapperImpl wrapper = new BeanWrapperImpl(target);

        wrapper.setPropertyValue("enumValue", SampleEnum.class.getCanonicalName() + ".SECOND");

        assertThat(target.getEnumValue()).isSameAs(SampleEnum.SECOND);
    }

    @Test
    public void convertStringToEnumUsingEnumFieldLookupFallback() {
        SimpleTypeConverter converter = new SimpleTypeConverter();

        SampleEnum value = converter.convertIfNecessary("FIRST", SampleEnum.class);

        assertThat(value).isSameAs(SampleEnum.FIRST);
    }

    @Test
    public void convertCollectionArrayAndScalarValuesToTypedArrays() {
        SimpleTypeConverter converter = new SimpleTypeConverter();

        Integer[] collectionResult = converter.convertIfNecessary(Arrays.asList("1", "2"), Integer[].class);
        Integer[] arrayResult = converter.convertIfNecessary(new String[] {"3", "4"}, Integer[].class);
        Integer[] scalarResult = converter.convertIfNecessary("5", Integer[].class);

        assertThat(collectionResult).containsExactly(1, 2);
        assertThat(arrayResult).containsExactly(3, 4);
        assertThat(scalarResult).containsExactly(5);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void convertToCustomTypedCollectionCreatesCollectionCopyWithDefaultConstructor() {
        SimpleTypeConverter converter = new SimpleTypeConverter();
        TypeDescriptor targetType = TypeDescriptor.collection(
                CustomValueCollection.class, TypeDescriptor.valueOf(StringConstructedValue.class));

        CustomValueCollection<StringConstructedValue> result = (CustomValueCollection<StringConstructedValue>)
                converter.convertIfNecessary(Arrays.asList("alpha", "bravo"), CustomValueCollection.class, targetType);

        assertThat(result).isInstanceOf(CustomValueCollection.class);
        assertThat(result).containsExactly(new StringConstructedValue("alpha"), new StringConstructedValue("bravo"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void convertToCustomTypedMapCreatesMapCopyWithDefaultConstructor() {
        SimpleTypeConverter converter = new SimpleTypeConverter();
        Map<String, String> source = new LinkedHashMap<>();
        source.put("left", "right");
        TypeDescriptor valueType = TypeDescriptor.valueOf(StringConstructedValue.class);
        TypeDescriptor targetType = TypeDescriptor.map(CustomValueMap.class, valueType, valueType);

        CustomValueMap<StringConstructedValue, StringConstructedValue> result =
                (CustomValueMap<StringConstructedValue, StringConstructedValue>)
                        converter.convertIfNecessary(source, CustomValueMap.class, targetType);

        assertThat(result).isInstanceOf(CustomValueMap.class);
        assertThat(result).containsEntry(new StringConstructedValue("left"), new StringConstructedValue("right"));
    }

    public enum SampleEnum {
        FIRST,
        SECOND
    }

    public static class RawEnumHolder {
        private Enum<?> enumValue;

        public Enum<?> getEnumValue() {
            return enumValue;
        }

        public void setEnumValue(Enum<?> enumValue) {
            this.enumValue = enumValue;
        }
    }

    public static class StringConstructedValue {
        private final String value;

        public StringConstructedValue(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof StringConstructedValue)) {
                return false;
            }
            StringConstructedValue that = (StringConstructedValue) other;
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    public static class CustomValueCollection<E> extends AbstractCollection<E> {
        private final Map<Integer, E> values = new LinkedHashMap<>();

        @Override
        public Iterator<E> iterator() {
            return values.values().iterator();
        }

        @Override
        public int size() {
            return values.size();
        }

        @Override
        public boolean add(E value) {
            values.put(values.size(), value);
            return true;
        }
    }

    public static class CustomValueMap<K, V> extends AbstractMap<K, V> {
        private final Map<K, V> values = new LinkedHashMap<>();

        @Override
        public Set<Entry<K, V>> entrySet() {
            return values.entrySet();
        }

        @Override
        public V put(K key, V value) {
            return values.put(key, value);
        }
    }
}
