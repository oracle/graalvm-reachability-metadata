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

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvDate;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvBadConverterException;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import java.io.StringReader;
import java.io.StringWriter;
import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.util.List;
import javax.xml.datatype.XMLGregorianCalendar;
import org.junit.jupiter.api.Test;

public class ConverterDateTest {
    private static final String DATATYPE_FACTORY_PROPERTY = "javax.xml.datatype.DatatypeFactory";

    @Test
    void parsesSqlDateFieldUsingAnnotatedBean() {
        List<SqlDateBean> beans = new CsvToBeanBuilder<SqlDateBean>(new StringReader("date\n2022-03-04\n"))
                .withType(SqlDateBean.class)
                .build()
                .parse();

        assertThat(beans).hasSize(1);
        assertThat(beans.get(0).date).isEqualTo(Date.valueOf("2022-03-04"));
    }

    @Test
    void reportsInvalidReadDateFormat() {
        HeaderColumnNameMappingStrategy<InvalidReadFormatBean> strategy = new HeaderColumnNameMappingStrategy<>();

        assertThatExceptionOfType(CsvBadConverterException.class)
                .isThrownBy(() -> strategy.setType(InvalidReadFormatBean.class));
    }

    @Test
    void reportsInvalidWriteDateFormat() {
        HeaderColumnNameMappingStrategy<InvalidWriteFormatBean> strategy = new HeaderColumnNameMappingStrategy<>();

        assertThatExceptionOfType(CsvBadConverterException.class)
                .isThrownBy(() -> strategy.setType(InvalidWriteFormatBean.class));
    }

    @Test
    void reportsUnsupportedTemporalAccessorField() {
        HeaderColumnNameMappingStrategy<UnsupportedTemporalAccessorBean> strategy =
                new HeaderColumnNameMappingStrategy<>();

        assertThatExceptionOfType(CsvBadConverterException.class)
                .isThrownBy(() -> strategy.setType(UnsupportedTemporalAccessorBean.class));
    }

    @Test
    void reportsUnknownChronologyField() {
        HeaderColumnNameMappingStrategy<UnknownChronologyBean> strategy = new HeaderColumnNameMappingStrategy<>();

        assertThatExceptionOfType(CsvBadConverterException.class)
                .isThrownBy(() -> strategy.setType(UnknownChronologyBean.class));
    }

    @Test
    void reportsCsvDateOnUnsupportedReadFieldType() {
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> new CsvToBeanBuilder<UnsupportedReadTypeBean>(
                        new StringReader("date\n2022-03-04\n"))
                        .withType(UnsupportedReadTypeBean.class)
                        .build()
                        .parse())
                .withCauseInstanceOf(CsvDataTypeMismatchException.class);
    }

    @Test
    void reportsCsvDateOnUnsupportedWriteFieldType() throws Exception {
        StringWriter writer = new StringWriter();
        StatefulBeanToCsv<UnsupportedWriteTypeBean> beanToCsv =
                new StatefulBeanToCsvBuilder<UnsupportedWriteTypeBean>(writer).build();

        assertThrows(CsvDataTypeMismatchException.class,
                () -> beanToCsv.write(new UnsupportedWriteTypeBean("2022-03-04")));
    }

    @Test
    void reportsXmlGregorianCalendarDatatypeFactoryConfiguration() {
        synchronized (ConverterDateTest.class) {
            String previousFactory = System.getProperty(DATATYPE_FACTORY_PROPERTY);
            System.setProperty(DATATYPE_FACTORY_PROPERTY, "com.example.MissingDatatypeFactory");
            try {
                assertThatExceptionOfType(RuntimeException.class)
                        .isThrownBy(() -> new CsvToBeanBuilder<XmlGregorianCalendarBean>(
                                new StringReader("date\n2022-03-04\n"))
                                .withType(XmlGregorianCalendarBean.class)
                                .build()
                                .parse())
                        .withCauseInstanceOf(CsvDataTypeMismatchException.class);
            } finally {
                if (previousFactory == null) {
                    System.clearProperty(DATATYPE_FACTORY_PROPERTY);
                } else {
                    System.setProperty(DATATYPE_FACTORY_PROPERTY, previousFactory);
                }
            }
        }
    }

    public static class SqlDateBean {
        @CsvBindByName(column = "date")
        @CsvDate("yyyy-MM-dd")
        public Date date;
    }

    public static class InvalidReadFormatBean {
        @CsvBindByName(column = "date")
        @CsvDate("yyyy-MM-dd '")
        public Date date;
    }

    public static class InvalidWriteFormatBean {
        @CsvBindByName(column = "date")
        @CsvDate(value = "yyyy-MM-dd", writeFormatEqualsReadFormat = false, writeFormat = "yyyy-MM-dd '")
        public Date date;
    }

    public static class UnsupportedTemporalAccessorBean {
        @CsvBindByName(column = "date")
        @CsvDate("yyyy-MM-dd")
        public CustomTemporalAccessor date;
    }

    public static class UnknownChronologyBean {
        @CsvBindByName(column = "date")
        @CsvDate(value = "yyyy-MM-dd", chronology = "missing-chronology")
        public LocalDate date;
    }

    public static class UnsupportedReadTypeBean {
        @CsvBindByName(column = "date")
        @CsvDate("yyyy-MM-dd")
        public String date;
    }

    public static class UnsupportedWriteTypeBean {
        @CsvBindByName(column = "date")
        @CsvDate("yyyy-MM-dd")
        public String date;

        public UnsupportedWriteTypeBean() {
        }

        public UnsupportedWriteTypeBean(String date) {
            this.date = date;
        }
    }

    public static class XmlGregorianCalendarBean {
        @CsvBindByName(column = "date")
        @CsvDate("yyyy-MM-dd")
        public XMLGregorianCalendar date;
    }

    public static class CustomTemporalAccessor implements TemporalAccessor {
        @Override
        public boolean isSupported(TemporalField field) {
            return false;
        }

        @Override
        public long getLong(TemporalField field) {
            throw new UnsupportedOperationException("No temporal fields are supported");
        }
    }
}
