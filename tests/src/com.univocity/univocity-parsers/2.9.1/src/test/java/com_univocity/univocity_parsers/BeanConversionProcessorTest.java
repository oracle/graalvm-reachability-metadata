/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_univocity.univocity_parsers;

import com.univocity.parsers.annotations.Convert;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.processor.BeanListProcessor;
import com.univocity.parsers.common.processor.BeanWriterProcessor;
import com.univocity.parsers.conversions.Conversion;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanConversionProcessorTest {
    @Test
    void appliesCustomConversionBeforeEvaluatingDefaultNumericConversion() {
        List<ConvertedBean> beans = parseBeans("quantity\nqty-42\n");

        assertThat(beans).hasSize(1);
        assertThat(beans.get(0).getQuantity()).isEqualTo(42);
        assertThat(writeBean(new ConvertedBean(17))).contains("quantity").contains("qty-17");
    }

    private static List<ConvertedBean> parseBeans(String input) {
        BeanListProcessor<ConvertedBean> processor = new BeanListProcessor<>(ConvertedBean.class);
        CsvParserSettings settings = new CsvParserSettings();
        settings.setHeaderExtractionEnabled(true);
        settings.setProcessor(processor);

        CsvParser parser = new CsvParser(settings);
        parser.parse(new StringReader(input));
        return processor.getBeans();
    }

    private static String writeBean(ConvertedBean bean) {
        CsvWriterSettings settings = new CsvWriterSettings();
        settings.setHeaders("quantity");
        settings.setRowWriterProcessor(new BeanWriterProcessor<>(ConvertedBean.class));

        StringWriter output = new StringWriter();
        CsvWriter writer = new CsvWriter(output, settings);
        writer.writeHeaders();
        writer.processRecord(bean);
        writer.close();
        return output.toString();
    }

    public static class ConvertedBean {
        @Parsed(field = "quantity")
        @Convert(conversionClass = PrefixedIntegerConversion.class)
        private Integer quantity;

        public ConvertedBean() {
        }

        public ConvertedBean(Integer quantity) {
            this.quantity = quantity;
        }

        public Integer getQuantity() {
            return quantity;
        }
    }

    public static class PrefixedIntegerConversion implements Conversion<String, Object> {
        @Override
        public Object execute(String input) {
            return Integer.valueOf(input.substring("qty-".length()));
        }

        @Override
        public String revert(Object input) {
            return "qty-" + input;
        }
    }
}
