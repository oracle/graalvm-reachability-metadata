/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import org.junit.jupiter.api.Test;
import org.mongodb.morphia.converters.ClassConverter;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassConverterTest {
    @Test
    public void decodesClassNamesAsClassInstances() {
        final ClassConverter converter = new ClassConverter();

        final Object decoded = converter.decode(Class.class, String.class.getName());

        assertThat(decoded).isSameAs(String.class);
    }

    @Test
    public void encodesClassInstancesAsClassNames() {
        final ClassConverter converter = new ClassConverter();

        final Object encoded = converter.encode(String.class);

        assertThat(encoded).isEqualTo(String.class.getName());
    }
}
