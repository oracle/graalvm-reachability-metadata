/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_univocity.univocity_parsers;

import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.processor.BeanListProcessor;
import com.univocity.parsers.common.processor.BeanWriterProcessor;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldMappingTest {
    @Test
    void parsesCsvIntoAnnotatedFields() {
        List<FieldWriteBean> beans = parseBeans(FieldWriteBean.class, "code,quantity\nA-1,7\n");

        assertThat(beans).hasSize(1);
        assertThat(beans.get(0).getCode()).isEqualTo("A-1");
        assertThat(beans.get(0).getQuantity()).isEqualTo(7);
    }

    @Test
    void parsesCsvThroughAnnotatedSetters() {
        List<MethodWriteBean> beans = parseBeans(MethodWriteBean.class, "code,quantity\nB-2,11\n");

        assertThat(beans).hasSize(1);
        assertThat(beans.get(0).getCode()).isEqualTo("B-2");
        assertThat(beans.get(0).getQuantity()).isEqualTo(11);
    }

    @Test
    void writesAnnotatedFieldsToCsv() {
        FieldReadBean bean = new FieldReadBean("C-3", 13);

        String csv = writeBean(bean, FieldReadBean.class);

        assertThat(csv).contains("code,quantity");
        assertThat(csv).contains("C-3,13");
    }

    @Test
    void writesCsvThroughAnnotatedGetters() {
        MethodReadBean bean = new MethodReadBean("D-4", 17);

        String csv = writeBean(bean, MethodReadBean.class);

        assertThat(csv).contains("code,quantity");
        assertThat(csv).contains("D-4,17");
    }

    private static <T> List<T> parseBeans(Class<T> beanType, String input) {
        BeanListProcessor<T> processor = new BeanListProcessor<>(beanType);
        CsvParserSettings settings = new CsvParserSettings();
        settings.setHeaderExtractionEnabled(true);
        settings.setProcessor(processor);

        CsvParser parser = new CsvParser(settings);
        parser.parse(new StringReader(input));
        return processor.getBeans();
    }

    private static <T> String writeBean(T bean, Class<T> beanType) {
        CsvWriterSettings settings = new CsvWriterSettings();
        settings.setHeaders("code", "quantity");
        settings.setRowWriterProcessor(new BeanWriterProcessor<>(beanType));

        StringWriter output = new StringWriter();
        CsvWriter writer = new CsvWriter(output, settings);
        writer.writeHeaders();
        writer.processRecord(bean);
        writer.close();
        return output.toString();
    }

    public static class FieldWriteBean {
        @Parsed(field = "code")
        private String code;

        @Parsed(field = "quantity")
        private int quantity;

        public FieldWriteBean() {
        }

        public String getCode() {
            return code;
        }

        public int getQuantity() {
            return quantity;
        }
    }

    public static class MethodWriteBean {
        private String code;
        private int quantity;

        public MethodWriteBean() {
        }

        public String getCode() {
            return code;
        }

        @Parsed(field = "code")
        public void setCode(String code) {
            this.code = code;
        }

        public int getQuantity() {
            return quantity;
        }

        @Parsed(field = "quantity")
        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }

    public static class FieldReadBean {
        @Parsed(field = "code")
        private final String code;

        @Parsed(field = "quantity")
        private final int quantity;

        public FieldReadBean(String code, int quantity) {
            this.code = code;
            this.quantity = quantity;
        }
    }

    public static class MethodReadBean {
        private final String code;
        private final int quantity;

        public MethodReadBean(String code, int quantity) {
            this.code = code;
            this.quantity = quantity;
        }

        @Parsed(field = "code")
        public String getCode() {
            return code;
        }

        @Parsed(field = "quantity")
        public int getQuantity() {
            return quantity;
        }
    }
}
