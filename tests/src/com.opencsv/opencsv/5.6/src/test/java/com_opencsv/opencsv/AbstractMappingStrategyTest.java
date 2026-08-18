/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVReader;
import com.opencsv.bean.AbstractBeanField;
import com.opencsv.bean.AbstractCsvConverter;
import com.opencsv.bean.CsvBindAndSplitByName;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvCustomBindByName;
import com.opencsv.bean.CsvDate;
import com.opencsv.bean.CsvNumber;
import com.opencsv.bean.CsvRecurse;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.exceptions.CsvBadConverterException;
import com.opencsv.exceptions.CsvBeanIntrospectionException;
import com.opencsv.exceptions.CsvConstraintViolationException;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRecursionException;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AbstractMappingStrategyTest {

    @Test
    void populateNewBeanCreatesRootAndRecursiveBeanInstances() throws Exception {
        HeaderColumnNameMappingStrategy<RecursiveRootBean> strategy = typedStrategy(RecursiveRootBean.class);

        RecursiveRootBean bean = populate(strategy, "name,child", "root", "nested");

        assertThat(bean.name).isEqualTo("root");
        assertThat(bean.child).isNotNull();
        assertThat(bean.child.child).isEqualTo("nested");
    }

    @Test
    void populateNewBeanRequiresAType() {
        HeaderColumnNameMappingStrategy<RecursiveRootBean> strategy = new HeaderColumnNameMappingStrategy<>();

        assertThatThrownBy(() -> strategy.populateNewBean(new String[] {"value"}))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void populateNewBeanReportsBeanInstantiationProblems() {
        HeaderColumnNameMappingStrategy<PrivateConstructorBean> strategy = typedStrategy(PrivateConstructorBean.class);

        assertThatThrownBy(() -> strategy.populateNewBean(new String[] {"value"}))
                .isInstanceOf(CsvBeanIntrospectionException.class);
    }

    @Test
    void customBeanFieldConverterIsInstantiatedAndApplied() throws Exception {
        HeaderColumnNameMappingStrategy<CustomBeanFieldBean> strategy = typedStrategy(CustomBeanFieldBean.class);

        CustomBeanFieldBean bean = populate(strategy, "value", "mixedCase");

        assertThat(bean.value).isEqualTo("MIXEDCASE");
    }

    @Test
    void invalidCustomBeanFieldConverterIsReported() {
        HeaderColumnNameMappingStrategy<InvalidCustomBeanFieldBean> strategy = new HeaderColumnNameMappingStrategy<>();

        assertThatThrownBy(() -> strategy.setType(InvalidCustomBeanFieldBean.class))
                .isInstanceOf(CsvBadConverterException.class);
    }

    @Test
    void customCsvConverterIsInstantiatedAndAppliedToSplitValues() throws Exception {
        HeaderColumnNameMappingStrategy<SplitConverterBean> strategy = typedStrategy(SplitConverterBean.class);

        SplitConverterBean bean = populate(strategy, "values", "one|two");

        assertThat(bean.values).containsExactly("ONE", "TWO");
    }

    @Test
    void invalidCustomCsvConverterIsReported() {
        HeaderColumnNameMappingStrategy<InvalidSplitConverterBean> strategy = new HeaderColumnNameMappingStrategy<>();

        assertThatThrownBy(() -> strategy.setType(InvalidSplitConverterBean.class))
                .isInstanceOf(CsvBadConverterException.class);
    }

    @Test
    void dateAnnotationWithUnmatchedProfileIsReported() {
        HeaderColumnNameMappingStrategy<DateProfileBean> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setProfile("missing");

        assertThatThrownBy(() -> strategy.setType(DateProfileBean.class))
                .isInstanceOf(CsvBadConverterException.class);
    }

    @Test
    void numberAnnotationWithUnmatchedProfileIsReported() {
        HeaderColumnNameMappingStrategy<NumberProfileBean> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setProfile("missing");

        assertThatThrownBy(() -> strategy.setType(NumberProfileBean.class))
                .isInstanceOf(CsvBadConverterException.class);
    }

    @Test
    void generateHeaderRequiresAType() {
        HeaderColumnNameMappingStrategy<RecursiveRootBean> strategy = new HeaderColumnNameMappingStrategy<>();

        assertThatThrownBy(() -> strategy.generateHeader(new RecursiveRootBean()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ignoreFieldsRejectsInconsistentClassFieldPairs() throws Exception {
        HeaderColumnNameMappingStrategy<RecursiveRootBean> strategy = new HeaderColumnNameMappingStrategy<>();
        MultiValuedMap<Class<?>, Field> fields = new ArrayListValuedHashMap<>();
        fields.put(RecursiveRootBean.class, OtherBean.class.getField("value"));

        assertThatThrownBy(() -> strategy.ignoreFields(fields))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recursivePrimitiveFieldsAreRejected() {
        HeaderColumnNameMappingStrategy<PrimitiveRecursiveBean> strategy = new HeaderColumnNameMappingStrategy<>();

        assertThatThrownBy(() -> strategy.setType(PrimitiveRecursiveBean.class))
                .isInstanceOf(CsvRecursionException.class);
    }

    @Test
    void duplicateRecursiveTypesAreRejected() {
        HeaderColumnNameMappingStrategy<DuplicateRecursiveTypeBean> strategy = new HeaderColumnNameMappingStrategy<>();

        assertThatThrownBy(() -> strategy.setType(DuplicateRecursiveTypeBean.class))
                .isInstanceOf(CsvRecursionException.class);
    }

    @Test
    void recursiveBindingFieldsAreRejected() {
        HeaderColumnNameMappingStrategy<RecursiveBindingBean> strategy = new HeaderColumnNameMappingStrategy<>();

        assertThatThrownBy(() -> strategy.setType(RecursiveBindingBean.class))
                .isInstanceOf(CsvRecursionException.class);
    }

    @Test
    void transmuteBeanReportsRecursiveGetterFailures() {
        HeaderColumnNameMappingStrategy<ThrowingRecursiveGetterBean> strategy = typedStrategy(
                ThrowingRecursiveGetterBean.class);

        assertThatThrownBy(() -> strategy.transmuteBean(new ThrowingRecursiveGetterBean()))
                .isInstanceOf(CsvBeanIntrospectionException.class);
    }

    private static <T> HeaderColumnNameMappingStrategy<T> typedStrategy(Class<? extends T> type) {
        HeaderColumnNameMappingStrategy<T> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setType(type);
        return strategy;
    }

    private static <T> T populate(
            HeaderColumnNameMappingStrategy<T> strategy, String header, String... line) throws Exception {
        try (CSVReader reader = new CSVReader(new StringReader(header + "\n"))) {
            strategy.captureHeader(reader);
        }
        return strategy.populateNewBean(line);
    }

    public static class RecursiveRootBean {
        @CsvBindByName(column = "name")
        public String name;

        @CsvRecurse
        public RecursiveChildBean child;
    }

    public static class RecursiveChildBean {
        @CsvBindByName(column = "child")
        public String child;
    }

    public static class PrivateConstructorBean {
        @CsvBindByName
        public String value;

        private PrivateConstructorBean() {
        }
    }

    public static class CustomBeanFieldBean {
        @CsvCustomBindByName(column = "value", converter = UppercaseBeanField.class)
        public String value;
    }

    public static class UppercaseBeanField extends AbstractBeanField<CustomBeanFieldBean, String> {
        @Override
        protected Object convert(String value)
                throws CsvDataTypeMismatchException, CsvConstraintViolationException {
            return value.toUpperCase(Locale.ROOT);
        }
    }

    public static class InvalidCustomBeanFieldBean {
        @CsvCustomBindByName(column = "value", converter = NoDefaultConstructorBeanField.class)
        public String value;
    }

    public static class NoDefaultConstructorBeanField extends AbstractBeanField<InvalidCustomBeanFieldBean, String> {
        public NoDefaultConstructorBeanField(String ignored) {
        }

        @Override
        protected Object convert(String value)
                throws CsvDataTypeMismatchException, CsvConstraintViolationException {
            return value;
        }
    }

    public static class SplitConverterBean {
        @CsvBindAndSplitByName(
                column = "values", elementType = String.class, splitOn = "\\|",
                converter = UppercaseCsvConverter.class)
        public List<String> values;
    }

    public static class UppercaseCsvConverter extends AbstractCsvConverter {
        @Override
        public Object convertToRead(String value)
                throws CsvDataTypeMismatchException, CsvConstraintViolationException {
            return value.toUpperCase(Locale.ROOT);
        }
    }

    public static class InvalidSplitConverterBean {
        @CsvBindAndSplitByName(
                column = "values", elementType = String.class, splitOn = "\\|",
                converter = NoDefaultConstructorCsvConverter.class)
        public List<String> values;
    }

    public static class NoDefaultConstructorCsvConverter extends AbstractCsvConverter {
        public NoDefaultConstructorCsvConverter(String ignored) {
        }

        @Override
        public Object convertToRead(String value)
                throws CsvDataTypeMismatchException, CsvConstraintViolationException {
            return value;
        }
    }

    public static class DateProfileBean {
        @CsvBindByName(column = "date")
        @CsvDate(value = "yyyy-MM-dd", profiles = "other")
        public LocalDate date;
    }

    public static class NumberProfileBean {
        @CsvBindByName(column = "amount")
        @CsvNumber(value = "#0.00", profiles = "other")
        public BigDecimal amount;
    }

    public static class OtherBean {
        public String value;
    }

    public static class PrimitiveRecursiveBean {
        @CsvRecurse
        public int value;
    }

    public static class DuplicateRecursiveTypeBean {
        @CsvRecurse
        public RecursiveChildBean first;

        @CsvRecurse
        public RecursiveChildBean second;
    }

    public static class RecursiveBindingBean {
        @CsvRecurse
        @CsvBindByName(column = "child")
        public RecursiveChildBean child;
    }

    public static class ThrowingRecursiveGetterBean {
        @CsvRecurse
        public RecursiveChildBean child;

        public RecursiveChildBean getChild() {
            throw new IllegalStateException("Unable to read recursive child");
        }
    }
}
