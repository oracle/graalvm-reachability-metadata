/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import com.opencsv.exceptions.CsvBadConverterException;
import com.opencsv.exceptions.CsvBeanIntrospectionException;
import com.opencsv.exceptions.CsvConstraintViolationException;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRecursionException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.junit.jupiter.api.Test;

public class AbstractMappingStrategyTest {
    @Test
    void parsesBeanWithRecursiveSubordinateBean() {
        List<RecursivePerson> people = new CsvToBeanBuilder<RecursivePerson>(new StringReader("street\nMain Street\n"))
                .withType(RecursivePerson.class)
                .build()
                .parse();

        assertThat(people).hasSize(1);
        assertThat(people.get(0).address).isNotNull();
        assertThat(people.get(0).address.street).isEqualTo("Main Street");
    }

    @Test
    void parsesCustomBeanFieldConverter() {
        List<CustomBeanFieldPerson> people = new CsvToBeanBuilder<CustomBeanFieldPerson>(
                new StringReader("name\n alice \n"))
                .withType(CustomBeanFieldPerson.class)
                .build()
                .parse();

        assertThat(people).extracting(person -> person.name).containsExactly("ALICE");
    }

    @Test
    void parsesSplitFieldWithCustomCsvConverter() {
        List<SplitFieldPerson> people = new CsvToBeanBuilder<SplitFieldPerson>(new StringReader("tags\none;two\n"))
                .withType(SplitFieldPerson.class)
                .build()
                .parse();

        assertThat(people).hasSize(1);
        assertThat(people.get(0).tags).containsExactly("ONE", "TWO");
    }

    @Test
    void createBeanReportsUnsetType() {
        HeaderColumnNameMappingStrategy<RecursivePerson> strategy = new HeaderColumnNameMappingStrategy<>();

        assertThrows(IllegalStateException.class, () -> strategy.populateNewBean(new String[] {"value"}));
    }

    @Test
    void createBeanReportsBeanInstantiationFailures() {
        HeaderColumnNameMappingStrategy<BeanWithoutNoArgConstructor> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setType(BeanWithoutNoArgConstructor.class);

        assertThrows(CsvBeanIntrospectionException.class, () -> strategy.populateNewBean(new String[] {"value"}));
    }

    @Test
    void generateHeaderReportsUnsetType() {
        HeaderColumnNameMappingStrategy<RecursivePerson> strategy = new HeaderColumnNameMappingStrategy<>();

        assertThrows(IllegalStateException.class, () -> strategy.generateHeader(new RecursivePerson()));
    }

    @Test
    void ignoreFieldsRejectsInconsistentClassFieldPairs() throws NoSuchFieldException {
        HeaderColumnNameMappingStrategy<RecursivePerson> strategy = new HeaderColumnNameMappingStrategy<>();
        MultiValuedMap<Class<?>, Field> ignoredFields = new ArrayListValuedHashMap<>();
        ignoredFields.put(RecursivePerson.class, Address.class.getDeclaredField("street"));

        assertThrows(IllegalArgumentException.class, () -> strategy.ignoreFields(ignoredFields));
    }

    @Test
    void loadRecursiveClassesRejectsPrimitiveRecursion() {
        HeaderColumnNameMappingStrategy<PrimitiveRecursionBean> strategy = new HeaderColumnNameMappingStrategy<>();

        assertThrows(CsvRecursionException.class, () -> strategy.setType(PrimitiveRecursionBean.class));
    }

    @Test
    void loadRecursiveClassesRejectsDuplicateRecursiveType() {
        HeaderColumnNameMappingStrategy<DuplicateRecursionBean> strategy = new HeaderColumnNameMappingStrategy<>();

        assertThrows(CsvRecursionException.class, () -> strategy.setType(DuplicateRecursionBean.class));
    }

    @Test
    void loadRecursiveClassesRejectsBindingOnRecursiveField() {
        HeaderColumnNameMappingStrategy<BoundRecursiveFieldBean> strategy = new HeaderColumnNameMappingStrategy<>();

        assertThrows(CsvRecursionException.class, () -> strategy.setType(BoundRecursiveFieldBean.class));
    }

    @Test
    void determineConverterReportsInaccessibleCustomCsvConverter() {
        HeaderColumnNameMappingStrategy<InaccessibleCustomCsvConverterBean> strategy =
                new HeaderColumnNameMappingStrategy<>();

        assertThatExceptionOfType(CsvBadConverterException.class)
                .isThrownBy(() -> strategy.setType(InaccessibleCustomCsvConverterBean.class));
    }

