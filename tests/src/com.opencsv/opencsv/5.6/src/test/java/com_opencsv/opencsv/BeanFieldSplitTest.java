/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.BeanFieldSplit;
import com.opencsv.bean.ConverterPrimitiveTypes;
import com.opencsv.exceptions.CsvBadConverterException;
import com.opencsv.exceptions.CsvBeanIntrospectionException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BeanFieldSplitTest {
    @Test
    void createsDefaultCollectionAndAddsSplitValuesWhenAssigningToField() throws Exception {
        BeanFieldSplit<SplitBean, String> beanField = beanFieldFor(
                SplitBean.class.getDeclaredField("values"), Collection.class);
        SplitBean bean = new SplitBean();

        beanField.setFieldValue(bean, "Ada|Lovelace", "values");

        assertThat(bean.values).isInstanceOf(ArrayList.class);
        assertThat(bean.values).containsExactly("Ada", "Lovelace");
    }

    @Test
    void reportsBeanFieldTypeThatIsNotACollection() throws Exception {
        assertThatThrownBy(() -> beanFieldFor(InvalidFieldTypeBean.class.getDeclaredField("values"),
                Collection.class))
                .isInstanceOf(CsvBadConverterException.class)
                .hasMessageContaining(String.class.toString());
    }

    @Test
    void reportsUnsupportedCollectionInterface() throws Exception {
        assertThatThrownBy(() -> beanFieldFor(UnsupportedInterfaceBean.class.getDeclaredField("values"),
                UnsupportedCollection.class))
                .isInstanceOf(CsvBadConverterException.class)
                .hasMessageContaining(UnsupportedCollection.class.toString());
    }

    @Test
    void reportsUnassignableRequestedCollectionImplementation() throws Exception {
        assertThatThrownBy(() -> beanFieldFor(ListBean.class.getDeclaredField("values"), HashSet.class))
                .isInstanceOf(CsvBadConverterException.class)
                .hasMessageContaining(HashSet.class.getName())
                .hasMessageContaining(List.class.getName());
    }

    @Test
    void reportsCollectionImplementationWithoutPublicNoArgumentConstructor() throws Exception {
        BeanFieldSplit<BeanWithUninstantiableCollection, String> beanField = beanFieldFor(
                BeanWithUninstantiableCollection.class.getDeclaredField("values"), Collection.class);

        assertThatThrownBy(() -> beanField.setFieldValue(
                new BeanWithUninstantiableCollection(), "Ada|Lovelace", "values"))
                .isInstanceOf(CsvBeanIntrospectionException.class)
                .hasRootCauseInstanceOf(IllegalAccessException.class)
                .hasMessageContaining(NoPublicNullaryCollection.class.getCanonicalName());
    }

    private static <T> BeanFieldSplit<T, String> beanFieldFor(
            Field field, Class<? extends Collection> collectionType) {
        return new BeanFieldSplit<>(
                field.getDeclaringClass(), field, false, Locale.US,
                new ConverterPrimitiveTypes(String.class, "", "", Locale.US),
                "\\|", ",", collectionType, String.class, "", "");
    }

    public static class SplitBean {
        public List<String> values;
    }

    public static class InvalidFieldTypeBean {
        public String values;
    }

    public interface UnsupportedCollection extends Collection<String> {
    }

    public static class UnsupportedInterfaceBean {
        public UnsupportedCollection values;
    }

    public static class ListBean {
        public List<String> values;
    }

    public static class NoPublicNullaryCollection extends ArrayList<String> {
        private NoPublicNullaryCollection() {
        }
    }

    public static class BeanWithUninstantiableCollection {
        public NoPublicNullaryCollection values;
    }
}
