/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.pattern.PatternConverter;
import org.apache.log4j.pattern.PatternParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PatternParserTest {

    @Test
    void createsConvertersThroughTheirStaticFactoryMethod() {
        Map<String, Class<?>> converterRegistry = new HashMap<String, Class<?>>();
        converterRegistry.put("factory", FactoryPatternConverter.class);

        PatternConverter converter = parseSingleConverter("%factory{custom-value}", converterRegistry);

        assertThat(format(converter)).isEqualTo("factory:custom-value");
        assertThat(PatternParser.getPatternLayoutRules()).containsKey("message");
    }

    @Test
    void fallsBackToTheDefaultConstructorWhenNoStaticFactoryMethodExists() {
        Map<String, Class<?>> converterRegistry = new HashMap<String, Class<?>>();
        converterRegistry.put("default", DefaultConstructorPatternConverter.class);

        PatternConverter converter = parseSingleConverter("%default", converterRegistry);

        assertThat(format(converter)).isEqualTo("default-constructor");
    }

    private static PatternConverter parseSingleConverter(String pattern, Map<String, Class<?>> converterRegistry) {
        List<Object> converters = new ArrayList<Object>();
        List<Object> formattingInfos = new ArrayList<Object>();

        PatternParser.parse(pattern, converters, formattingInfos, converterRegistry, PatternParser.getPatternLayoutRules());

        assertThat(converters).hasSize(1);
        assertThat(formattingInfos).hasSize(1);
        assertThat(converters.get(0)).isInstanceOf(PatternConverter.class);
        return (PatternConverter) converters.get(0);
    }

    private static String format(PatternConverter converter) {
        StringBuffer output = new java.lang.StringBuffer();
        converter.format(new Object(), output);
        return output.toString();
    }

    public static final class FactoryPatternConverter extends PatternConverter {
        private final String value;

        private FactoryPatternConverter(String value) {
            super("factory", "factory");
            this.value = value;
        }

        public static FactoryPatternConverter newInstance(String[] options) {
            String configuredValue = options.length > 0 ? options[0] : "missing";
            return new FactoryPatternConverter("factory:" + configuredValue);
        }

        @Override
        public void format(Object obj, StringBuffer toAppendTo) {
            toAppendTo.append(value);
        }
    }

    public static final class DefaultConstructorPatternConverter extends PatternConverter {

        public DefaultConstructorPatternConverter() {
            super("default", "default");
        }

        @Override
        public void format(Object obj, StringBuffer toAppendTo) {
            toAppendTo.append("default-constructor");
        }
    }
}
