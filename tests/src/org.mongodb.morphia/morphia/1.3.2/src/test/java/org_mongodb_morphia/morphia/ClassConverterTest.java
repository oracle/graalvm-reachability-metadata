/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mongodb.morphia.converters.ClassConverter;

public class ClassConverterTest {
    @Test
    void decodesClassNameAsClass() {
        ClassConverter converter = new ClassConverter();

        Object decoded = converter.decode(Class.class, getClass().getName());

        assertThat(decoded).isEqualTo(getClass());
    }

    @Test
    void encodesClassAsClassName() {
        ClassConverter converter = new ClassConverter();

        Object encoded = converter.encode(ClassConverterTest.class);

        assertThat(encoded).isEqualTo(ClassConverterTest.class.getName());
    }
}
