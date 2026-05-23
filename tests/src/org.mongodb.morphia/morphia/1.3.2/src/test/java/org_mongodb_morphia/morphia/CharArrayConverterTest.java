/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mongodb.morphia.converters.CharArrayConverter;

public class CharArrayConverterTest {
    @Test
    void decodesStringAsWrapperCharacterArray() {
        CharArrayConverter converter = new CharArrayConverter();

        Object decoded = converter.decode(Character[].class, "morphia");

        assertThat(decoded).isInstanceOf(Character[].class);
        assertThat((Character[]) decoded).containsExactly('m', 'o', 'r', 'p', 'h', 'i', 'a');
    }
}