    @Test
    void instantiateCustomConverterReportsInaccessibleCustomBeanField() {
        HeaderColumnNameMappingStrategy<InaccessibleCustomBeanFieldBean> strategy =
                new HeaderColumnNameMappingStrategy<>();

        assertThatExceptionOfType(CsvBadConverterException.class)
                .isThrownBy(() -> strategy.setType(InaccessibleCustomBeanFieldBean.class));
    }

    @Test
    void determineConverterReportsMissingDateProfile() {
        HeaderColumnNameMappingStrategy<MissingDateProfileBean> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setProfile("selected");

        assertThatExceptionOfType(CsvBadConverterException.class)
                .isThrownBy(() -> strategy.setType(MissingDateProfileBean.class));
    }

    @Test
    void determineConverterReportsMissingNumberProfile() {
        HeaderColumnNameMappingStrategy<MissingNumberProfileBean> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setProfile("selected");

        assertThatExceptionOfType(CsvBadConverterException.class)
                .isThrownBy(() -> strategy.setType(MissingNumberProfileBean.class));
    }

    @Test
    void transmuteBeanReportsRecursiveBeanIntrospectionFailure() throws Exception {
        HeaderColumnNameMappingStrategy<ThrowingRecursiveGetterBean> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setType(ThrowingRecursiveGetterBean.class);
        strategy.generateHeader(new ThrowingRecursiveGetterBean());

        assertThrows(CsvBeanIntrospectionException.class,
                () -> strategy.transmuteBean(new ThrowingRecursiveGetterBean()));
    }

    public static class RecursivePerson {
        @CsvRecurse
        private Address address;
    }

    public static class Address {
        @CsvBindByName
        private String street;
    }

    public static class CustomBeanFieldPerson {
        @CsvCustomBindByName(column = "name", converter = UppercaseBeanField.class)
        private String name;
    }

    public static class UppercaseBeanField extends AbstractBeanField<CustomBeanFieldPerson, String> {
        @Override
        protected Object convert(String value)
                throws CsvDataTypeMismatchException, CsvConstraintViolationException {
            return value == null ? null : value.trim().toUpperCase();
        }
    }

    public static class SplitFieldPerson {
        @CsvBindAndSplitByName(column = "tags", elementType = String.class, splitOn = ";",
                converter = UppercaseCsvConverter.class)
        private List<String> tags;
    }

    public static class UppercaseCsvConverter extends AbstractCsvConverter {
        @Override
        public Object convertToRead(String value) {
            return value == null ? null : value.toUpperCase();
        }
    }

    public static class BeanWithoutNoArgConstructor {
        @CsvBindByName
        private final String value;

        public BeanWithoutNoArgConstructor(String value) {
            this.value = value;
        }
    }

    public static class PrimitiveRecursionBean {
        @CsvRecurse
        private int primitive;
    }

    public static class DuplicateRecursionBean {
        @CsvRecurse
        private Address first;

        @CsvRecurse
        private Address second;
    }

    public static class BoundRecursiveFieldBean {
        @CsvRecurse
        @CsvBindByName
        private Address address;
    }

    public static class InaccessibleCustomCsvConverterBean {
        @CsvBindAndSplitByName(column = "tags", elementType = String.class,
                converter = PrivateCsvConverter.class)
        private List<String> tags;
    }

    private static class PrivateCsvConverter extends AbstractCsvConverter {
        @Override
        public Object convertToRead(String value) {
            return value;
        }
    }

    public static class InaccessibleCustomBeanFieldBean {
        @CsvCustomBindByName(column = "name", converter = PrivateBeanField.class)
        private String name;
    }

    private static class PrivateBeanField extends AbstractBeanField<InaccessibleCustomBeanFieldBean, String> {
        @Override
        protected Object convert(String value) {
            return value;
        }
    }

    public static class MissingDateProfileBean {
        @CsvDate(value = "yyyy-MM-dd", profiles = "other")
        private Date date;
    }

    public static class MissingNumberProfileBean {
        @CsvNumber(value = "#,##0.00", profiles = "other")
        private BigDecimal amount;
    }

    public static class ThrowingRecursiveGetterBean {
        @CsvRecurse
        private ThrowingChild child = new ThrowingChild();

        public ThrowingChild getChild() {
            throw new IllegalStateException("child is unavailable");
        }
    }

    public static class ThrowingChild {
        @CsvBindByName
        private String value = "data";
    }
}
