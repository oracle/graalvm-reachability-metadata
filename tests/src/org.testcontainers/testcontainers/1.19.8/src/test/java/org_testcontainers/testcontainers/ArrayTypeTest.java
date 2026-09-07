/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.type.ArrayType;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.type.TypeFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayTypeTest {
    @Test
    void changesTheComponentTypeAndBuildsTheCorrespondingArrayClass() {
        TypeFactory factory = TypeFactory.defaultInstance();
        ArrayType strings = factory.constructArrayType(String.class);
        ArrayType integers = strings.withContentType(factory.constructType(Integer.class));

        assertThat(integers.getRawClass()).isEqualTo(Integer[].class);
        assertThat(integers.getContentType().getRawClass()).isEqualTo(Integer.class);
    }
}
