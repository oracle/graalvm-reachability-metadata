/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.pattern.FormattingInfo;
import org.apache.log4j.pattern.PatternConverter;
import org.apache.log4j.pattern.PatternParser;
import org.junit.jupiter.api.Test;

public class PatternParserTest {
    @Test
    void createsCustomConverterThroughStaticFactory() {
        List<PatternConverter> converters = new ArrayList<>();
        List<FormattingInfo> formattingInfos = new ArrayList<>();
        Map<String, Class<?>> rules = new HashMap<>();
        rules.put("custom", FactoryConverter.class);

        PatternParser.parse("%custom{first}{second}", converters, formattingInfos, null, rules);

        assertThat(converters).hasSize(1);
        assertThat(converters.get(0)).isInstanceOf(FactoryConverter.class);
        assertThat(format(converters.get(0))).isEqualTo("factory:first,second");
        assertThat(formattingInfos).hasSize(1);
    }

    @Test
    void fallsBackToDefaultConstructorWhenStaticFactoryIsUnavailable() {
        List<PatternConverter> converters = new ArrayList<>();
        List<FormattingInfo> formattingInfos = new ArrayList<>();
        Map<String, Class<?>> rules = new HashMap<>();
        rules.put("fallback", DefaultConstructorConverter.class);

        PatternParser.parse("%fallback", converters, formattingInfos, null, rules);

        assertThat(converters).hasSize(1);
        assertThat(converters.get(0)).isInstanceOf(DefaultConstructorConverter.class);
        assertThat(format(converters.get(0))).isEqualTo("default-constructor");
        assertThat(formattingInfos).hasSize(1);
    }

    private static String format(PatternConverter converter) {
        StringBuffer buffer = new StringBuffer();
        converter.format(new Object(), buffer);
        return buffer.toString();
    }

    public static final class FactoryConverter extends PatternConverter {
        private final String[] options;

        private FactoryConverter(String[] options) {
            super("factory", "factory");
            this.options = options.clone();
        }

        public static PatternConverter newInstance(String[] options) {
            return new FactoryConverter(options);
        }

        @Override
        public void format(Object obj, StringBuffer toAppendTo) {
            toAppendTo.append("factory:");
            toAppendTo.append(String.join(",", options));
        }
    }

    public static final class DefaultConstructorConverter extends PatternConverter {
        public DefaultConstructorConverter() {
            super("defaultConstructor", "default-constructor");
        }

        @Override
        public void format(Object obj, StringBuffer toAppendTo) {
            toAppendTo.append("default-constructor");
        }
    }
}
