/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_jupiter.junit_jupiter_params;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.text.DecimalFormat;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.shadow.com.univocity.parsers.annotations.Convert;
import org.junit.jupiter.params.shadow.com.univocity.parsers.annotations.Copy;
import org.junit.jupiter.params.shadow.com.univocity.parsers.annotations.Parsed;
import org.junit.jupiter.params.shadow.com.univocity.parsers.annotations.helpers.AnnotationHelper;
import org.junit.jupiter.params.shadow.com.univocity.parsers.annotations.helpers.MethodFilter;
import org.junit.jupiter.params.shadow.com.univocity.parsers.common.processor.BeanListProcessor;
import org.junit.jupiter.params.shadow.com.univocity.parsers.conversions.Conversion;
import org.junit.jupiter.params.shadow.com.univocity.parsers.csv.CsvParser;
import org.junit.jupiter.params.shadow.com.univocity.parsers.csv.CsvParserSettings;

public class AnnotationHelperTest {

    @Test
    void parsesAnnotatedBeansWithCustomConversions() {
        CsvParserSettings settings = new CsvParserSettings();
        BeanListProcessor<ConvertedRecord> processor = new BeanListProcessor<>(ConvertedRecord.class);
        settings.setHeaderExtractionEnabled(true);
        settings.setProcessor(processor);

        new CsvParser(settings).parse(new StringReader("""
                prefixed,plain
                value,second
                """));

        List<ConvertedRecord> records = processor.getBeans();
        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.prefixed).isEqualTo("prefix-value");
            assertThat(record.plain).isEqualTo("SECOND");
        });
    }

    @Test
    void discoversAnnotatedFieldsAndMethods() {
        assertThat(AnnotationHelper.getAllFields(ConvertedRecord.class)).hasSizeGreaterThanOrEqualTo(3);
        assertThat(AnnotationHelper.getAnnotatedFields(ConvertedRecord.class))
                .extracting("name")
                .contains("prefixed", "plain");
        assertThat(AnnotationHelper.getAnnotatedMethods(
                        ConvertedRecord.class, MethodFilter.ONLY_SETTERS))
                .extracting("name")
                .contains("setMethodValue");
    }

    @Test
    void appliesCopiedAnnotationAttributesToFieldSequences() {
        assertThat(AnnotationHelper.getFieldSequence(
                        ComposedRecord.class, false, null, MethodFilter.ONLY_SETTERS))
                .hasSize(1);
    }

    @Test
    void appliesDecimalFormatSettings() {
        DecimalFormat format = new DecimalFormat();

        AnnotationHelper.applyFormatSettings(format, new String[] {"decimalSeparator=,"});

        assertThat(format.getDecimalFormatSymbols().getDecimalSeparator()).isEqualTo(',');
    }

    public static class ConvertedRecord {

        @Parsed(field = "prefixed")
        @Convert(conversionClass = PrefixConversion.class, args = "prefix-")
        public String prefixed;

        @Parsed(field = "plain")
        @Convert(conversionClass = UpperCaseConversion.class)
        public String plain;

        private String methodValue;

        @Parsed(field = "methodValue")
        public void setMethodValue(String value) {
            methodValue = value;
        }
    }

    public static class PrefixConversion implements Conversion<String, String> {

        private final String prefix;

        public PrefixConversion(String[] arguments) {
            prefix = arguments[0];
        }

        @Override
        public String execute(String input) {
            return prefix + input;
        }

        @Override
        public String revert(String output) {
            return output.substring(prefix.length());
        }
    }

    public static class UpperCaseConversion implements Conversion<String, String> {

        @Override
        public String execute(String input) {
            return input.toUpperCase();
        }

        @Override
        public String revert(String output) {
            return output.toLowerCase();
        }
    }

    public static class ComposedRecord {

        @ComposedParsed
        public String value;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @Parsed(field = "value")
    public @interface ComposedParsed {

        @Copy(to = Parsed.class, property = "field")
        String field() default "value";
    }
}
