/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.pattern.FormattingInfo;
import org.apache.log4j.pattern.PatternConverter;
import org.apache.log4j.pattern.PatternParser;
import org.junit.jupiter.api.Test;

public class PatternParserTest {
    @Test
    void createsConverterThroughPublicNewInstanceFactory() {
        FactoryCreatedConverter.lastOptions = null;
        List<PatternConverter> converters = new ArrayList<>();
        List<FormattingInfo> formattingInfos = new ArrayList<>();

        PatternParser.parse(
                "%factory{first}{second}",
                converters,
                formattingInfos,
                Map.of("factory", FactoryCreatedConverter.class),
                null);

        assertThat(converters).hasSize(1);
        assertThat(formattingInfos).hasSize(1);
        assertThat(FactoryCreatedConverter.lastOptions).containsExactly("first", "second");
        assertThat(format(converters.get(0), "message")).isEqualTo("factory:first,second:message");
    }

    @Test
    void fallsBackToPublicDefaultConstructorWhenFactoryMethodIsUnavailable() {
        DefaultConstructorConverter.constructorInvocations = 0;
        List<PatternConverter> converters = new ArrayList<>();
        List<FormattingInfo> formattingInfos = new ArrayList<>();

        PatternParser.parse(
                "%defaultCtor",
                converters,
                formattingInfos,
                Map.of("defaultCtor", DefaultConstructorConverter.class),
                null);

        assertThat(converters).hasSize(1);
        assertThat(formattingInfos).hasSize(1);
        assertThat(DefaultConstructorConverter.constructorInvocations).isEqualTo(1);
        assertThat(format(converters.get(0), "message")).isEqualTo("default:message");
    }

    private static String format(PatternConverter converter, Object event) {
        StringBuffer buffer = new StringBuffer();
        converter.format(event, buffer);
        return buffer.toString();
    }

    public static class FactoryCreatedConverter extends PatternConverter {
        static String[] lastOptions;

        private final String[] options;

        private FactoryCreatedConverter(String[] options) {
            super("factory", "factory");
            this.options = options;
        }

        public static PatternConverter newInstance(String[] options) {
            lastOptions = options;
            return new FactoryCreatedConverter(options);
        }

        @Override
        public void format(Object obj, StringBuffer toAppendTo) {
            toAppendTo.append("factory:");
            toAppendTo.append(String.join(",", options));
            toAppendTo.append(':');
            toAppendTo.append(obj);
        }
    }

    public static class DefaultConstructorConverter extends PatternConverter {
        static int constructorInvocations;

        public DefaultConstructorConverter() {
            super("default", "default");
            constructorInvocations++;
        }

        @Override
        public void format(Object obj, StringBuffer toAppendTo) {
            toAppendTo.append("default:");
            toAppendTo.append(obj);
        }
    }
}
