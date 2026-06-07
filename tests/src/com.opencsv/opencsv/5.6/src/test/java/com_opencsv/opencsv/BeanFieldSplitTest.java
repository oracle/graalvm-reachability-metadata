/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.opencsv.bean.BeanFieldSplit;
import com.opencsv.bean.ConverterPrimitiveTypes;
import com.opencsv.bean.CsvConverter;
import com.opencsv.exceptions.CsvBadConverterException;
import com.opencsv.exceptions.CsvBeanIntrospectionException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class BeanFieldSplitTest {
    @Test
    void reportsNonCollectionField() throws Exception {
        Field field = NonCollectionFieldBean.class.getDeclaredField("values");

        assertThatExceptionOfType(CsvBadConverterException.class)
                .isThrownBy(() -> newSplitField(NonCollectionFieldBean.class, field, Collection.class));
    }

    @Test
    void reportsUnsupportedCollectionInterface() throws Exception {
        Field field = UnsupportedCollectionInterfaceBean.class.getDeclaredField("values");

        assertThatExceptionOfType(CsvBadConverterException.class)
                .isThrownBy(() -> newSplitField(
                        UnsupportedCollectionInterfaceBean.class, field, Collection.class));
    }

    @Test
    void reportsUnassignableCollectionImplementation() throws Exception {
        Field field = UnassignableCollectionImplementationBean.class.getDeclaredField("values");

        assertThatExceptionOfType(CsvBadConverterException.class)
                .isThrownBy(() -> newSplitField(
                        UnassignableCollectionImplementationBean.class, field, HashSet.class));
    }

    @Test
    void reportsCollectionImplementationWithoutNoArgConstructor() throws Exception {
        BeanFieldSplit<Object, String> beanField = newSplitField(
                NoDefaultConstructorCollectionBean.class,
                NoDefaultConstructorCollectionBean.class.getDeclaredField("values"),
                NoDefaultConstructorCollection.class);

        assertThatExceptionOfType(CsvBeanIntrospectionException.class)
                .isThrownBy(() -> beanField.setFieldValue(
                        new NoDefaultConstructorCollectionBean(), "one two", "values"));
    }

    private static BeanFieldSplit<Object, String> newSplitField(
            Class<?> beanType,
            Field field,
            Class<? extends Collection> collectionType) {
        CsvConverter converter = new ConverterPrimitiveTypes(String.class, null, null, Locale.US);
        return new BeanFieldSplit<>(
                beanType, field, false, Locale.US, converter, "\\s+", " ", collectionType,
                String.class, "", "");
    }

    public static class NonCollectionFieldBean {
        private String values;
    }

    public interface UnsupportedCollection extends Collection<String> {
    }

    public static class UnsupportedCollectionInterfaceBean {
        private UnsupportedCollection values;
    }

    public static class UnassignableCollectionImplementationBean {
        private List<String> values;
    }

    public static class NoDefaultConstructorCollectionBean {
        private NoDefaultConstructorCollection values;
    }

    public static class NoDefaultConstructorCollection extends ArrayList<String> {
        public NoDefaultConstructorCollection(String ignored) {
        }
    }
}
