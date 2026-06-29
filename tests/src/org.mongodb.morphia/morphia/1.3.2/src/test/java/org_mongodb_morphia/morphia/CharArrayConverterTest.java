/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import org.junit.jupiter.api.Test;
import org.mongodb.morphia.converters.CharArrayConverter;

import static org.assertj.core.api.Assertions.assertThat;

public class CharArrayConverterTest {
    @Test
    public void decodesTextAsWrapperCharacterArrays() {
        final CharArrayConverter converter = new CharArrayConverter();

        final Object decoded = converter.decode(Character[].class, "Morphia");

        assertThat(decoded).isInstanceOf(Character[].class);
        assertThat((Character[]) decoded).containsExactly('M', 'o', 'r', 'p', 'h', 'i', 'a');
    }

    @Test
    public void decodesTextAsPrimitiveCharacterArrays() {
        final CharArrayConverter converter = new CharArrayConverter();

        final Object decoded = converter.decode(char[].class, "ODM");

        assertThat(decoded).isInstanceOf(char[].class);
        assertThat((char[]) decoded).containsExactly('O', 'D', 'M');
    }

    @Test
    public void encodesWrapperCharacterArraysAsText() {
        final CharArrayConverter converter = new CharArrayConverter();

        final Object encoded = converter.encode(new Character[] {'o', 'd', 'm'});

        assertThat(encoded).isEqualTo("odm");
    }
}
