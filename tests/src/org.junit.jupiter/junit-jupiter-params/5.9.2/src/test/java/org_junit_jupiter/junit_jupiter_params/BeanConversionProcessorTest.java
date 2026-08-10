/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_jupiter.junit_jupiter_params;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.shadow.com.univocity.parsers.annotations.Convert;
import org.junit.jupiter.params.shadow.com.univocity.parsers.annotations.Parsed;
import org.junit.jupiter.params.shadow.com.univocity.parsers.common.processor.BeanListProcessor;
import org.junit.jupiter.params.shadow.com.univocity.parsers.conversions.Conversion;
import org.junit.jupiter.params.shadow.com.univocity.parsers.csv.CsvParser;
import org.junit.jupiter.params.shadow.com.univocity.parsers.csv.CsvParserSettings;

public class BeanConversionProcessorTest {

    @Test
    void appliesAnnotatedConversionWhileParsingBean() {
        CsvParserSettings settings = new CsvParserSettings();
        BeanListProcessor<ConvertedRecord> processor = new BeanListProcessor<>(ConvertedRecord.class);
        settings.setHeaderExtractionEnabled(true);
        settings.setProcessor(processor);

        new CsvParser(settings).parse(new StringReader("""
                value
                41
                """));

        assertThat(processor.getBeans())
                .singleElement()
                .extracting(ConvertedRecord::getValue)
                .isEqualTo(410);
    }

    public static class ConvertedRecord {

        @Parsed(field = "value")
        @Convert(conversionClass = AppendZeroConversion.class)
        private Integer value;

        public Integer getValue() {
            return value;
        }
    }

    public static class AppendZeroConversion implements Conversion<String, String> {

        @Override
        public String execute(String input) {
            return input + "0";
        }

        @Override
        public String revert(String output) {
            return output.substring(0, output.length() - 1);
        }
    }
}
