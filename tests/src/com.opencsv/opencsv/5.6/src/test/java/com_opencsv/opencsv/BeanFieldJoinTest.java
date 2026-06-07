/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.opencsv.bean.BeanFieldJoinStringIndex;
import com.opencsv.bean.ConverterPrimitiveTypes;
import com.opencsv.bean.CsvBindAndJoinByName;
import com.opencsv.bean.CsvConverter;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.exceptions.CsvBadConverterException;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import org.apache.commons.collections4.ListValuedMap;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.junit.jupiter.api.Test;

public class BeanFieldJoinTest {
    @Test
    void parsesJoinedColumnsIntoDefaultMapImplementation() {
        List<JoinedColumnsBean> beans = new CsvToBeanBuilder<JoinedColumnsBean>(
                new StringReader("tagA,tagB,ignored\none,two,unused\n"))
                .withType(JoinedColumnsBean.class)
                .build()
                .parse();

        assertThat(beans).hasSize(1);
        assertThat(beans.get(0).values).isInstanceOf(ArrayListValuedHashMap.class);
        assertThat(beans.get(0).values.values()).containsExactlyInAnyOrder("one", "two");
    }

    @Test
    void reportsJoinedAnnotationOnNonMapField() {
        HeaderColumnNameMappingStrategy<NonMapJoinedFieldBean> strategy = new HeaderColumnNameMappingStrategy<>();

        assertThatExceptionOfType(CsvBadConverterException.class)
                .isThrownBy(() -> strategy.setType(NonMapJoinedFieldBean.class));
    }

    @Test
    void reportsUnsupportedJoinedMapInterface() {
        HeaderColumnNameMappingStrategy<UnsupportedMapInterfaceBean> strategy = new HeaderColumnNameMappingStrategy<>();

        assertThatExceptionOfType(CsvBadConverterException.class)
                .isThrownBy(() -> strategy.setType(UnsupportedMapInterfaceBean.class));
    }

    @Test
    void reportsUnassignableJoinedMapImplementation() {
        HeaderColumnNameMappingStrategy<UnassignableMapImplementationBean> strategy =
                new HeaderColumnNameMappingStrategy<>();

        assertThatExceptionOfType(CsvBadConverterException.class)
                .isThrownBy(() -> strategy.setType(UnassignableMapImplementationBean.class));
    }

    @Test
    void reportsJoinedMapImplementationWithoutNoArgConstructor() throws Exception {
        BeanFieldJoinStringIndex<Object> beanField = newJoinedField(
                NoDefaultConstructorMapBean.class, "values", MultiValuedMap.class);

        assertThatExceptionOfType(CsvBadConverterException.class)
                .isThrownBy(() -> beanField.setFieldValue(new NoDefaultConstructorMapBean(), "one", "tagA"));
    }

    @Test
    void reportsNonMapValueWhenSplittingJoinedFieldForWrite() throws Exception {
        BeanFieldJoinStringIndex<Object> beanField = newJoinedField(
                JoinedColumnsBean.class, "values", MultiValuedMap.class);

        assertThatExceptionOfType(CsvDataTypeMismatchException.class)
                .isThrownBy(() -> beanField.indexAndSplitMultivaluedField("not a map", "tagA"));
    }

    private static BeanFieldJoinStringIndex<Object> newJoinedField(
            Class<?> beanType, String fieldName, Class<? extends MultiValuedMap> mapType) throws NoSuchFieldException {
        Field field = beanType.getDeclaredField(fieldName);
        CsvConverter converter = new ConverterPrimitiveTypes(String.class, null, null, Locale.US);
        return new BeanFieldJoinStringIndex<>(beanType, field, false, Locale.US, converter, mapType, null, null);
    }

    public static class JoinedColumnsBean {
        @CsvBindAndJoinByName(column = "tag.*", elementType = String.class)
        private MultiValuedMap<String, String> values;
    }

    public static class NonMapJoinedFieldBean {
        @CsvBindAndJoinByName(column = "tag.*", elementType = String.class)
        private String values;
    }

    public interface UnsupportedJoinedMap extends MultiValuedMap<String, String> {
    }

    public static class UnsupportedMapInterfaceBean {
        @CsvBindAndJoinByName(column = "tag.*", elementType = String.class)
        private UnsupportedJoinedMap values;
    }

    public static class UnassignableMapImplementationBean {
        @CsvBindAndJoinByName(column = "tag.*", elementType = String.class, mapType = HashSetValuedHashMap.class)
        private ListValuedMap<String, String> values;
    }

    public static class NoDefaultConstructorMapBean {
        private NoDefaultConstructorMap values;
    }

    public static class NoDefaultConstructorMap extends ArrayListValuedHashMap<String, String> {
        public NoDefaultConstructorMap(String ignored) {
        }
    }
}
