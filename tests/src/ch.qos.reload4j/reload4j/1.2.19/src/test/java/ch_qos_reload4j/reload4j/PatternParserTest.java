/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

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
    void createsConverterWithStaticFactoryMethodAndOptions() {
        Map<String, Class<?>> converterRegistry = new HashMap<>();
        converterRegistry.put("factory", FactoryMethodPatternConverter.class);
        List<PatternConverter> patternConverters = new ArrayList<>();
        List<Object> formattingInfos = new ArrayList<>();

        PatternParser.parse(
                "%factory{alpha}{beta}",
                patternConverters,
                formattingInfos,
                converterRegistry,
                PatternParser.getPatternLayoutRules());

        assertThat(patternConverters).hasSize(1);
        assertThat(formattingInfos).hasSize(1);
        assertThat(patternConverters.get(0)).isInstanceOf(FactoryMethodPatternConverter.class);

        StringBuffer formatted = new java.lang.StringBuffer();
        patternConverters.get(0).format("ignored", formatted);
        assertThat(formatted).hasToString("factory:alpha,beta");
    }

    @Test
    void fallsBackToPublicDefaultConstructorWhenFactoryMethodIsAbsent() {
        Map<String, Class<?>> converterRegistry = new HashMap<>();
        converterRegistry.put("fallback", DefaultConstructorPatternConverter.class);
        List<PatternConverter> patternConverters = new ArrayList<>();
        List<Object> formattingInfos = new ArrayList<>();

        PatternParser.parse(
                "%fallback",
                patternConverters,
                formattingInfos,
                converterRegistry,
                PatternParser.getPatternLayoutRules());

        assertThat(patternConverters).hasSize(1);
        assertThat(formattingInfos).hasSize(1);
        assertThat(patternConverters.get(0)).isInstanceOf(DefaultConstructorPatternConverter.class);

        StringBuffer formatted = new java.lang.StringBuffer();
        patternConverters.get(0).format("ignored", formatted);
        assertThat(formatted).hasToString("default-constructor");
    }

    public static final class FactoryMethodPatternConverter extends PatternConverter {
        private final String[] options;

        private FactoryMethodPatternConverter(String[] options) {
            super("factory", "factory");
            this.options = options.clone();
        }

        public static FactoryMethodPatternConverter newInstance(String[] options) {
            return new FactoryMethodPatternConverter(options);
        }

        @Override
        public void format(Object obj, StringBuffer toAppendTo) {
            toAppendTo.append("factory:");
            for (int i = 0; i < options.length; i++) {
                if (i > 0) {
                    toAppendTo.append(',');
                }
                toAppendTo.append(options[i]);
            }
        }
    }

    public static final class DefaultConstructorPatternConverter extends PatternConverter {
        public DefaultConstructorPatternConverter() {
            super("fallback", "fallback");
        }

        @Override
        public void format(Object obj, StringBuffer toAppendTo) {
            toAppendTo.append("default-constructor");
        }
    }
}
