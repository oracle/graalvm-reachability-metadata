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
import org.junit.jupiter.params.shadow.com.univocity.parsers.annotations.Parsed;
import org.junit.jupiter.params.shadow.com.univocity.parsers.common.processor.BeanListProcessor;
import org.junit.jupiter.params.shadow.com.univocity.parsers.common.processor.BeanWriterProcessor;
import org.junit.jupiter.params.shadow.com.univocity.parsers.csv.CsvParser;
import org.junit.jupiter.params.shadow.com.univocity.parsers.csv.CsvParserSettings;

public class FieldMappingTest {

    @Test
    void writesAnnotatedFieldsAndGetterMethods() {
        BeanWriterProcessor<WritableRecord> processor = new BeanWriterProcessor<>(WritableRecord.class);
        WritableRecord record = new WritableRecord();
        record.fieldValue = "field";
        record.setReadableValue("getter");

        Object[] values = processor.write(record, new String[] {"field", "getter"}, new int[] {0, 1});

        assertThat(values).containsExactly("field", "getter");
    }

    @Test
    void parsesAnnotatedSetterMethods() {
        CsvParserSettings settings = new CsvParserSettings();
        BeanListProcessor<WritableRecord> processor = new BeanListProcessor<>(WritableRecord.class);
        settings.setHeaderExtractionEnabled(true);
        settings.setProcessor(processor);

        new CsvParser(settings).parse(new StringReader("""
                setter
                parsed
                """));

        assertThat(processor.getBeans())
                .singleElement()
                .extracting(WritableRecord::getWrittenValue)
                .isEqualTo("parsed");
    }

    public static class WritableRecord {

        @Parsed(field = "field")
        public String fieldValue;

        private String readableValue;
        private String writtenValue;

        public void setReadableValue(String readableValue) {
            this.readableValue = readableValue;
        }

        @Parsed(field = "getter")
        public String getReadableValue() {
            return readableValue;
        }

        @Parsed(field = "setter")
        public void setWrittenValue(String writtenValue) {
            this.writtenValue = writtenValue;
        }

        public String getWrittenValue() {
            return writtenValue;
        }
    }
}
