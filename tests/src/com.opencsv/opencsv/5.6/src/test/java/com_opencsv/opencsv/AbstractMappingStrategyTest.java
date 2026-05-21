/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.AbstractBeanField;
import com.opencsv.bean.AbstractCsvConverter;
import com.opencsv.bean.CsvBindAndSplitByName;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvCustomBindByName;
import com.opencsv.bean.CsvDate;
import com.opencsv.bean.CsvNumber;
import com.opencsv.bean.CsvRecurse;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvBadConverterException;
import com.opencsv.exceptions.CsvBeanIntrospectionException;
import com.opencsv.exceptions.CsvConstraintViolationException;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRecursionException;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AbstractMappingStrategyTest {
    @Test
    void populatesRootAndRecursiveBeans() {
        List<RecursiveRootBean> beans = new CsvToBeanBuilder<RecursiveRootBean>(new StringReader("name\nAda\n"))
                .withType(RecursiveRootBean.class)
                .withErrorLocale(Locale.US)
                .build()
                .parse();

        assertThat(beans).hasSize(1);
        assertThat(beans.get(0).child).isNotNull();
        assertThat(beans.get(0).child.name).isEqualTo("Ada");
    }

    @Test
    void instantiatesCustomBeanFieldConverter() {
        List<CustomBeanFieldBean> beans = new CsvToBeanBuilder<CustomBeanFieldBean>(new StringReader("name\nada\n"))
                .withType(CustomBeanFieldBean.class)
                .build()
                .parse();

        assertThat(beans).hasSize(1);
        assertThat(beans.get(0).name).isEqualTo("ADA");
    }

    @Test
    void instantiatesCustomCsvConverter() {
        HeaderColumnNameMappingStrategy<CustomCsvConverterBean> strategy = new HeaderColumnNameMappingStrategy<>();

        strategy.setType(CustomCsvConverterBean.class);

        assertThat(strategy.getType()).isEqualTo(CustomCsvConverterBean.class);
    }

    @Test
    void reportsUnsetTypeBeforeBeanCreation() {
        HeaderColumnNameMappingStrategy<SimpleBean> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setErrorLocale(Locale.US);

        assertThatThrownBy(() -> strategy.populateNewBean(new String[] {"value"}))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reportsUnsetTypeBeforeHeaderGeneration() {
        HeaderColumnNameMappingStrategy<SimpleBean> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setErrorLocale(Locale.US);

        assertThatThrownBy(() -> strategy.generateHeader(new SimpleBean()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reportsBeanInstantiationFailure() {
        HeaderColumnNameMappingStrategy<NoAccessibleConstructorBean> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setErrorLocale(Locale.US);
        strategy.setType(NoAccessibleConstructorBean.class);

        assertThatThrownBy(() -> strategy.populateNewBean(new String[] {"value"}))
                .isInstanceOf(CsvBeanIntrospectionException.class)
                .hasRootCauseInstanceOf(IllegalAccessException.class);
    }

    @Test
    void reportsInvalidCustomBeanFieldConverter() {
        HeaderColumnNameMappingStrategy<InvalidCustomBeanFieldBean> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setErrorLocale(Locale.US);

        assertThatThrownBy(() -> strategy.setType(InvalidCustomBeanFieldBean.class))
                .isInstanceOf(CsvBadConverterException.class)
                .hasRootCauseInstanceOf(IllegalAccessException.class);
    }

    @Test
    void reportsInvalidCustomCsvConverter() {
        HeaderColumnNameMappingStrategy<InvalidCustomCsvConverterBean> strategy =
                new HeaderColumnNameMappingStrategy<>();
        strategy.setErrorLocale(Locale.US);

        assertThatThrownBy(() -> strategy.setType(InvalidCustomCsvConverterBean.class))
                .isInstanceOf(CsvBadConverterException.class)
                .hasRootCauseInstanceOf(IllegalAccessException.class);
    }

    @Test
    void reportsMissingDateProfile() {
        HeaderColumnNameMappingStrategy<DateProfileBean> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setProfile("missing");

        assertThatThrownBy(() -> strategy.setType(DateProfileBean.class))
                .isInstanceOf(CsvBadConverterException.class);
    }

    @Test
    void reportsMissingNumberProfile() {
        HeaderColumnNameMappingStrategy<NumberProfileBean> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setProfile("missing");

        assertThatThrownBy(() -> strategy.setType(NumberProfileBean.class))
                .isInstanceOf(CsvBadConverterException.class);
    }

    @Test
    void reportsInconsistentIgnoredFields() throws NoSuchFieldException {
        HeaderColumnNameMappingStrategy<SimpleBean> strategy = new HeaderColumnNameMappingStrategy<>();
        MultiValuedMap<Class<?>, Field> ignoredFields = new ArrayListValuedHashMap<>();
        ignoredFields.put(SimpleBean.class, OtherBean.class.getDeclaredField("other"));

        assertThatThrownBy(() -> strategy.ignoreFields(ignoredFields))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reportsRecursionIntoPrimitiveType() {
        HeaderColumnNameMappingStrategy<PrimitiveRecursiveBean> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setErrorLocale(Locale.US);

        assertThatThrownBy(() -> strategy.setType(PrimitiveRecursiveBean.class))
                .isInstanceOf(CsvRecursionException.class);
    }

    @Test
    void reportsDuplicateRecursiveType() {
        HeaderColumnNameMappingStrategy<DuplicateRecursiveRootBean> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setErrorLocale(Locale.US);

        assertThatThrownBy(() -> strategy.setType(DuplicateRecursiveRootBean.class))
                .isInstanceOf(CsvRecursionException.class);
    }

    @Test
    void reportsMutuallyExclusiveRecursiveBinding() {
        HeaderColumnNameMappingStrategy<BoundRecursiveBean> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setErrorLocale(Locale.US);

        assertThatThrownBy(() -> strategy.setType(BoundRecursiveBean.class))
                .isInstanceOf(CsvRecursionException.class);
    }

    @Test
    void reportsRecursiveGetterFailureWhileTransmutingBean() {
        StringWriter writer = new StringWriter();
        StatefulBeanToCsv<ThrowingRecursiveGetterBean> beanToCsv =
                new StatefulBeanToCsvBuilder<ThrowingRecursiveGetterBean>(writer)
                        .build();

        assertThatThrownBy(() -> beanToCsv.write(new ThrowingRecursiveGetterBean()))
                .isInstanceOf(CsvBeanIntrospectionException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    public static class SimpleBean {
        public String value;
    }

    public static class OtherBean {
        public String other;
    }

    public static class RecursiveRootBean {
        @CsvRecurse
        public RecursiveChildBean child;
    }

    public static class RecursiveChildBean {
        @CsvBindByName(column = "name")
        public String name;
    }

    public static class CustomBeanFieldBean {
        @CsvCustomBindByName(column = "name", converter = UppercaseBeanField.class)
        public String name;
    }

    public static class UppercaseBeanField extends AbstractBeanField<CustomBeanFieldBean, String> {
        @Override
        protected Object convert(String value) {
            return value.toUpperCase(Locale.ROOT);
        }
    }

    public static class CustomCsvConverterBean {
        @CsvBindAndSplitByName(column = "values", elementType = String.class, converter = IdentityCsvConverter.class)
        public List<String> values;
    }

    public static class IdentityCsvConverter extends AbstractCsvConverter {
        @Override
        public Object convertToRead(String value) {
            return value;
        }
    }

    public static class NoAccessibleConstructorBean {
        public String value;

        private NoAccessibleConstructorBean() {
        }
    }

    public static class InvalidCustomBeanFieldBean {
        @CsvCustomBindByName(column = "name", converter = InaccessibleBeanField.class)
        public String name;
    }

    public static class InaccessibleBeanField extends AbstractBeanField<InvalidCustomBeanFieldBean, String> {
        private InaccessibleBeanField() {
        }

        @Override
        protected Object convert(String value) {
            return value;
        }
    }

    public static class InvalidCustomCsvConverterBean {
        @CsvBindAndSplitByName(
                column = "values",
                elementType = String.class,
                converter = InaccessibleCsvConverter.class)
        public List<String> values;
    }

    public static class InaccessibleCsvConverter extends AbstractCsvConverter {
        private InaccessibleCsvConverter() {
        }

        @Override
        public Object convertToRead(String value) throws CsvDataTypeMismatchException, CsvConstraintViolationException {
            return value;
        }
    }

    public static class DateProfileBean {
        @CsvBindByName(column = "created")
        @CsvDate(value = "yyyy-MM-dd", profiles = "available")
        public LocalDate created;
    }

    public static class NumberProfileBean {
        @CsvBindByName(column = "amount")
        @CsvNumber(value = "#0.00", profiles = "available")
        public Integer amount;
    }

    public static class PrimitiveRecursiveBean {
        @CsvRecurse
        public int child;
    }

    public static class DuplicateRecursiveRootBean {
        @CsvRecurse
        public DuplicateRecursiveChildBean first;

        @CsvRecurse
        public DuplicateRecursiveChildBean second;
    }

    public static class DuplicateRecursiveChildBean {
        public String value;
    }

    public static class BoundRecursiveBean {
        @CsvRecurse
        @CsvBindByName(column = "child")
        public RecursiveChildBean child;
    }

    public static class ThrowingRecursiveGetterBean {
        @CsvRecurse
        private ThrowingRecursiveChildBean child = new ThrowingRecursiveChildBean();

        public ThrowingRecursiveChildBean getChild() {
            throw new IllegalStateException("getter failed");
        }
    }

    public static class ThrowingRecursiveChildBean {
        @CsvBindByName(column = "name")
        public String name = "Ada";
    }
}
