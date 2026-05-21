/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.BeanFieldJoinStringIndex;
import com.opencsv.bean.ConverterPrimitiveTypes;
import com.opencsv.exceptions.CsvBadConverterException;
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
    void createsDefaultMapAndAddsValuesWhenAssigningToNullField() throws Exception {
        BeanFieldJoinStringIndex<JoinedBean> beanField = beanFieldFor(
                JoinedBean.class.getDeclaredField("values"), MultiValuedMap.class);
        JoinedBean bean = new JoinedBean();

        beanField.setFieldValue(bean, "Ada", "first");
        beanField.setFieldValue(bean, "Lovelace", "last");

        assertThat(bean.values).isInstanceOf(ArrayListValuedHashMap.class);
        assertThat(bean.values.get("first")).containsExactly("Ada");
        assertThat(bean.values.get("last")).containsExactly("Lovelace");
    }

    @Test
    void reportsBeanFieldTypeThatIsNotMultiValuedMap() throws Exception {
        assertThatThrownBy(() -> beanFieldFor(InvalidFieldTypeBean.class.getDeclaredField("values"),
                MultiValuedMap.class))
                .isInstanceOf(CsvBadConverterException.class)
                .hasMessageContaining(String.class.toString());
    }

    @Test
    void reportsUnsupportedMultiValuedMapInterface() throws Exception {
        assertThatThrownBy(() -> beanFieldFor(UnsupportedInterfaceBean.class.getDeclaredField("values"),
                MultiValuedMap.class))
                .isInstanceOf(CsvBadConverterException.class)
                .hasMessageContaining(MultiValuedMap.class.toString());
    }

    @Test
    void reportsUnassignableRequestedMapImplementation() throws Exception {
        assertThatThrownBy(() -> beanFieldFor(ListValuedMapBean.class.getDeclaredField("values"),
                HashSetValuedHashMap.class))
                .isInstanceOf(CsvBadConverterException.class)
                .hasMessageContaining(HashSetValuedHashMap.class.getName())
                .hasMessageContaining(ListValuedMap.class.getName());
    }

    @Test
    void reportsMapImplementationWithoutPublicNoArgumentConstructor() throws Exception {
        BeanFieldJoinStringIndex<BeanWithUninstantiableMap> beanField = beanFieldFor(
                BeanWithUninstantiableMap.class.getDeclaredField("values"), MultiValuedMap.class);

        assertThatThrownBy(() -> beanField.setFieldValue(new BeanWithUninstantiableMap(), "Ada", "name"))
                .isInstanceOf(CsvBadConverterException.class)
                .hasRootCauseInstanceOf(NoSuchMethodException.class)
                .hasMessageContaining(NoPublicNullaryMap.class.getName());
    }

    @Test
    void reportsNonMultiValuedFieldWhileSplittingForWrite() throws Exception {
        BeanFieldJoinStringIndex<JoinedBean> beanField = beanFieldFor(
                JoinedBean.class.getDeclaredField("values"), MultiValuedMap.class);

        assertThatThrownBy(() -> beanField.indexAndSplitMultivaluedField("not a map", "name"))
                .isInstanceOf(CsvDataTypeMismatchException.class);
    }

    @Test
    void splitsValuesForRequestedHeaderWhenWriting() throws Exception {
        BeanFieldJoinStringIndex<JoinedBean> beanField = beanFieldFor(
                JoinedBean.class.getDeclaredField("values"), MultiValuedMap.class);
        JoinedBean bean = new JoinedBean();
        bean.values = new ArrayListValuedHashMap<>();
        bean.values.put("name", "Ada");
        bean.values.put("name", "Lovelace");
        bean.values.put("unused", "ignored");

        assertThat(beanField.write(bean, "name")).containsExactly("Ada", "Lovelace");
    }

    private static <T> BeanFieldJoinStringIndex<T> beanFieldFor(
            Field field, Class<? extends MultiValuedMap> mapType) {
        return new BeanFieldJoinStringIndex<>(
                field.getDeclaringClass(), field, false, Locale.US,
                new ConverterPrimitiveTypes(String.class, "", "", Locale.US),
                mapType, "", "");
    }

    public static class JoinedBean {
        public MultiValuedMap<String, String> values;
    }

    public static class InvalidFieldTypeBean {
        public String values;
    }

    public interface UnsupportedMap extends MultiValuedMap<String, String> {
    }

    public static class UnsupportedInterfaceBean {
        public UnsupportedMap values;
    }

    public static class ListValuedMapBean {
        public ListValuedMap<String, String> values;
    }

    public static class NoPublicNullaryMap extends ArrayListValuedHashMap<String, String> {
        private NoPublicNullaryMap() {
        }
    }

    public static class BeanWithUninstantiableMap {
        public NoPublicNullaryMap values;
    }
}
