/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_univocity.univocity_parsers;

import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.csv.CsvRoutines;
import com.univocity.parsers.csv.CsvWriterSettings;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FieldMappingTest {
    @Test
    public void readsAndWritesAnnotatedFieldsDirectly() {
        final CsvRoutines parser = new CsvRoutines(parserSettings());
        final List<FieldMappedRecord> records = parser.parseAll(
                FieldMappedRecord.class,
                new StringReader("code\nfield-value\n")
        );

        assertEquals(1, records.size());
        assertEquals("field-value", records.get(0).code);

        final StringWriter output = new StringWriter();
        final CsvRoutines writer = new CsvRoutines(writerSettings());
        writer.writeAll(records, FieldMappedRecord.class, output, "code");

        assertEquals("code\nfield-value\n", output.toString());
    }

    @Test
    public void invokesAnnotatedSetterWhenParsingRows() {
        final CsvRoutines parser = new CsvRoutines(parserSettings());
        final List<SetterMappedRecord> records = parser.parseAll(
                SetterMappedRecord.class,
                new StringReader("code\nsetter-value\n")
        );

        assertEquals(1, records.size());
        assertEquals("parsed:setter-value", records.get(0).value());
    }

    @Test
    public void invokesAnnotatedGetterWhenWritingRows() {
        final StringWriter output = new StringWriter();
        final CsvRoutines writer = new CsvRoutines(writerSettings());
        writer.writeAll(
                Collections.singletonList(new GetterMappedRecord("getter-value")),
                GetterMappedRecord.class,
                output,
                "code"
        );

        assertEquals("code\ngetter-value\n", output.toString());
    }

    private static CsvParserSettings parserSettings() {
        final CsvParserSettings settings = new CsvParserSettings();
        settings.setHeaderExtractionEnabled(true);
        return settings;
    }

    private static CsvWriterSettings writerSettings() {
        final CsvWriterSettings settings = new CsvWriterSettings();
        settings.getFormat().setLineSeparator("\n");
        return settings;
    }

    public static class FieldMappedRecord {
        @Parsed(field = "code")
        private String code;

        public FieldMappedRecord() {
        }
    }

    public static class SetterMappedRecord {
        private String code;

        public SetterMappedRecord() {
        }

        @Parsed(field = "code")
        public void setCode(String code) {
            this.code = "parsed:" + code;
        }

        public String value() {
            return code;
        }
    }

    public static class GetterMappedRecord {
        private final String code;

        public GetterMappedRecord(String code) {
            this.code = code;
        }

        @Parsed(field = "code")
        public String getCode() {
            return code;
        }
    }
}
