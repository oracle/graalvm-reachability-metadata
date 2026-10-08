/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.AbstractCsvConverter;
import com.opencsv.bean.BeanFieldJoinStringIndex;
import com.opencsv.exceptions.CsvBadConverterException;
import com.opencsv.exceptions.CsvConstraintViolationException;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import org.apache.commons.collections4.ListValuedMap;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BeanFieldJoinTest {

    @Test
    void setFieldValueInstantiatesDefaultMapAndJoinsValuesByHeader() throws Exception {
        BeanFieldJoinStringIndex<Object> field = joinField(
                DefaultMapBean.class, "values", MultiValuedMap.class);
        DefaultMapBean bean = new DefaultMapBean();

        field.setFieldValue(bean, "alpha", "first");
        field.setFieldValue(bean, "beta", "first");
        field.setFieldValue(bean, "gamma", "second");

        assertThat(bean.values).isInstanceOf(ArrayListValuedHashMap.class);
        assertThat(bean.values.get("first")).containsExactly("alpha", "beta");
        assertThat(bean.values.get("second")).containsExactly("gamma");
    }

    @Test
    void constructorRejectsFieldsThatAreNotMultiValuedMaps() {
        assertThatThrownBy(() -> joinField(NonMapFieldBean.class, "values", MultiValuedMap.class))
                .isInstanceOf(CsvBadConverterException.class)
                .hasMessageContaining(String.class.getName());
    }

    @Test
    void constructorRejectsUnsupportedMultiValuedMapInterfacesWithoutImplementation() {
        assertThatThrownBy(() -> joinField(UnsupportedInterfaceBean.class, "values", MultiValuedMap.class))
                .isInstanceOf(CsvBadConverterException.class)
                .hasMessageContaining(MultiValuedMap.class.getSimpleName());
    }

    @Test
    void constructorRejectsMapImplementationNotAssignableToFieldType() {
        assertThatThrownBy(() -> joinField(ListValuedMapBean.class, "values", HashSetValuedHashMap.class))
                .isInstanceOf(CsvBadConverterException.class)
                .hasMessageContaining(HashSetValuedHashMap.class.getName())
                .hasMessageContaining(ListValuedMap.class.getName());
    }

    @Test
    void setFieldValueReportsMapTypesWithoutPublicNoArgumentConstructors() throws Exception {
        BeanFieldJoinStringIndex<Object> field = joinField(
                NoDefaultConstructorMapBean.class, "values", MultiValuedMap.class);
        NoDefaultConstructorMapBean bean = new NoDefaultConstructorMapBean();

        assertThatThrownBy(() -> field.setFieldValue(bean, "alpha", "first"))
                .isInstanceOf(CsvBadConverterException.class)
                .hasMessageContaining(NoDefaultConstructorValuedMap.class.getName());
    }

    @Test
    void indexAndSplitMultivaluedFieldRejectsNonMapValues() throws Exception {
        BeanFieldJoinStringIndex<Object> field = joinField(
                DefaultMapBean.class, "values", MultiValuedMap.class);

        assertThatThrownBy(() -> field.indexAndSplitMultivaluedField("not a map", "first"))
                .isInstanceOf(CsvDataTypeMismatchException.class)
                .hasMessageContaining(MultiValuedMap.class.getName());
    }

    private static BeanFieldJoinStringIndex<Object> joinField(
            Class<?> beanType, String fieldName, Class<? extends MultiValuedMap> mapType)
            throws NoSuchFieldException {
        Field field = beanType.getField(fieldName);
        return new BeanFieldJoinStringIndex<>(
                beanType, field, false, Locale.US, new IdentityConverter(), mapType, "", "");
    }

    public static class IdentityConverter extends AbstractCsvConverter {
        @Override
        public Object convertToRead(String value)
                throws CsvDataTypeMismatchException, CsvConstraintViolationException {
            return value;
        }
    }

    public static class DefaultMapBean {
        public MultiValuedMap<String, Object> values;
    }

    public static class NonMapFieldBean {
        public String values;
    }

    public interface UnsupportedMultiValuedMap extends MultiValuedMap<String, Object> {
    }

    public static class UnsupportedInterfaceBean {
        public UnsupportedMultiValuedMap values;
    }

    public static class ListValuedMapBean {
        public ListValuedMap<String, Object> values;
    }

    public static class NoDefaultConstructorValuedMap extends ArrayListValuedHashMap<String, Object> {
        public NoDefaultConstructorValuedMap(String ignored) {
        }
    }

    public static class NoDefaultConstructorMapBean {
        public NoDefaultConstructorValuedMap values;
    }
}
