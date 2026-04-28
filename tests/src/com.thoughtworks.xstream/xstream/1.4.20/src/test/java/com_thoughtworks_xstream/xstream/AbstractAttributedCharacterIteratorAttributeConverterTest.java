/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import com.thoughtworks.xstream.converters.reflection.AbstractAttributedCharacterIteratorAttributeConverter;

import java.text.AttributedCharacterIterator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractAttributedCharacterIteratorAttributeConverterTest {
    @Test
    void convertsCustomAttributedCharacterIteratorAttributesByName() {
        AbstractAttributedCharacterIteratorAttributeConverter converter =
            new AbstractAttributedCharacterIteratorAttributeConverter(SampleAttribute.class);

        assertThat(converter.canConvert(SampleAttribute.class)).isTrue();
        assertThat(converter.toString(SampleAttribute.PRIMARY)).isEqualTo("primary");
        assertThat(converter.fromString("secondary")).isSameAs(SampleAttribute.SECONDARY);
    }

    public static final class SampleAttribute extends AttributedCharacterIterator.Attribute {
        private static final long serialVersionUID = 1L;

        public static final SampleAttribute PRIMARY = new SampleAttribute("primary");
        public static final SampleAttribute SECONDARY = new SampleAttribute("secondary");

        private SampleAttribute(String name) {
            super(name);
        }
    }
}
