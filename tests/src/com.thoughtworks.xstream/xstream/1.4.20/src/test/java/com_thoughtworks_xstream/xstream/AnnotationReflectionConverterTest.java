/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.AnnotationProvider;
import com.thoughtworks.xstream.annotations.AnnotationReflectionConverter;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import com.thoughtworks.xstream.converters.basic.StringConverter;
import com.thoughtworks.xstream.converters.collections.CharArrayConverter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationReflectionConverterTest {
    @Test
    @SuppressWarnings("deprecation")
    void createsAnnotatedSingleValueAndFullConvertersForFields() {
        XStream xstream = new XStream();
        xstream.allowTypes(new Class[]{AnnotatedFields.class, char[].class});
        xstream.alias("annotated-fields", AnnotatedFields.class);
        xstream.registerConverter(new AnnotationReflectionConverter(
            xstream.getMapper(),
            xstream.getReflectionProvider(),
            new AnnotationProvider()), XStream.PRIORITY_VERY_HIGH);

        String xml = xstream.toXML(new AnnotatedFields("xstream", "native".toCharArray()));

        assertThat(xml).contains("<name>xstream</name>");
        assertThat(xml).contains("<letters>native</letters>");
    }

    public static final class AnnotatedFields {
        @XStreamConverter(StringConverter.class)
        private String name;

        @XStreamConverter(CharArrayConverter.class)
        private char[] letters;

        AnnotatedFields(String name, char[] letters) {
            this.name = name;
            this.letters = letters;
        }
    }
}
