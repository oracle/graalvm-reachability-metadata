/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.AbstractCsvConverter;
import com.opencsv.bean.BeanFieldSplit;
import com.opencsv.exceptions.CsvBadConverterException;
import com.opencsv.exceptions.CsvBeanIntrospectionException;
import com.opencsv.exceptions.CsvConstraintViolationException;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BeanFieldSplitTest {

    @Test
    void constructorRejectsFieldsThatAreNotCollections() {
        assertThatThrownBy(() -> splitField(NonCollectionFieldBean.class, "values", Collection.class))
                .isInstanceOf(CsvBadConverterException.class)
                .hasMessageContaining(String.class.getName());
    }

    @Test
    void constructorRejectsUnsupportedCollectionInterfacesWithoutImplementation() {
        assertThatThrownBy(() -> splitField(UnsupportedInterfaceBean.class, "values", Collection.class))
                .isInstanceOf(CsvBadConverterException.class)
                .hasMessageContaining(Collection.class.getName());
    }

    @Test
    void constructorRejectsCollectionImplementationNotAssignableToFieldType() {
        assertThatThrownBy(() -> splitField(ListBean.class, "values", HashSet.class))
                .isInstanceOf(CsvBadConverterException.class)
                .hasMessageContaining(HashSet.class.getName())
                .hasMessageContaining(List.class.getName());
    }

    @Test
    void setFieldValueReportsCollectionTypesWithoutPublicNoArgumentConstructors() throws Exception {
        BeanFieldSplit<Object, Object> field = splitField(
                NoDefaultConstructorCollectionBean.class, "values", Collection.class);
        NoDefaultConstructorCollectionBean bean = new NoDefaultConstructorCollectionBean();

        assertThatThrownBy(() -> field.setFieldValue(bean, "alpha,beta", "values"))
                .isInstanceOf(CsvBeanIntrospectionException.class)
                .hasMessageContaining(NoDefaultConstructorCollection.class.getCanonicalName());
    }

    private static BeanFieldSplit<Object, Object> splitField(
            Class<?> beanType, String fieldName, Class<? extends Collection> collectionType)
            throws NoSuchFieldException {
        Field field = beanType.getField(fieldName);
        return new BeanFieldSplit<>(
                beanType, field, false, Locale.US, new IdentityConverter(), ",", ",",
                collectionType, String.class, "", "");
    }

    public static class IdentityConverter extends AbstractCsvConverter {
        @Override
        public Object convertToRead(String value)
                throws CsvDataTypeMismatchException, CsvConstraintViolationException {
            return value;
        }
    }

    public static class NonCollectionFieldBean {
        public String values;
    }

    public interface UnsupportedCollection extends Collection<Object> {
    }

    public static class UnsupportedInterfaceBean {
        public UnsupportedCollection values;
    }

    public static class ListBean {
        public List<Object> values;
    }

    public static class NoDefaultConstructorCollection extends ArrayList<Object> {
        public NoDefaultConstructorCollection(String ignored) {
        }
    }

    public static class NoDefaultConstructorCollectionBean {
        public NoDefaultConstructorCollection values;
    }
}
